package org.acme.services.github.issue.prioritization;

import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.github.issue.LabelConfig;
import org.acme.services.github.issue.classification.ClassificationCommentFormatter;
import org.acme.services.github.issue.triage.TriageCommentFormatter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.util.stream.Collectors;

@ApplicationScoped
public class IssuePrioritizationService {

    private static final Logger LOG = Logger.getLogger(IssuePrioritizationService.class);

    @Inject
    IssuePrioritizationAiService aiService;

    @Inject
    LabelConfig labelConfig;

    @ConfigProperty(name = "tsd-agent.prioritization.auto-apply-threshold")
    double autoApplyThreshold;

    void onLabeled(@Issue.Labeled GHEventPayload.Issue payload) {
        // Only trigger when a kind/* label is applied (Stage 3 complete)
        if (!payload.getLabel().getName().startsWith("kind/")) {
            return;
        }

        // Only prioritize if needs-priority is still present
        GHIssue issue = payload.getIssue();
        boolean needsPriority = issue.getLabels().stream()
                .map(GHLabel::getName)
                .anyMatch("needs-priority"::equals);
        if (!needsPriority) {
            return;
        }

        String repoName = payload.getRepository().getFullName();
        int issueNumber = issue.getNumber();

        LOG.infof("AI prioritization started for issue #%d in %s", issueNumber, repoName);

        Thread.startVirtualThread(() -> runWithRequestContext(() -> {
            try {
                doPrioritize(issue, issueNumber);
            } catch (Exception e) {
                LOG.warnf(e, "AI prioritization failed for issue #%d in %s", issueNumber, repoName);
            }
        }));
    }

    void doPrioritize(GHIssue issue, int issueNumber) throws IOException {
        String priorityLabels = labelConfig.getLabels().stream()
                .map(l -> l.name)
                .filter(n -> n.startsWith("priority/"))
                .map(n -> n.substring("priority/".length()))
                .collect(Collectors.joining(", "));

        String title = issue.getTitle();
        String body = issue.getBody() != null ? issue.getBody() : "";

        // Include comments for context
        for (GHIssueComment c : issue.listComments()) {
            if (c.getBody() != null
                    && !c.getBody().contains(TriageCommentFormatter.AI_TRIAGE_MARKER)
                    && !c.getBody().contains(ClassificationCommentFormatter.AI_CLASSIFICATION_MARKER)
                    && !c.getBody().contains(PrioritizationCommentFormatter.AI_PRIORITIZATION_MARKER)) {
                body += "\n\n---\n**Comment by " + c.getUser().getLogin() + ":**\n" + c.getBody();
            }
        }

        PrioritizationResult result = aiService.prioritizeIssue(title, body, priorityLabels);

        // Post comment
        String comment = PrioritizationCommentFormatter.format(result);
        issue.comment(comment);
        LOG.infof("AI prioritization completed for issue #%d: priority=%s (%.0f%%)",
                issueNumber, result.suggestedPriority(), result.confidence() * 100);

        // Auto-apply priority label if confidence exceeds threshold (higher bar than triage/kind)
        if (result.confidence() >= autoApplyThreshold) {
            String priorityLabel = "priority/" + result.suggestedPriority();
            if (labelConfig.findByName(priorityLabel) != null) {
                issue.addLabels(priorityLabel);
                LOG.infof("Auto-applied label for issue #%d: %s", issueNumber, priorityLabel);
            }
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
