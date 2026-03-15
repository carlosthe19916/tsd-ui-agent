package org.acme.services.discovery;

import org.acme.models.jpa.entity.TaskEntity;

import java.util.List;

public interface RequirementSource {
    String name();
    boolean supports(TaskEntity task);
    default int priority() { return 0; }
    List<RequirementContext.Comment> fetchComments(TaskEntity task);
}
