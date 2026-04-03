package org.acme.services;

import org.acme.models.jpa.entity.ExecutionMode;
import org.acme.models.jpa.entity.GitVendorType;

public record PipelineContext(
        String workspaceId,
        ExecutionMode executionMode,
        String requirement,
        String gitUrl,
        String gitBranch,
        String gitToken,
        String forkUrl,
        GitVendorType vendorType,
        String taskTitle,
        String taskUrl,
        Long taskId
) {}
