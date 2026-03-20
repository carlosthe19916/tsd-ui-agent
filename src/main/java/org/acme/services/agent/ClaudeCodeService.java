package org.acme.services.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class ClaudeCodeService implements CodingAgentService {

    private static final Logger LOG = Logger.getLogger(ClaudeCodeService.class);

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @ConfigProperty(name = "tsd-agent.claude.command")
    String claudeCommand;

    @Override
    public String generatePlan(Workspace workspace, String requirement, Long taskId) {
        String prompt = """
                Analyze this codebase and generate a detailed implementation plan in Markdown format \
                for the following requirement:

                %s

                Output ONLY the plan in Markdown. Include: Overview, affected files and components, \
                step-by-step implementation instructions, and testing approach.
                """.formatted(requirement);

        LOG.infof("Starting Claude CLI for plan generation in %s", workspace.workingDirectory());

        broadcaster.start(taskId);
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringBuilder rawOutput = new StringBuilder();
            String[] resultHolder = {null};

            workspace.execWithStdinStreaming(
                    prompt.getBytes(StandardCharsets.UTF_8),
                    line -> {
                        LOG.infof("claude-plan> %s", line);
                        rawOutput.append(line).append("\n");
                        broadcaster.publish(taskId, line);
                        try {
                            JsonNode node = mapper.readTree(line);
                            if ("result".equals(node.path("type").asText())) {
                                resultHolder[0] = node.path("result").asText();
                            }
                        } catch (Exception ignored) {
                        }
                    },
                    claudeCommand, "-p",
                    "--permission-mode", "bypassPermissions",
                    "--verbose",
                    "--output-format", "stream-json",
                    "--tools", "Read,Glob,Grep"
            );

            String resultText = resultHolder[0];
            if (resultText == null || resultText.isBlank()) {
                throw new RuntimeException("Claude CLI produced no result");
            }

            return resultText.trim();
        } catch (WorkspaceException e) {
            throw new RuntimeException("Claude CLI plan generation failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(taskId);
        }
    }

    @Override
    public void executePlan(Workspace workspace, String planText, Long taskId) {
        LOG.infof("Task %d: Starting Claude CLI for plan execution in %s", taskId, workspace.workingDirectory());
        LOG.infof("Task %d: Plan text length: %d chars", taskId, planText.length());

        broadcaster.start(taskId);
        try {
            StringBuilder output = new StringBuilder();

            workspace.execWithStdinStreaming(
                    planText.getBytes(StandardCharsets.UTF_8),
                    line -> {
                        LOG.infof("Task %d: claude> %s", taskId, line);
                        broadcaster.publish(taskId, line);
                        output.append(line).append("\n");
                    },
                    claudeCommand, "-p",
                    "--permission-mode", "bypassPermissions",
                    "--verbose",
                    "--output-format", "stream-json"
            );
        } catch (WorkspaceException e) {
            throw new RuntimeException("Claude CLI plan execution failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(taskId);
        }
    }
}
