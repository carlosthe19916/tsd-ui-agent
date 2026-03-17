package org.acme.dto;

import java.time.Instant;

public class PlanDto {

    public Long id;

    public String plan;

    public String requirement;

    public GitDto git;

    public boolean isRequirementInProgress;

    public String requirementError;

    public boolean isExecutionPlanInProgress;

    public String executionPlanError;

    public Instant executionPlanCompletedAt;

    public Instant createdAt;

    public Instant updatedAt;

    public String worktreePath;

    public String claudeSessionId;

    public boolean isChangeRequestInProgress;

    public String changeRequestError;

    public String changeRequestUrl;
}
