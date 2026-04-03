package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.sync.jira.JiraSyncClient;
import org.acme.services.changerequest.ChangeRequestParams;
import org.acme.services.changerequest.ChangeRequestProvider;
import org.acme.services.changerequest.ChangeRequestResult;
import org.acme.services.git.GitManager;
import org.acme.models.jpa.entity.ExecutionMode;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceException;
import org.acme.services.workspace.WorkspaceHealthStatus;
import org.acme.services.workspace.WorkspaceGitOperations;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class ChangeRequestService {

    private static final Logger LOG = Logger.getLogger(ChangeRequestService.class);

    @Inject
    WorkspaceManagerResolver workspaceManagerResolver;

    @Inject
    WorkspaceGitOperations workspaceGit;

    @Inject
    Instance<ChangeRequestProvider> providers;

    @Inject
    JiraSyncClient jiraSyncClient;

    public void triggerChangeRequest(Long taskId) {
        Thread.startVirtualThread(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                doChangeRequest(taskId);
            } finally {
                requestContext.terminate();
            }
        });
    }

    // --- Entity-based flow (UI) ---

    public void doChangeRequest(Long taskId) {
        try {
            record ChangeRequestContext(
                    String workspaceId, String gitUrl,
                    String forkUrl, String taskTitle, String requirement,
                    Long planId, String gitToken, String gitBranch,
                    GitVendorType vendorType,
                    String taskUrl, SourceType sourceType, String taskExternalId,
                    String jiraApiUrl, String jiraToken,
                    ExecutionMode executionMode
            ) {}

            ChangeRequestContext context = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null || task.workspace == null || task.workspace.git == null) {
                    LOG.warnf("Task %d, plan, workspace, or git not found during change request", taskId);
                    return null;
                }

                return new ChangeRequestContext(
                        task.workspace.workspaceId, task.workspace.git.url,
                        task.workspace.git.forkUrl, task.title, task.plan.requirement,
                        task.plan.id, task.workspace.git.credential != null ? task.workspace.git.credential.token : null,
                        task.workspace.git.branch, task.workspace.git.vendorType,
                        task.url, task.type, task.externalId,
                        task.type == SourceType.JIRA ? task.project.apiUrl : null,
                        task.type == SourceType.JIRA ? task.project.credential.token : null,
                        task.workspace.executionMode
                );
            });

            if (context == null) {
                return;
            }

            PipelineContext pipelineCtx = new PipelineContext(
                    context.workspaceId(), context.executionMode(),
                    context.requirement(), context.gitUrl(), context.gitBranch(),
                    context.gitToken(), context.forkUrl(), context.vendorType(),
                    context.taskTitle(), context.taskUrl(), taskId
            );

            doChangeRequestInternal(pipelineCtx, context.sourceType(), context.taskExternalId(),
                    context.jiraApiUrl(), context.jiraToken());
        } catch (Exception e) {
            LOG.errorf(e, "Change request failed for task %d", taskId);
            setChangeRequestError(taskId, e.getMessage());
        }
    }

    // --- Context-based flow (/implement) ---

    public void doChangeRequest(PipelineContext ctx) {
        try {
            doChangeRequestInternal(ctx, SourceType.GITHUB, null, null, null);
        } catch (Exception e) {
            LOG.errorf(e, "Change request failed for task %d", ctx.taskId());
            setChangeRequestError(ctx.taskId(), e.getMessage());
        }
    }

    // --- Shared implementation ---

    private void doChangeRequestInternal(PipelineContext ctx, SourceType sourceType,
                                         String taskExternalId, String jiraApiUrl, String jiraToken) throws Exception {
        Workspace workspace = workspaceManagerResolver.resolve(ctx.executionMode()).getWorkspace(ctx.workspaceId())
                .orElseThrow(() -> new WorkspaceException("Workspace not found: " + ctx.workspaceId()));
        if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
            throw new WorkspaceException("Workspace is not running");
        }

        try {
            workspaceGit.addAll(workspace);
            workspaceGit.commit(workspace, ctx.taskTitle());
        } catch (WorkspaceException e) {
            if (e.getMessage() != null && e.getMessage().contains("nothing to commit")) {
                LOG.infof("Task %d: No changes to commit, proceeding with push: %s", ctx.taskId(), e.getMessage());
            }
            throw e;
        }

        String baseBranch;
        if (ctx.gitBranch() != null && !ctx.gitBranch().isBlank()) {
            baseBranch = ctx.gitBranch();
        } else {
            String remoteInfo = workspace.exec("git", "remote", "show", "origin");
            baseBranch = remoteInfo.lines()
                    .filter(l -> l.contains("HEAD branch:"))
                    .map(l -> l.split(":\\s*")[1].trim())
                    .findFirst()
                    .orElse("main");
        }
        String branchName = workspaceGit.getCurrentBranch(workspace);

        String commitLog = workspace.exec("git", "log", "origin/" + baseBranch + "..HEAD", "--oneline");
        if (commitLog.isBlank()) {
            throw new WorkspaceException("No commits ahead of " + baseBranch + " — nothing to create a change request for");
        }
        LOG.infof("Task %d: Commits to push:\n%s", ctx.taskId(), commitLog);

        GitVendorType vendorType = ctx.vendorType();
        if (vendorType == null) {
            throw new IllegalStateException("Git vendor type is not set for git URL: " + ctx.gitUrl());
        }

        ChangeRequestProvider provider = providers.stream()
                .filter(p -> p.supports(vendorType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No change request provider for " + vendorType));

        // Push
        String pushTargetUrl = ctx.forkUrl() != null ? ctx.forkUrl() : ctx.gitUrl();
        if (ctx.gitToken() != null) {
            String authenticatedUrl = provider.buildAuthenticatedPushUrl(pushTargetUrl, ctx.gitToken());
            workspaceGit.pushToUrl(workspace, authenticatedUrl, "HEAD:" + branchName);
        } else if (ctx.forkUrl() == null) {
            workspaceGit.push(workspace, "origin", branchName);
        } else {
            workspaceGit.push(workspace, "fork", branchName);
        }

        String ownerRepo = GitManager.extractOwnerRepo(ctx.gitUrl());
        String description = ctx.requirement() != null ? ctx.requirement() : "";
        if (ctx.taskUrl() != null && !ctx.taskUrl().isBlank()) {
            description = "Closes " + ctx.taskUrl() + "\n\n" + description;
        }
        ChangeRequestParams params = new ChangeRequestParams(
                ctx.gitUrl(), ctx.forkUrl(), ctx.gitToken(),
                ownerRepo, branchName, baseBranch,
                ctx.taskTitle(), description
        );

        ChangeRequestResult result;
        try {
            result = provider.createChangeRequest(params);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                LOG.infof("Task %d: CR already exists, fetching existing URL", ctx.taskId());
                result = provider.findExistingChangeRequest(params);
            } else {
                throw e;
            }
        }

        String htmlUrl = result.htmlUrl();
        LOG.infof("Task %d: Change request created at %s", ctx.taskId(), htmlUrl);

        if (sourceType == SourceType.JIRA
                && jiraApiUrl != null
                && taskExternalId != null
                && jiraToken != null) {
            try {
                jiraSyncClient.addRemoteLink(jiraApiUrl, taskExternalId,
                        htmlUrl, "Pull Request: " + ctx.taskTitle(), jiraToken);
                LOG.infof("Task %d: Linked PR to Jira issue %s", ctx.taskId(), taskExternalId);
            } catch (Exception e) {
                LOG.warnf(e, "Task %d: Failed to link PR to Jira issue %s (non-fatal)",
                        ctx.taskId(), taskExternalId);
            }
        }

        QuarkusTransaction.requiringNew().run(() -> {
            TaskEntity task = TaskEntity.findById(ctx.taskId());
            if (task != null && task.plan != null) {
                task.plan.isChangeRequestInProgress = false;
                task.plan.changeRequestError = null;
                task.plan.changeRequestUrl = htmlUrl;
                task.plan.updatedAt = Instant.now();
                task.plan.persist();
            }
        });
    }

    private void setChangeRequestError(Long taskId, String errorMessage) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    task.plan.isChangeRequestInProgress = false;
                    task.plan.changeRequestError = errorMessage;
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception inner) {
            LOG.errorf(inner, "Failed to set error status for task %d change request", taskId);
        }
    }
}
