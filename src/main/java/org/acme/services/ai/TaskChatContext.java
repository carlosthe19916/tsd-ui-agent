package org.acme.services.ai;

import jakarta.enterprise.context.RequestScoped;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.workspace.Workspace;

@RequestScoped
public class TaskChatContext {

    public Long taskId;
    public TaskEntity task;
    public Workspace workspace;
}
