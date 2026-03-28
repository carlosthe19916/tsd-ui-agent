package org.acme.services.workspace;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface Workspace {

    String id();

    String workingDirectory();

    String exec(String... command) throws WorkspaceException;

    void execStreaming(Consumer<String> lineConsumer, String... command) throws WorkspaceException;

    String execWithStdin(byte[] stdin, String... command) throws WorkspaceException;

    void execWithStdinStreaming(byte[] stdin, Consumer<String> lineConsumer, String... command) throws WorkspaceException;

    default WorkspaceHealthStatus healthStatus() {
        return WorkspaceHealthStatus.error("Not implemented");
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

    default PtyProcess createPtyProcess(int cols, int rows) throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        return new PtyProcessBuilder()
                .setCommand(new String[]{"/bin/bash", "-l"})
                .setDirectory(workingDirectory())
                .setEnvironment(env)
                .setInitialColumns(cols)
                .setInitialRows(rows)
                .start();
    }
}
