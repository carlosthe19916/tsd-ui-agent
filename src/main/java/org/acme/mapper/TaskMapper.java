package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.TaskDto;
import org.acme.models.jpa.entity.TaskEntity;

@ApplicationScoped
public class TaskMapper {

    public TaskDto toDto(TaskEntity entity) {
        TaskDto dto = new TaskDto();
        dto.id = entity.id;
        dto.externalId = entity.externalId;
        dto.url = entity.url;
        dto.title = entity.title;
        dto.description = entity.description;
        dto.status = entity.status;
        dto.assignee = entity.assignee;
        dto.labels = entity.labels;
        dto.priority = entity.priority;
        dto.type = entity.type;
        dto.createdAt = entity.createdAt;
        dto.updatedAt = entity.updatedAt;
        dto.projectId = entity.project.id;
        return dto;
    }
}
