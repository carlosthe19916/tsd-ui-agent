package org.acme.services.workspace;

public enum WorkspaceCommandType {
    NAVIGATE("Navigate"),
    CONTAINER_EXEC("Container Exec"),
    REMOTE_EXEC("Remote"),
    VSCODE("VSCode");

    public final String label;

    WorkspaceCommandType(String label) {
        this.label = label;
    }
}
