package org.acme.services.workspace.filesystem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.git.GitManager;
import org.acme.services.workspace.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
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
            String cloneUrl = request.gitUrl();
            if (request.gitToken() != null && cloneUrl != null && cloneUrl.startsWith("https://")) {
                cloneUrl = cloneUrl.replace("https://", "https://oauth2:" + request.gitToken() + "@");
            }
            gitManager.cloneRepository(cloneUrl, request.gitBranch(), cloneDir);
            if (request.forkUrl() != null && !request.forkUrl().isBlank()) {
                String forkUrl = request.forkUrl();
                if (request.gitToken() != null && forkUrl.startsWith("https://")) {
                    forkUrl = forkUrl.replace("https://", "https://oauth2:" + request.gitToken() + "@");
                }
                gitManager.addForkRemote(cloneDir, forkUrl);
            }
        } else {
            String branch = request.gitBranch() != null && !request.gitBranch().isBlank() ? request.gitBranch() : "HEAD";
            gitManager.pullRepository(cloneDir, branch);
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
}
