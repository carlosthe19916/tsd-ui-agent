package org.acme.services.sync;

import org.acme.models.jpa.entity.TaskStatus;

import java.time.Instant;

public class ExternalIssue {

    public String externalId;
    public String url;
    public String title;
    public String description;
    public TaskStatus status;
    public String assignee;
    public String labels;
    public String priority;
    public Instant createdAt;
    public Instant updatedAt;
}
