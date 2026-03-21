package org.acme.services.workspace.filesystem;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.services.workspace.ExecutionMode;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceToolsService;
import org.acme.services.workspace.WorkspaceToolsServiceType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@WorkspaceToolsServiceType(type = ExecutionMode.FILESYSTEM)
@ApplicationScoped
public class FilesystemWorkspaceToolsService implements WorkspaceToolsService {

    private static final Logger LOG = Logger.getLogger(FilesystemWorkspaceToolsService.class);

    @ConfigProperty(name = "tsd-agent.vscode.command")
    String vscodeCommand;

    @ConfigProperty(name = "tsd-agent.terminal.command")
    String terminalCommand;

    @ConfigProperty(name = "tsd-agent.terminal.exec-command")
    String terminalExecCommand;

    @ConfigProperty(name = "tsd-agent.claude.command")
    String claudeCommand;

    @Override
    public void openIDE(Workspace workspace) {
        try {
            new ProcessBuilder(vscodeCommand, workspace.workingDirectory())
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open VSCode: " + e.getMessage(), e);
        }
    }

    @Override
    public void openTerminal(Workspace workspace) {
        String resolved = terminalCommand.formatted(workspace.workingDirectory());
        String[] parts = resolved.split("\\s+");
        try {
            new ProcessBuilder(parts)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open terminal: " + e.getMessage(), e);
        }
    }

    @Override
    public String openClaude(Workspace workspace, Long taskId, String requirement, String planApiUrl, String existingSessionId) {
        String worktreePath = workspace.workingDirectory();
        try {
            if (existingSessionId != null) {
                Path resumeScriptPath = Files.createTempFile("tsd-claude-resume-", ".sh");
                String resumeScript = """
                        #!/bin/bash
                        %s --resume %s
                        """.formatted(claudeCommand, existingSessionId);
                Files.writeString(resumeScriptPath, resumeScript);
                Files.setPosixFilePermissions(resumeScriptPath, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                ));

                String resolved = terminalExecCommand
                        .replace("%s", worktreePath)
                        .replace("%c", resumeScriptPath.toString());
                String[] parts = resolved.split("\\s+");
                new ProcessBuilder(parts)
                        .inheritIO()
                        .start();
                return existingSessionId;
            }

            String sessionId = java.util.UUID.randomUUID().toString();
            Path scriptPath = Files.createTempFile("tsd-claude-plan-", ".sh");
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
                      %s --session-id %s --permission-mode plan \\
                        --append-system-prompt "Once the plan is ready, ask the user: 'Would you like me to save this plan to the app?' If they confirm, do an HTTP PATCH to $TASK_URL with a JSON body containing the field 'plan' as a plain markdown string." \\
                        "$(cat <<'PROMPT_EOF'
                    %s
                    PROMPT_EOF
                    )"
                    fi
                    """.formatted(planApiUrl, requirement, claudeCommand, sessionId, requirement);

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
            return sessionId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open Claude: " + e.getMessage(), e);
        }
    }
}
