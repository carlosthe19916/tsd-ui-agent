package org.acme.services.workspace;

import java.util.Map;

public record WorkspaceRequest(
        String mainClonePath,
        String branchAlias,
        Map<String, String> environment
) {
    public WorkspaceRequest(String mainClonePath, String branchAlias) {
        this(mainClonePath, branchAlias, Map.of());
    }
}
