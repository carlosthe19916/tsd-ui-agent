package org.acme.dto;

import org.acme.models.jpa.entity.ExecutionMode;

import java.time.Instant;

public class WorkspaceDto {

    public Long id;

    public GitDto git;

    public String localPath;

    public boolean isCloneInProgress;

    public String cloneError;

    public String workspaceId;

    public ExecutionMode executionMode;

    public String claudeSessionId;

    public Instant createdAt;

    public Instant updatedAt;
}
