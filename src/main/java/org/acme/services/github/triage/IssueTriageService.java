package org.acme.services.github.triage;

import io.quarkiverse.githubapp.event.Issue;
import io.quarkiverse.githubapp.event.IssueComment;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.github.LabelConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class IssueTriageService {

    private static final Logger LOG = Logger.getLogger(IssueTriageService.class);

    private static final Set<String> STALLING_TRIAGE_LABELS = Set.of(
            "triage/needs-information", "triage/not-reproducible");

    @Inject
    IssueTriageAiService aiService;

    @Inject
    LabelConfig labelConfig;

    @ConfigProperty(name = "tsd-agent.triage.auto-apply-threshold")
    double autoApplyThreshold;

    @ConfigProperty(name = "tsd-agent.triage.recent-issues-count")
    int recentIssuesCount;

    void onLabeled(@Issue.Labeled GHEventPayload.Issue payload) {
        // Only trigger when needs-triage is applied (Stage 1 complete)
        if (!"needs-triage".equals(payload.getLabel().getName())) {
            return;
        }

        triggerTriage(payload.getIssue(), payload.getRepository(), "labeled needs-triage");
    }

    void onEdited(@Issue.Edited GHEventPayload.Issue payload) {
        GHIssue issue = payload.getIssue();
        if (!hasStallingTriageLabel(issue)) {
            return;
        }

        triggerTriage(issue, payload.getRepository(), "issue edited");
    }

    void onCommentCreated(@IssueComment.Created GHEventPayload.IssueComment payload) {
        GHIssue issue = payload.getIssue();
        if (!hasStallingTriageLabel(issue)) {
            return;
        }

        // Only re-triage when the original reporter adds a comment
        try {
            String reporter = issue.getUser().getLogin();
            String commenter = payload.getComment().getUser().getLogin();
            if (!reporter.equals(commenter)) {
                return;
            }
        } catch (IOException e) {
            LOG.warnf("Failed to check comment author for issue #%d: %s", issue.getNumber(), e.getMessage());
            return;
        }

        triggerTriage(issue, payload.getRepository(), "reporter commented");
    }

    private void triggerTriage(GHIssue issue, GHRepository repo, String trigger) {
        String repoName = repo.getFullName();
        int issueNumber = issue.getNumber();

        LOG.infof("AI re-triage started for issue #%d in %s (trigger: %s)", issueNumber, repoName, trigger);

        Thread.startVirtualThread(() -> runWithRequestContext(() -> {
            try {
                doTriage(issue, repo, repoName, issueNumber);
            } catch (Exception e) {
                LOG.warnf(e, "AI triage failed for issue #%d in %s", issueNumber, repoName);
            }
        }));
    }

    void doTriage(GHIssue issue, GHRepository repo, String repoName, int issueNumber) throws IOException {
        String recentIssues = fetchRecentIssueTitles(repo, issueNumber);

        String triageLabels = labelConfig.getLabels().stream()
                .map(l -> l.name)
                .filter(n -> n.startsWith("triage/"))
                .map(n -> n.substring("triage/".length()))
                .collect(Collectors.joining(", "));

        String title = issue.getTitle();
        String body = issue.getBody() != null ? issue.getBody() : "";

        // Append issue comments so the AI sees the full conversation
        for (GHIssueComment c : issue.listComments()) {
            if (c.getBody() != null && !c.getBody().contains(TriageCommentFormatter.AI_TRIAGE_MARKER)) {
                body += "\n\n---\n**Comment by " + c.getUser().getLogin() + ":**\n" + c.getBody();
            }
        }

        TriageResult result = aiService.triageIssue(title, body, triageLabels, recentIssues);

        // Remove previous AI triage comment if present
        removePreviousTriageComment(issue);

        // Post new comment
        String comment = TriageCommentFormatter.format(result);
        issue.comment(comment);
        LOG.infof("AI triage completed for issue #%d: triage=%s (%.0f%%)",
                issueNumber, result.suggestedTriage(), result.confidence() * 100);

        // Auto-apply triage label if confidence exceeds threshold
        if (result.confidence() >= autoApplyThreshold) {
            String newTriageLabel = "triage/" + result.suggestedTriage();
            if (labelConfig.findByName(newTriageLabel) != null) {
                // Remove old triage/* labels first
                removeOldTriageLabels(issue);
                issue.addLabels(newTriageLabel);
                LOG.infof("Auto-applied label for issue #%d: %s", issueNumber, newTriageLabel);
            }
        }
    }

    private boolean hasStallingTriageLabel(GHIssue issue) {
        return issue.getLabels().stream()
                .map(GHLabel::getName)
                .anyMatch(STALLING_TRIAGE_LABELS::contains);
    }

    private void removeOldTriageLabels(GHIssue issue) {
        for (GHLabel label : issue.getLabels()) {
            if (label.getName().startsWith("triage/")) {
                try {
                    issue.removeLabel(label.getName());
                } catch (IOException e) {
                    LOG.debugf("Could not remove label %s from issue #%d: %s",
                            label.getName(), issue.getNumber(), e.getMessage());
                }
            }
        }
    }

    private void removePreviousTriageComment(GHIssue issue) {
        try {
            for (GHIssueComment comment : issue.listComments()) {
                if (comment.getBody() != null
                        && comment.getBody().contains(TriageCommentFormatter.AI_TRIAGE_MARKER)) {
                    comment.delete();
                    break;
                }
            }
        } catch (IOException e) {
            LOG.debugf("Could not remove previous triage comment from issue #%d: %s",
                    issue.getNumber(), e.getMessage());
        }
    }

    private String fetchRecentIssueTitles(GHRepository repo, int currentIssueNumber) {
        try {
            var sb = new StringBuilder();
            int count = 0;
            for (GHIssue recentIssue : repo.queryIssues().state(GHIssueState.OPEN).pageSize(recentIssuesCount).list()) {
                if (recentIssue.getNumber() == currentIssueNumber) {
                    continue;
                }
                if (recentIssue.isPullRequest()) {
                    continue;
                }
                sb.append("- #").append(recentIssue.getNumber())
                        .append(": ").append(recentIssue.getTitle()).append("\n");
                count++;
                if (count >= recentIssuesCount) {
                    break;
                }
            }
            return sb.length() > 0 ? sb.toString() : "No recent open issues found.";
        } catch (Exception e) {
            LOG.warnf("Failed to fetch recent issues for duplicate detection: %s", e.getMessage());
            return "Could not fetch recent issues for comparison.";
        }
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
}
