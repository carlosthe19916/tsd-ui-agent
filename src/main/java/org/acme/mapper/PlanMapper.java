package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.PlanDto;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.PlanEntity;

import java.time.Instant;

@ApplicationScoped
public class PlanMapper {

    @Inject
    GitMapper gitMapper;

    public PlanDto toDto(PlanEntity entity) {
        PlanDto dto = new PlanDto();
        dto.id = entity.id;
        dto.content = entity.content;
        dto.requirement = entity.requirement;
        dto.discoveryStatus = entity.discoveryStatus;
        dto.discoveryError = entity.discoveryError;
        dto.status = entity.status;
        dto.type = entity.type;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;
        if (entity.git != null) {
            dto.git = gitMapper.toDto(entity.git);
        }
        return dto;
    }

    public PlanEntity toEntity(PlanDto dto) {
        PlanEntity entity = new PlanEntity();
        entity.content = dto.content;
        entity.requirement = dto.requirement;
        entity.status = dto.status;
        entity.type = dto.type;
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        if (dto.git != null && dto.git.id != null) {
            entity.git = GitEntity.findById(dto.git.id);
        }
        return entity;
    }

    public void updateEntity(PlanDto dto, PlanEntity entity) {
        entity.content = dto.content;
        entity.requirement = dto.requirement;
        entity.status = dto.status;
        entity.type = dto.type;
        entity.updatedAt = Instant.now();
        if (dto.git != null && dto.git.id != null) {
            entity.git = GitEntity.findById(dto.git.id);
        } else {
            entity.git = null;
        }
    }
}
