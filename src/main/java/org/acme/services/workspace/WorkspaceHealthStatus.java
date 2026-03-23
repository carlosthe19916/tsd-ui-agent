package org.acme.services.workspace;

public record WorkspaceHealthStatus(Status status, String reason, boolean supportsStartStop) {

    public enum Status {
        RUNNING,
        STOPPED,
        ERROR
    }

    public static WorkspaceHealthStatus running() {
        return new WorkspaceHealthStatus(Status.RUNNING, null, false);
    }

    public static WorkspaceHealthStatus running(boolean supportsStartStop) {
        return new WorkspaceHealthStatus(Status.RUNNING, null, supportsStartStop);
    }

    public static WorkspaceHealthStatus stopped(String reason) {
        return new WorkspaceHealthStatus(Status.STOPPED, reason, false);
    }

    public static WorkspaceHealthStatus stopped(String reason, boolean supportsStartStop) {
        return new WorkspaceHealthStatus(Status.STOPPED, reason, supportsStartStop);
    }

    public static WorkspaceHealthStatus error(String reason) {
        return new WorkspaceHealthStatus(Status.ERROR, reason, false);
    }
}
