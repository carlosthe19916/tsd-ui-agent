package org.acme.services.github.issue.triage;

import io.quarkiverse.githubapp.GitHubClientProvider;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class StaleIssueService {

    private static final Logger LOG = Logger.getLogger(StaleIssueService.class);

    private static final Set<String> STALLING_LABELS = Set.of(
            "triage/needs-information", "triage/not-reproducible");

    @Inject
    GitHubClientProvider gitHubClientProvider;

    @ConfigProperty(name = "tsd-agent.triage.stale-issue-close.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "tsd-agent.triage.stale-issue-close.days", defaultValue = "30")
    int staleDays;

    @Scheduled(cron = "0 0 8 * * ?")
    void closeStaleIssues() {
        if (!enabled) {
            return;
        }

        LOG.info("Stale issue check started");

        try {
            GitHub appClient = gitHubClientProvider.getApplicationClient();
            for (GHAppInstallation installation : appClient.getApp().listInstallations()) {
                long installationId = installation.getId();
                GitHub installClient = gitHubClientProvider.getInstallationClient(installationId);

                for (GHRepository repo : installClient.getInstallation().listRepositories()) {
                    try {
                        processRepo(repo);
                    } catch (IOException e) {
                        LOG.warnf("Failed to check stale issues for %s: %s", repo.getFullName(), e.getMessage());
                    }
                }
            }
            LOG.info("Stale issue check completed");
        } catch (Exception e) {
            LOG.warn("Failed to run stale issue check: " + e.getMessage());
        }
    }

    private void processRepo(GHRepository repo) throws IOException {
        Instant cutoff = Instant.now().minus(staleDays, ChronoUnit.DAYS);

        for (GHIssue issue : repo.queryIssues().state(GHIssueState.OPEN).list()) {
            if (issue.isPullRequest()) {
                continue;
            }

            List<String> labelNames = issue.getLabels().stream()
                    .map(GHLabel::getName)
                    .toList();

            String stallingLabel = labelNames.stream()
                    .filter(STALLING_LABELS::contains)
                    .findFirst()
                    .orElse(null);

            if (stallingLabel == null) {
                continue;
            }

            if (issue.getUpdatedAt().toInstant().isBefore(cutoff)) {
                String comment = "This issue has been automatically closed because it has been labeled `"
                        + stallingLabel + "` for over " + staleDays
                        + " days without activity.\n\n"
                        + "If you believe this issue is still relevant, please reopen it with the requested information.";

                issue.comment(comment);
                issue.close();
                LOG.infof("[%s] Closed stale issue #%d (label: %s, last updated: %s)",
                        repo.getFullName(), issue.getNumber(), stallingLabel, issue.getUpdatedAt());
            }
        }
    }
}
