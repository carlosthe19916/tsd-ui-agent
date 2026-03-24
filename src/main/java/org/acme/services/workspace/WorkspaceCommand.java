package org.acme.services.workspace;

public record WorkspaceCommand(WorkspaceCommandType type, String command) {

    public String label() {
        return type.label;
    }
}
