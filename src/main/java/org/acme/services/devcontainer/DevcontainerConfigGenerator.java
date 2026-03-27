package org.acme.services.devcontainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.codeagent.CodingAgentType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class DevcontainerConfigGenerator {

    private static final Logger LOG = Logger.getLogger(DevcontainerConfigGenerator.class);

    private static final String BASE_ALIAS = "default";

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "tsd-agent.coding-agent")
    CodingAgentType codingAgentType;

    @ConfigProperty(name = "tsd-agent.devcontainer.image")
    String image;

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

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    /**
     * Generates the base devcontainer.json for a git repository.
     * Uses placeholder alias "default" for workspace-specific fields.
     * Called during git provisioning (POST /gits).
     */
    public Path generateBaseConfig(String sanitizedUrl, Path repoPath,
            EnrichmentService.EnrichmentResult enrichment) {
        try {
            Path configDir = Path.of(baseDir, "devcontainers", sanitizedUrl);
            Files.createDirectories(configDir);

            boolean hasProjectConfig = hasProjectDevcontainerConfig(repoPath);
            boolean isComposeConfig = hasProjectConfig && isDockerComposeConfig(repoPath);

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
                }
                default -> throw new RuntimeException("Unsupported coding agent: " + codingAgentType);
            }

            Map<String, String> envVars = new LinkedHashMap<>();
            for (String name : envPassthroughNames.orElse(List.of())) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    envVars.put(trimmed, "${localEnv:" + trimmed + "}");
                }
            }
            envVars.put("DEVCONTAINER", "true");
            if (codingAgentType == CodingAgentType.OPENCODE) {
                envVars.put("OPENCODE_PERMISSION", "{\"tools\":{\"*\":{\"allow\":true}}}");
            }

            List<String> mountList = new ArrayList<>(agentMounts.orElse(Map.of()).values().stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());

            mountList.add("source=code-agent-config-" + BASE_ALIAS + ",target=/home/" + remoteUser + "/" + codingAgentType.configDir + ",type=volume");

            DevcontainerSpec config = new DevcontainerSpec();
            config.image = effectiveImage;
            config.remoteUser = remoteUser;
            config.workspaceFolder = "/workspaces/trees/" + BASE_ALIAS;
            config.runArgs = List.of("--tmpfs=/tmp:rw,exec,nosuid,nodev,mode=1777");
            config.containerEnv = envVars;
            config.mounts = mountList.isEmpty() ? null : mountList;
            config.postCreateCommand = postCreateCommand;

            if (enrichment != null) {
                config.features = enrichment.features();
                config.overrideFeatureInstallOrder = enrichment.featureInstallOrder();
                List<String> vscodeExtensions = enrichment.vscodeExtensions();
                config.setVscodeExtensions(vscodeExtensions != null && !vscodeExtensions.isEmpty() ? vscodeExtensions : null);
            }

            String configContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);

            Path configPath = configDir.resolve("devcontainer.json");
            Files.writeString(configPath, configContent);
            LOG.debugf("Generated base devcontainer config at %s", configPath);
            return configPath;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate base devcontainer config", e);
        }
    }

    private boolean hasProjectDevcontainerConfig(Path repoPath) {
        return Files.exists(repoPath.resolve(".devcontainer/devcontainer.json"))
                || Files.exists(repoPath.resolve(".devcontainer.json"));
    }

    private boolean isDockerComposeConfig(Path repoPath) {
        for (Path configFile : List.of(
                repoPath.resolve(".devcontainer/devcontainer.json"),
                repoPath.resolve(".devcontainer.json"))) {
            if (Files.exists(configFile)) {
                try {
                    String content = Files.readString(configFile);
                    var json = jakarta.json.Json.createReader(new java.io.StringReader(content)).readObject();
                    return json.containsKey("dockerComposeFile");
                } catch (Exception e) {
                    LOG.warnf("Failed to parse project devcontainer config: %s", e.getMessage());
                }
            }
        }
        return false;
    }
}
