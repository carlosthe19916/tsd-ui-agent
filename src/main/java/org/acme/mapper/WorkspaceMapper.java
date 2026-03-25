package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.WorkspaceDto;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;

@ApplicationScoped
public class WorkspaceMapper {

    @Inject
    GitMapper gitMapper;

    public WorkspaceDto toDto(WorkspaceEntity entity) {
        WorkspaceDto dto = new WorkspaceDto();
        dto.id = entity.id;
        dto.isProvisioningInProgress = entity.isProvisioningInProgress;
        dto.provisioningError = entity.provisioningError;
        dto.workspaceId = entity.workspaceId;
        if (entity.git != null) {
            dto.git = gitMapper.toDto(entity.git);
        }
        return dto;
    }

    public WorkspaceEntity toEntity(WorkspaceDto dto) {
        WorkspaceEntity entity = new WorkspaceEntity();
        if (dto.git != null && dto.git.id != null) {
            entity.git = GitEntity.findById(dto.git.id);
        }
        return entity;
    }
}
