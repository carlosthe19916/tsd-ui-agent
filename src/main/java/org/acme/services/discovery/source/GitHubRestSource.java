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
public class GitHubRestSource implements RequirementSource {

    private static final Logger LOG = Logger.getLogger(GitHubRestSource.class);

    @Inject
    SyncManager syncManager;

    @Override
    public String name() {
        return "github-rest";
    }

    @Override
    public boolean supports(TaskEntity task) {
        return task.type == SourceType.GITHUB;
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
            LOG.warnf(e, "GitHub REST source failed for task %s", task.externalId);
            return List.of();
        }
    }
}
