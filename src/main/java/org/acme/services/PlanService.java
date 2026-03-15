package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.ai.RequirementSummarizerService;
import org.acme.services.sync.ExternalIssueContext;
import org.acme.services.sync.SyncManager;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlanService {

    private static final Logger LOG = Logger.getLogger(PlanService.class);

    @Inject
    SyncManager syncManager;

    @Inject
    RequirementSummarizerService requirementSummarizerService;

    public void triggerRequirementEnrichment(Long taskId) {
        Thread.startVirtualThread(() -> doRequirementEnrichment(taskId));
    }

    void doRequirementEnrichment(Long taskId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            // Phase 1: Collect data in a short transaction
            ExternalIssueContext context = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null) {
                    LOG.warnf("Task %d or plan not found during discovery", taskId);
                    return null;
                }

                List<ExternalIssueContext.Comment> allComments = syncManager.fetchComments(task);
                List<String> labelsList = syncManager.fetchLabels(task);
                String labelsText = labelsList != null && !labelsList.isEmpty()
                        ? String.join(", ", labelsList)
                        : "No labels available.";

                return new ExternalIssueContext(
                        task.title,
                        task.description != null ? task.description : "No description provided.",
                        allComments,
                        labelsText
                );
            });

            if (context == null) {
                return;
            }

            // Phase 2: Call AI outside of any transaction
            String commentsText = context.comments().stream()
                    .map(c -> "- " + c.author() + ": " + c.body())
                    .collect(Collectors.joining("\n"));
            if (commentsText.isBlank()) {
                commentsText = "No comments available.";
            }

            String result = requirementSummarizerService.summarize(
                    context.taskTitle(),
                    context.taskDescription(),
                    commentsText,
                    context.labels()
            );

            // Phase 3: Store result in a short transaction
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    task.plan.requirement = result;
                    task.plan.isRequirementInProgress = false;
                    task.plan.requirementError = null;
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Discovery failed for task %d", taskId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null) {
                        task.plan.isRequirementInProgress = false;
                        task.plan.requirementError = e.getMessage();
                        task.plan.updatedAt = Instant.now();
                        task.plan.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to set ERROR status for task %d discovery", taskId);
            }
        } finally {
            requestContext.terminate();
        }
    }
}
