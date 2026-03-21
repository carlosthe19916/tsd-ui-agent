package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.WorkspaceDto;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;

import java.time.Instant;

@ApplicationScoped
public class WorkspaceMapper {

    @Inject
    GitMapper gitMapper;

    public WorkspaceDto toDto(WorkspaceEntity entity) {
        WorkspaceDto dto = new WorkspaceDto();
        dto.id = entity.id;
        dto.localPath = entity.localPath;
        dto.isCloneInProgress = entity.isCloneInProgress;
        dto.cloneError = entity.cloneError;
        dto.workspaceId = entity.workspaceId;
        dto.executionMode = entity.executionMode;
        dto.claudeSessionId = entity.claudeSessionId;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;
        if (entity.git != null) {
            dto.git = gitMapper.toDto(entity.git);
        }
        return dto;
    }

    public WorkspaceEntity toEntity(WorkspaceDto dto) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.executionMode = dto.executionMode;
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        if (dto.git != null && dto.git.id != null) {
            entity.git = GitEntity.findById(dto.git.id);
        }
        return entity;
    }

    public void updateEntity(WorkspaceDto dto, WorkspaceEntity entity) {
        entity.executionMode = dto.executionMode;
        entity.updatedAt = Instant.now();
    }

    public void patchEntity(WorkspaceDto dto, WorkspaceEntity entity) {
        if (dto.claudeSessionId != null) {
            entity.claudeSessionId = dto.claudeSessionId;
        }
        if (dto.executionMode != null) {
            entity.executionMode = dto.executionMode;
        }
        entity.updatedAt = Instant.now();
    }
}
