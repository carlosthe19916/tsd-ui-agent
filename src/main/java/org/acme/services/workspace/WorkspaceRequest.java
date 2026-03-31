package org.acme.services.workspace;

import java.nio.file.Path;
import java.util.Map;

public record WorkspaceRequest(
        String gitUrl,
        String gitBranch,
        String gitToken,
        String forkUrl,
        Path configRepoPath,
        Map<String, String> environment
) {
    public WorkspaceRequest(String gitUrl, String gitBranch, String gitToken, String forkUrl) {
        this(gitUrl, gitBranch, gitToken, forkUrl, null, Map.of());
    }
}
