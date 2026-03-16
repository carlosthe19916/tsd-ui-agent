package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.PlanStatus;

import java.time.Instant;

public class PlanDto {

    public Long id;

    public String content;

    public String requirement;

    public GitDto git;

    public boolean isRequirementInProgress;

    public String requirementError;

    @NotNull
    public PlanStatus status;

    public Instant createdAt;

    public Instant updatedAt;

    public String worktreePath;
}
