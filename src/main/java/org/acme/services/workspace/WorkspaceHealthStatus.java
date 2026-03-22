package org.acme.services.workspace;

public record WorkspaceHealthStatus(Status status, String reason) {

    public enum Status {
        RUNNING,
        STOPPED,
        ERROR
    }

    public static WorkspaceHealthStatus running() {
        return new WorkspaceHealthStatus(Status.RUNNING, null);
    }

    public static WorkspaceHealthStatus stopped(String reason) {
        return new WorkspaceHealthStatus(Status.STOPPED, reason);
    }

    public static WorkspaceHealthStatus error(String reason) {
        return new WorkspaceHealthStatus(Status.ERROR, reason);
    }
}
