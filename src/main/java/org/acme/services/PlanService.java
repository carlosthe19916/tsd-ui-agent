package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.agent.CodingAgentService;
import org.acme.services.ai.RequirementSummarizerService;
import org.acme.services.git.GitManager;
import org.acme.services.sync.ExternalIssueContext;
import org.acme.services.sync.SyncManager;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.WorkspaceRequest;
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
    WorkspaceManager workspaceManager;

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
            // Phase 1a: Collect data in a short transaction
            record ProvisionContext(String existingWorkspaceId, String alias,
                    String gitUrl, String gitBranch, String gitToken, String forkUrl,
                    String requirement) {}

            ProvisionContext pctx = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null || task.plan.workspace == null) {
                    LOG.warnf("Task %d, plan, or workspace not found during plan generation", taskId);
                    return null;
                }

                String wsId = task.plan.workspace.workspaceId;
                if (wsId != null && workspaceManager.exists(wsId)) {
                    return new ProvisionContext(wsId, null, null, null, null, null, task.plan.requirement);
                }

                String alias = GitManager.planBranchName(task.plan.id);
                String gitUrl = task.plan.workspace.git != null ? task.plan.workspace.git.url : null;
                String gitBranch = task.plan.workspace.git != null ? task.plan.workspace.git.branch : null;
                String gitToken = (task.plan.workspace.git != null && task.plan.workspace.git.credential != null)
                        ? task.plan.workspace.git.credential.token : null;
                String forkUrl = task.plan.workspace.git != null ? task.plan.workspace.git.forkUrl : null;

                return new ProvisionContext(null, alias, gitUrl, gitBranch, gitToken, forkUrl, task.plan.requirement);
            });

            if (pctx == null) {
                return;
            }

            // Phase 1b: Provision workspace outside transaction (may involve clone)
            String workspaceId = pctx.existingWorkspaceId();
            if (workspaceId == null) {
                WorkspaceRequest request = WorkspaceRequest.forRemoteClone(
                        pctx.alias(), pctx.gitUrl(), pctx.gitBranch(), pctx.gitToken(), pctx.forkUrl());
                Workspace ws = workspaceManager.provision(request);
                workspaceId = ws.id();

                // Phase 1c: Store workspaceId in a short transaction
                String wsId = workspaceId;
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null && task.plan.workspace != null) {
                        task.plan.workspace.workspaceId = wsId;
                        task.plan.workspace.persist();
                    }
                });
            }

            // Phase 2: Call coding agent outside of any transaction
            Workspace workspace = workspaceManager.reconnect(workspaceId);
            String result = codingAgentService.generatePlan(workspace, pctx.requirement(), taskId);

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
            // Phase 1a: Collect data in a short transaction
            record ProvisionContext(String existingWorkspaceId, String alias,
                    String gitUrl, String gitBranch, String gitToken, String forkUrl,
                    String planText) {}

            ProvisionContext pctx = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null || task.plan.workspace == null) {
                    LOG.warnf("Task %d, plan, or workspace not found during plan execution", taskId);
                    return null;
                }

                String wsId = task.plan.workspace.workspaceId;
                if (wsId != null && workspaceManager.exists(wsId)) {
                    return new ProvisionContext(wsId, null, null, null, null, null, task.plan.plan);
                }

                String alias = GitManager.planBranchName(task.plan.id);
                String gitUrl = task.plan.workspace.git != null ? task.plan.workspace.git.url : null;
                String gitBranch = task.plan.workspace.git != null ? task.plan.workspace.git.branch : null;
                String gitToken = (task.plan.workspace.git != null && task.plan.workspace.git.credential != null)
                        ? task.plan.workspace.git.credential.token : null;
                String forkUrl = task.plan.workspace.git != null ? task.plan.workspace.git.forkUrl : null;

                return new ProvisionContext(null, alias, gitUrl, gitBranch, gitToken, forkUrl, task.plan.plan);
            });

            if (pctx == null) {
                return;
            }

            // Phase 1b: Provision workspace outside transaction (may involve clone)
            String workspaceId = pctx.existingWorkspaceId();
            if (workspaceId == null) {
                WorkspaceRequest request = WorkspaceRequest.forRemoteClone(
                        pctx.alias(), pctx.gitUrl(), pctx.gitBranch(), pctx.gitToken(), pctx.forkUrl());
                Workspace ws = workspaceManager.provision(request);
                workspaceId = ws.id();

                // Phase 1c: Store workspaceId in a short transaction
                String wsId = workspaceId;
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null && task.plan.workspace != null) {
                        task.plan.workspace.workspaceId = wsId;
                        task.plan.workspace.persist();
                    }
                });
            }

            // Phase 2: Delegate to coding agent outside of any transaction
            Workspace workspace = workspaceManager.reconnect(workspaceId);
            codingAgentService.executePlan(workspace, pctx.planText(), taskId);

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
