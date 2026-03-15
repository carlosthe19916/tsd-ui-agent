package org.acme.services.discovery;

import java.time.Instant;
import java.util.List;

public record RequirementContext(
    String taskTitle,
    String taskDescription,
    String sourceType,
    List<Comment> comments,
    List<String> additionalContexts
) {
    public record Comment(String author, String body, Instant createdAt) {}
}
