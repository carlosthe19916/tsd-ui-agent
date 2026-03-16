package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.models.jpa.entity.PlanEntity;
import org.acme.services.git.GitManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@ApplicationScoped
public class WorktreeService {

    private static final Logger LOG = Logger.getLogger(WorktreeService.class);

    @Inject
    GitManager gitManager;

    @ConfigProperty(name = "tsd-agent.vscode.command", defaultValue = "code")
    String vscodeCommand;

    @ConfigProperty(name = "tsd-agent.terminal.command", defaultValue = "ptyxis --new-window -d %s")
    String terminalCommand;

    @ConfigProperty(name = "tsd-agent.terminal.exec-command", defaultValue = "ptyxis --new-window -d %s -- %c")
    String terminalExecCommand;

    @ConfigProperty(name = "tsd-agent.claude.command", defaultValue = "claude")
    String claudeCommand;

    @Transactional
    public String ensureWorktree(PlanEntity plan) {
        if (plan.worktreePath != null && Files.isDirectory(Path.of(plan.worktreePath))) {
            return plan.worktreePath;
        }

        if (plan.worktreePath != null) {
            plan.worktreePath = null;
        }

        String alias = "plan-" + plan.id;
        String sourceBranch = (plan.git.branch == null || plan.git.branch.isBlank()) ? "HEAD" : plan.git.branch;

        String worktreePath = gitManager.addWorktree(plan.git.localPath, alias, sourceBranch);
        plan.worktreePath = worktreePath;
        plan.persist();

        return worktreePath;
    }

    public void openVSCode(String worktreePath) {
        try {
            new ProcessBuilder(vscodeCommand, worktreePath)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open VSCode: " + e.getMessage(), e);
        }
    }

    public void openTerminal(String worktreePath) {
        String resolved = terminalCommand.formatted(worktreePath);
        String[] parts = resolved.split("\\s+");
        try {
            new ProcessBuilder(parts)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open terminal: " + e.getMessage(), e);
        }
    }

    public void openClaude(String worktreePath, Long taskId, String requirement, String planApiUrl) {
        try {
            Path scriptPath = Path.of(worktreePath, ".tsd-claude-plan.sh");
            String script = """
                    #!/bin/bash
                    TASK_URL="%s"
                    echo ""
                    echo "=== Task Requirement ==="
                    cat <<'REQUIREMENT_EOF'
                    %s
                    REQUIREMENT_EOF
                    echo "========================"
                    echo ""
                    echo "Plan API URL: $TASK_URL"
                    echo ""
                    read -p "Should Claude create a plan for this? (yes/no): " confirm
                    if [ "$confirm" = "yes" ]; then
                      %s --permission-mode plan \\
                        --append-system-prompt "Once the plan is ready, ask the user: 'Would you like me to save this plan to the app?' If they confirm, do an HTTP PATCH to $TASK_URL with a JSON body containing the field 'executionPlan' as a plain markdown string." \\
                        "$(cat <<'PROMPT_EOF'
                    %s
                    PROMPT_EOF
                    )"
                    fi
                    """.formatted(planApiUrl, requirement, claudeCommand, requirement);

            Files.writeString(scriptPath, script);
            Files.setPosixFilePermissions(scriptPath, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));

            String resolved = terminalExecCommand
                    .replace("%s", worktreePath)
                    .replace("%c", scriptPath.toString());
            String[] parts = resolved.split("\\s+");
            new ProcessBuilder(parts)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open Claude: " + e.getMessage(), e);
        }
    }
}
