package org.acme.services.github.issue.implement;

import io.quarkiverse.githubapp.InstallationTokenProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.ExecutionMode;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.models.jpa.entity.PlanEntity;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.ExecutionOutputBroadcaster;
import static org.acme.services.ExecutionOutputBroadcaster.Channel;
import org.acme.services.PipelineContext;
import org.acme.services.PlanService;
import org.acme.services.devcontainer.DevcontainerConfigGenerator;
import org.acme.services.devcontainer.EnrichmentService;
import org.acme.services.git.GitManager;
import org.acme.services.github.issue.classification.ClassificationCommentFormatter;
import org.acme.services.github.issue.prioritization.PrioritizationCommentFormatter;
import org.acme.services.github.issue.triage.TriageCommentFormatter;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.acme.services.workspace.WorkspaceRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class IssueImplementationService {

    private static final Logger LOG = Logger.getLogger(IssueImplementationService.class);

    private static final String BOT_SUFFIX = "[bot]";
    private static final String SLASH_COMMAND_PREFIX = "/";

    private static final Set<String> AI_MARKERS = Set.of(
            TriageCommentFormatter.AI_TRIAGE_MARKER,
            ClassificationCommentFormatter.AI_CLASSIFICATION_MARKER,
            PrioritizationCommentFormatter.AI_PRIORITIZATION_MARKER);

    @Inject
    InstallationTokenProvider tokenProvider;

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @Inject
    GitManager gitManager;

    @Inject
    EnrichmentService enrichmentService;

    @Inject
    DevcontainerConfigGenerator devcontainerConfigGenerator;

    @Inject
    WorkspaceManagerResolver workspaceManagerResolver;

    @Inject
    PlanService planService;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    public void implement(GHEventPayload.IssueComment payload) throws IOException {
        GHIssue issue = payload.getIssue();
        GHRepository repo = payload.getRepository();
        int issueNumber = issue.getNumber();
        String repoName = repo.getFullName();

        // Validate issue is "Ready to Work"
        Set<String> labels = issue.getLabels().stream()
                .map(GHLabel::getName)
                .collect(Collectors.toSet());

        String missingLabels = validateReadyToWork(labels);
        if (missingLabels != null) {
            issue.comment("Cannot implement — this issue is not ready for implementation.\n\nMissing: " + missingLabels);
            return;
        }

        LOG.infof("Implementation requested for issue #%d in %s", issueNumber, repoName);
        issue.comment("Implementation started. A PR will be created when ready.");

        Thread.startVirtualThread(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            Long taskId = null;
            try {
                taskId = doImplement(payload, issue, repo, issueNumber, repoName);
            } catch (Exception e) {
                LOG.errorf(e, "Implementation failed for issue #%d in %s", issueNumber, repoName);
                // Clear stuck provisioning state
                if (taskId != null) {
                    setProvisioningError(taskId, e.getMessage());
                }
                try {
                    issue.comment("Implementation failed: " + e.getMessage());
                } catch (IOException commentError) {
                    LOG.errorf(commentError, "Failed to post error comment on issue #%d", issueNumber);
                }
            } finally {
                requestContext.terminate();
            }
        });
    }

    private Long doImplement(GHEventPayload.IssueComment payload, GHIssue issue,
                             GHRepository repo, int issueNumber, String repoName) throws Exception {
        // Get installation token for git operations
        long installationId = payload.getInstallation().getId();
        String gitToken = tokenProvider.getInstallationToken(installationId).token();

        // Build enriched requirement
        String requirement = buildRequirement(issue);

        // Create entities for tracking
        record EntityIds(Long taskId, Long workspaceEntityId) {}

        EntityIds ids = QuarkusTransaction.requiringNew().call(() -> {
            PlanEntity plan = new PlanEntity();
            plan.requirement = requirement;
            plan.isRequirementInProgress = false;
            plan.createdAt = Instant.now();
            plan.updatedAt = Instant.now();
            plan.persist();

            WorkspaceEntity workspace = new WorkspaceEntity();
            workspace.executionMode = ExecutionMode.DOCKER;
            workspace.isProvisioningInProgress = true;
            workspace.persist();

            TaskEntity task = new TaskEntity();
            task.externalId = String.valueOf(issueNumber);
            task.title = issue.getTitle();
            task.description = issue.getBody();
            task.url = issue.getHtmlUrl().toString();
            task.type = SourceType.GITHUB;
            task.status = TaskStatus.IN_PROGRESS;
            task.createdAt = Instant.now();
            task.updatedAt = Instant.now();
            task.plan = plan;
            task.workspace = workspace;
            task.persist();

            return new EntityIds(task.id, workspace.id);
        });

        Long taskId = ids.taskId();
        broadcaster.start(Channel.WORKSPACE, ids.workspaceEntityId());

        String repoUrl = repo.getHtmlUrl().toString();
        String cloneDir = GitManager.cloneDir(baseDir, repoUrl, null);

        if (!Files.isDirectory(Path.of(cloneDir))) {
            LOG.infof("Issue #%d: Cloning repository %s", issueNumber, repoUrl);
            gitManager.cloneRepository(repoUrl, null, cloneDir, gitToken);
        } else {
            LOG.infof("Issue #%d: Repository already cloned, pulling latest", issueNumber);
            gitManager.pullRepository(cloneDir, null, gitToken);
        }

        String sanitized = GitManager.sanitizeUrl(repoUrl);
        EnrichmentService.EnrichmentResult enrichment = enrichmentService.enrich(
                Path.of(cloneDir), line -> LOG.debugf("Issue #%d enrich: %s", issueNumber, line));
        devcontainerConfigGenerator.generateBaseConfig(sanitized, GitManager.DEFAULT_BRANCH_DIR, Path.of(cloneDir), enrichment);

        // Provision workspace (creates worktree + runs devcontainer up)
        WorkspaceRequest request = new WorkspaceRequest(repoUrl, null, gitToken, null, null, Map.of());
        Workspace workspace;
        try {
            workspace = workspaceManagerResolver.resolve(ExecutionMode.DOCKER)
                    .provision(request, line -> broadcaster.publish(Channel.WORKSPACE, ids.workspaceEntityId(), line));
        } finally {
            broadcaster.complete(Channel.WORKSPACE, ids.workspaceEntityId());
        }

        // Store workspace ID
        QuarkusTransaction.requiringNew().run(() -> {
            TaskEntity task = TaskEntity.findById(taskId);
            if (task != null && task.workspace != null) {
                task.workspace.workspaceId = workspace.id();
                task.workspace.isProvisioningInProgress = false;
                task.workspace.persist();
            }
        });

        LOG.infof("Workspace provisioned for issue #%d: %s", issueNumber, workspace.id());

        // Build pipeline context and run
        PipelineContext ctx = new PipelineContext(
                workspace.id(),
                ExecutionMode.DOCKER,
                requirement,
                repoUrl,
                null, // default branch
                gitToken,
                null, // no fork
                GitVendorType.GITHUB,
                issue.getTitle(),
                issue.getHtmlUrl().toString(),
                taskId
        );

        planService.doFullPipeline(ctx);

        // Post PR link on the issue
        String prUrl = QuarkusTransaction.requiringNew().call(() -> {
            TaskEntity task = TaskEntity.findById(taskId);
            return (task != null && task.plan != null) ? task.plan.changeRequestUrl : null;
        });

        if (prUrl != null) {
            issue.comment("PR created: " + prUrl);
            LOG.infof("Implementation completed for issue #%d: %s", issueNumber, prUrl);
        }

        return taskId;
    }

    private void setProvisioningError(Long taskId, String errorMessage) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.workspace != null) {
                    task.workspace.isProvisioningInProgress = false;
                    task.workspace.provisioningError = errorMessage;
                    task.workspace.persist();
                }
                if (task != null) {
                    task.status = TaskStatus.OPEN;
                    task.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to set provisioning error for task %d", taskId);
        }
    }

    private String buildRequirement(GHIssue issue) throws IOException {
        var sb = new StringBuilder();

        // Labels
        String labels = issue.getLabels().stream()
                .map(GHLabel::getName)
                .collect(Collectors.joining(", "));

        sb.append("## Issue #").append(issue.getNumber()).append(": ").append(issue.getTitle()).append("\n");
        sb.append("**Labels:** ").append(labels).append("\n\n");

        // Body
        sb.append("## Description\n");
        sb.append(issue.getBody() != null ? issue.getBody() : "No description provided.").append("\n\n");

        // Comments (only meaningful human discussion)
        sb.append("## Discussion\n");
        boolean hasComments = false;
        for (GHIssueComment c : issue.listComments()) {
            if (c.getBody() == null) continue;
            // Skip AI marker comments
            if (AI_MARKERS.stream().anyMatch(m -> c.getBody().contains(m))) continue;
            if (c.getUser().getLogin().endsWith(BOT_SUFFIX)) continue;
            String trimmed = c.getBody().trim();
            if (trimmed.startsWith(SLASH_COMMAND_PREFIX) && !trimmed.contains("\n")) continue;

            sb.append("---\n**Comment by ").append(c.getUser().getLogin()).append(":**\n");
            sb.append(c.getBody()).append("\n\n");
            hasComments = true;
        }
        if (!hasComments) {
            sb.append("No discussion.\n");
        }

        return sb.toString();
    }

    String validateReadyToWork(Set<String> labels) {
        var missing = new StringBuilder();
        if (!labels.contains("triage/accepted")) {
            missing.append("`triage/accepted` ");
        }
        if (labels.stream().noneMatch(l -> l.startsWith("kind/"))) {
            missing.append("`kind/*` ");
        }
        if (labels.stream().noneMatch(l -> l.startsWith("priority/"))) {
            missing.append("`priority/*` ");
        }
        if (labels.stream().anyMatch(l -> l.startsWith("needs-"))) {
            missing.append("(has `needs-*` labels) ");
        }
        return missing.isEmpty() ? null : missing.toString().trim();
    }
}
