package org.acme.services.workspace.filesystem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.git.GitManager;
import org.acme.services.workspace.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@WorkspaceManagerType(type = ExecutionMode.FILESYSTEM)
@ApplicationScoped
public class FilesystemWorkspaceManager implements WorkspaceManager {

    @Inject
    GitManager gitManager;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String sanitized = GitManager.sanitizeUrl(request.gitUrl());
        String cloneDir = Path.of(baseDir, "repositories", sanitized, "default").toString();

        if (!Files.isDirectory(Path.of(cloneDir))) {
            gitManager.cloneRepository(request.gitUrl(), request.gitBranch(), cloneDir, request.gitToken());
            if (request.forkUrl() != null && !request.forkUrl().isBlank()) {
                gitManager.addForkRemote(cloneDir, request.forkUrl());
            }
        } else {
            String branch = request.gitBranch() != null && !request.gitBranch().isBlank() ? request.gitBranch() : null;
            gitManager.pullRepository(cloneDir, branch, request.gitToken());
        }

        String alias = UUID.randomUUID().toString().substring(0, 8);
        String worktreePath = gitManager.addWorktree(cloneDir, alias);
        return new FilesystemWorkspace(worktreePath);
    }

    @Override
    public Workspace reconnect(String workspaceId) throws WorkspaceException {
        if (!Files.isDirectory(Path.of(workspaceId))) {
            throw new WorkspaceException("Workspace directory does not exist: " + workspaceId);
        }
        return new FilesystemWorkspace(workspaceId);
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

    @Override
    public boolean exists(String workspaceId) {
        return Files.isDirectory(Path.of(workspaceId));
    }

    @Override
    public WorkspaceHealthStatus healthStatus(String workspaceId) {
        if (Files.isDirectory(Path.of(workspaceId))) {
            return WorkspaceHealthStatus.running();
        }
        return WorkspaceHealthStatus.error("Directory does not exist: " + workspaceId);
    }

    @Override
    public List<WorkspaceCommand> commands(String workspaceId) {
        return List.of(new WorkspaceCommand(WorkspaceCommandType.NAVIGATE, "cd " + workspaceId));
    }
}
