package org.acme.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.SyncStatus;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.SyncManager;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TaskSyncService {

    private static final Logger LOG = Logger.getLogger(TaskSyncService.class);

    @Inject
    SyncManager syncManager;

    @Inject
    ManagedExecutor managedExecutor;

    public void triggerSync(Long projectId) {
        managedExecutor.runAsync(() -> doSync(projectId));
    }

    void doSync(Long projectId) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                ProjectEntity project = ProjectEntity.findById(projectId);
                if (project == null) {
                    LOG.warnf("Project %d not found during sync", projectId);
                    return;
                }

                List<ExternalIssue> issues = syncManager.fetchIssues(project);

                Set<String> fetchedIds = issues.stream()
                        .map(i -> i.externalId)
                        .collect(Collectors.toSet());

                for (ExternalIssue issue : issues) {
                    TaskEntity task = TaskEntity
                            .find("externalId = ?1 and project = ?2", issue.externalId, project)
                            .firstResult();

                    if (task == null) {
                        task = new TaskEntity();
                        task.externalId = issue.externalId;
                        task.project = project;
                        task.type = project.type;
                    }

                    task.url = issue.url;
                    task.title = issue.title;
                    task.description = issue.description;
                    task.externalStatus = issue.externalStatus;

                    task.createdAt = issue.createdAt;
                    task.updatedAt = issue.updatedAt;
                    task.persist();
                }

                if (!fetchedIds.isEmpty()) {
                    TaskEntity.delete("project = ?1 and externalId not in ?2", project, fetchedIds);
                } else {
                    TaskEntity.delete("project", project);
                }

                project.syncStatus = SyncStatus.SYNCHRONIZED;
                project.lastSyncAt = Instant.now();
                project.persist();
            });
        } catch (Exception e) {
            LOG.errorf(e, "Sync failed for project %d", projectId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    ProjectEntity project = ProjectEntity.findById(projectId);
                    if (project != null) {
                        project.syncStatus = SyncStatus.SYNC_ERROR;
                        project.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to set SYNC_ERROR status for project %d", projectId);
            }
        }
    }
}
