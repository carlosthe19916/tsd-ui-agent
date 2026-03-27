package org.acme.services.codeagent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import static org.acme.services.ExecutionOutputBroadcaster.Channel;

import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
@CodingAgentQualifier(CodingAgentType.CLAUDE)
public class ClaudeCodeService implements CodingAgentService {

    private static final Logger LOG = Logger.getLogger(ClaudeCodeService.class);

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @ConfigProperty(name = "tsd-agent.claude.command")
    String claudeCommand;

    @ConfigProperty(name = "tsd-agent.claude.model")
    String model;

    @Override
    public String generatePlan(Workspace workspace, String requirement, Long taskId) {
        String prompt = PLAN_GENERATION_PROMPT.formatted(requirement);

        LOG.infof("Starting Claude CLI for plan generation in %s", workspace.workingDirectory());

        broadcaster.start(Channel.TASK, taskId);
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringBuilder rawOutput = new StringBuilder();
            String[] resultHolder = {null};

            workspace.execWithStdinStreaming(
                    prompt.getBytes(StandardCharsets.UTF_8),
                    line -> {
                        LOG.infof("claude-plan> %s", line);
                        rawOutput.append(line).append("\n");
                        broadcaster.publish(Channel.TASK, taskId, line);
                        try {
                            JsonNode node = mapper.readTree(line);
                            if ("result".equals(node.path("type").asText())) {
                                resultHolder[0] = node.path("result").asText();
                            }
                        } catch (Exception ignored) {
                        }
                    },
                    claudeCommand, "-p",
                    "--model", model,
                    "--dangerously-skip-permissions",
                    "--verbose",
                    "--output-format", "stream-json"
            );

            String resultText = resultHolder[0];
            if (resultText == null || resultText.isBlank()) {
                throw new RuntimeException("Claude CLI produced no result");
            }

            return resultText.trim();
        } catch (WorkspaceException e) {
            throw new RuntimeException("Claude CLI plan generation failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(Channel.TASK, taskId);
        }
    }

    @Override
    public void executePlan(Workspace workspace, String planText, Long taskId) {
        LOG.infof("Task %d: Starting Claude CLI for plan execution in %s", taskId, workspace.workingDirectory());
        LOG.infof("Task %d: Plan text length: %d chars", taskId, planText.length());

        broadcaster.start(Channel.TASK, taskId);
        try {
            StringBuilder output = new StringBuilder();

            workspace.execWithStdinStreaming(
                    planText.getBytes(StandardCharsets.UTF_8),
                    line -> {
                        LOG.infof("Task %d: claude> %s", taskId, line);
                        broadcaster.publish(Channel.TASK, taskId, line);
                        output.append(line).append("\n");
                    },
                    claudeCommand, "-p",
                    "--model", model,
                    "--dangerously-skip-permissions",
                    "--verbose",
                    "--output-format", "stream-json"
            );
        } catch (WorkspaceException e) {
            throw new RuntimeException("Claude CLI plan execution failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(Channel.TASK, taskId);
        }
    }

    @Override
    public String chat(Workspace workspace, String prompt, Long taskId) {
        LOG.infof("Task %d: Starting Claude CLI chat in %s", taskId, workspace.workingDirectory());

        broadcaster.start(Channel.CHAT, taskId);
        try {
            ObjectMapper mapper = new ObjectMapper();
            String[] resultHolder = {null};

            workspace.execWithStdinStreaming(
                    prompt.getBytes(StandardCharsets.UTF_8),
                    line -> {
                        LOG.infof("Task %d: claude-chat> %s", taskId, line);
                        broadcaster.publish(Channel.CHAT, taskId, line);
                        try {
                            JsonNode node = mapper.readTree(line);
                            if ("result".equals(node.path("type").asText())) {
                                resultHolder[0] = node.path("result").asText();
                            }
                        } catch (Exception ignored) {
                        }
                    },
                    claudeCommand, "-p",
                    "--model", model,
                    "--dangerously-skip-permissions",
                    "--verbose",
                    "--output-format", "stream-json"
            );

            return resultHolder[0] != null ? resultHolder[0].trim() : "Command completed with no text output.";
        } catch (WorkspaceException e) {
            throw new RuntimeException("Claude CLI chat failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(Channel.CHAT, taskId);
        }
    }

    @Override
    public String chatReadOnly(Workspace workspace, String prompt, Long taskId) {
        LOG.infof("Task %d: Starting Claude CLI read-only chat in %s", taskId, workspace.workingDirectory());

        broadcaster.start(Channel.CHAT, taskId);
        try {
            ObjectMapper mapper = new ObjectMapper();
            String[] resultHolder = {null};

            workspace.execWithStdinStreaming(
                    prompt.getBytes(StandardCharsets.UTF_8),
                    line -> {
                        LOG.infof("Task %d: claude-chat-ro> %s", taskId, line);
                        broadcaster.publish(Channel.CHAT, taskId, line);
                        try {
                            JsonNode node = mapper.readTree(line);
                            if ("result".equals(node.path("type").asText())) {
                                resultHolder[0] = node.path("result").asText();
                            }
                        } catch (Exception ignored) {
                        }
                    },
                    claudeCommand, "-p",
                    "--model", model,
                    "--verbose",
                    "--output-format", "stream-json",
                    "--allowedTools", "Read,Glob,Grep,Bash(git log:*),Bash(git diff:*),Bash(git show:*)"
            );

            return resultHolder[0] != null ? resultHolder[0].trim() : "Query completed with no text output.";
        } catch (WorkspaceException e) {
            throw new RuntimeException("Claude CLI read-only chat failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(Channel.CHAT, taskId);
        }
    }
}
