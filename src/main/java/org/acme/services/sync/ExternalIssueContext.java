package org.acme.services.sync;

import java.time.Instant;
import java.util.List;

public record ExternalIssueContext(
    String taskTitle,
    String taskDescription,
    List<Comment> comments,
    String labels
) {
    public record Comment(String author, String body, Instant createdAt) {}
}
