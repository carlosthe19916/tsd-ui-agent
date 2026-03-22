package org.acme.services.workspace.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class KubernetesWorkspace implements Workspace {

    private final String devWorkspaceName;
    private final String podName;
    private final String namespace;
    private final String containerName;
    private final String containerWorkingDir;
    private final KubernetesClient client;

    public KubernetesWorkspace(String devWorkspaceName, String podName, String namespace,
            String containerName, String containerWorkingDir, KubernetesClient client) {
        this.devWorkspaceName = devWorkspaceName;
        this.podName = podName;
        this.namespace = namespace;
        this.containerName = containerName;
        this.containerWorkingDir = containerWorkingDir;
        this.client = client;
    }

    @Override
    public String id() {
        return devWorkspaceName;
    }

    public String podName() {
        return podName;
    }

    @Override
    public String workingDirectory() {
        return containerWorkingDir;
    }

    @Override
    public boolean isAlive() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CompletableFuture<Integer> exitCode = new CompletableFuture<>();
            try (ExecWatch watch = client.pods()
                    .inNamespace(namespace).withName(podName).inContainer(containerName)
                    .writingOutput(out).writingError(out)
                    .usingListener(exitCodeListener(exitCode))
                    .exec("true")) {
                Integer code = exitCode.get(30, TimeUnit.SECONDS);
                return code == 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String exec(String... command) throws WorkspaceException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            CompletableFuture<Integer> exitCode = new CompletableFuture<>();
            try (ExecWatch watch = client.pods()
                    .inNamespace(namespace).withName(podName).inContainer(containerName)
                    .writingOutput(out).writingError(err)
                    .usingListener(exitCodeListener(exitCode))
                    .exec(command)) {
                Integer code = exitCode.get(30, TimeUnit.MINUTES);
                String output = out.toString(StandardCharsets.UTF_8);
                if (code != 0) {
                    String errorOutput = err.toString(StandardCharsets.UTF_8);
                    String combined = output.isEmpty() ? errorOutput : output + "\n" + errorOutput;
                    throw new WorkspaceException("Command exited with code " + code + ": " + combined);
                }
                return output;
            }
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("Command interrupted: " + String.join(" ", command), e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    @Override
    public void execStreaming(Consumer<String> lineConsumer, String... command) throws WorkspaceException {
        try {
            PipedOutputStream pipedOut = new PipedOutputStream();
            PipedInputStream pipedIn = new PipedInputStream(pipedOut);
            CompletableFuture<Integer> exitCode = new CompletableFuture<>();
            try (ExecWatch watch = client.pods()
                    .inNamespace(namespace).withName(podName).inContainer(containerName)
                    .writingOutput(pipedOut).writingError(pipedOut)
                    .usingListener(exitCodeListener(exitCode))
                    .exec(command);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(pipedIn, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
                Integer code = exitCode.get(30, TimeUnit.MINUTES);
                if (code != 0) {
                    throw new WorkspaceException("Command exited with code " + code);
                }
            }
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("Command interrupted: " + String.join(" ", command), e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    @Override
    public String execWithStdin(byte[] stdin, String... command) throws WorkspaceException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            CompletableFuture<Integer> exitCode = new CompletableFuture<>();
            try (ExecWatch watch = client.pods()
                    .inNamespace(namespace).withName(podName).inContainer(containerName)
                    .redirectingInput()
                    .writingOutput(out).writingError(err)
                    .usingListener(exitCodeListener(exitCode))
                    .exec(command)) {
                try (OutputStream os = watch.getInput()) {
                    os.write(stdin);
                }
                Integer code = exitCode.get(30, TimeUnit.MINUTES);
                String output = out.toString(StandardCharsets.UTF_8);
                if (code != 0) {
                    String errorOutput = err.toString(StandardCharsets.UTF_8);
                    String combined = output.isEmpty() ? errorOutput : output + "\n" + errorOutput;
                    throw new WorkspaceException("Command exited with code " + code + ": " + combined);
                }
                return output;
            }
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("Command interrupted: " + String.join(" ", command), e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    @Override
    public void execWithStdinStreaming(byte[] stdin, Consumer<String> lineConsumer, String... command)
            throws WorkspaceException {
        try {
            PipedOutputStream pipedOut = new PipedOutputStream();
            PipedInputStream pipedIn = new PipedInputStream(pipedOut);
            CompletableFuture<Integer> exitCode = new CompletableFuture<>();
            try (ExecWatch watch = client.pods()
                    .inNamespace(namespace).withName(podName).inContainer(containerName)
                    .redirectingInput()
                    .writingOutput(pipedOut).writingError(pipedOut)
                    .usingListener(exitCodeListener(exitCode))
                    .exec(command)) {
                try (OutputStream os = watch.getInput()) {
                    os.write(stdin);
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(pipedIn, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineConsumer.accept(line);
                    }
                }
                Integer code = exitCode.get(30, TimeUnit.MINUTES);
                if (code != 0) {
                    throw new WorkspaceException("Command exited with code " + code);
                }
            }
        } catch (WorkspaceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("Command interrupted: " + String.join(" ", command), e);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    private static ExecListener exitCodeListener(CompletableFuture<Integer> exitCode) {
        return new ExecListener() {
            @Override
            public void onClose(int code, String reason) {
                exitCode.complete(code);
            }

            @Override
            public void onFailure(Throwable t, Response failureResponse) {
                exitCode.completeExceptionally(t);
            }
        };
    }
}
