package org.acme.services.workspace.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.workspace.ExecutionMode;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.acme.services.workspace.WorkspaceHealthStatus;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.WorkspaceManagerType;
import org.acme.services.workspace.WorkspaceRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;

@WorkspaceManagerType(type = ExecutionMode.KUBERNETES)
@ApplicationScoped
public class KubernetesWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = Logger.getLogger(KubernetesWorkspaceManager.class);
    private static final String CONTAINER_NAME = "workspace";

    private static final CustomResourceDefinitionContext DEV_WORKSPACE_CONTEXT =
            new CustomResourceDefinitionContext.Builder()
                    .withGroup("workspace.devfile.io")
                    .withVersion("v1alpha2")
                    .withPlural("devworkspaces")
                    .withScope("Namespaced")
                    .build();

    private static final CustomResourceDefinitionContext DEV_WORKSPACE_TEMPLATE_CONTEXT =
            new CustomResourceDefinitionContext.Builder()
                    .withGroup("workspace.devfile.io")
                    .withVersion("v1alpha2")
                    .withPlural("devworkspacetemplates")
                    .withScope("Namespaced")
                    .build();

    @ConfigProperty(name = "tsd-agent.kubernetes.namespace")
    public String namespace;

    @ConfigProperty(name = "tsd-agent.kubernetes.image", defaultValue = "mcr.microsoft.com/devcontainers/base:ubuntu")
    public String image;

    @ConfigProperty(name = "tsd-agent.kubernetes.working-dir", defaultValue = "/projects/project")
    public String workingDir;

    @ConfigProperty(name = "tsd-agent.kubernetes.env-passthrough", defaultValue = "ANTHROPIC_API_KEY")
    public Optional<List<String>> envPassthrough;

    @ConfigProperty(name = "tsd-agent.kubernetes.service-account")
    public Optional<String> serviceAccount;

    @ConfigProperty(name = "tsd-agent.kubernetes.routing-class", defaultValue = "che")
    public Optional<String> routingClass;

    @ConfigProperty(name = "tsd-agent.kubernetes.devworkspace-config-namespace", defaultValue = "eclipse-che")
    public Optional<String> devworkspaceConfigNamespace;

    @ConfigProperty(name = "tsd-agent.kubernetes.che-code-image", defaultValue = "quay.io/che-incubator/che-code:latest")
    public String cheCodeImage;

    @ConfigProperty(name = "tsd-agent.kubernetes.che-url")
    public Optional<String> cheUrl;

    @Inject
    KubernetesClient client;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String workspaceName = "tsd-ws-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        ensureNamespace();

        String editorTemplateName = "che-code-" + workspaceName;
        GenericKubernetesResource editorTemplate = buildEditorTemplateResource(workspaceName, editorTemplateName);
        client.genericKubernetesResources(DEV_WORKSPACE_TEMPLATE_CONTEXT)
                .inNamespace(namespace)
                .resource(editorTemplate)
                .serverSideApply();

        GenericKubernetesResource devWorkspace = buildDevWorkspaceResource(workspaceName, editorTemplateName, request);
        client.genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                .inNamespace(namespace)
                .resource(devWorkspace)
                .serverSideApply();

        waitForDevWorkspaceRunning(workspaceName);

        String podName = getDevWorkspacePodName(workspaceName);
        LOG.infof("Eclipse Che workspace provisioned: devworkspace=%s, pod=%s, namespace=%s",
                workspaceName, podName, namespace);

        return new KubernetesWorkspace(workspaceName, podName, namespace,
                CONTAINER_NAME, workingDir, client);
    }

    @Override
    public Workspace reconnect(String workspaceId) throws WorkspaceException {
        String phase = getDevWorkspacePhase(workspaceId);
        if (!"Running".equals(phase)) {
            throw new WorkspaceException("DevWorkspace " + workspaceId + " is not running (phase: " + phase + ")");
        }
        String podName = getDevWorkspacePodName(workspaceId);
        return new KubernetesWorkspace(workspaceId, podName, namespace,
                CONTAINER_NAME, workingDir, client);
    }

    @Override
    public void destroy(String workspaceId) throws WorkspaceException {
        try {
            client.genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                    .inNamespace(namespace)
                    .withName(workspaceId)
                    .delete();
            LOG.infof("Deleted DevWorkspace %s in namespace %s", workspaceId, namespace);
        } catch (Exception e) {
            LOG.warnf("Failed to delete DevWorkspace %s: %s", workspaceId, e.getMessage());
        }
        try {
            client.genericKubernetesResources(DEV_WORKSPACE_TEMPLATE_CONTEXT)
                    .inNamespace(namespace)
                    .withName("che-code-" + workspaceId)
                    .delete();
        } catch (Exception e) {
            LOG.warnf("Failed to delete editor template for %s: %s", workspaceId, e.getMessage());
        }
    }

    @Override
    public boolean exists(String workspaceId) {
        try {
            GenericKubernetesResource resource = client.genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                    .inNamespace(namespace)
                    .withName(workspaceId)
                    .get();
            return resource != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public WorkspaceHealthStatus healthStatus(String workspaceId) {
        try {
            String phase = getDevWorkspacePhase(workspaceId);
            if ("Running".equals(phase)) {
                return WorkspaceHealthStatus.running();
            }
            if ("Failed".equals(phase)) {
                return WorkspaceHealthStatus.error("DevWorkspace phase: Failed");
            }
            return WorkspaceHealthStatus.stopped("DevWorkspace phase: " + phase);
        } catch (Exception e) {
            return WorkspaceHealthStatus.error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    GenericKubernetesResource buildDevWorkspaceResource(String workspaceName, String editorTemplateName,
            WorkspaceRequest request) {
        List<Map<String, Object>> envList = new ArrayList<>();

        List<String> envVars = envPassthrough.orElse(List.of());
        for (String envVar : envVars) {
            String trimmed = envVar.trim();
            if (!trimmed.isEmpty()) {
                String value = System.getenv(trimmed);
                if (value != null) {
                    envList.add(Map.of("name", trimmed, "value", value));
                }
            }
        }

        for (Map.Entry<String, String> entry : request.environment().entrySet()) {
            envList.add(Map.of("name", entry.getKey(), "value", entry.getValue()));
        }

        String gitUrl = request.gitUrl();
        if (request.gitToken() != null && gitUrl != null && gitUrl.startsWith("https://")) {
            gitUrl = gitUrl.replace("https://", "https://oauth2:" + request.gitToken() + "@");
        }

        Map<String, Object> gitRemotes = Map.of("origin", gitUrl);
        Map<String, Object> gitSection = new HashMap<>();
        gitSection.put("remotes", gitRemotes);

        Map<String, Object> project = new HashMap<>();
        project.put("name", "project");
        project.put("git", gitSection);

        if (request.gitBranch() != null && !request.gitBranch().isBlank()) {
            gitSection.put("checkoutFrom", Map.of("revision", request.gitBranch()));
        }

        Map<String, Object> container = new HashMap<>();
        container.put("image", image);
        container.put("env", envList);
        container.put("args", List.of("tail", "-f", "/dev/null"));
        container.put("mountSources", true);

        Map<String, Object> component = Map.of(
                "name", CONTAINER_NAME,
                "container", container
        );

        Map<String, Object> template = new HashMap<>();
        template.put("projects", List.of(project));
        template.put("components", List.of(component));

        devworkspaceConfigNamespace.ifPresent(ns ->
                template.put("attributes", Map.of(
                        "controller.devfile.io/devworkspace-config", Map.of(
                                "name", "devworkspace-config",
                                "namespace", ns
                        )
                ))
        );

        Map<String, Object> spec = new HashMap<>();
        spec.put("started", true);
        spec.put("template", template);
        spec.put("contributions", List.of(
                Map.of("name", "editor", "kubernetes", Map.of("name", editorTemplateName))
        ));
        routingClass.ifPresent(rc -> spec.put("routingClass", rc));

        Map<String, String> annotations = new HashMap<>();
        annotations.put("che.eclipse.org/che-editor", "che-incubator/che-code/latest");

        return new GenericKubernetesResourceBuilder()
                .withApiVersion("workspace.devfile.io/v1alpha2")
                .withKind("DevWorkspace")
                .withNewMetadata()
                    .withName(workspaceName)
                    .withNamespace(namespace)
                    .withAnnotations(annotations)
                    .withLabels(Map.of(
                            "app", "tsd-agent-workspace",
                            "tsd-agent.workspace-id", workspaceName
                    ))
                .endMetadata()
                .withAdditionalProperties(Map.of("spec", spec))
                .build();
    }

    GenericKubernetesResource buildEditorTemplateResource(String workspaceName, String templateName) {
        String dashboardUrl = cheUrl.orElse("");

        List<Map<String, Object>> cheEnv = List.of(
                Map.of("name", "CHE_DASHBOARD_URL", "value", dashboardUrl),
                Map.of("name", "CHE_PLUGIN_REGISTRY_URL", "value", ""),
                Map.of("name", "CHE_PLUGIN_REGISTRY_INTERNAL_URL", "value", ""),
                Map.of("name", "OPENVSX_REGISTRY_URL", "value", "https://open-vsx.org")
        );

        // che-code-injector: init container that copies the editor into the shared volume
        Map<String, Object> injectorContainer = new HashMap<>();
        injectorContainer.put("command", List.of("/entrypoint-init-container.sh"));
        injectorContainer.put("image", cheCodeImage);
        injectorContainer.put("cpuLimit", "500m");
        injectorContainer.put("cpuRequest", "30m");
        injectorContainer.put("memoryLimit", "256Mi");
        injectorContainer.put("memoryRequest", "32Mi");
        injectorContainer.put("sourceMapping", "/projects");
        injectorContainer.put("env", cheEnv);
        injectorContainer.put("volumeMounts", List.of(
                Map.of("name", "checode", "path", "/checode")
        ));

        Map<String, Object> injectorComponent = Map.of(
                "name", "che-code-injector",
                "container", injectorContainer
        );

        // che-code-runtime-description: the runtime container with endpoints
        Map<String, Object> runtimeContainer = new HashMap<>();
        runtimeContainer.put("image", "quay.io/devfile/universal-developer-image:latest");
        runtimeContainer.put("cpuLimit", "500m");
        runtimeContainer.put("cpuRequest", "30m");
        runtimeContainer.put("memoryLimit", "1024Mi");
        runtimeContainer.put("memoryRequest", "256Mi");
        runtimeContainer.put("sourceMapping", "/projects");
        runtimeContainer.put("mountSources", true);
        runtimeContainer.put("env", cheEnv);
        runtimeContainer.put("volumeMounts", List.of(
                Map.of("name", "checode", "path", "/checode")
        ));
        runtimeContainer.put("endpoints", List.of(
                Map.of(
                        "name", "che-code",
                        "targetPort", 3100,
                        "exposure", "public",
                        "protocol", "https",
                        "secure", true,
                        "attributes", Map.of(
                                "type", "main",
                                "cookiesAuthEnabled", true,
                                "discoverable", false,
                                "urlRewriteSupported", true
                        )
                ),
                Map.of(
                        "name", "code-redirect-1",
                        "targetPort", 13131,
                        "exposure", "public",
                        "protocol", "https",
                        "attributes", Map.of("discoverable", false, "urlRewriteSupported", false)
                ),
                Map.of(
                        "name", "code-redirect-2",
                        "targetPort", 13132,
                        "exposure", "public",
                        "protocol", "https",
                        "attributes", Map.of("discoverable", false, "urlRewriteSupported", false)
                ),
                Map.of(
                        "name", "code-redirect-3",
                        "targetPort", 13133,
                        "exposure", "public",
                        "protocol", "https",
                        "attributes", Map.of("discoverable", false, "urlRewriteSupported", false)
                )
        ));

        Map<String, Object> runtimeComponent = Map.of(
                "name", "che-code-runtime-description",
                "attributes", Map.of(
                        "app.kubernetes.io/component", "che-code-runtime",
                        "app.kubernetes.io/part-of", "che-code.eclipse.org",
                        "controller.devfile.io/container-contribution", true
                ),
                "container", runtimeContainer
        );

        // checode shared volume
        Map<String, Object> volumeComponent = Map.of(
                "name", "checode",
                "volume", Map.of()
        );

        Map<String, Object> spec = new HashMap<>();
        spec.put("components", List.of(injectorComponent, runtimeComponent, volumeComponent));
        spec.put("commands", List.of(
                Map.of("id", "init-container-command",
                        "apply", Map.of("component", "che-code-injector")),
                Map.of("id", "init-che-code-command",
                        "exec", Map.of(
                                "commandLine", "nohup /checode/entrypoint-volume.sh > /checode/entrypoint-logs.txt 2>&1 &",
                                "component", "che-code-runtime-description"
                        ))
        ));
        spec.put("events", Map.of(
                "preStart", List.of("init-container-command"),
                "postStart", List.of("init-che-code-command")
        ));

        return new GenericKubernetesResourceBuilder()
                .withApiVersion("workspace.devfile.io/v1alpha2")
                .withKind("DevWorkspaceTemplate")
                .withNewMetadata()
                    .withName(templateName)
                    .withNamespace(namespace)
                    .withAnnotations(Map.of(
                            "che.eclipse.org/components-update-policy", "managed"
                    ))
                .endMetadata()
                .withAdditionalProperties(Map.of("spec", spec))
                .build();
    }

    private void waitForDevWorkspaceRunning(String workspaceName) throws WorkspaceException {
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            String phase = getDevWorkspacePhase(workspaceName);
            if ("Running".equals(phase)) {
                return;
            }
            if ("Failed".equals(phase)) {
                String message = getDevWorkspaceStatusMessage(workspaceName);
                throw new WorkspaceException("DevWorkspace " + workspaceName + " failed to start: " + message);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WorkspaceException("Interrupted while waiting for DevWorkspace", e);
            }
        }
        throw new WorkspaceException("Timed out waiting for DevWorkspace " + workspaceName + " to become Running");
    }

    @SuppressWarnings("unchecked")
    private String getDevWorkspacePhase(String workspaceName) throws WorkspaceException {
        GenericKubernetesResource resource = client.genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                .inNamespace(namespace)
                .withName(workspaceName)
                .get();
        if (resource == null) {
            throw new WorkspaceException("DevWorkspace " + workspaceName + " not found");
        }
        Map<String, Object> status = (Map<String, Object>) resource.getAdditionalProperties().get("status");
        if (status == null) {
            return "Unknown";
        }
        Object phase = status.get("phase");
        return phase != null ? phase.toString() : "Unknown";
    }

    @SuppressWarnings("unchecked")
    private String getDevWorkspaceStatusMessage(String workspaceName) {
        try {
            GenericKubernetesResource resource = client.genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                    .inNamespace(namespace)
                    .withName(workspaceName)
                    .get();
            if (resource == null) return "DevWorkspace not found";
            Map<String, Object> status = (Map<String, Object>) resource.getAdditionalProperties().get("status");
            if (status == null) return "no status available";
            Object message = status.get("message");
            return message != null ? message.toString() : "no message available";
        } catch (Exception e) {
            return "could not retrieve status: " + e.getMessage();
        }
    }

    private String getDevWorkspacePodName(String workspaceName) throws WorkspaceException {
        List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .withLabel("controller.devfile.io/devworkspace_name", workspaceName)
                .list()
                .getItems();
        if (pods.isEmpty()) {
            throw new WorkspaceException("No pod found for DevWorkspace: " + workspaceName);
        }
        return pods.getFirst().getMetadata().getName();
    }

    private void ensureNamespace() {
        Namespace ns = client.namespaces().withName(namespace).get();
        if (ns == null) {
            client.namespaces().resource(
                    new NamespaceBuilder()
                            .withNewMetadata().withName(namespace).endMetadata()
                            .build()
            ).create();
        }
    }
}
