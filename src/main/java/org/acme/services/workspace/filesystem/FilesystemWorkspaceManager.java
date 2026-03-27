package org.acme.services.workspace.filesystem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    @Override
    public Workspace provision(WorkspaceRequest request) throws WorkspaceException {
        String sanitized = GitManager.sanitizeUrl(request.gitUrl());
        String cloneDir = Path.of(baseDir, "repositories", sanitized, "default").toString();

        if (!Files.isDirectory(Path.of(cloneDir))) {
            throw new WorkspaceException("Clone directory not found: " + cloneDir
                    + ". Was the git repository provisioned?");
        }

        String alias = UUID.randomUUID().toString().substring(0, 8);
        String worktreePath = gitManager.addWorktree(cloneDir, alias);
        return new FilesystemWorkspace(worktreePath);
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
