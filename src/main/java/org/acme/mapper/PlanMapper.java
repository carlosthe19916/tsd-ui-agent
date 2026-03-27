package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.PlanDto;
import org.acme.models.jpa.entity.PlanEntity;

import java.time.Instant;

@ApplicationScoped
public class PlanMapper {

    public PlanDto toDto(PlanEntity entity) {
        PlanDto dto = new PlanDto();
        dto.id = entity.id;
        dto.plan = entity.plan;
        dto.requirement = entity.requirement;
        dto.isRequirementInProgress = entity.isRequirementInProgress;
        dto.requirementError = entity.requirementError;
        dto.isExecutionPlanInProgress = entity.isExecutionPlanInProgress;
        dto.executionPlanError = entity.executionPlanError;
        dto.executionPlanCompletedAt = entity.executionPlanCompletedAt;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;
        dto.isPlanGenerationInProgress = entity.isPlanGenerationInProgress;
        dto.planGenerationError = entity.planGenerationError;
        dto.isChangeRequestInProgress = entity.isChangeRequestInProgress;
        dto.changeRequestError = entity.changeRequestError;
        dto.changeRequestUrl = entity.changeRequestUrl;
        dto.changeRequestTitle = entity.changeRequestTitle;
        dto.changeRequestStatus = entity.changeRequestStatus;
        return dto;
    }

    public PlanEntity toEntity(PlanDto dto) {
        PlanEntity entity = new PlanEntity();
        entity.plan = dto.plan;
        entity.requirement = dto.requirement;
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public void updateEntity(PlanDto dto, PlanEntity entity) {
        entity.plan = dto.plan;
        entity.requirement = dto.requirement;
        entity.updatedAt = Instant.now();
    }

    public void patchEntity(PlanDto dto, PlanEntity entity) {
        if (dto.plan != null) {
            entity.plan = dto.plan;
        }
        if (dto.requirement != null) {
            entity.requirement = dto.requirement;
        }
        if (dto.changeRequestTitle != null) {
            entity.changeRequestTitle = dto.changeRequestTitle;
        }
        if (dto.changeRequestStatus != null) {
            entity.changeRequestStatus = dto.changeRequestStatus;
        }
        entity.updatedAt = Instant.now();
    }
}
