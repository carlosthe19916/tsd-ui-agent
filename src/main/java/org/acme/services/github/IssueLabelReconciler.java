package org.acme.services.github;

import io.quarkiverse.githubapp.event.Issue;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class IssueLabelReconciler {

    private static final Logger LOG = Logger.getLogger(IssueLabelReconciler.class);

    private static final String TRIAGE_COMMENT = """
            This issue is currently awaiting triage.
            If contributors determine this is a relevant issue, they will accept it by applying the `triage/accepted` label and provide further guidance.
            The `triage/accepted` label can be added by org members.""";

    private static final String GOOD_FIRST_ISSUE_COMMENT = """
            This issue has been marked 'good first issue'
            Please, make sure it aligns with the criteria found [here](https://contribute.cncf.io/maintainers/templates/issue-labels/#good-first-issue)""";

    void onOpened(@Issue.Opened GHEventPayload.Issue payload) throws IOException {
        reconcileLabels(payload.getIssue());
    }

    void onEdited(@Issue.Edited GHEventPayload.Issue payload) throws IOException {
        reconcileLabels(payload.getIssue());
    }

    void onReopened(@Issue.Reopened GHEventPayload.Issue payload) throws IOException {
        reconcileLabels(payload.getIssue());
    }

    void onLabeled(@Issue.Labeled GHEventPayload.Issue payload) throws IOException {
        reconcileLabels(payload.getIssue());
    }

    void onUnlabeled(@Issue.Unlabeled GHEventPayload.Issue payload) throws IOException {
        reconcileLabels(payload.getIssue());
    }

    void reconcileLabels(GHIssue issue) throws IOException {
        Set<String> currentLabels = issue.getLabels().stream()
                .map(GHLabel::getName)
                .collect(Collectors.toSet());

        List<String> toAdd = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        // Triage: any triage/* label satisfies the requirement
        boolean hasTriage = currentLabels.stream().anyMatch(l -> l.startsWith("triage/"));
        if (hasTriage) {
            if (currentLabels.contains("needs-triage")) {
                toRemove.add("needs-triage");
            }
        } else {
            if (!currentLabels.contains("needs-triage")) {
                toAdd.add("needs-triage");
            }
            ensureComment(issue, TRIAGE_COMMENT, true);
        }

        // Kind: any kind/* label satisfies the requirement
        boolean hasKind = currentLabels.stream().anyMatch(l -> l.startsWith("kind/"));
        if (hasKind) {
            if (currentLabels.contains("needs-kind")) {
                toRemove.add("needs-kind");
            }
        } else {
            if (!currentLabels.contains("needs-kind")) {
                toAdd.add("needs-kind");
            }
        }

        // Priority: any priority/* label satisfies the requirement
        boolean hasPriority = currentLabels.stream().anyMatch(l -> l.startsWith("priority/"));
        if (hasPriority) {
            if (currentLabels.contains("needs-priority")) {
                toRemove.add("needs-priority");
            }
        } else {
            if (!currentLabels.contains("needs-priority")) {
                toAdd.add("needs-priority");
            }
        }

        // Good first issue
        boolean hasGoodFirstIssue = currentLabels.contains("good first issue");
        ensureComment(issue, GOOD_FIRST_ISSUE_COMMENT, hasGoodFirstIssue);

        if (!toAdd.isEmpty()) {
            issue.addLabels(toAdd.toArray(String[]::new));
            LOG.infof("Issue #%d: added labels %s", issue.getNumber(), toAdd);
        }

        for (String label : toRemove) {
            try {
                issue.removeLabel(label);
                LOG.infof("Issue #%d: removed label %s", issue.getNumber(), label);
            } catch (IOException e) {
                // Label may have already been removed
                LOG.debugf("Issue #%d: could not remove label %s: %s", issue.getNumber(), label, e.getMessage());
            }
        }
    }

    private void ensureComment(GHIssue issue, String body, boolean shouldExist) throws IOException {
        for (GHIssueComment comment : issue.listComments()) {
            if (comment.getBody() != null && comment.getBody().contains(body)) {
                if (!shouldExist) {
                    comment.delete();
                    LOG.infof("Issue #%d: deleted comment", issue.getNumber());
                }
                return;
            }
        }

        if (shouldExist) {
            issue.comment(body);
            LOG.infof("Issue #%d: posted comment", issue.getNumber());
        }
    }
}
