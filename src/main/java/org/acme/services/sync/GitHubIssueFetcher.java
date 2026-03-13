package org.acme.services.sync;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GitHubIssueFetcher implements IssueFetcher {

    @Override
    public SourceType getType() {
        return SourceType.GITHUB;
    }

    @Override
    public List<ExternalIssue> fetchIssues(ProjectEntity project) {
        try {
            GitHub gh = new GitHubBuilder()
                    .withOAuthToken(project.credential.token)
                    .build();

            String ownerRepo = parseOwnerRepo(project.url);
            List<GHIssue> issues;

            if (project.query != null && !project.query.isBlank()) {
                issues = gh.searchIssues()
                        .q("repo:" + ownerRepo + " " + project.query)
                        .list()
                        .toList();
            } else {
                GHRepository repo = gh.getRepository(ownerRepo);
                issues = repo.getIssues(GHIssueState.ALL);
            }

            List<ExternalIssue> result = new ArrayList<>();
            for (GHIssue issue : issues) {
                if (issue.isPullRequest()) {
                    continue;
                }
                ExternalIssue ext = new ExternalIssue();
                ext.externalId = String.valueOf(issue.getNumber());
                ext.url = issue.getHtmlUrl().toString();
                ext.title = issue.getTitle();
                ext.description = issue.getBody();
                ext.status = issue.getState() == GHIssueState.OPEN ? TaskStatus.OPEN : TaskStatus.CLOSED;
                ext.assignee = issue.getAssignee() != null ? issue.getAssignee().getLogin() : null;
                ext.labels = issue.getLabels().stream()
                        .map(l -> l.getName())
                        .collect(Collectors.joining(","));
                ext.priority = null;
                ext.createdAt = issue.getCreatedAt().toInstant();
                ext.updatedAt = issue.getUpdatedAt().toInstant();
                result.add(ext);
            }
            return result;
        } catch (IOException e) {
            throw new SyncException("Failed to fetch GitHub issues: " + e.getMessage(), e);
        }
    }

    private String parseOwnerRepo(String url) {
        // Handles URLs like https://github.com/owner/repo or https://github.com/owner/repo.git
        String path = url.replaceFirst("https?://github\\.com/", "");
        path = path.replaceFirst("\\.git$", "");
        path = path.replaceFirst("/$", "");
        return path;
    }
}
