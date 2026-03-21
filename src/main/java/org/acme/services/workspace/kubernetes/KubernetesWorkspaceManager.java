package org.acme.services.workspace.kubernetes;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.workspace.ExecutionMode;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.WorkspaceManagerType;
import org.acme.services.workspace.WorkspaceRequest;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@WorkspaceManagerType(type = ExecutionMode.KUBERNETES)
@ApplicationScoped
public class KubernetesWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = Logger.getLogger(KubernetesWorkspaceManager.class);
    private static final String CONTAINER_NAME = "workspace";

    @Inject
    KubernetesConfig config;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String workspaceName = "tsd-ws-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        ensureNamespace();

        String yaml = generateDevWorkspaceYaml(workspaceName, request);
        runKubectlWithStdin(yaml.getBytes(StandardCharsets.UTF_8), "apply", "-f", "-");

        waitForDevWorkspaceRunning(workspaceName);

        String podName = getDevWorkspacePodName(workspaceName);
        LOG.infof("Eclipse Che workspace provisioned: devworkspace=%s, pod=%s, namespace=%s",
                workspaceName, podName, config.namespace);

        return new KubernetesWorkspace(workspaceName, podName, config.namespace,
                CONTAINER_NAME, config.workingDir, config.command);
    }

    @Override
    public Workspace reconnect(String workspaceId) throws WorkspaceException {
        String phase = getDevWorkspacePhase(workspaceId);
        if (!"Running".equals(phase)) {
            throw new WorkspaceException("DevWorkspace " + workspaceId + " is not running (phase: " + phase + ")");
        }
        String podName = getDevWorkspacePodName(workspaceId);
        return new KubernetesWorkspace(workspaceId, podName, config.namespace,
                CONTAINER_NAME, config.workingDir, config.command);
    }

    @Override
    public void destroy(String workspaceId) throws WorkspaceException {
        try {
            runKubectl("delete", "devworkspace", workspaceId, "-n", config.namespace, "--ignore-not-found");
            LOG.infof("Deleted DevWorkspace %s in namespace %s", workspaceId, config.namespace);
        } catch (Exception e) {
            LOG.warnf("Failed to delete DevWorkspace %s: %s", workspaceId, e.getMessage());
        }
    }

    @Override
    public boolean exists(String workspaceId) {
        try {
            runKubectl("get", "devworkspace", workspaceId, "-n", config.namespace);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    String generateDevWorkspaceYaml(String workspaceName, WorkspaceRequest request) {
        StringBuilder envSection = new StringBuilder();

        List<String> envVars = config.envPassthrough.orElse(List.of());
        for (String envVar : envVars) {
            String trimmed = envVar.trim();
            if (!trimmed.isEmpty()) {
                String value = System.getenv(trimmed);
                if (value != null) {
                    envSection.append("        - name: ").append(trimmed).append("\n");
                    envSection.append("          value: \"").append(escapeYamlValue(value)).append("\"\n");
                }
            }
        }

        for (Map.Entry<String, String> entry : request.environment().entrySet()) {
            envSection.append("        - name: ").append(entry.getKey()).append("\n");
            envSection.append("          value: \"").append(escapeYamlValue(entry.getValue())).append("\"\n");
        }

        String gitUrl = request.gitUrl();
        if (request.gitToken() != null && gitUrl != null && gitUrl.startsWith("https://")) {
            gitUrl = gitUrl.replace("https://", "https://oauth2:" + request.gitToken() + "@");
        }

        String revisionSection = (request.gitBranch() != null && !request.gitBranch().isBlank())
                ? "        checkoutFrom:\n          revision: " + request.gitBranch() + "\n"
                : "";

        return """
                apiVersion: workspace.devfile.io/v1alpha2
                kind: DevWorkspace
                metadata:
                  name: %s
                  namespace: %s
                  labels:
                    app: tsd-agent-workspace
                    tsd-agent.workspace-id: %s
                spec:
                  started: true
                  template:
                    projects:
                    - name: project
                      git:
                        remotes:
                          origin: %s
                %s    components:
                    - name: %s
                      container:
                        image: %s
                        env:
                %s""".formatted(
                workspaceName, config.namespace, workspaceName,
                gitUrl,
                revisionSection,
                CONTAINER_NAME, config.image,
                envSection
        );
    }

    private void waitForDevWorkspaceRunning(String workspaceName) throws WorkspaceException {
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            String phase = getDevWorkspacePhase(workspaceName);
            if ("Running".equals(phase)) {
                return;
            }
            if ("Failed".equals(phase)) {
                throw new WorkspaceException("DevWorkspace " + workspaceName + " failed to start");
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

    private String getDevWorkspacePhase(String workspaceName) throws WorkspaceException {
        return runKubectl("get", "devworkspace", workspaceName, "-n", config.namespace,
                "-o", "jsonpath={.status.phase}").trim();
    }

    private String getDevWorkspacePodName(String workspaceName) throws WorkspaceException {
        String podName = runKubectl("get", "pods", "-n", config.namespace,
                "-l", "controller.devfile.io/devworkspace_name=" + workspaceName,
                "-o", "jsonpath={.items[0].metadata.name}").trim();
        if (podName.isEmpty()) {
            throw new WorkspaceException("No pod found for DevWorkspace: " + workspaceName);
        }
        return podName;
    }

    private void ensureNamespace() throws WorkspaceException {
        try {
            runKubectl("get", "namespace", config.namespace);
        } catch (WorkspaceException e) {
            runKubectl("create", "namespace", config.namespace);
        }
    }

    private String runKubectl(String... args) throws WorkspaceException {
        try {
            List<String> command = new ArrayList<>();
            command.add(config.command);
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new WorkspaceException("kubectl command timed out: " + String.join(" ", args));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkspaceException("kubectl command failed (exit " + exitCode + "): " + output);
            }

            return output;
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("kubectl command interrupted", e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to run kubectl: " + e.getMessage(), e);
        }
    }

    private String runKubectlWithStdin(byte[] stdin, String... args) throws WorkspaceException {
        try {
            List<String> command = new ArrayList<>();
            command.add(config.command);
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectErrorStream(true);
            Process process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin);
            }

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new WorkspaceException("kubectl command timed out: " + String.join(" ", args));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkspaceException("kubectl command failed (exit " + exitCode + "): " + output);
            }

            return output;
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("kubectl command interrupted", e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to run kubectl: " + e.getMessage(), e);
        }
    }

    static String sanitizePodName(String alias) {
        String sanitized = alias.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        String name = "tsd-ws-" + sanitized;
        if (name.length() > 63) {
            name = name.substring(0, 63).replaceAll("-$", "");
        }
        return name;
    }

    private static String escapeYamlValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
