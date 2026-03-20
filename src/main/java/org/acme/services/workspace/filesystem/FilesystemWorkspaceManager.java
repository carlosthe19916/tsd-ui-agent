package org.acme.services.workspace.filesystem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.git.GitManager;
import org.acme.services.workspace.*;

import java.nio.file.Files;
import java.nio.file.Path;

@WorkspaceManagerType(type = ExecutionMode.FILESYSTEM)
@ApplicationScoped
public class FilesystemWorkspaceManager implements WorkspaceManager {

    @Inject
    GitManager gitManager;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String worktreePath = gitManager.addWorktree(request.mainClonePath(), request.branchAlias());
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
        // Worktree removal requires knowing the main clone path.
        // For now, this is a no-op; cleanup is handled via GitManager externally.
    }

    @Override
    public boolean exists(String workspaceId) {
        return Files.isDirectory(Path.of(workspaceId));
    }
}
