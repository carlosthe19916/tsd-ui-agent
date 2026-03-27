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

            Workspace workspace = workspaceManagerResolver.resolve(context.executionMode()).getWorkspace(context.workspaceId())
                    .orElseThrow(() -> new WorkspaceException("Workspace not found: " + context.workspaceId()));
            if (workspace.healthStatus().status() != WorkspaceHealthStatus.Status.RUNNING) {
                throw new WorkspaceException("Workspace is not running");
            }

            try {
                workspaceGit.addAll(workspace);
                workspaceGit.commit(workspace, context.taskTitle());
            } catch (WorkspaceException e) {
                if (e.getMessage() != null && e.getMessage().contains("nothing to commit")) {
                    LOG.infof("Task %d: No changes to commit, proceeding with push: %s", taskId, e.getMessage());
                }
                throw e;
            }

            String baseBranch;
            if (context.gitBranch() != null && !context.gitBranch().isBlank()) {
                baseBranch = context.gitBranch();
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
            LOG.infof("Task %d: Commits to push:\n%s", taskId, commitLog);

            GitVendorType vendorType = context.vendorType();
            if (vendorType == null) {
                throw new IllegalStateException("Git vendor type is not set for git URL: " + context.gitUrl());
            }

            ChangeRequestProvider provider = providers.stream()
                    .filter(p -> p.supports(vendorType))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No change request provider for " + vendorType));

            // Push
            String pushTargetUrl = context.forkUrl() != null ? context.forkUrl() : context.gitUrl();
            if (context.gitToken() != null) {
                String authenticatedUrl = provider.buildAuthenticatedPushUrl(pushTargetUrl, context.gitToken());
                workspaceGit.pushToUrl(workspace, authenticatedUrl, "HEAD:" + branchName);
            } else if (context.forkUrl() == null) {
                workspaceGit.push(workspace, "origin", branchName);
            } else {
                workspaceGit.push(workspace, "fork", branchName);
            }

            String ownerRepo = GitManager.extractOwnerRepo(context.gitUrl());
            String description = context.requirement() != null ? context.requirement() : "";
            if (context.taskUrl() != null && !context.taskUrl().isBlank()) {
                description = "Fixes: " + context.taskUrl() + "\n\n" + description;
            }
            ChangeRequestParams params = new ChangeRequestParams(
                    context.gitUrl(), context.forkUrl(), context.gitToken(),
                    ownerRepo, branchName, baseBranch,
                    context.taskTitle(), description
            );

            ChangeRequestResult result;
            try {
                result = provider.createChangeRequest(params);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    LOG.infof("Task %d: CR already exists, fetching existing URL", taskId);
                    result = provider.findExistingChangeRequest(params);
                } else {
                    throw e;
                }
            }

            String htmlUrl = result.htmlUrl();
            LOG.infof("Task %d: Change request created at %s", taskId, htmlUrl);

            if (context.sourceType() == SourceType.JIRA
                    && context.jiraApiUrl() != null
                    && context.taskExternalId() != null
                    && context.jiraToken() != null) {
                try {
                    jiraSyncClient.addRemoteLink(context.jiraApiUrl(), context.taskExternalId(),
                            htmlUrl, "Pull Request: " + context.taskTitle(), context.jiraToken());
                    LOG.infof("Task %d: Linked PR to Jira issue %s", taskId, context.taskExternalId());
                } catch (Exception e) {
                    LOG.warnf(e, "Task %d: Failed to link PR to Jira issue %s (non-fatal)",
                            taskId, context.taskExternalId());
                }
            }

            String finalHtmlUrl = htmlUrl;
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    task.plan.isChangeRequestInProgress = false;
                    task.plan.changeRequestError = null;
                    task.plan.changeRequestUrl = finalHtmlUrl;
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Change request failed for task %d", taskId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null) {
                        task.plan.isChangeRequestInProgress = false;
                        task.plan.changeRequestError = e.getMessage();
                        task.plan.updatedAt = Instant.now();
                        task.plan.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to set error status for task %d change request", taskId);
            }
        }
    }
}
