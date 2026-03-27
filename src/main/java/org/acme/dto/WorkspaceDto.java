package org.acme.dto;

public class WorkspaceDto {

    public Long id;

    public GitDto git;

    public boolean isProvisioningInProgress;

    public String provisioningError;

    public String workspaceId;

    public String executionMode;

    public TaskDto task;
}
