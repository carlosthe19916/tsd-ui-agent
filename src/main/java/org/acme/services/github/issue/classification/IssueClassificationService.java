package org.acme.services.github.issue.classification;

import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.github.issue.LabelConfig;
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
public class IssueClassificationService {

    private static final Logger LOG = Logger.getLogger(IssueClassificationService.class);

    @Inject
    IssueClassificationAiService aiService;

    @Inject
    LabelConfig labelConfig;

    @ConfigProperty(name = "tsd-agent.triage.auto-apply-threshold")
    double autoApplyThreshold;

    void onLabeled(@Issue.Labeled GHEventPayload.Issue payload) {
        // Only trigger when triage/accepted is applied (Stage 2 complete)
        if (!"triage/accepted".equals(payload.getLabel().getName())) {
            return;
        }

        // Only classify if needs-kind is still present
        GHIssue issue = payload.getIssue();
        boolean needsKind = issue.getLabels().stream()
                .map(GHLabel::getName)
                .anyMatch("needs-kind"::equals);
        if (!needsKind) {
            return;
        }

        String repoName = payload.getRepository().getFullName();
        int issueNumber = issue.getNumber();

        LOG.infof("AI classification started for issue #%d in %s", issueNumber, repoName);

        Thread.startVirtualThread(() -> runWithRequestContext(() -> {
            try {
                doClassify(issue, issueNumber);
            } catch (Exception e) {
                LOG.warnf(e, "AI classification failed for issue #%d in %s", issueNumber, repoName);
            }
        }));
    }

    void doClassify(GHIssue issue, int issueNumber) throws IOException {
        String kindLabels = labelConfig.getLabels().stream()
                .map(l -> l.name)
                .filter(n -> n.startsWith("kind/"))
                .map(n -> n.substring("kind/".length()))
                .collect(Collectors.joining(", "));

        String title = issue.getTitle();
        String body = issue.getBody() != null ? issue.getBody() : "";

        // Include comments for context
        for (GHIssueComment c : issue.listComments()) {
            if (c.getBody() != null
                    && !c.getBody().contains(TriageCommentFormatter.AI_TRIAGE_MARKER)
                    && !c.getBody().contains(ClassificationCommentFormatter.AI_CLASSIFICATION_MARKER)) {
                body += "\n\n---\n**Comment by " + c.getUser().getLogin() + ":**\n" + c.getBody();
            }
        }

        ClassificationResult result = aiService.classifyIssue(title, body, kindLabels);

        // Post comment
        String comment = ClassificationCommentFormatter.format(result);
        issue.comment(comment);
        LOG.infof("AI classification completed for issue #%d: kind=%s (%.0f%%)",
                issueNumber, result.suggestedKind(), result.confidence() * 100);

        // Auto-apply kind label if confidence exceeds threshold
        if (result.confidence() >= autoApplyThreshold) {
            String kindLabel = "kind/" + result.suggestedKind();
            if (labelConfig.findByName(kindLabel) != null) {
                issue.addLabels(kindLabel);
                LOG.infof("Auto-applied label for issue #%d: %s", issueNumber, kindLabel);
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
