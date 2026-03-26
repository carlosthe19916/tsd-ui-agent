package org.acme.services.workspace;

import java.util.Optional;
import java.util.function.Consumer;

public interface WorkspaceManager {

    Workspace provision(WorkspaceRequest request) throws WorkspaceException;

    default Workspace provision(WorkspaceRequest request, Consumer<String> outputConsumer) throws WorkspaceException {
        return provision(request);
    }

    Optional<Workspace> getWorkspace(String workspaceId);

    void destroy(String workspaceId) throws WorkspaceException;

    default Optional<String> getConfiguration(String workspaceId) {
        return Optional.empty();
    }
}
