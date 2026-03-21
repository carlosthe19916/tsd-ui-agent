package org.acme.services.workspace.kubernetes;

import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KubernetesWorkspace implements Workspace {

    private final String podName;
    private final String namespace;
    private final String containerName;
    private final String containerWorkingDir;
    private final String kubectlCommand;

    public KubernetesWorkspace(String podName, String namespace, String containerName,
            String containerWorkingDir, String kubectlCommand) {
        this.podName = podName;
        this.namespace = namespace;
        this.containerName = containerName;
        this.containerWorkingDir = containerWorkingDir;
        this.kubectlCommand = kubectlCommand;
    }

    @Override
    public String id() {
        return podName;
    }

    @Override
    public String workingDirectory() {
        return containerWorkingDir;
    }

    @Override
    public boolean isAlive() {
        try {
            ProcessBuilder pb = buildExecProcess("true")
                    .redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String exec(String... command) throws WorkspaceException {
        try {
            ProcessBuilder pb = buildExecProcess(command)
                    .redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new WorkspaceException("Command timed out: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkspaceException("Command exited with code " + exitCode + ": " + output);
            }

            return output;
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
            ProcessBuilder pb = buildExecProcess(command)
                    .redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new WorkspaceException("Command timed out: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkspaceException("Command exited with code " + exitCode);
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
            ProcessBuilder pb = buildExecProcess(command)
                    .redirectErrorStream(true);
            Process process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin);
            }

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new WorkspaceException("Command timed out: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkspaceException("Command exited with code " + exitCode + ": " + output);
            }

            return output;
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
            ProcessBuilder pb = buildExecProcess(command)
                    .redirectErrorStream(true);
            Process process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new WorkspaceException("Command timed out: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new WorkspaceException("Command exited with code " + exitCode);
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

    private ProcessBuilder buildExecProcess(String... command) {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(kubectlCommand);
        fullCommand.add("exec");
        fullCommand.add(podName);
        fullCommand.add("-n");
        fullCommand.add(namespace);
        fullCommand.add("-c");
        fullCommand.add(containerName);
        fullCommand.add("--");
        fullCommand.addAll(Arrays.asList(command));
        return new ProcessBuilder(fullCommand);
    }
}
