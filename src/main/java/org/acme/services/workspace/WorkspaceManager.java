package org.acme.services.workspace;

import java.util.Optional;

public interface WorkspaceManager {

    Workspace provision(WorkspaceRequest request) throws WorkspaceException;

    Optional<Workspace> getWorkspace(String workspaceId);

    void destroy(String workspaceId) throws WorkspaceException;
}
