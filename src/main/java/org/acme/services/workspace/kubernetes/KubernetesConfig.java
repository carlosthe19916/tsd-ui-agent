package org.acme.services.workspace.kubernetes;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class KubernetesConfig {

    @ConfigProperty(name = "tsd-agent.kubernetes.command", defaultValue = "kubectl")
    public String command;

    @ConfigProperty(name = "tsd-agent.kubernetes.namespace", defaultValue = "tsd-agent-workspaces")
    public String namespace;

    @ConfigProperty(name = "tsd-agent.kubernetes.image", defaultValue = "mcr.microsoft.com/devcontainers/base:ubuntu")
    public String image;

    @ConfigProperty(name = "tsd-agent.kubernetes.storage-class")
    public Optional<String> storageClass;

    @ConfigProperty(name = "tsd-agent.kubernetes.storage-size", defaultValue = "5Gi")
    public String storageSize;

    @ConfigProperty(name = "tsd-agent.kubernetes.working-dir", defaultValue = "/workspace")
    public String workingDir;

    @ConfigProperty(name = "tsd-agent.kubernetes.env-passthrough", defaultValue = "ANTHROPIC_API_KEY")
    public Optional<List<String>> envPassthrough;

    @ConfigProperty(name = "tsd-agent.kubernetes.service-account")
    public Optional<String> serviceAccount;
}
