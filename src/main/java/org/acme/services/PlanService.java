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

    // --- Trigger methods (async via virtual thread) ---

    public void triggerFullPipeline(Long taskId) {
        Thread.startVirtualThread(() -> runWithRequestContext(() -> doFullPipeline(taskId)));
    }

    public void triggerFullPipeline(PipelineContext ctx) {
        Thread.startVirtualThread(() -> runWithRequestContext(() -> doFullPipeline(ctx)));
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

    // --- Context-based pipeline (used by /implement) ---

    public void doFullPipeline(PipelineContext ctx) {
        // Requirement is already set in context, skip enrichment
        // Set plan generation in progress
        QuarkusTransaction.requiringNew().run(() -> {
            TaskEntity task = TaskEntity.findById(ctx.taskId());
            if (task != null && task.plan != null) {
                task.plan.isPlanGenerationInProgress = true;
                task.plan.updatedAt = Instant.now();
                task.plan.persist();
            }
        });

        doPlanGeneration(ctx);
        if (!transitionToNextPhase(ctx.taskId(), "planGeneration")) return;

        doPlanExecution(ctx);
        if (!transitionToNextPhase(ctx.taskId(), "execution")) return;

        changeRequestService.doChangeRequest(ctx);
    }

    public void doPlanGeneration(PipelineContext ctx) {
        try {
            Workspace workspace = workspaceManagerResolver.resolve(ctx.executionMode()).getWorkspace(ctx.workspaceId())
                    .orElseThrow(() -> new WorkspaceException("Workspace not found: " + ctx.workspaceId()));
            if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
                throw new WorkspaceException("Workspace is not running");
            }
            String result = codingAgentService.generatePlan(workspace, ctx.requirement(), ctx.taskId());

            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(ctx.taskId());
                if (task != null && task.plan != null) {
                    task.plan.plan = result;
                    task.plan.isPlanGenerationInProgress = false;
                    task.plan.planGenerationError = null;
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Plan generation failed for task %d", ctx.taskId());
            setPlanError(ctx.taskId(), "planGeneration", e.getMessage());
        }
    }

    public void doPlanExecution(PipelineContext ctx) {
        try {
            // Re-read plan text from DB (it was stored by doPlanGeneration)
            String planText = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(ctx.taskId());
                return (task != null && task.plan != null) ? task.plan.plan : null;
            });
            if (planText == null) {
                LOG.warnf("Task %d: no plan text found for execution", ctx.taskId());
                return;
            }

            Workspace workspace = workspaceManagerResolver.resolve(ctx.executionMode()).getWorkspace(ctx.workspaceId())
                    .orElseThrow(() -> new WorkspaceException("Workspace not found: " + ctx.workspaceId()));
            if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
                throw new WorkspaceException("Workspace is not running");
            }
            codingAgentService.executePlan(workspace, planText, ctx.taskId());

            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(ctx.taskId());
                if (task != null && task.plan != null) {
                    task.plan.isExecutionPlanInProgress = false;
                    task.plan.executionPlanError = null;
                    task.plan.executionPlanCompletedAt = Instant.now();
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Plan execution failed for task %d", ctx.taskId());
            setPlanError(ctx.taskId(), "execution", e.getMessage());
        }
    }

    // --- Entity-based pipeline (used by UI) ---

    private void doFullPipeline(Long taskId) {
        doRequirementEnrichment(taskId);
        if (!transitionToNextPhase(taskId, "requirement")) return;

        doPlanGeneration(taskId);
        if (!transitionToNextPhase(taskId, "planGeneration")) return;

        doPlanExecution(taskId);
        if (!transitionToNextPhase(taskId, "execution")) return;

        changeRequestService.doChangeRequest(taskId);
    }

    public void doPlanGeneration(Long taskId) {
        try {
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

            Workspace workspace = workspaceManagerResolver.resolve(ctx.executionMode()).getWorkspace(ctx.workspaceId())
                    .orElseThrow(() -> new WorkspaceException("Workspace not found: " + ctx.workspaceId()));
            if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
                throw new WorkspaceException("Workspace is not running");
            }
            String result = codingAgentService.generatePlan(workspace, ctx.requirement(), taskId);

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
            setPlanError(taskId, "planGeneration", e.getMessage());
        }
    }

    public void doPlanExecution(Long taskId) {
        try {
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

            Workspace workspace = workspaceManagerResolver.resolve(ctx.executionMode()).getWorkspace(ctx.workspaceId())
                    .orElseThrow(() -> new WorkspaceException("Workspace not found: " + ctx.workspaceId()));
            if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
                throw new WorkspaceException("Workspace is not running");
            }
            codingAgentService.executePlan(workspace, ctx.planText(), taskId);

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
            setPlanError(taskId, "execution", e.getMessage());
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
            setPlanError(taskId, "requirement", e.getMessage());
        }
    }

    // --- Shared helpers ---

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

    private void setPlanError(Long taskId, String phase, String errorMessage) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    switch (phase) {
                        case "requirement" -> {
                            task.plan.isRequirementInProgress = false;
                            task.plan.requirementError = errorMessage;
                        }
                        case "planGeneration" -> {
                            task.plan.isPlanGenerationInProgress = false;
                            task.plan.planGenerationError = errorMessage;
                        }
                        case "execution" -> {
                            task.plan.isExecutionPlanInProgress = false;
                            task.plan.executionPlanError = errorMessage;
                        }
                    }
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception inner) {
            LOG.errorf(inner, "Failed to set error status for task %d phase %s", taskId, phase);
        }
    }
}
