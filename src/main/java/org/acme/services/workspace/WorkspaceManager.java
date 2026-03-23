package org.acme.services.workspace;

public interface WorkspaceManager {

    Workspace provision(WorkspaceRequest request) throws WorkspaceException;

    Workspace reconnect(String workspaceId) throws WorkspaceException;

    void destroy(String workspaceId) throws WorkspaceException;

    boolean exists(String workspaceId);

    WorkspaceHealthStatus healthStatus(String workspaceId);

    default void start(String workspaceId) throws WorkspaceException {
        throw new UnsupportedOperationException("Start not supported for this workspace type");
    }

    default void stop(String workspaceId) throws WorkspaceException {
        throw new UnsupportedOperationException("Stop not supported for this workspace type");
    }
}
