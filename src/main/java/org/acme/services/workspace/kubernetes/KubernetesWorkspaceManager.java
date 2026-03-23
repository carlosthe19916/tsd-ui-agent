package org.acme.services.workspace.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
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
    private static final String CONTAINER_NAME = "tools";

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

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance devfile(
                String workspaceName, String image,
                List<EnvVar> envVars);

        public static native TemplateInstance devworkspace(
                String workspaceName, String namespace, String editorTemplateName,
                String routingClass, String devworkspaceConfigNamespace,
                String gitUrl, String gitBranch, String image,
                List<EnvVar> envVars, String devfileContent);

        public static native TemplateInstance editorTemplate(
                String templateName, String namespace,
                String cheCodeImage, String dashboardUrl);
    }

    public record EnvVar(String name, String value) {}

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
        GenericKubernetesResource editorTemplate = renderEditorTemplate(workspaceName, editorTemplateName);
        client.genericKubernetesResources(DEV_WORKSPACE_TEMPLATE_CONTEXT)
                .inNamespace(namespace)
                .resource(editorTemplate)
                .serverSideApply();

        GenericKubernetesResource devWorkspace = renderDevWorkspace(workspaceName, editorTemplateName, request);
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
                return WorkspaceHealthStatus.running(true);
            }
            if ("Failed".equals(phase)) {
                return WorkspaceHealthStatus.error("DevWorkspace phase: Failed");
            }
            return WorkspaceHealthStatus.stopped("DevWorkspace phase: " + phase, true);
        } catch (Exception e) {
            return WorkspaceHealthStatus.error(e.getMessage());
        }
    }

    @Override
    public void start(String workspaceId) throws WorkspaceException {
        setDevWorkspaceStarted(workspaceId, true);
    }

    @Override
    public void stop(String workspaceId) throws WorkspaceException {
        setDevWorkspaceStarted(workspaceId, false);
    }

    @SuppressWarnings("unchecked")
    private void setDevWorkspaceStarted(String workspaceName, boolean started) throws WorkspaceException {
        try {
            GenericKubernetesResource resource = client.genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                    .inNamespace(namespace)
                    .withName(workspaceName)
                    .get();
            if (resource == null) {
                throw new WorkspaceException("DevWorkspace " + workspaceName + " not found");
            }
            Map<String, Object> spec = (Map<String, Object>) resource.getAdditionalProperties().get("spec");
            spec.put("started", started);
            client.genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                    .inNamespace(namespace)
                    .resource(resource)
                    .serverSideApply();
        } catch (WorkspaceException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkspaceException("Failed to " + (started ? "start" : "stop") + " DevWorkspace " + workspaceName, e);
        }
    }

    GenericKubernetesResource renderDevWorkspace(String workspaceName, String editorTemplateName,
            WorkspaceRequest request) {
        List<EnvVar> envVars = resolveEnvVars(request);

        String gitUrl = request.gitUrl();
        if (request.gitToken() != null && gitUrl != null && gitUrl.startsWith("https://")) {
            gitUrl = gitUrl.replace("https://", "https://oauth2:" + request.gitToken() + "@");
        }

        String devfileContent = renderDevfile(workspaceName, envVars);

        String yaml = Templates.devworkspace(
                workspaceName, namespace, editorTemplateName,
                routingClass.orElse(null),
                devworkspaceConfigNamespace.orElse(null),
                gitUrl,
                (request.gitBranch() != null && !request.gitBranch().isBlank()) ? request.gitBranch() : null,
                image,
                envVars.isEmpty() ? null : envVars,
                devfileContent
        ).render();

        return Serialization.unmarshal(yaml, GenericKubernetesResource.class);
    }

    String renderDevfile(String workspaceName, List<EnvVar> envVars) {
        String devfile = Templates.devfile(
                workspaceName, image,
                envVars.isEmpty() ? null : envVars
        ).render();

        // Indent each line by 6 spaces for embedding in the YAML block scalar annotation
        return devfile.lines()
                .map(line -> "      " + line)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    GenericKubernetesResource renderEditorTemplate(String workspaceName, String templateName) {
        String yaml = Templates.editorTemplate(
                templateName, namespace,
                cheCodeImage, cheUrl.orElse("")
        ).render();

        return Serialization.unmarshal(yaml, GenericKubernetesResource.class);
    }

    private List<EnvVar> resolveEnvVars(WorkspaceRequest request) {
        List<EnvVar> envVars = new ArrayList<>();
        for (String envVar : envPassthrough.orElse(List.of())) {
            String trimmed = envVar.trim();
            if (!trimmed.isEmpty()) {
                String value = System.getenv(trimmed);
                if (value != null) {
                    envVars.add(new EnvVar(trimmed, value));
                }
            }
        }
        for (Map.Entry<String, String> entry : request.environment().entrySet()) {
            envVars.add(new EnvVar(entry.getKey(), entry.getValue()));
        }
        return envVars;
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
