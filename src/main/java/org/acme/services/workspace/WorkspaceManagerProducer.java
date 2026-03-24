package org.acme.services.workspace;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WorkspaceManagerProducer {

    @Inject
    @Any
    Instance<WorkspaceManager> workspaceManagers;

    @ConfigProperty(name = "tsd-agent.execution-mode")
    ExecutionMode executionMode;

    @Produces
    @ApplicationScoped
    public WorkspaceManager workspaceManager() {
        Instance<WorkspaceManager> selected = workspaceManagers.select(new WorkspaceManagerTypeLiteral(executionMode));
        if (selected.isUnsatisfied()) {
            throw new UnsupportedOperationException(executionMode + " execution mode has no WorkspaceManager implementation");
        }
        return selected.get();
    }
}
