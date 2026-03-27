package org.acme.services;

import java.time.Instant;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.acme.dto.TaskDto;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.models.jpa.entity.TaskStatus;

@Transactional
@ApplicationScoped
public class TaskService {

    public TaskEntity create(TaskDto dto) {
        TaskEntity entity = new TaskEntity();
        entity.title = dto.title;
        entity.description = dto.description;
        entity.status = TaskStatus.OPEN;
        entity.type = SourceType.MANUAL;
        entity.externalId = "manual-" + UUID.randomUUID();
        entity.createdAt = Instant.now();
        entity.updatedAt = entity.createdAt;
        entity.persist();
        return entity;
    }
}
