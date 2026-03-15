package org.acme.services.sync;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SyncManager {

    @Inject
    ProducerTemplate template;

    @SuppressWarnings("unchecked")
    public List<ExternalIssue> fetchIssues(ProjectEntity project) {
        String routePrefix = project.type.name().toLowerCase();
        Map<String, Object> headers = Map.of(
                "apiUrl", project.apiUrl,
                "token", project.credential.token,
                "query", project.query != null ? project.query : ""
        );

        try {
            return (List<ExternalIssue>) template.requestBodyAndHeaders("direct:" + routePrefix + "-fetch-issues", null, headers);
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Failed to fetch issues: " + e.getMessage(), e);
        }
    }

    public void testQuery(SourceType type, String url, String query, String token) {
        String routePrefix = type.name().toLowerCase();
        Map<String, Object> headers = Map.of(
                "apiUrl", url,
                "token", token,
                "query", query != null ? query : ""
        );

        try {
            template.requestBodyAndHeaders("direct:" + routePrefix + "-test-query", null, headers, Object.class);
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Query test failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<ExternalIssueContext.Comment> fetchComments(TaskEntity task) {
        String routePrefix = task.type.name().toLowerCase();
        Map<String, Object> headers = new HashMap<>();
        headers.put("token", task.project.credential.token);

        if (task.type == SourceType.GITHUB) {
            // Extract owner/repo/issue from URL like https://github.com/owner/repo/issues/123
            URI uri = URI.create(task.project.apiUrl);
            String apiBase = uri.getScheme() + "://" + uri.getAuthority();
            headers.put("apiUrl", apiBase.replace("github.com", "api.github.com"));
            String[] urlParts = task.url.replaceFirst("https?://[^/]+/", "").split("/");
            headers.put("owner", urlParts.length > 0 ? urlParts[0] : "");
            headers.put("repo", urlParts.length > 1 ? urlParts[1] : "");
            headers.put("issueNumber", task.externalId.replaceAll("[^0-9]", ""));
        } else if (task.type == SourceType.JIRA) {
            headers.put("apiUrl", task.project.apiUrl);
            headers.put("issueKey", task.externalId);
        }

        try {
            return (List<ExternalIssueContext.Comment>) template.requestBodyAndHeaders("direct:" + routePrefix + "-fetch-comments", null, headers);
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Failed to fetch comments: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> fetchLabels(TaskEntity task) {
        String routePrefix = task.type.name().toLowerCase();
        Map<String, Object> headers = new HashMap<>();
        headers.put("token", task.project.credential.token);

        if (task.type == SourceType.GITHUB) {
            URI uri = URI.create(task.project.apiUrl);
            String apiBase = uri.getScheme() + "://" + uri.getAuthority();
            headers.put("apiUrl", apiBase.replace("github.com", "api.github.com"));
            String[] urlParts = task.url.replaceFirst("https?://[^/]+/", "").split("/");
            headers.put("owner", urlParts.length > 0 ? urlParts[0] : "");
            headers.put("repo", urlParts.length > 1 ? urlParts[1] : "");
            headers.put("issueNumber", task.externalId.replaceAll("[^0-9]", ""));
        } else if (task.type == SourceType.JIRA) {
            headers.put("apiUrl", task.project.apiUrl);
            headers.put("issueKey", task.externalId);
        }

        try {
            return (List<String>) template.requestBodyAndHeaders("direct:" + routePrefix + "-fetch-labels", null, headers);
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Failed to fetch labels: " + e.getMessage(), e);
        }
    }

    public void testConnection(SourceType type, String url, String query, String token) {
        String routePrefix = type.name().toLowerCase();
        Map<String, Object> headers = Map.of(
                "apiUrl", url,
                "token", token,
                "query", query != null ? query : ""
        );

        try {
            template.requestBodyAndHeaders("direct:" + routePrefix + "-test-connection", null, headers, Object.class);
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException("Connection test failed: " + e.getMessage(), e);
        }
    }
}
