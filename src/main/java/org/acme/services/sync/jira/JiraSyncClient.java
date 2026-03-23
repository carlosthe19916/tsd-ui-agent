package org.acme.services.sync.jira;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.ExternalIssueContext;
import org.acme.services.sync.SyncException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@ApplicationScoped
public class JiraSyncClient {

    static final int MAX_RESULTS = 50;
    static final String FIELDS = "summary,description,status,labels,created,updated";

    private static final DateTimeFormatter JIRA_DATE = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .appendOffset("+HHmm", "Z")
            .toFormatter();

    public List<ExternalIssue> fetchIssues(String apiUrl, String query, String token) {
        String baseUrl = stripTrailingSlash(apiUrl);
        JiraSyncApi api = buildClient(baseUrl, token);
        String jql = (query != null && !query.isBlank()) ? query : "project is not EMPTY order by updated DESC";

        try {
            List<JiraIssue> allIssues = new ArrayList<>();
            String nextPageToken = null;

            while (true) {
                JiraPageResponse response = api.searchJql(jql, MAX_RESULTS, FIELDS, nextPageToken);
                if (response.issues() != null) {
                    allIssues.addAll(response.issues());
                }
                if (response.isLast()) break;
                nextPageToken = response.nextPageToken();
            }

            return allIssues.stream()
                    .filter(issue -> issue.fields() != null)
                    .map(issue -> toExternalIssue(issue, baseUrl))
                    .toList();
        } catch (SyncException e) {
            throw e;
        } catch (WebApplicationException e) {
            throw toSyncException("Jira sync failed", e);
        } catch (Exception e) {
            throw new SyncException("Jira sync failed: " + e.getMessage(), e);
        }
    }

    public void testConnection(String apiUrl, String token) {
        String baseUrl = stripTrailingSlash(apiUrl);
        JiraSyncApi api = buildClient(baseUrl, token);
        try {
            api.myself();
        } catch (WebApplicationException e) {
            throw toSyncException("Jira connection test failed", e);
        } catch (Exception e) {
            throw new SyncException("Jira connection test failed: " + e.getMessage(), e);
        }
    }

    public void testQuery(String apiUrl, String query, String token) {
        String baseUrl = stripTrailingSlash(apiUrl);
        JiraSyncApi api = buildClient(baseUrl, token);
        String jql = (query != null && !query.isBlank()) ? query : "project is not EMPTY order by updated DESC";

        try {
            api.searchJql(jql, 1, FIELDS, null);
        } catch (WebApplicationException e) {
            throw toSyncException("Jira query test failed", e);
        } catch (Exception e) {
            throw new SyncException("Jira query test failed: " + e.getMessage(), e);
        }
    }

    public List<ExternalIssueContext.Comment> fetchComments(String apiUrl, String issueKey, String token) {
        String baseUrl = stripTrailingSlash(apiUrl);
        JiraSyncApi api = buildClient(baseUrl, token);
        try {
            JsonNode root = api.getComments(issueKey);
            JsonNode commentsNode = root.has("comments") ? root.get("comments") : root;
            List<ExternalIssueContext.Comment> comments = new ArrayList<>();
            if (commentsNode.isArray()) {
                for (JsonNode node : commentsNode) {
                    String author = node.path("author").path("displayName").asText("unknown");
                    String text = extractText(node.path("body"));
                    comments.add(new ExternalIssueContext.Comment(author, text, null));
                }
            }
            return comments;
        } catch (WebApplicationException e) {
            throw toSyncException("Jira comment fetch failed", e);
        } catch (Exception e) {
            throw new SyncException("Jira comment fetch failed: " + e.getMessage(), e);
        }
    }

    public List<String> fetchLabels(String apiUrl, String issueKey, String token) {
        String baseUrl = stripTrailingSlash(apiUrl);
        JiraSyncApi api = buildClient(baseUrl, token);
        try {
            JsonNode root = api.getIssue(issueKey, "labels");
            List<String> labels = new ArrayList<>();
            JsonNode labelsNode = root.path("fields").path("labels");
            if (labelsNode.isArray()) {
                for (JsonNode node : labelsNode) {
                    labels.add(node.asText());
                }
            }
            return labels;
        } catch (WebApplicationException e) {
            throw toSyncException("Jira label fetch failed", e);
        } catch (Exception e) {
            throw new SyncException("Jira label fetch failed: " + e.getMessage(), e);
        }
    }

    private JiraSyncApi buildClient(String baseUrl, String token) {
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUrl));
        String auth = jiraAuth(token);
        builder.clientHeadersFactory((inbound, outbound) -> {
            outbound.add("Authorization", auth);
            outbound.add("Accept", "application/json");
            return outbound;
        });
        return builder.build(JiraSyncApi.class);
    }

    static String jiraAuth(String token) {
        if (token.contains(":")) {
            return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        }
        return "Bearer " + token;
    }

    private ExternalIssue toExternalIssue(JiraIssue issue, String baseUrl) {
        JiraIssue.JiraFields fields = issue.fields();
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = issue.key();
        ext.url = baseUrl + "/browse/" + issue.key();
        ext.title = fields.summary();
        ext.description = extractText(fields.description());
        ext.externalStatus = fields.status() != null ? fields.status().name() : null;
        ext.labels = fields.labels() != null ? fields.labels() : List.of();
        ext.createdAt = fields.created() != null ? parseJiraDate(fields.created()) : null;
        ext.updatedAt = fields.updated() != null ? parseJiraDate(fields.updated()) : null;
        return ext;
    }

    private Instant parseJiraDate(String value) {
        return OffsetDateTime.parse(value, JIRA_DATE).toInstant();
    }

    private String extractText(JsonNode adf) {
        if (adf == null || adf.isNull()) return null;
        StringBuilder sb = new StringBuilder();
        collectText(adf, sb);
        String result = sb.toString().strip();
        return result.isEmpty() ? null : result;
    }

    private void collectText(JsonNode node, StringBuilder sb) {
        if (node.has("text")) {
            sb.append(node.get("text").asText());
        }
        if (node.has("content")) {
            for (JsonNode child : node.get("content")) {
                collectText(child, sb);
            }
            String type = node.path("type").asText("");
            if ("paragraph".equals(type) || "heading".equals(type)) {
                sb.append("\n");
            }
        }
    }

    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private SyncException toSyncException(String prefix, WebApplicationException e) {
        String body = e.getResponse().readEntity(String.class);
        int status = e.getResponse().getStatus();
        String message = (body != null && !body.isBlank())
                ? prefix + " (status " + status + "): " + body
                : prefix + ": " + e.getMessage();
        return new SyncException(message, e);
    }
}
