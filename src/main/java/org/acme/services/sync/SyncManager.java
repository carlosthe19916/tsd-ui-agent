package org.acme.services.sync;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.sync.github.GitHubSyncClient;
import org.acme.services.sync.jira.JiraSyncClient;

import java.net.URI;
import java.util.List;

@ApplicationScoped
public class SyncManager {

    @Inject
    JiraSyncClient jiraClient;

    @Inject
    GitHubSyncClient gitHubClient;

    public List<ExternalIssue> fetchIssues(ProjectEntity project) {
        try {
            return switch (project.type) {
                case JIRA -> jiraClient.fetchIssues(project.apiUrl, project.query, project.credential.token);
                case GITHUB -> gitHubClient.fetchIssues(project.apiUrl, project.query, project.credential.token);
            };
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Failed to fetch issues: " + e.getMessage(), e);
        }
    }

    public void testQuery(SourceType type, String url, String query, String token) {
        try {
            switch (type) {
                case JIRA -> jiraClient.testQuery(url, query, token);
                case GITHUB -> gitHubClient.testQuery(url, query, token);
            }
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Query test failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<ExternalIssueContext.Comment> fetchComments(TaskEntity task) {
        String token = task.project.credential.token;

        try {
            return switch (task.type) {
                case GITHUB -> {
                    URI uri = URI.create(task.project.apiUrl);
                    String apiBase = uri.getScheme() + "://" + uri.getAuthority();
                    if (!uri.getAuthority().startsWith("api.")) {
                        apiBase = apiBase.replace("github.com", "api.github.com");
                    }
                    String[] urlParts = task.url.replaceFirst("https?://[^/]+/", "").split("/");
                    String owner = urlParts.length > 0 ? urlParts[0] : "";
                    String repo = urlParts.length > 1 ? urlParts[1] : "";
                    int issueNumber = Integer.parseInt(task.externalId.replaceAll("[^0-9]", ""));
                    yield gitHubClient.fetchComments(apiBase, owner, repo, issueNumber, token);
                }
                case JIRA -> jiraClient.fetchComments(task.project.apiUrl, task.externalId, token);
            };
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Failed to fetch comments: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> fetchLabels(TaskEntity task) {
        String token = task.project.credential.token;

        try {
            return switch (task.type) {
                case GITHUB -> {
                    URI uri = URI.create(task.project.apiUrl);
                    String apiBase = uri.getScheme() + "://" + uri.getAuthority();
                    if (!uri.getAuthority().startsWith("api.")) {
                        apiBase = apiBase.replace("github.com", "api.github.com");
                    }
                    String[] urlParts = task.url.replaceFirst("https?://[^/]+/", "").split("/");
                    String owner = urlParts.length > 0 ? urlParts[0] : "";
                    String repo = urlParts.length > 1 ? urlParts[1] : "";
                    int issueNumber = Integer.parseInt(task.externalId.replaceAll("[^0-9]", ""));
                    yield gitHubClient.fetchLabels(apiBase, owner, repo, issueNumber, token);
                }
                case JIRA -> jiraClient.fetchLabels(task.project.apiUrl, task.externalId, token);
            };
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Failed to fetch labels: " + e.getMessage(), e);
        }
    }

    public void testConnection(SourceType type, String url, String query, String token) {
        try {
            switch (type) {
                case JIRA -> jiraClient.testConnection(url, token);
                case GITHUB -> gitHubClient.testConnection(url, token);
            }
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Connection test failed: " + e.getMessage(), e);
        }
    }
}
