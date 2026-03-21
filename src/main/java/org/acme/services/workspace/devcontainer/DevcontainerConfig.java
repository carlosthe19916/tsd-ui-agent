package org.acme.services.workspace.devcontainer;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DevcontainerConfig {

    @ConfigProperty(name = "tsd-agent.devcontainer.command", defaultValue = "devcontainer")
    public String command;

    @ConfigProperty(name = "tsd-agent.devcontainer.image", defaultValue = "mcr.microsoft.com/devcontainers/base:ubuntu")
    public String image;

    @ConfigProperty(name = "tsd-agent.devcontainer.override-config-dir", defaultValue = "${HOME}/.tsd-agent-ui/devcontainer-configs")
    public String overrideConfigDir;

    @ConfigProperty(name = "tsd-agent.devcontainer.env-passthrough", defaultValue = "ANTHROPIC_API_KEY")
    public Optional<List<String>> envPassthrough;
}
