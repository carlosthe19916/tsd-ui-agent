package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.TaskContextDto;
import org.acme.models.jpa.entity.TaskContextEntity;
import org.acme.models.jpa.entity.TaskEntity;

@ApplicationScoped
public class TaskContextMapper {

    public TaskContextDto toDto(TaskContextEntity entity) {
        TaskContextDto dto = new TaskContextDto();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.description = entity.description;
        dto.type = entity.type;
        dto.content = entity.content;
        dto.repositoryUrl = entity.repositoryUrl;
        dto.branch = entity.branch;
        return dto;
    }

    public TaskContextEntity toEntity(TaskContextDto dto, TaskEntity task) {
        TaskContextEntity entity = new TaskContextEntity();
        entity.name = dto.name;
        entity.description = dto.description;
        entity.type = dto.type;
        entity.content = dto.content;
        entity.repositoryUrl = dto.repositoryUrl;
        entity.branch = dto.branch;
        entity.task = task;
        return entity;
    }

    public void updateEntity(TaskContextDto dto, TaskContextEntity entity) {
        entity.name = dto.name;
        entity.description = dto.description;
        entity.type = dto.type;
        entity.content = dto.content;
        entity.repositoryUrl = dto.repositoryUrl;
        entity.branch = dto.branch;
    }
}
