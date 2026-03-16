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

@ApplicationScoped
public class WorktreeService {

    private static final Logger LOG = Logger.getLogger(WorktreeService.class);

    @Inject
    GitManager gitManager;

    @ConfigProperty(name = "tsd-agent.vscode.command", defaultValue = "code")
    String vscodeCommand;

    @ConfigProperty(name = "tsd-agent.terminal.command", defaultValue = "ptyxis --new-window -d %s")
    String terminalCommand;

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
}
