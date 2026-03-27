package org.acme.services.codeagent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import static org.acme.services.ExecutionOutputBroadcaster.Channel;

import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.acme.services.workspace.devcontainer.PortAllocator;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Path;

@ApplicationScoped
@CodingAgentQualifier(CodingAgentType.OPENCODE)
public class OpenCodeService implements CodingAgentService {

    private static final Logger LOG = Logger.getLogger(OpenCodeService.class);

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @Inject
    PortAllocator portAllocator;

    @ConfigProperty(name = "tsd-agent.opencode.command")
    String opencodeCommand;

    @ConfigProperty(name = "tsd-agent.opencode.model")
    String model;

    @Override
    public String generatePlan(Workspace workspace, String requirement, Long taskId) {
        String prompt = PLAN_GENERATION_PROMPT.formatted(requirement);
        String worktreeAlias = Path.of(workspace.workingDirectory()).getFileName().toString();
        int port = portAllocator.allocate(worktreeAlias);

        LOG.infof("Starting OpenCode CLI for plan generation in %s (port %d)", workspace.workingDirectory(), port);

        broadcaster.start(Channel.TASK, taskId);
        try {
            StringBuilder result = new StringBuilder();

            workspace.execStreaming(
                    line -> {
                        LOG.infof("opencode-plan> %s", line);
                        broadcaster.publish(Channel.TASK, taskId, line);
                        result.append(line).append("\n");
                    },
                    opencodeCommand, "run",
                    "--model", model,
                    "--attach", "http://localhost:" + port,
                    prompt
            );

            String resultText = result.toString().trim();
            if (resultText.isBlank()) {
                throw new RuntimeException("OpenCode CLI produced no result");
            }

            return resultText;
        } catch (WorkspaceException e) {
            throw new RuntimeException("OpenCode CLI plan generation failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(Channel.TASK, taskId);
        }
    }

    @Override
    public void executePlan(Workspace workspace, String planText, Long taskId) {
        String worktreeAlias = Path.of(workspace.workingDirectory()).getFileName().toString();
        int port = portAllocator.allocate(worktreeAlias);

        LOG.infof("Task %d: Starting OpenCode CLI for plan execution in %s (port %d)", taskId, workspace.workingDirectory(), port);
        LOG.infof("Task %d: Plan text length: %d chars", taskId, planText.length());

        broadcaster.start(Channel.TASK, taskId);
        try {
            workspace.execStreaming(
                    line -> {
                        LOG.infof("Task %d: opencode> %s", taskId, line);
                        broadcaster.publish(Channel.TASK, taskId, line);
                    },
                    opencodeCommand, "run",
                    "--model", model,
                    "--attach", "http://localhost:" + port,
                    planText
            );
        } catch (WorkspaceException e) {
            throw new RuntimeException("OpenCode CLI plan execution failed: " + e.getMessage(), e);
        } finally {
            broadcaster.complete(Channel.TASK, taskId);
        }
    }
}
