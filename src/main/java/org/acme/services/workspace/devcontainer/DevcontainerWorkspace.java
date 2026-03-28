package org.acme.services.workspace.devcontainer;

import org.acme.services.codeagent.CodingAgentType;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceCommand;
import org.acme.services.workspace.WorkspaceCommandType;
import org.acme.services.workspace.WorkspaceException;
import org.acme.services.workspace.WorkspaceHealthStatus;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DevcontainerWorkspace implements Workspace {

    private final String containerId;
    private final String hostWorkspaceFolder;
    private final String containerWorkingDir;
    private final String devcontainerCommand;
    private final String containerRuntime;
    private final String remoteUser;
    private final CodingAgentType codingAgentType;
    private final PortAllocator portAllocator;

    public DevcontainerWorkspace(String containerId, String hostWorkspaceFolder, String containerWorkingDir,
                                 String devcontainerCommand, String containerRuntime, String remoteUser,
                                 CodingAgentType codingAgentType, PortAllocator portAllocator) {
        this.containerId = containerId;
        this.hostWorkspaceFolder = hostWorkspaceFolder;
        this.containerWorkingDir = containerWorkingDir;
        this.devcontainerCommand = devcontainerCommand;
        this.containerRuntime = containerRuntime;
        this.remoteUser = remoteUser;
        this.codingAgentType = codingAgentType;
        this.portAllocator = portAllocator;
    }

    @Override
    public String id() {
        return containerId + ":" + hostWorkspaceFolder;
    }

    public String containerId() {
        return containerId;
    }

    public String hostWorkspaceFolder() {
        return hostWorkspaceFolder;
    }

    @Override
    public String workingDirectory() {
        return containerWorkingDir;
    }

    @Override
    public WorkspaceHealthStatus healthStatus() {
        if (containerId == null || containerId.isBlank() || "unknown".equals(containerId)) {
            return WorkspaceHealthStatus.error("No container ID");
        }
        try {
            String running = runContainerCommand(containerRuntime, "inspect", "--format", "{{.State.Running}}", containerId).trim();
            if ("true".equals(running)) {
                return WorkspaceHealthStatus.running(true);
            }
            return WorkspaceHealthStatus.stopped("Container is stopped", true);
        } catch (Exception e) {
            return WorkspaceHealthStatus.error(e.getMessage());
        }
    }

    @Override
    public void start() throws WorkspaceException {
        try {
            runContainerCommand(containerRuntime, "start", containerId);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to start container " + containerId, e);
        }
    }

    @Override
    public void stop() throws WorkspaceException {
        try {
            runContainerCommand(containerRuntime, "stop", containerId);
        } catch (Exception e) {
            throw new WorkspaceException("Failed to stop container " + containerId, e);
        }
    }

    @Override
    public List<WorkspaceCommand> commands() {
        var commands = new ArrayList<WorkspaceCommand>();

        commands.add(new WorkspaceCommand(WorkspaceCommandType.NAVIGATE, "cd " + hostWorkspaceFolder));

        String worktreeAlias = Path.of(hostWorkspaceFolder).getFileName().toString();
        String shortId = containerId.length() > 12 ? containerId.substring(0, 12) : containerId;
        String hexConfig = java.util.HexFormat.of().formatHex(shortId.getBytes(StandardCharsets.UTF_8));
        commands.add(new WorkspaceCommand(WorkspaceCommandType.VSCODE,
                "code --folder-uri \"vscode-remote://attached-container+%s/workspaces/trees/%s\"".formatted(hexConfig, worktreeAlias)
        ));

        switch (codingAgentType) {
            case CLAUDE -> {
                commands.add(new WorkspaceCommand(WorkspaceCommandType.CONTAINER_EXEC,
                        "%s exec -it --user %s -w /workspaces/trees/%s %s claude".formatted(containerRuntime, remoteUser, worktreeAlias, containerId)
                ));
            }
            case OPENCODE -> {
                int port = portAllocator.allocate(worktreeAlias);
                commands.add(new WorkspaceCommand(WorkspaceCommandType.CONTAINER_EXEC,
                        "%s exec -it --user %s -w /workspaces/trees/%s %s opencode attach http://localhost:%s".formatted(containerRuntime, remoteUser, worktreeAlias, containerId, port)
                ));
                commands.add(new WorkspaceCommand(WorkspaceCommandType.REMOTE_EXEC,
                        "opencode attach http://localhost:%d".formatted(port)
                ));
            }
        }

        return commands;
    }

    @Override
    public PtyProcess createPtyProcess(int cols, int rows) throws IOException {
        return new PtyProcessBuilder()
                .setCommand(new String[]{
                        containerRuntime, "exec", "-it",
                        "--user", remoteUser,
                        "-w", containerWorkingDir,
                        containerId,
                        "/bin/bash"
                })
                .setEnvironment(new HashMap<>(System.getenv()))
                .setInitialColumns(cols)
                .setInitialRows(rows)
                .start();
    }

    @Override
    public String exec(String... command) throws WorkspaceException {
        try {
            ProcessBuilder pb = buildExecProcess(command)
                    .redirectErrorStream(true);
            Process process = pb.start();
            process.getOutputStream().close();

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
            process.getOutputStream().close();

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
    public void execWithStdinStreaming(byte[] stdin, Consumer<String> lineConsumer, String... command) throws WorkspaceException {
        try {
            ProcessBuilder pb = buildExecProcess(command).redirectErrorStream(true);
            Process process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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
        fullCommand.add(containerRuntime);
        fullCommand.add("exec");
        fullCommand.add("-i");
        fullCommand.add("--user");
        fullCommand.add(remoteUser);
        fullCommand.add("-w");
        fullCommand.add(containerWorkingDir);
        fullCommand.add(containerId);
        fullCommand.addAll(Arrays.asList(command));
        return new ProcessBuilder(fullCommand);
    }

    static String runContainerCommand(String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(args).redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out: " + String.join(" ", args));
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command failed (exit " + process.exitValue() + "): " + output);
        }
        return output;
    }
}
