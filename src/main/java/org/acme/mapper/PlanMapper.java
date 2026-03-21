package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.PlanDto;
import org.acme.models.jpa.entity.PlanEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;

import java.time.Instant;
import java.util.Objects;

@ApplicationScoped
public class PlanMapper {

    @Inject
    WorkspaceMapper workspaceMapper;

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
        if (entity.workspace != null) {
            dto.workspace = workspaceMapper.toDto(entity.workspace);
        }
        return dto;
    }

    public PlanEntity toEntity(PlanDto dto) {
        PlanEntity entity = new PlanEntity();
        entity.plan = dto.plan;
        entity.requirement = dto.requirement;
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        if (dto.workspace != null && dto.workspace.id != null) {
            entity.workspace = WorkspaceEntity.findById(dto.workspace.id);
        }
        return entity;
    }

    public void updateEntity(PlanDto dto, PlanEntity entity) {
        entity.plan = dto.plan;
        entity.requirement = dto.requirement;
        entity.updatedAt = Instant.now();

        Long oldWorkspaceId = entity.workspace != null ? entity.workspace.id : null;
        Long newWorkspaceId = dto.workspace != null ? dto.workspace.id : null;

        if (dto.workspace != null && dto.workspace.id != null) {
            entity.workspace = WorkspaceEntity.findById(dto.workspace.id);
        } else {
            entity.workspace = null;
        }

        if (!Objects.equals(oldWorkspaceId, newWorkspaceId)) {
            // Workspace changed — runtime state is no longer valid
            // (claudeSessionId and workspaceId live on WorkspaceEntity now)
        }
    }

    public void patchEntity(PlanDto dto, PlanEntity entity) {
        if (dto.plan != null) {
            entity.plan = dto.plan;
        }
        if (dto.requirement != null) {
            entity.requirement = dto.requirement;
        }
        if (dto.workspace != null) {
            if (dto.workspace.id != null) {
                entity.workspace = WorkspaceEntity.findById(dto.workspace.id);
            } else {
                entity.workspace = null;
            }
        }
        entity.updatedAt = Instant.now();
    }
}
