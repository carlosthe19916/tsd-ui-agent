package org.acme.services.workspace;

import java.util.Map;

public record WorkspaceRequest(
        String gitUrl,
        String gitBranch,
        String gitToken,
        String forkUrl,
        Map<String, String> environment
) {
    public WorkspaceRequest(String gitUrl, String gitBranch, String gitToken, String forkUrl) {
        this(gitUrl, gitBranch, gitToken, forkUrl, Map.of());
    }
}
