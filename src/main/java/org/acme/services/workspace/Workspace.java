package org.acme.services.workspace;

import java.util.function.Consumer;

public interface Workspace {

    String id();

    String workingDirectory();

    boolean isAlive();

    String exec(String... command) throws WorkspaceException;

    void execStreaming(Consumer<String> lineConsumer, String... command) throws WorkspaceException;

    String execWithStdin(byte[] stdin, String... command) throws WorkspaceException;

    void execWithStdinStreaming(byte[] stdin, Consumer<String> lineConsumer, String... command) throws WorkspaceException;
}
