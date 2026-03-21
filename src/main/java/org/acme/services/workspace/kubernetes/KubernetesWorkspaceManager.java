package org.acme.services.workspace.kubernetes;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.git.GitManager;
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
import java.nio.file.Path;
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
    GitManager gitManager;

    @Inject
    KubernetesConfig config;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String worktreePath = gitManager.addWorktree(request.mainClonePath(), request.branchAlias());
        String podName = sanitizePodName(request.branchAlias());

        ensureNamespace();
        createPvc(podName);
        createPod(podName, request.environment());
        waitForReady(podName);
        copyWorktreeContent(worktreePath, podName);

        LOG.infof("Kubernetes workspace provisioned: pod=%s, namespace=%s", podName, config.namespace);
        return new KubernetesWorkspace(podName, config.namespace, CONTAINER_NAME, config.workingDir, config.command);
    }

    @Override
    public Workspace reconnect(String workspaceId) throws WorkspaceException {
        String phase = getPodPhase(workspaceId);
        if (!"Running".equals(phase)) {
            throw new WorkspaceException("Pod " + workspaceId + " is not running (phase: " + phase + ")");
        }
        return new KubernetesWorkspace(workspaceId, config.namespace, CONTAINER_NAME, config.workingDir, config.command);
    }

    @Override
    public void destroy(String workspaceId) throws WorkspaceException {
        try {
            runKubectl("delete", "pod", workspaceId, "-n", config.namespace, "--grace-period=10");
            LOG.infof("Deleted pod %s in namespace %s", workspaceId, config.namespace);
        } catch (Exception e) {
            LOG.warnf("Failed to delete pod %s: %s", workspaceId, e.getMessage());
        }

        try {
            runKubectl("delete", "pvc", workspaceId, "-n", config.namespace);
            LOG.infof("Deleted PVC %s in namespace %s", workspaceId, config.namespace);
        } catch (Exception e) {
            LOG.warnf("Failed to delete PVC %s: %s", workspaceId, e.getMessage());
        }
    }

    @Override
    public boolean exists(String workspaceId) {
        try {
            runKubectl("get", "pod", workspaceId, "-n", config.namespace);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureNamespace() throws WorkspaceException {
        runKubectl("create", "namespace", config.namespace, "--dry-run=client", "-o", "yaml");
        // Pipe the output to apply — simpler to just do two calls
        try {
            runKubectl("get", "namespace", config.namespace);
        } catch (WorkspaceException e) {
            runKubectl("create", "namespace", config.namespace);
        }
    }

    private void createPvc(String pvcName) throws WorkspaceException {
        String yaml = generatePvcYaml(pvcName);
        runKubectlWithStdin(yaml.getBytes(StandardCharsets.UTF_8), "apply", "-f", "-");
        LOG.debugf("Created PVC %s in namespace %s", pvcName, config.namespace);
    }

    private void createPod(String podName, Map<String, String> environment) throws WorkspaceException {
        String yaml = generatePodYaml(podName, environment);
        runKubectlWithStdin(yaml.getBytes(StandardCharsets.UTF_8), "apply", "-f", "-");
        LOG.debugf("Created pod %s in namespace %s", podName, config.namespace);
    }

    private void waitForReady(String podName) throws WorkspaceException {
        runKubectl("wait", "--for=condition=Ready", "pod/" + podName,
                "-n", config.namespace, "--timeout=300s");
    }

    private void copyWorktreeContent(String worktreePath, String podName) throws WorkspaceException {
        runKubectl("cp", worktreePath + "/.", config.namespace + "/" + podName + ":" + config.workingDir,
                "-c", CONTAINER_NAME);
        LOG.debugf("Copied worktree content from %s to pod %s:%s", worktreePath, podName, config.workingDir);
    }

    private String getPodPhase(String podName) throws WorkspaceException {
        return runKubectl("get", "pod", podName, "-n", config.namespace,
                "-o", "jsonpath={.status.phase}").trim();
    }

    String generatePodYaml(String podName, Map<String, String> environment) {
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

        for (Map.Entry<String, String> entry : environment.entrySet()) {
            envSection.append("        - name: ").append(entry.getKey()).append("\n");
            envSection.append("          value: \"").append(escapeYamlValue(entry.getValue())).append("\"\n");
        }

        String serviceAccountLine = config.serviceAccount
                .map(sa -> "  serviceAccountName: " + sa + "\n")
                .orElse("");

        return """
                apiVersion: v1
                kind: Pod
                metadata:
                  name: %s
                  namespace: %s
                  labels:
                    app: tsd-agent-workspace
                    tsd-agent.workspace-id: %s
                spec:
                  %scontainers:
                  - name: %s
                    image: %s
                    workingDir: %s
                    command: ["sleep", "infinity"]
                    env:
                %s    volumeMounts:
                    - name: workspace-storage
                      mountPath: %s
                  volumes:
                  - name: workspace-storage
                    persistentVolumeClaim:
                      claimName: %s
                """.formatted(
                podName, config.namespace, podName,
                serviceAccountLine,
                CONTAINER_NAME, config.image, config.workingDir,
                envSection,
                config.workingDir,
                podName
        );
    }

    String generatePvcYaml(String pvcName) {
        String storageClassLine = config.storageClass
                .map(sc -> "  storageClassName: " + sc + "\n")
                .orElse("");

        return """
                apiVersion: v1
                kind: PersistentVolumeClaim
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  %saccessModes:
                  - ReadWriteOnce
                  resources:
                    requests:
                      storage: %s
                """.formatted(pvcName, config.namespace, storageClassLine, config.storageSize);
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
