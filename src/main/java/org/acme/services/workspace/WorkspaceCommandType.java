package org.acme.services.workspace;

public enum WorkspaceCommandType {
    NAVIGATE("Navigate"),
    CLAUDE_CLI("Claude CLI"),
    OPENCODE("OpenCode");

    public final String label;

    WorkspaceCommandType(String label) {
        this.label = label;
    }
}
