package org.acme.services.sync.camel.processor;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.sync.ExternalIssue;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class JiraIssueMapper implements Processor {

    private static final Set<String> CLOSED_STATUSES = Set.of("done", "closed", "resolved");
    private static final Set<String> IN_PROGRESS_STATUSES = Set.of("in progress");
    private static final DateTimeFormatter JIRA_DATE = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .appendOffset("+HHmm", "Z")
            .toFormatter();

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        List<JiraIssue> items = exchange.getIn().getBody(List.class);
        String baseUrl = exchange.getProperty("baseUrl", String.class);
        List<ExternalIssue> result = items.stream()
                .filter(issue -> issue.fields() != null)
                .map(issue -> toExternalIssue(issue, baseUrl))
                .toList();
        exchange.getIn().setBody(result);
    }

    private ExternalIssue toExternalIssue(JiraIssue issue, String baseUrl) {
        JiraIssue.JiraFields fields = issue.fields();
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = issue.key();
        ext.url = baseUrl + "/browse/" + issue.key();
        ext.title = fields.summary();
        ext.description = extractText(fields.description());
        ext.status = fields.status() != null ? mapJiraStatus(fields.status().name()) : TaskStatus.OPEN;

        if (fields.labels() != null && !fields.labels().isEmpty()) {
            ext.labels = String.join(",", fields.labels());
        }

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
            if ("paragraph".equals(node.path("type").asText()) || "heading".equals(node.path("type").asText())) {
                sb.append("\n");
            }
        }
    }

    private TaskStatus mapJiraStatus(String statusName) {
        if (statusName == null) return TaskStatus.OPEN;
        String lower = statusName.toLowerCase();
        if (CLOSED_STATUSES.contains(lower)) return TaskStatus.CLOSED;
        if (IN_PROGRESS_STATUSES.contains(lower)) return TaskStatus.IN_PROGRESS;
        return TaskStatus.OPEN;
    }
}
