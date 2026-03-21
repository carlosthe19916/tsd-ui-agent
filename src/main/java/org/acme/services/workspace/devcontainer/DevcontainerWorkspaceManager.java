package org.acme.services.workspace.devcontainer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.acme.services.git.GitManager;
import org.acme.services.workspace.*;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@WorkspaceManagerType(type = ExecutionMode.DOCKER)
@ApplicationScoped
public class DevcontainerWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = Logger.getLogger(DevcontainerWorkspaceManager.class);

    @Inject
    GitManager gitManager;

    @Inject
    DevcontainerConfig config;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String clonePath = request.mainClonePath();
        if (clonePath == null) {
            clonePath = gitManager.cloneRepository(request.gitUrl(), request.gitBranch());
            if (request.forkUrl() != null && !request.forkUrl().isBlank()) {
                gitManager.addForkRemote(clonePath, request.forkUrl());
            }
        }
        String worktreePath = gitManager.addWorktree(clonePath, request.branchAlias());
        String folderName = Path.of(worktreePath).getFileName().toString();

        Path overrideConfigPath = generateOverrideConfig(folderName);

        String output = runDevcontainerUp(worktreePath, overrideConfigPath.toString());
        String remoteWorkspaceFolder = parseRemoteWorkspaceFolder(output, folderName);

        LOG.infof("Devcontainer provisioned for %s, remote workspace: %s", worktreePath, remoteWorkspaceFolder);
        return new DevcontainerWorkspace(worktreePath, remoteWorkspaceFolder, config.command);
    }

    @Override
    public Workspace reconnect(String workspaceId) throws WorkspaceException {
        if (!Files.isDirectory(Path.of(workspaceId))) {
            throw new WorkspaceException("Workspace directory does not exist: " + workspaceId);
        }

        String folderName = Path.of(workspaceId).getFileName().toString();
        String remoteWorkspaceFolder = "/workspaces/" + folderName;

        if (!isContainerRunning(workspaceId)) {
            LOG.infof("Container not running for %s, starting devcontainer up", workspaceId);
            Path overrideConfigDir = Path.of(config.overrideConfigDir).resolve(folderName);
            Path overrideConfigPath = overrideConfigDir.resolve("devcontainer.json");

            if (Files.exists(overrideConfigPath)) {
                String output = runDevcontainerUp(workspaceId, overrideConfigPath.toString());
                remoteWorkspaceFolder = parseRemoteWorkspaceFolder(output, folderName);
            } else {
                generateOverrideConfig(folderName);
                String output = runDevcontainerUp(workspaceId, overrideConfigDir.resolve("devcontainer.json").toString());
                remoteWorkspaceFolder = parseRemoteWorkspaceFolder(output, folderName);
            }
        }

        return new DevcontainerWorkspace(workspaceId, remoteWorkspaceFolder, config.command);
    }

    @Override
    public void destroy(String workspaceId) throws WorkspaceException {
        try {
            String containerId = findContainerId(workspaceId);
            if (containerId != null && !containerId.isBlank()) {
                LOG.infof("Removing container %s for workspace %s", containerId, workspaceId);
                ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", containerId)
                        .redirectErrorStream(true);
                Process process = pb.start();
                process.waitFor(30, TimeUnit.SECONDS);
            }

            String folderName = Path.of(workspaceId).getFileName().toString();
            Path overrideConfigDir = Path.of(config.overrideConfigDir).resolve(folderName);
            if (Files.isDirectory(overrideConfigDir)) {
                try (var files = Files.walk(overrideConfigDir)) {
                    files.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception e) {
                                    LOG.warnf("Failed to delete %s: %s", p, e.getMessage());
                                }
                            });
                }
            }
        } catch (Exception e) {
            throw new WorkspaceException("Failed to destroy devcontainer workspace: " + workspaceId, e);
        }
    }

    @Override
    public boolean exists(String workspaceId) {
        return Files.isDirectory(Path.of(workspaceId));
    }

    private Path generateOverrideConfig(String folderName) throws WorkspaceException {
        try {
            Path configDir = Path.of(config.overrideConfigDir).resolve(folderName);
            Files.createDirectories(configDir);

            JsonObjectBuilder envBuilder = Json.createObjectBuilder();
            List<String> envVars = config.envPassthrough.orElse(List.of());
            for (String envVar : envVars) {
                String trimmed = envVar.trim();
                if (!trimmed.isEmpty()) {
                    envBuilder.add(trimmed, "${localEnv:" + trimmed + "}");
                }
            }

            JsonObject configJson = Json.createObjectBuilder()
                    .add("image", config.image)
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
                    config.command, "up",
                    "--workspace-folder", workspaceFolder,
                    "--override-config", overrideConfigPath)
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

    private String parseRemoteWorkspaceFolder(String output, String folderName) {
        try {
            // devcontainer up outputs JSON with remoteWorkspaceFolder
            // The JSON may be on the last line or mixed with other output
            String[] lines = output.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.startsWith("{")) {
                    JsonObject json = Json.createReader(new StringReader(line)).readObject();
                    if (json.containsKey("remoteWorkspaceFolder")) {
                        return json.getString("remoteWorkspaceFolder");
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to parse remoteWorkspaceFolder from devcontainer up output, using default: %s", e.getMessage());
        }
        return "/workspaces/" + folderName;
    }

    private boolean isContainerRunning(String workspaceId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    config.command, "exec", "--workspace-folder", workspaceId, "true")
                    .redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String findContainerId(String workspaceId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-aq",
                    "--filter", "label=devcontainer.local_folder=" + workspaceId)
                    .redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            process.waitFor(30, TimeUnit.SECONDS);
            return output.trim();
        } catch (Exception e) {
            LOG.warnf("Failed to find container ID for %s: %s", workspaceId, e.getMessage());
            return null;
        }
    }
}
