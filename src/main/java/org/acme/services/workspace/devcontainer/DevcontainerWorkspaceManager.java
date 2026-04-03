package org.acme.services.workspace.devcontainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.acme.services.codeagent.CodingAgentType;
import org.acme.services.devcontainer.DevcontainerSpec;
import org.acme.services.git.GitManager;

import org.acme.services.workspace.*;
import org.acme.services.workspace.filesystem.FilesystemWorkspaceManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@WorkspaceManagerType(type = ExecutionMode.DOCKER)
@ApplicationScoped
public class DevcontainerWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = Logger.getLogger(DevcontainerWorkspaceManager.class);

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "tsd-agent.coding-agent")
    CodingAgentType codingAgentType;

    @ConfigProperty(name = "tsd-agent.devcontainer.command")
    public String command;

    @ConfigProperty(name = "tsd-agent.devcontainer.image")
    String image;

    @ConfigProperty(name = "tsd-agent.devcontainer.container-runtime")
    String containerRuntime;

    @ConfigProperty(name = "tsd-agent.devcontainer.remote-user")
    String remoteUserConfig;

    @Inject
    PortAllocator portAllocator;

    @Inject
    @WorkspaceManagerType(type = ExecutionMode.FILESYSTEM)
    FilesystemWorkspaceManager filesystemManager;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        return provision(request, line -> {});
    }

    @Override
    public Workspace provision(WorkspaceRequest request, Consumer<String> outputConsumer) throws WorkspaceException {
        String worktreePath = filesystemManager.createWorktree(request);
        filesystemManager.installAgentConfig(request, worktreePath);
        String sanitizedUrl = deriveSanitizedUrl(worktreePath);
        String branchDir = GitManager.branchDir(request.gitBranch());
        String worktreeAlias = Path.of(worktreePath).getFileName().toString();

        boolean hasProjectConfig = hasProjectDevcontainerConfig(Path.of(worktreePath));
        Path configPath = patchBaseConfig(sanitizedUrl, branchDir, worktreeAlias);

        String output = runDevcontainerUp(worktreePath, configPath.toString(), hasProjectConfig, outputConsumer);
        DevcontainerUpResult result = parseDevcontainerUpOutput(output, worktreeAlias);
        String containerId = result.containerId() != null ? result.containerId() : "unknown";
        String remoteWorkspaceFolder = result.remoteWorkspaceFolder();

        LOG.infof("Devcontainer provisioned: container=%s, worktree=%s, remote=%s", containerId, worktreePath, remoteWorkspaceFolder);
        return createWorkspace(containerId, worktreePath, remoteWorkspaceFolder);
    }

    @Override
    public Optional<Workspace> getWorkspace(String workspaceId) {
        String containerId = parseContainerId(workspaceId);
        String worktreePath = parseWorktreePath(workspaceId);

        if (!Files.isDirectory(Path.of(worktreePath))) {
            return Optional.empty();
        }

        String worktreeAlias = Path.of(worktreePath).getFileName().toString();
        String remoteWorkspaceFolder = "/workspaces/trees/" + worktreeAlias;

        DevcontainerWorkspace workspace = createWorkspace(containerId, worktreePath, remoteWorkspaceFolder);

        return Optional.of(workspace);
    }

    @Override
    public void destroy(String workspaceId) throws WorkspaceException {
        try {
            String containerId = parseContainerId(workspaceId);
            String worktreePath = parseWorktreePath(workspaceId);

            // Release allocated port
            String worktreeAlias = Path.of(worktreePath).getFileName().toString();
            portAllocator.release(worktreeAlias);

            if (containerId == null || containerId.isBlank() || "unknown".equals(containerId)) {
                return;
            }

            // Inspect container to get image info BEFORE removal
            String imageId = null;
            try {
                imageId = DevcontainerWorkspace.runContainerCommand(containerRuntime, "inspect", "--format", "{{.Image}}", containerId).trim();
            } catch (Exception e) {
                LOG.warnf("Failed to inspect container %s: %s", containerId, e.getMessage());
            }

            // Remove container
            LOG.infof("Removing container %s", containerId);
            DevcontainerWorkspace.runContainerCommand(containerRuntime, "rm", "-f", containerId);

            // Best-effort removal of derived devcontainer image
            if (imageId != null) {
                removeDevcontainerImage(imageId);
            }

            // Best-effort removal of per-workspace config volume
            String volumeName = "code-agent-config-" + worktreeAlias;
            try {
                DevcontainerWorkspace.runContainerCommand(containerRuntime, "volume", "rm", volumeName);
                LOG.infof("Removed volume %s", volumeName);
            } catch (Exception e) {
                LOG.warnf("Failed to remove volume %s: %s", volumeName, e.getMessage());
            }
        } catch (Exception e) {
            throw new WorkspaceException("Failed to destroy devcontainer workspace: " + workspaceId, e);
        }
    }

    private DevcontainerWorkspace createWorkspace(String containerId, String worktreePath, String remoteWorkspaceFolder) {
        return new DevcontainerWorkspace(containerId, worktreePath, remoteWorkspaceFolder,
                command, containerRuntime, remoteUserConfig, codingAgentType, portAllocator);
    }

    private static String parseContainerId(String workspaceId) {
        int colonIdx = workspaceId.indexOf(':');
        return colonIdx >= 0 ? workspaceId.substring(0, colonIdx) : workspaceId;
    }

    private static String parseWorktreePath(String workspaceId) {
        int colonIdx = workspaceId.indexOf(':');
        return colonIdx >= 0 ? workspaceId.substring(colonIdx + 1) : workspaceId;
    }

    private Path devcontainerConfigDir(String sanitizedUrl, String branchDir, String alias) {
        return Path.of(baseDir, "devcontainers", sanitizedUrl, branchDir, alias);
    }

    private String deriveSanitizedUrl(String worktreePath) {
        // Worktree path: {baseDir}/repositories/{sanitizedUrl}/trees/{alias}
        return Path.of(worktreePath).getParent().getParent().getFileName().toString();
    }

    private Path patchBaseConfig(String sanitizedUrl, String branchDir, String worktreeAlias) throws WorkspaceException {
        try {
            Path baseConfigPath = Path.of(baseDir, "devcontainers", sanitizedUrl, branchDir, "devcontainer.json");
            if (!Files.exists(baseConfigPath)) {
                throw new WorkspaceException("Base devcontainer config not found at " + baseConfigPath
                        + ". Was the git repository provisioned?");
            }

            String baseContent = Files.readString(baseConfigPath);

            // Patch workspace-specific values: replace placeholder alias "default" with actual worktree alias
            String patched = baseContent
                    .replace("/workspaces/trees/default", "/workspaces/trees/" + worktreeAlias)
                    .replace("code-agent-config-default", "code-agent-config-" + worktreeAlias);

            DevcontainerSpec config = objectMapper.readValue(patched, DevcontainerSpec.class);

            // For OPENCODE: allocate port and add workspace-specific postStartCommand and appPort
            if (codingAgentType == CodingAgentType.OPENCODE) {
                int openCodePort = portAllocator.allocate(worktreeAlias);
                config.postStartCommand = "/home/vscode/.opencode/bin/opencode serve --port " + openCodePort + " --hostname 0.0.0.0 > /tmp/opencode-server.log 2>&1 & while ! curl -s http://localhost:" + openCodePort + " > /dev/null 2>&1; do sleep 1; done";
                config.waitFor = "postStartCommand";
                config.appPort = List.of(openCodePort);
            }

            patched = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);

            // Write per-workspace config
            Path wsConfigDir = devcontainerConfigDir(sanitizedUrl, branchDir, worktreeAlias);
            Files.createDirectories(wsConfigDir);
            Path wsConfigPath = wsConfigDir.resolve("devcontainer.json");
            Files.writeString(wsConfigPath, patched);
            LOG.debugf("Patched devcontainer config at %s", wsConfigPath);
            return wsConfigPath;
        } catch (WorkspaceException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkspaceException("Failed to patch devcontainer config for workspace", e);
        }
    }

    private boolean hasProjectDevcontainerConfig(Path worktreePath) {
        return Files.exists(worktreePath.resolve(".devcontainer/devcontainer.json"))
                || Files.exists(worktreePath.resolve(".devcontainer.json"));
    }

    private String runDevcontainerUp(String workspaceFolder, String configPath,
            boolean hasProjectConfig, Consumer<String> outputConsumer) throws WorkspaceException {
        try {
            String configFlag = hasProjectConfig ? "--override-config" : "--config";
            ProcessBuilder pb = new ProcessBuilder(
                    command, "up",
                    "--workspace-folder", workspaceFolder,
                    configFlag, configPath,
                    "--remove-existing-container",
                    "--mount-git-worktree-common-dir")
                    .redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    outputConsumer.accept(line);
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new WorkspaceException("devcontainer up timed out for: " + workspaceFolder);
            }

            int exitCode = process.exitValue();
            String result = output.toString();
            if (exitCode != 0) {
                throw new WorkspaceException("devcontainer up failed (exit " + exitCode + "): " + result);
            }

            LOG.debugf("devcontainer up output: %s", result);
            return result;
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("devcontainer up interrupted", e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to run devcontainer up: " + e.getMessage(), e);
        }
    }

    private record DevcontainerUpResult(String containerId, String remoteWorkspaceFolder) {
    }

    private DevcontainerUpResult parseDevcontainerUpOutput(String output, String folderName) {
        try {
            String[] lines = output.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.startsWith("{")) {
                    JsonObject json = Json.createReader(new StringReader(line)).readObject();
                    String containerId = json.containsKey("containerId") ? json.getString("containerId") : null;
                    String remoteFolder = json.containsKey("remoteWorkspaceFolder")
                            ? json.getString("remoteWorkspaceFolder")
                            : "/workspaces/" + folderName;
                    return new DevcontainerUpResult(containerId, remoteFolder);
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to parse devcontainer up output: %s", e.getMessage());
        }
        return new DevcontainerUpResult(null, "/workspaces/" + folderName);
    }

    private void removeDevcontainerImage(String imageId) {
        try {
            String repoTags = DevcontainerWorkspace.runContainerCommand(containerRuntime, "image", "inspect", "--format", "{{.RepoTags}}", imageId).trim();
            if (repoTags.contains(image)) {
                LOG.debugf("Skipping image cleanup: %s is the configured base image", image);
                return;
            }
            LOG.infof("Removing devcontainer image %s", imageId);
            DevcontainerWorkspace.runContainerCommand(containerRuntime, "rmi", imageId);
        } catch (Exception e) {
            LOG.warnf("Failed to remove devcontainer image %s: %s", imageId, e.getMessage());
        }
    }
}
