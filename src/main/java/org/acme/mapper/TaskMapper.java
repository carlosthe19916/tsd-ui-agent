package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.TaskDto;
import org.acme.models.jpa.entity.TaskEntity;

@ApplicationScoped
public class TaskMapper {

    @Inject
    ProjectMapper projectMapper;

    @Inject
    PlanMapper planMapper;

    public TaskDto toDto(TaskEntity entity) {
        TaskDto dto = new TaskDto();
        dto.id = entity.id;
        dto.externalId = entity.externalId;
        dto.url = entity.url;
        dto.title = entity.title;
        dto.description = entity.description;
        dto.status = entity.status;
        dto.externalStatus = entity.externalStatus;

        dto.type = entity.type;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;
        dto.project = projectMapper.toDto(entity.project);
        dto.plan = entity.plan != null ? planMapper.toDto(entity.plan) : null;
        return dto;
    }
}
