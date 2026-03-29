package org.acme.services.workspace.filesystem;

import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceCommand;
import org.acme.services.workspace.WorkspaceCommandType;
import org.acme.services.workspace.WorkspaceException;
import org.acme.services.workspace.WorkspaceHealthStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class FilesystemWorkspace implements Workspace {

    private final String directory;

    public FilesystemWorkspace(String directory) {
        this.directory = directory;
    }

    @Override
    public String id() {
        return directory;
    }

    @Override
    public String workingDirectory() {
        return directory;
    }

    @Override
    public String exec(String... command) throws WorkspaceException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(new java.io.File(directory))
                    .redirectErrorStream(true);
            Process process = pb.start();
            process.getOutputStream().close();
            AtomicBoolean timedOut = new AtomicBoolean(false);
            Thread watchdog = startWatchdog(process, timedOut);

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            watchdog.join();
            if (timedOut.get()) {
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
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(new java.io.File(directory))
                    .redirectErrorStream(true);
            Process process = pb.start();
            process.getOutputStream().close();
            AtomicBoolean timedOut = new AtomicBoolean(false);
            Thread watchdog = startWatchdog(process, timedOut);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            }

            watchdog.join();
            if (timedOut.get()) {
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
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(new java.io.File(directory))
                    .redirectErrorStream(true);
            Process process = pb.start();
            AtomicBoolean timedOut = new AtomicBoolean(false);
            Thread watchdog = startWatchdog(process, timedOut);

            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin);
            }

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            watchdog.join();
            if (timedOut.get()) {
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
    public WorkspaceHealthStatus healthStatus() {
        if (Files.isDirectory(Path.of(directory))) {
            return WorkspaceHealthStatus.running();
        }
        return WorkspaceHealthStatus.error("Directory does not exist: " + directory);
    }

    @Override
    public List<WorkspaceCommand> commands() {
        return List.of(
                new WorkspaceCommand(WorkspaceCommandType.NAVIGATE, "cd " + directory),
                new WorkspaceCommand(WorkspaceCommandType.VSCODE, "code " + directory)
        );
    }

    @Override
    public void execWithStdinStreaming(byte[] stdin, Consumer<String> lineConsumer, String... command)
            throws WorkspaceException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(new java.io.File(directory))
                    .redirectErrorStream(true);
            Process process = pb.start();
            AtomicBoolean timedOut = new AtomicBoolean(false);
            Thread watchdog = startWatchdog(process, timedOut);

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

            watchdog.join();
            if (timedOut.get()) {
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

    private Thread startWatchdog(Process process, AtomicBoolean timedOut) {
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                boolean finished = process.waitFor(30, TimeUnit.MINUTES);
                if (!finished) {
                    timedOut.set(true);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return watchdog;
    }
}
