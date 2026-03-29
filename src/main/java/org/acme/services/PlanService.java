package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.ExecutionMode;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.codeagent.CodingAgentService;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.acme.services.workspace.WorkspaceHealthStatus;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class PlanService {

    private static final Logger LOG = Logger.getLogger(PlanService.class);

    @Inject
    CodingAgentService codingAgentService;

    @Inject
    WorkspaceManagerResolver workspaceManagerResolver;

    @Inject
    ChangeRequestService changeRequestService;

    public void triggerFullPipeline(Long taskId) {
        Thread.startVirtualThread(() -> runWithRequestContext(() -> doFullPipeline(taskId)));
    }

    public void triggerRequirementEnrichment(Long taskId) {
        Thread.startVirtualThread(() -> runWithRequestContext(() -> doRequirementEnrichment(taskId)));
    }

    public void triggerPlanGeneration(Long taskId) {
        Thread.startVirtualThread(() -> runWithRequestContext(() -> doPlanGeneration(taskId)));
    }

    public void triggerPlanExecution(Long taskId) {
        Thread.startVirtualThread(() -> runWithRequestContext(() -> doPlanExecution(taskId)));
    }

    private void runWithRequestContext(Runnable action) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            action.run();
        } finally {
            requestContext.terminate();
        }
    }

    public void doPlanGeneration(Long taskId) {
        try {
            // Phase 1: Collect data in a short transaction
            record PlanGenerationContext(String workspaceId, String requirement, ExecutionMode executionMode) {}

            PlanGenerationContext ctx = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null || task.workspace == null) {
                    LOG.warnf("Task %d, plan, or workspace not found during plan generation", taskId);
                    return null;
                }
                if (task.workspace.workspaceId == null) {
                    LOG.warnf("Task %d has no provisioned workspace for plan generation", taskId);
                    return null;
                }
                return new PlanGenerationContext(task.workspace.workspaceId, task.plan.requirement, task.workspace.executionMode);
            });

            if (ctx == null) {
                return;
            }

            // Phase 2: Call coding agent outside of any transaction
            Workspace workspace = workspaceManagerResolver.resolve(ctx.executionMode()).getWorkspace(ctx.workspaceId())
                    .orElseThrow(() -> new WorkspaceException("Workspace not found: " + ctx.workspaceId()));
            if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
                throw new WorkspaceException("Workspace is not running");
            }
            String result = codingAgentService.generatePlan(workspace, ctx.requirement(), taskId);

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
        }
    }

    public void doPlanExecution(Long taskId) {
        try {
            // Phase 1: Collect data in a short transaction
            record PlanExecutionContext(String workspaceId, String planText, ExecutionMode executionMode) {}

            PlanExecutionContext ctx = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null || task.workspace == null) {
                    LOG.warnf("Task %d, plan, or workspace not found during plan execution", taskId);
                    return null;
                }
                if (task.workspace.workspaceId == null) {
                    LOG.warnf("Task %d has no provisioned workspace for plan execution", taskId);
                    return null;
                }
                return new PlanExecutionContext(task.workspace.workspaceId, task.plan.plan, task.workspace.executionMode);
            });

            if (ctx == null) {
                return;
            }

            // Phase 2: Delegate to coding agent outside of any transaction
            Workspace workspace = workspaceManagerResolver.resolve(ctx.executionMode()).getWorkspace(ctx.workspaceId())
                    .orElseThrow(() -> new WorkspaceException("Workspace not found: " + ctx.workspaceId()));
            if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
                throw new WorkspaceException("Workspace is not running");
            }
            codingAgentService.executePlan(workspace, ctx.planText(), taskId);

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
        }
    }

    private void doFullPipeline(Long taskId) {
        // Phase 1: Requirement enrichment (flag already set by endpoint)
        doRequirementEnrichment(taskId);
        if (!transitionToNextPhase(taskId, "requirement")) return;

        // Phase 2: Plan generation
        doPlanGeneration(taskId);
        if (!transitionToNextPhase(taskId, "planGeneration")) return;

        // Phase 3: Plan execution
        doPlanExecution(taskId);
        if (!transitionToNextPhase(taskId, "execution")) return;

        // Phase 4: Change request
        changeRequestService.doChangeRequest(taskId);
    }

    private boolean transitionToNextPhase(Long taskId, String completedPhase) {
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null) return false;

                switch (completedPhase) {
                    case "requirement":
                        if (task.plan.requirementError != null) return false;
                        task.plan.isPlanGenerationInProgress = true;
                        task.plan.planGenerationError = null;
                        break;
                    case "planGeneration":
                        if (task.plan.planGenerationError != null) return false;
                        task.plan.isExecutionPlanInProgress = true;
                        task.plan.executionPlanError = null;
                        break;
                    case "execution":
                        if (task.plan.executionPlanError != null) return false;
                        task.plan.isChangeRequestInProgress = true;
                        task.plan.changeRequestError = null;
                        break;
                    default:
                        return false;
                }
                task.plan.updatedAt = Instant.now();
                task.plan.persist();
                return true;
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to transition to next phase after '%s' for task %d", completedPhase, taskId);
            return false;
        }
    }

    public void doRequirementEnrichment(Long taskId) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null) {
                    LOG.warnf("Task %d or plan not found during requirement enrichment", taskId);
                    return;
                }

                boolean hasDescription = task.description != null && !task.description.isBlank();
                task.plan.requirement = hasDescription
                        ? task.title + "\n\n" + task.description
                        : task.title;
                task.plan.isRequirementInProgress = false;
                task.plan.requirementError = null;
                task.plan.updatedAt = Instant.now();
                task.plan.persist();
            });
        } catch (Exception e) {
            LOG.errorf(e, "Requirement enrichment failed for task %d", taskId);
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
                LOG.errorf(inner, "Failed to set error status for task %d requirement enrichment", taskId);
            }
        }
    }
}
