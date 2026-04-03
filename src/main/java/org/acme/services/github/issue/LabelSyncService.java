package org.acme.services.github.issue;

import io.quarkiverse.githubapp.GitHubClientProvider;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class LabelSyncService {

    private static final Logger LOG = Logger.getLogger(LabelSyncService.class);

    @Inject
    GitHubClientProvider gitHubClientProvider;

    @Inject
    LabelConfig labelConfig;

    @ConfigProperty(name = "tsd-agent.github.label-sync.enabled")
    boolean enabled;

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            LOG.info("GitHub label sync disabled (tsd-agent.github.label-sync.enabled=false)");
            return;
        }

        Thread.ofVirtual().name("github-label-sync").start(this::syncAllInstallations);
    }

    private void syncAllInstallations() {
        try {
            GitHub appClient = gitHubClientProvider.getApplicationClient();
            for (GHAppInstallation installation : appClient.getApp().listInstallations()) {
                long installationId = installation.getId();
                GitHub installClient = gitHubClientProvider.getInstallationClient(installationId);

                for (GHRepository repo : installClient.getInstallation().listRepositories()) {
                    try {
                        syncLabels(repo);
                    } catch (IOException e) {
                        LOG.warnf("Failed to sync labels for %s: %s", repo.getFullName(), e.getMessage());
                    }
                }
            }
            LOG.info("GitHub label sync completed");
        } catch (Exception e) {
            LOG.warn("Failed to sync GitHub labels: " + e.getMessage());
        }
    }

    private void syncLabels(GHRepository repo) throws IOException {
        Map<String, GHLabel> existing = repo.listLabels().toList().stream()
                .collect(Collectors.toMap(l -> l.getName().toLowerCase(), l -> l, (a, b) -> a));

        for (LabelDefinition wanted : labelConfig.getLabels()) {
            GHLabel current = existing.get(wanted.name.toLowerCase());

            if (current == null) {
                repo.createLabel(wanted.name, wanted.color, wanted.description);
                LOG.infof("[%s] Created label: %s", repo.getFullName(), wanted.name);
            } else {
                boolean colorChanged = !wanted.color.equalsIgnoreCase(current.getColor());
                boolean descChanged = wanted.description != null
                        && !wanted.description.equals(current.getDescription());

                if (colorChanged || descChanged) {
                    if (colorChanged) {
                        current.set().color(wanted.color);
                    }
                    if (descChanged) {
                        current.set().description(wanted.description);
                    }
                    LOG.infof("[%s] Updated label: %s", repo.getFullName(), wanted.name);
                }
            }
        }
    }
}
