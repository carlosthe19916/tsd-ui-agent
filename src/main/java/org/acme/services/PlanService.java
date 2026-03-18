package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.agent.CodingAgentService;
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

    @Inject
    CodingAgentService codingAgentService;

    @Inject
    WorktreeService worktreeService;

    public void triggerRequirementEnrichment(Long taskId) {
        Thread.startVirtualThread(() -> doRequirementEnrichment(taskId));
    }

    public void triggerPlanGeneration(Long taskId) {
        Thread.startVirtualThread(() -> doPlanGeneration(taskId));
    }

    public void triggerPlanExecution(Long taskId) {
        Thread.startVirtualThread(() -> doPlanExecution(taskId));
    }

    void doPlanGeneration(Long taskId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            // Phase 1: Collect requirement and worktree path in a short transaction
            record PlanGenerationContext(String worktreePath, String requirement) {}

            PlanGenerationContext context = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null) {
                    LOG.warnf("Task %d or plan not found during plan generation", taskId);
                    return null;
                }

                String path = worktreeService.ensureWorktree(task.plan);
                return new PlanGenerationContext(path, task.plan.requirement);
            });

            if (context == null) {
                return;
            }

            // Phase 2: Call coding agent outside of any transaction
            String result = codingAgentService.generatePlan(context.worktreePath(), context.requirement(), taskId);

            // Phase 3: Store result in a short transaction
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    task.plan.plan = result;
                    task.plan.isPlanGenerationInProgress = false;
                    task.plan.planGenerationError = null;
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Plan generation failed for task %d", taskId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null) {
                        task.plan.isPlanGenerationInProgress = false;
                        task.plan.planGenerationError = e.getMessage();
                        task.plan.updatedAt = Instant.now();
                        task.plan.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to set error status for task %d plan generation", taskId);
            }
        } finally {
            requestContext.terminate();
        }
    }

    void doPlanExecution(Long taskId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            // Phase 1: Collect worktree path and plan text in a short transaction
            record PlanExecutionContext(String worktreePath, String planText) {}

            PlanExecutionContext context = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null) {
                    LOG.warnf("Task %d or plan not found during plan execution", taskId);
                    return null;
                }

                String path = worktreeService.ensureWorktree(task.plan);
                return new PlanExecutionContext(path, task.plan.plan);
            });

            if (context == null) {
                return;
            }

            // Phase 2: Delegate to coding agent outside of any transaction
            codingAgentService.executePlan(context.worktreePath(), context.planText(), taskId);

            // Phase 3: Store success in a short transaction
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    task.plan.isExecutionPlanInProgress = false;
                    task.plan.executionPlanError = null;
                    task.plan.executionPlanCompletedAt = Instant.now();
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Plan execution failed for task %d", taskId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null) {
                        task.plan.isExecutionPlanInProgress = false;
                        task.plan.executionPlanError = e.getMessage();
                        task.plan.updatedAt = Instant.now();
                        task.plan.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to set error status for task %d plan execution", taskId);
            }
        } finally {
            requestContext.terminate();
        }
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
