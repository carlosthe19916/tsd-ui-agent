package org.acme.services.workspace;

public interface WorkspaceManager {

    Workspace provision(WorkspaceRequest request) throws WorkspaceException;

    Workspace reconnect(String workspaceId) throws WorkspaceException;

    void destroy(String workspaceId) throws WorkspaceException;

    boolean exists(String workspaceId);

    WorkspaceHealthStatus healthStatus(String workspaceId);
}
