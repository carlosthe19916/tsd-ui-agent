package org.acme.services.workspace;

import java.util.Map;

public record WorkspaceRequest(
        String mainClonePath,
        String branchAlias,
        Map<String, String> environment,
        String gitUrl,
        String gitBranch,
        String gitToken,
        String forkUrl
) {
    public WorkspaceRequest(String mainClonePath, String branchAlias) {
        this(mainClonePath, branchAlias, Map.of(), null, null, null, null);
    }

    public WorkspaceRequest(String mainClonePath, String branchAlias, Map<String, String> environment) {
        this(mainClonePath, branchAlias, environment, null, null, null, null);
    }

    public static WorkspaceRequest forRemoteClone(String branchAlias, String gitUrl,
            String gitBranch, String gitToken, String forkUrl) {
        return new WorkspaceRequest(null, branchAlias, Map.of(), gitUrl, gitBranch, gitToken, forkUrl);
    }
}
