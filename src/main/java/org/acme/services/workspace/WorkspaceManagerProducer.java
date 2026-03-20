package org.acme.services.workspace;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.acme.services.workspace.filesystem.FilesystemWorkspaceManager;
import org.acme.services.workspace.filesystem.FilesystemWorkspaceToolsService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WorkspaceManagerProducer {

    @Inject
    FilesystemWorkspaceManager filesystemWorkspaceManager;

    @Inject
    FilesystemWorkspaceToolsService filesystemWorkspaceToolsService;

    @ConfigProperty(name = "tsd-agent.execution-mode", defaultValue = "FILESYSTEM")
    ExecutionMode executionMode;

    @Produces
    @ApplicationScoped
    public WorkspaceManager workspaceManager() {
        return switch (executionMode) {
            case FILESYSTEM -> filesystemWorkspaceManager;
            case DOCKER -> throw new UnsupportedOperationException("Docker execution mode not yet implemented");
            case KUBERNETES ->
                    throw new UnsupportedOperationException("Kubernetes execution mode not yet implemented");
        };
    }

    @Produces
    @ApplicationScoped
    public WorkspaceToolsService workspaceToolsService() {
        return switch (executionMode) {
            case FILESYSTEM -> filesystemWorkspaceToolsService;
            case DOCKER -> throw new UnsupportedOperationException("Docker execution mode not yet implemented");
            case KUBERNETES ->
                    throw new UnsupportedOperationException("Kubernetes execution mode not yet implemented");
        };
    }
}
