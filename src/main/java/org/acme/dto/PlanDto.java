package org.acme.dto;

import org.acme.models.jpa.entity.PlanStatus;

import java.time.Instant;

public class PlanDto {

    public Long id;

    public String executionPlan;

    public String requirement;

    public GitDto git;

    public boolean isRequirementInProgress;

    public String requirementError;

    public PlanStatus status;

    public Instant createdAt;

    public Instant updatedAt;

    public String worktreePath;

    public String claudeSessionId;
}
