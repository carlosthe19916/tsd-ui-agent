package org.acme.services.codeagent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
@CodingAgentQualifier(CodingAgentType.OPENCODE)
public class OpenCodeService implements CodingAgentService {

    private static final Logger LOG = Logger.getLogger(OpenCodeService.class);

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @ConfigProperty(name = "tsd-agent.opencode.command")
    String opencodeCommand;

    @Override
    public String generatePlan(Workspace workspace, String requirement, Long taskId) {
        String prompt = PLAN_GENERATION_PROMPT.formatted(requirement);

        LOG.infof("Starting OpenCode CLI for plan generation in %s", workspace.workingDirectory());

        broadcaster.start(taskId);
        try {
            ObjectMapper mapper = new ObjectMapper();
            String[] resultHolder = {null};

            workspace.execStreaming(
                    line -> {
                        LOG.infof("opencode-plan> %s", line);
                        broadcaster.publish(taskId, line);
                        try {
                            JsonNode node = mapper.readTree(line);
                            if ("message.part.updated".equals(node.path("type").asText())) {
                                JsonNode part = node.path("part");
                                if ("text".equals(part.path("type").asText())) {
                                    resultHolder[0] = part.path("text").asText();
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    },
                    opencodeCommand, "run",
                    "--dangerously-skip-permissions",
                    "--format", "json",
                    "--quiet",
                    prompt
            );

            String resultText = resultHolder[0];
            if (resultText == null || resultText.isBlank()) {
                throw new RuntimeException("OpenCode CLI produced no result");
            }

            return resultText.trim();
        } catch (WorkspaceException e) {
            throw new RuntimeException("OpenCode CLI plan generation failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(taskId);
        }
    }

    @Override
    public void executePlan(Workspace workspace, String planText, Long taskId) {
        LOG.infof("Task %d: Starting OpenCode CLI for plan execution in %s", taskId, workspace.workingDirectory());
        LOG.infof("Task %d: Plan text length: %d chars", taskId, planText.length());

        broadcaster.start(taskId);
        try {
            workspace.execStreaming(
                    line -> {
                        LOG.infof("Task %d: opencode> %s", taskId, line);
                        broadcaster.publish(taskId, line);
                    },
                    opencodeCommand, "run",
                    "--dangerously-skip-permissions",
                    "--format", "json",
                    "--quiet",
                    planText
            );
        } catch (WorkspaceException e) {
            throw new RuntimeException("OpenCode CLI plan execution failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(taskId);
        }
    }
}
