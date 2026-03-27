package org.acme.services.workspace;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorkspaceManagerResolver {

    @Inject
    @Any
    Instance<WorkspaceManager> workspaceManagers;

    public WorkspaceManager resolve(org.acme.models.jpa.entity.ExecutionMode mode) {
        ExecutionMode serviceMode = mode != null
                ? ExecutionMode.valueOf(mode.name())
                : ExecutionMode.FILESYSTEM;
        return resolve(serviceMode);
    }

    public WorkspaceManager resolve(ExecutionMode mode) {
        if (mode == null) {
            mode = ExecutionMode.FILESYSTEM;
        }
        Instance<WorkspaceManager> selected = workspaceManagers.select(new WorkspaceManagerTypeLiteral(mode));
        if (selected.isUnsatisfied()) {
            throw new UnsupportedOperationException(mode + " execution mode has no WorkspaceManager implementation");
        }
        return selected.get();
    }
}
