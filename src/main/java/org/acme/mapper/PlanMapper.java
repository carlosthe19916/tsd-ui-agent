package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.PlanDto;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.PlanEntity;
import org.acme.models.jpa.entity.PlanStatus;

import java.time.Instant;

@ApplicationScoped
public class PlanMapper {

    @Inject
    GitMapper gitMapper;

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
        dto.status = entity.status;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;
        dto.worktreePath = entity.worktreePath;
        dto.claudeSessionId = entity.claudeSessionId;
        if (entity.git != null) {
            dto.git = gitMapper.toDto(entity.git);
        }
        return dto;
    }

    public PlanEntity toEntity(PlanDto dto) {
        PlanEntity entity = new PlanEntity();
        entity.plan = dto.plan;
        entity.requirement = dto.requirement;
        entity.status = dto.status != null ? dto.status : PlanStatus.IN_PROGRESS;
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        if (dto.git != null && dto.git.id != null) {
            entity.git = GitEntity.findById(dto.git.id);
        }
        return entity;
    }

    public void updateEntity(PlanDto dto, PlanEntity entity) {
        entity.plan = dto.plan;
        entity.requirement = dto.requirement;
        if (dto.status != null) {
            entity.status = dto.status;
        }
        entity.updatedAt = Instant.now();

        Long oldGitId = entity.git != null ? entity.git.id : null;
        Long newGitId = dto.git != null ? dto.git.id : null;

        if (dto.git != null && dto.git.id != null) {
            entity.git = GitEntity.findById(dto.git.id);
        } else {
            entity.git = null;
        }

        if (!java.util.Objects.equals(oldGitId, newGitId)) {
            entity.worktreePath = null;
            entity.claudeSessionId = null;
        }
    }

    public void patchEntity(PlanDto dto, PlanEntity entity) {
        if (dto.plan != null) {
            entity.plan = dto.plan;
        }
        if (dto.requirement != null) {
            entity.requirement = dto.requirement;
        }
        if (dto.status != null) {
            entity.status = dto.status;
        }
        if (dto.claudeSessionId != null) {
            entity.claudeSessionId = dto.claudeSessionId.isEmpty() ? null : dto.claudeSessionId;
        }
        if (dto.git != null) {
            Long oldGitId = entity.git != null ? entity.git.id : null;
            if (dto.git.id != null) {
                entity.git = GitEntity.findById(dto.git.id);
            } else {
                entity.git = null;
            }
            if (!java.util.Objects.equals(oldGitId, dto.git.id)) {
                entity.worktreePath = null;
                entity.claudeSessionId = null;
            }
        }
        entity.updatedAt = Instant.now();
    }
}
