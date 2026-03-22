package org.acme.services.workspace.devcontainer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
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
import java.util.stream.Collectors;

@WorkspaceManagerType(type = ExecutionMode.DOCKER)
@ApplicationScoped
public class DevcontainerWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = Logger.getLogger(DevcontainerWorkspaceManager.class);

    @ConfigProperty(name = "tsd-agent.devcontainer.command")
    public String command;

    @ConfigProperty(name = "tsd-agent.devcontainer.image")
    public String image;

    @ConfigProperty(name = "tsd-agent.devcontainer.env-passthrough")
    public Optional<List<String>> envPassthrough;

    @Inject
    DockerClient dockerClient;

    @Inject
    @WorkspaceManagerType(type = ExecutionMode.FILESYSTEM)
    FilesystemWorkspaceManager filesystemManager;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        Workspace fsWorkspace = filesystemManager.provision(request);
        String worktreePath = fsWorkspace.id();
        String sanitizedUrl = deriveSanitizedUrl(worktreePath);
        String worktreeAlias = Path.of(worktreePath).getFileName().toString();

        Path overrideConfigPath = generateOverrideConfig(sanitizedUrl, worktreeAlias);

        String output = runDevcontainerUp(worktreePath, overrideConfigPath.toString());
        DevcontainerUpResult result = parseDevcontainerUpOutput(output, worktreeAlias);
        String containerId = result.containerId() != null ? result.containerId() : "unknown";
        String remoteWorkspaceFolder = result.remoteWorkspaceFolder();

        LOG.infof("Devcontainer provisioned: container=%s, worktree=%s, remote=%s", containerId, worktreePath, remoteWorkspaceFolder);
        return new DevcontainerWorkspace(containerId, worktreePath, remoteWorkspaceFolder, command);
    }

    @Override
    public Workspace reconnect(String workspaceId) throws WorkspaceException {
        String containerId = parseContainerId(workspaceId);
        String worktreePath = parseWorktreePath(workspaceId);

        if (!Files.isDirectory(Path.of(worktreePath))) {
            throw new WorkspaceException("Workspace directory does not exist: " + worktreePath);
        }

        String sanitizedUrl = deriveSanitizedUrl(worktreePath);
        String worktreeAlias = Path.of(worktreePath).getFileName().toString();
        String remoteWorkspaceFolder = "/workspaces/trees/" + worktreeAlias;

        if (!isContainerRunning(worktreePath)) {
            LOG.infof("Container not running for %s, starting devcontainer up", worktreePath);
            Path configPath = devcontainerConfigPath(sanitizedUrl, worktreeAlias);

            if (!Files.exists(configPath)) {
                generateOverrideConfig(sanitizedUrl, worktreeAlias);
            }

            String output = runDevcontainerUp(worktreePath, configPath.toString());
            DevcontainerUpResult result = parseDevcontainerUpOutput(output, worktreeAlias);
            containerId = result.containerId() != null ? result.containerId() : "unknown";
            remoteWorkspaceFolder = result.remoteWorkspaceFolder();
        }

        return new DevcontainerWorkspace(containerId, worktreePath, remoteWorkspaceFolder, command);
    }

    @Override
    public void destroy(String workspaceId) throws WorkspaceException {
        try {
            String containerId = parseContainerId(workspaceId);
            if (containerId == null || containerId.isBlank() || "unknown".equals(containerId)) {
                return;
            }

            // Inspect container to get image info BEFORE removal
            String imageId = null;
            try {
                InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
                imageId = info.getImageId();
            } catch (Exception e) {
                LOG.warnf("Failed to inspect container %s: %s", containerId, e.getMessage());
            }

            // Remove container
            LOG.infof("Removing container %s", containerId);
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();

            // Best-effort removal of derived devcontainer image
            if (imageId != null) {
                removeDevcontainerImage(imageId);
            }
        } catch (Exception e) {
            throw new WorkspaceException("Failed to destroy devcontainer workspace: " + workspaceId, e);
        }
    }

    @Override
    public boolean exists(String workspaceId) {
        String worktreePath = parseWorktreePath(workspaceId);
        return Files.isDirectory(Path.of(worktreePath));
    }

    @Override
    public WorkspaceHealthStatus healthStatus(String workspaceId) {
        String containerId = parseContainerId(workspaceId);
        if (containerId == null || containerId.isBlank() || "unknown".equals(containerId)) {
            return WorkspaceHealthStatus.error("No container ID");
        }
        try {
            InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = info.getState();
            if (Boolean.TRUE.equals(state.getRunning())) {
                return WorkspaceHealthStatus.running();
            }
            return WorkspaceHealthStatus.stopped("Container is stopped");
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            return WorkspaceHealthStatus.error("Container does not exist");
        } catch (Exception e) {
            return WorkspaceHealthStatus.error(e.getMessage());
        }
    }

    private static String parseContainerId(String workspaceId) {
        int colonIdx = workspaceId.indexOf(':');
        return colonIdx >= 0 ? workspaceId.substring(0, colonIdx) : workspaceId;
    }

    private static String parseWorktreePath(String workspaceId) {
        int colonIdx = workspaceId.indexOf(':');
        return colonIdx >= 0 ? workspaceId.substring(colonIdx + 1) : workspaceId;
    }

    private Path devcontainerConfigDir(String sanitizedUrl, String alias) {
        return Path.of(baseDir, "devcontainers", sanitizedUrl, alias);
    }

    private Path devcontainerConfigPath(String sanitizedUrl, String alias) {
        return devcontainerConfigDir(sanitizedUrl, alias).resolve("devcontainer.json");
    }

    private String deriveSanitizedUrl(String worktreePath) {
        // Worktree path: {baseDir}/repositories/{sanitizedUrl}/trees/{alias}
        return Path.of(worktreePath).getParent().getParent().getFileName().toString();
    }

    private Path generateOverrideConfig(String sanitizedUrl, String worktreeAlias) throws WorkspaceException {
        try {
            Path configDir = devcontainerConfigDir(sanitizedUrl, worktreeAlias);
            Files.createDirectories(configDir);

            JsonObjectBuilder envBuilder = Json.createObjectBuilder();
            List<String> envVars = envPassthrough.orElse(List.of());
            for (String envVar : envVars) {
                String trimmed = envVar.trim();
                if (!trimmed.isEmpty()) {
                    envBuilder.add(trimmed, "${localEnv:" + trimmed + "}");
                }
            }

            JsonObject configJson = Json.createObjectBuilder()
                    .add("image", image)
                    .add("workspaceFolder", "/workspaces/trees/" + worktreeAlias)
                    .add("containerEnv", envBuilder)
                    .build();

            Path configPath = configDir.resolve("devcontainer.json");
            Files.writeString(configPath, configJson.toString());
            LOG.debugf("Generated override config at %s", configPath);
            return configPath;
        } catch (Exception e) {
            throw new WorkspaceException("Failed to generate devcontainer override config", e);
        }
    }

    private String runDevcontainerUp(String workspaceFolder, String overrideConfigPath) throws WorkspaceException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    command, "up",
                    "--workspace-folder", workspaceFolder,
                    "--override-config", overrideConfigPath,
                    "--mount-git-worktree-common-dir")
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
                throw new WorkspaceException("devcontainer up timed out for: " + workspaceFolder);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkspaceException("devcontainer up failed (exit " + exitCode + "): " + output);
            }

            LOG.debugf("devcontainer up output: %s", output);
            return output;
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("devcontainer up interrupted", e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to run devcontainer up: " + e.getMessage(), e);
        }
    }

    private record DevcontainerUpResult(String containerId, String remoteWorkspaceFolder) {}

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

    private boolean isContainerRunning(String workspaceId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    command, "exec", "--workspace-folder", workspaceId, "true")
                    .redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }


    private void removeDevcontainerImage(String imageId) {
        try {
            InspectImageResponse imageInfo = dockerClient.inspectImageCmd(imageId).exec();
            List<String> repoTags = imageInfo.getRepoTags();
            if (repoTags != null && repoTags.contains(image)) {
                LOG.debugf("Skipping image cleanup: %s is the configured base image", image);
                return;
            }
            LOG.infof("Removing devcontainer image %s", imageId);
            dockerClient.removeImageCmd(imageId).exec();
        } catch (Exception e) {
            LOG.warnf("Failed to remove devcontainer image %s: %s", imageId, e.getMessage());
        }
    }
}
