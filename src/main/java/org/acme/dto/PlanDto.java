package org.acme.dto;

import java.time.Instant;

public class PlanDto {

    public Long id;

    public String plan;

    public String requirement;

    public boolean isRequirementInProgress;

    public String requirementError;

    public boolean isExecutionPlanInProgress;

    public String executionPlanError;

    public Instant executionPlanCompletedAt;

    public Instant createdAt;

    public Instant updatedAt;

    public boolean isPlanGenerationInProgress;

    public String planGenerationError;

    public boolean isChangeRequestInProgress;

    public String changeRequestError;

    public String changeRequestUrl;

    public String changeRequestTitle;

    public String changeRequestStatus;
}
