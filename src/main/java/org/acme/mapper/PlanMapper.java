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
        dto.content = entity.content;
        dto.status = entity.status;
        dto.type = entity.type;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;
        return dto;
    }

    public PlanEntity toEntity(PlanDto dto) {
        PlanEntity entity = new PlanEntity();
        entity.content = dto.content;
        entity.status = dto.status;
        entity.type = dto.type;
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public void updateEntity(PlanDto dto, PlanEntity entity) {
        entity.content = dto.content;
        entity.status = dto.status;
        entity.type = dto.type;
        entity.updatedAt = Instant.now();
    }
}
