package org.acme.services.workspace.filesystem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.CodeAgentConfigInstaller;
import org.acme.services.git.GitManager;
import org.acme.services.workspace.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@WorkspaceManagerType(type = ExecutionMode.FILESYSTEM)
@ApplicationScoped
public class FilesystemWorkspaceManager implements WorkspaceManager {

    @Inject
    GitManager gitManager;

    @Inject
    CodeAgentConfigInstaller codeAgentConfigInstaller;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String worktreePath = createWorktree(request);
        installAgentConfig(request, worktreePath);
        return new FilesystemWorkspace(worktreePath);
    }

    /**
     * Creates a git worktree from the cloned repository.
     * Pulls latest before creating the worktree to ensure a fresh start.
     */
    public String createWorktree(WorkspaceRequest request) throws WorkspaceException {
        String cloneDir = GitManager.cloneDir(baseDir, request.gitUrl(), request.gitBranch());

        if (!Files.isDirectory(Path.of(cloneDir))) {
            throw new WorkspaceException("Clone directory not found: " + cloneDir
                    + ". Was the git repository provisioned?");
        }

        // Pull latest before creating worktree for a fresh start
        String branch = (request.gitBranch() != null && !request.gitBranch().isBlank()) ? request.gitBranch() : null;
        gitManager.pullRepository(cloneDir, branch, request.gitToken());

        String alias = UUID.randomUUID().toString().substring(0, 8);
        return gitManager.addWorktree(cloneDir, alias);
    }

    /**
     * Installs agent config files from the config repo into the worktree.
     */
    public void installAgentConfig(WorkspaceRequest request, String worktreePath) {
        if (request.configRepoPath() != null) {
            codeAgentConfigInstaller.installConfigFiles(Path.of(worktreePath), request.configRepoPath());
        }
    }

    @Override
    public Optional<Workspace> getWorkspace(String workspaceId) {
        if (!Files.isDirectory(Path.of(workspaceId))) {
            return Optional.empty();
        }
        return Optional.of(new FilesystemWorkspace(workspaceId));
    }

    @Override
    public void destroy(String workspaceId) throws WorkspaceException {
        try {
            Path worktreePath = Path.of(workspaceId);
            if (Files.isDirectory(worktreePath)) {
                gitManager.deleteClonedDirectory(worktreePath.toString());
            }
        } catch (Exception e) {
            throw new WorkspaceException("Failed to clean up workspace: " + workspaceId, e);
        }
    }
}
