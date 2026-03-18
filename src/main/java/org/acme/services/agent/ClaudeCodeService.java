package org.acme.services.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.ExecutionOutputBroadcaster;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ClaudeCodeService implements CodingAgentService {

    private static final Logger LOG = Logger.getLogger(ClaudeCodeService.class);

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @ConfigProperty(name = "tsd-agent.claude.command")
    String claudeCommand;

    @Override
    public String generatePlan(String workdir, String requirement, Long taskId) {
        String prompt = """
                Analyze this codebase and generate a detailed implementation plan in Markdown format \
                for the following requirement:

                %s

                Output ONLY the plan in Markdown. Include: Overview, affected files and components, \
                step-by-step implementation instructions, and testing approach.
                """.formatted(requirement);

        List<String> command = List.of(
                claudeCommand, "-p",
                "--permission-mode", "bypassPermissions",
                "--verbose",
                "--output-format", "stream-json",
                "--tools", "Read,Glob,Grep"
        );

        LOG.infof("Starting Claude CLI for plan generation in %s", workdir);
        LOG.infof("Command: %s", String.join(" ", command));

        broadcaster.start(taskId);
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(new java.io.File(workdir))
                    .redirectErrorStream(true);
            Process process = pb.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            }

            ObjectMapper mapper = new ObjectMapper();
            String resultText = null;
            StringBuilder rawOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.infof("claude-plan> %s", line);
                    rawOutput.append(line).append("\n");
                    broadcaster.publish(taskId, line);
                    try {
                        JsonNode node = mapper.readTree(line);
                        if ("result".equals(node.path("type").asText())) {
                            resultText = node.path("result").asText();
                        }
                    } catch (Exception ignored) {}
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Claude CLI timed out after 30 minutes");
            }

            int exitCode = process.exitValue();
            LOG.infof("Claude CLI plan generation exited with code %d", exitCode);
            if (exitCode != 0) {
                throw new RuntimeException("Claude CLI exited with code " + exitCode + ": " + rawOutput);
            }

            if (resultText == null || resultText.isBlank()) {
                throw new RuntimeException("Claude CLI produced no result");
            }

            return resultText.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Claude CLI plan generation was interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run Claude CLI for plan generation", e);
        } finally {
            broadcaster.complete(taskId);
        }
    }
}
