package org.acme.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Readiness
@ApplicationScoped
public class ToolAvailabilityHealthCheck implements HealthCheck {

    @ConfigProperty(name = "tsd-agent.devcontainer.container-runtime")
    String containerRuntime;

    @ConfigProperty(name = "tsd-agent.devcontainer.command")
    String devcontainerCommand;

    @Override
    public HealthCheckResponse call() {
        var tools = List.of(containerRuntime, devcontainerCommand, "git");
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Required tools").up();
        for (String tool : tools) {
            try {
                var process = new ProcessBuilder(tool, "--version")
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    String version = new String(process.getInputStream().readAllBytes()).trim();
                    builder.withData(tool, version);
                } else {
                    builder.down().withData(tool, "exited with code " + process.exitValue());
                }
            } catch (Exception e) {
                builder.down().withData(tool, "not found: " + e.getMessage());
            }
        }
        return builder.build();
    }
}
