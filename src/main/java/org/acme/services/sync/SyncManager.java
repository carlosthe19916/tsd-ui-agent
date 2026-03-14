package org.acme.services.sync;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.SourceType;

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
