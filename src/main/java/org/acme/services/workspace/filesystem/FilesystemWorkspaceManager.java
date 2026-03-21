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
        String clonePath = request.mainClonePath();
        if (clonePath == null) {
            clonePath = gitManager.cloneRepository(request.gitUrl(), request.gitBranch());
            if (request.forkUrl() != null && !request.forkUrl().isBlank()) {
                gitManager.addForkRemote(clonePath, request.forkUrl());
            }
        }
        String worktreePath = gitManager.addWorktree(clonePath, request.branchAlias());
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
            // Worktree is at {UUID}/trees/{branch}, clone dir is the {UUID} parent
            Path cloneRoot = worktreePath.getParent().getParent();
            if (Files.isDirectory(cloneRoot)) {
                gitManager.deleteClonedDirectory(cloneRoot.toString());
            }
        } catch (Exception e) {
            throw new WorkspaceException("Failed to clean up workspace: " + workspaceId, e);
        }
    }

    @Override
    public boolean exists(String workspaceId) {
        return Files.isDirectory(Path.of(workspaceId));
    }
}
