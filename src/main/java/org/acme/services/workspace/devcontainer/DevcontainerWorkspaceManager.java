package org.acme.services.workspace.devcontainer;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.acme.services.codeagent.CodingAgentType;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@WorkspaceManagerType(type = ExecutionMode.DOCKER)
@ApplicationScoped
public class DevcontainerWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = Logger.getLogger(DevcontainerWorkspaceManager.class);

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance devcontainer(
                String image, String remoteUser, String workspaceFolder,
                List<EnvVar> envVars, List<String> mounts,
                String postCreateCommand, String postStartCommand,
                List<Integer> appPort);
    }

    public record EnvVar(String name, String value) {
    }

    @ConfigProperty(name = "tsd-agent.coding-agent")
    CodingAgentType codingAgentType;

    @ConfigProperty(name = "tsd-agent.devcontainer.command")
    public String command;

    @ConfigProperty(name = "tsd-agent.devcontainer.image")
    public String image;

    @ConfigProperty(name = "tsd-agent.devcontainer.container-runtime")
    String containerRuntime;

    @ConfigProperty(name = "tsd-agent.devcontainer.remote-user")
    String remoteUserConfig;

    @ConfigProperty(name = "tsd-agent.devcontainer.claude.post-create-command")
    Optional<String> claudePostCreateCommand;

    @ConfigProperty(name = "tsd-agent.devcontainer.claude.env-passthrough")
    Optional<List<String>> claudeEnvPassthrough;

    @ConfigProperty(name = "tsd-agent.devcontainer.claude.mounts")
    Optional<Map<String, String>> claudeMounts;

    @ConfigProperty(name = "tsd-agent.devcontainer.opencode.post-create-command")
    Optional<String> opencodePostCreateCommand;

    @ConfigProperty(name = "tsd-agent.devcontainer.opencode.env-passthrough")
    Optional<List<String>> opencodeEnvPassthrough;

    @ConfigProperty(name = "tsd-agent.devcontainer.opencode.mounts")
    Optional<Map<String, String>> opencodeMounts;

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
        Workspace fsWorkspace = filesystemManager.provision(request);
        String worktreePath = fsWorkspace.id();
        String sanitizedUrl = deriveSanitizedUrl(worktreePath);
        String worktreeAlias = Path.of(worktreePath).getFileName().toString();

        Path overrideConfigPath = generateOverrideConfig(sanitizedUrl, worktreeAlias);

        String output = runDevcontainerUp(worktreePath, overrideConfigPath.toString(), outputConsumer);
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

            // Check if project has its own devcontainer config
            Path worktreePath = Path.of(baseDir, "repositories", sanitizedUrl, "trees", worktreeAlias);
            boolean hasProjectConfig = hasProjectDevcontainerConfig(worktreePath);
            boolean isComposeConfig = hasProjectConfig && isDockerComposeConfig(worktreePath);

            // If project has a non-compose config, don't set image (let project's config win)
            boolean useProjectImage = hasProjectConfig && !isComposeConfig;
            if (useProjectImage) {
                LOG.infof("Project has devcontainer config, merging with agent overrides");
            }
            if (isComposeConfig) {
                LOG.infof("Project uses docker-compose devcontainer, generating config from scratch");
            }

            String effectiveImage = useProjectImage ? null : image;
            String remoteUser = remoteUserConfig;
            String postCreateCommand;
            String postStartCommand = null;
            List<Integer> appPort = null;
            Optional<List<String>> envPassthroughNames;
            Optional<Map<String, String>> agentMounts;

            switch (codingAgentType) {
                case CLAUDE -> {
                    postCreateCommand = claudePostCreateCommand.orElseThrow();
                    envPassthroughNames = claudeEnvPassthrough;
                    agentMounts = claudeMounts;
                }
                case OPENCODE -> {
                    postCreateCommand = opencodePostCreateCommand.orElseThrow();
                    envPassthroughNames = opencodeEnvPassthrough;
                    agentMounts = opencodeMounts;
                    int openCodePort = portAllocator.allocate(worktreeAlias);
                    postStartCommand = "/home/vscode/.opencode/bin/opencode serve --port " + openCodePort + " --hostname 0.0.0.0 > /tmp/opencode-server.log 2>&1 & while ! curl -s http://localhost:" + openCodePort + " > /dev/null 2>&1; do sleep 1; done";
                    appPort = List.of(openCodePort);
                }
                default -> throw new WorkspaceException("Unsupported coding agent: " + codingAgentType);
            }

            List<EnvVar> envVars = new java.util.ArrayList<>(envPassthroughNames.orElse(List.of()).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(name -> new EnvVar(name, "${localEnv:" + name + "}"))
                    .toList());
            envVars.add(new EnvVar("DEVCONTAINER", "true"));

            List<String> mountList = new java.util.ArrayList<>(agentMounts.orElse(Map.of()).values().stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());

            mountList.add("source=code-agent-config-" + worktreeAlias + ",target=/home/" + remoteUser + "/" + codingAgentType.configDir + ",type=volume");

            String configContent = Templates.devcontainer(
                    effectiveImage, remoteUser, "/workspaces/trees/" + worktreeAlias,
                    envVars, mountList.isEmpty() ? null : mountList,
                    postCreateCommand, postStartCommand, appPort
            ).render();

            Path configPath = configDir.resolve("devcontainer.json");
            Files.writeString(configPath, configContent);
            LOG.debugf("Generated override config at %s", configPath);
            return configPath;
        } catch (Exception e) {
            throw new WorkspaceException("Failed to generate devcontainer override config", e);
        }
    }

    private boolean hasProjectDevcontainerConfig(Path worktreePath) {
        return Files.exists(worktreePath.resolve(".devcontainer/devcontainer.json"))
                || Files.exists(worktreePath.resolve(".devcontainer.json"));
    }

    private boolean isDockerComposeConfig(Path worktreePath) {
        for (Path configFile : List.of(
                worktreePath.resolve(".devcontainer/devcontainer.json"),
                worktreePath.resolve(".devcontainer.json"))) {
            if (Files.exists(configFile)) {
                try {
                    String content = Files.readString(configFile);
                    JsonObject json = Json.createReader(new StringReader(content)).readObject();
                    return json.containsKey("dockerComposeFile");
                } catch (Exception e) {
                    LOG.warnf("Failed to parse project devcontainer config: %s", e.getMessage());
                }
            }
        }
        return false;
    }

    private String runDevcontainerUp(String workspaceFolder, String overrideConfigPath, Consumer<String> outputConsumer) throws WorkspaceException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    command, "up",
                    "--workspace-folder", workspaceFolder,
                    "--override-config", overrideConfigPath,
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
