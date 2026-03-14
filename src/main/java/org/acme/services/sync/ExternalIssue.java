package org.acme.services.sync;

import org.acme.models.jpa.entity.TaskStatus;

import java.time.Instant;

public class ExternalIssue {

    public String externalId;
    public String url;
    public String title;
    public String description;
    public TaskStatus status;
    public String labels;
    public Instant createdAt;
    public Instant updatedAt;
}
