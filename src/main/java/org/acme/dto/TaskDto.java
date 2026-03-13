package org.acme.dto;

import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;

import java.time.Instant;

public class TaskDto {

    public Long id;
    public String externalId;
    public String url;
    public String title;
    public String description;
    public TaskStatus status;
    public String assignee;
    public String labels;
    public String priority;
    public SourceType type;
    public Instant createdAt;
    public Instant updatedAt;
    public Long projectId;
}
