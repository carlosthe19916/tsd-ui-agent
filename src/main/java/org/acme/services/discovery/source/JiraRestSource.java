package org.acme.services.discovery.source;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.discovery.RequirementContext;
import org.acme.services.discovery.RequirementSource;
import org.acme.services.sync.SyncManager;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class JiraRestSource implements RequirementSource {

    private static final Logger LOG = Logger.getLogger(JiraRestSource.class);

    @Inject
    SyncManager syncManager;

    @Override
    public String name() {
        return "jira-rest";
    }

    @Override
    public boolean supports(TaskEntity task) {
        return task.type == SourceType.JIRA;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public List<RequirementContext.Comment> fetchComments(TaskEntity task) {
        try {
            return syncManager.fetchComments(task);
        } catch (Exception e) {
            LOG.warnf(e, "Jira REST source failed for task %s", task.externalId);
            return List.of();
        }
    }
}
