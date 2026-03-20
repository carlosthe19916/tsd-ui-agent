package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.changerequest.ChangeRequestParams;
import org.acme.services.changerequest.ChangeRequestProvider;
import org.acme.services.changerequest.ChangeRequestResult;
import org.acme.services.git.GitException;
import org.acme.services.git.GitManager;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class ChangeRequestService {

    private static final Logger LOG = Logger.getLogger(ChangeRequestService.class);

    @Inject
    GitManager gitManager;

    @Inject
    Instance<ChangeRequestProvider> providers;

    public void triggerChangeRequest(Long taskId) {
        Thread.startVirtualThread(() -> doChangeRequest(taskId));
    }

    void doChangeRequest(Long taskId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            record ChangeRequestContext(
                    String worktreePath, String mainClonePath, String gitUrl,
                    String forkUrl, String taskTitle, String requirement,
                    Long planId, String gitToken, String gitBranch,
                    GitVendorType vendorType
            ) {}

            ChangeRequestContext context = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null || task.plan.git == null) {
                    LOG.warnf("Task %d, plan, or git not found during change request", taskId);
                    return null;
                }

                return new ChangeRequestContext(
                        task.plan.worktreePath, task.plan.git.localPath, task.plan.git.url,
                        task.plan.git.forkUrl, task.title, task.plan.requirement,
                        task.plan.id, task.plan.git.credential != null ? task.plan.git.credential.token : null,
                        task.plan.git.branch, task.plan.git.vendorType
                );
            });

            if (context == null) {
                return;
            }

            try {
                gitManager.addAll(context.worktreePath());
                gitManager.commit(context.worktreePath(), context.taskTitle());
            } catch (GitException e) {
                LOG.infof("Task %d: No changes to commit, proceeding with push: %s", taskId, e.getMessage());
            }

            String baseBranch = (context.gitBranch() != null && !context.gitBranch().isBlank())
                    ? context.gitBranch()
                    : gitManager.getCurrentBranch(context.mainClonePath());
            String branchName = GitManager.planBranchName(context.planId());

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
                gitManager.pushToUrl(context.worktreePath(), authenticatedUrl, "HEAD:" + branchName);
            } else if (context.forkUrl() == null) {
                gitManager.push(context.worktreePath(), "origin", branchName);
            } else {
                gitManager.push(context.worktreePath(), "fork", branchName);
            }

            String ownerRepo = GitManager.extractOwnerRepo(context.gitUrl());
            ChangeRequestParams params = new ChangeRequestParams(
                    context.gitUrl(), context.forkUrl(), context.gitToken(),
                    ownerRepo, branchName, baseBranch,
                    context.taskTitle(), context.requirement() != null ? context.requirement() : ""
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
        } finally {
            requestContext.terminate();
        }
    }
}
