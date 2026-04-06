package org.acme.services.workspace;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface Workspace {

    record TtydInfo(Process process, int port) {}

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

    default TtydInfo startTtyd(String ttydCommand, int port) throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");

        ProcessBuilder pb = new ProcessBuilder(
                ttydCommand, "-W", "--once", "--port", String.valueOf(port),
                "/bin/bash", "-l")
                .directory(new java.io.File(workingDirectory()));
        pb.environment().putAll(env);
        Process process = pb.start();

        waitForPort(port);
        return new TtydInfo(process, port);
    }

    private static void waitForPort(int port) throws IOException {
        for (int i = 0; i < 50; i++) {
            try (Socket socket = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for ttyd to start", ie);
                }
            }
        }
        throw new IOException("ttyd did not start within 5 seconds on port " + port);
    }
}
