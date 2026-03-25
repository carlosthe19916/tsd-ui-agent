package org.acme.services.workspace;

import java.util.List;
import java.util.function.Consumer;

public interface Workspace {

    String id();

    String workingDirectory();

    boolean isAlive();

    String exec(String... command) throws WorkspaceException;

    void execStreaming(Consumer<String> lineConsumer, String... command) throws WorkspaceException;

    String execWithStdin(byte[] stdin, String... command) throws WorkspaceException;

    void execWithStdinStreaming(byte[] stdin, Consumer<String> lineConsumer, String... command) throws WorkspaceException;

    default WorkspaceHealthStatus healthStatus() {
        if (isAlive()) {
            return WorkspaceHealthStatus.running();
        }
        return WorkspaceHealthStatus.error("Workspace is not alive");
    }

    default void start() throws WorkspaceException {
        throw new UnsupportedOperationException("Start not supported for this workspace type");
    }

    default void stop() throws WorkspaceException {
        throw new UnsupportedOperationException("Stop not supported for this workspace type");
    }

    default List<WorkspaceCommand> commands() {
        return List.of();
    }
}
