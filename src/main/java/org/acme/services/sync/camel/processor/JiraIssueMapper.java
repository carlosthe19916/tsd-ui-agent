package org.acme.services.sync.camel.processor;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.sync.ExternalIssue;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class JiraIssueMapper implements Processor {

    private static final Set<String> CLOSED_STATUSES = Set.of("done", "closed", "resolved");
    private static final Set<String> IN_PROGRESS_STATUSES = Set.of("in progress");

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        List<JiraIssue> items = exchange.getIn().getBody(List.class);
        String baseUrl = exchange.getProperty("baseUrl", String.class);
        List<ExternalIssue> result = items.stream().map(issue -> toExternalIssue(issue, baseUrl)).toList();
        exchange.getIn().setBody(result);
    }

    private ExternalIssue toExternalIssue(JiraIssue issue, String baseUrl) {
        JiraIssue.JiraFields fields = issue.fields();
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = issue.key();
        ext.url = baseUrl + "/browse/" + issue.key();
        ext.title = fields.summary();
        ext.description = fields.description();
        ext.status = fields.status() != null ? mapJiraStatus(fields.status().name()) : TaskStatus.OPEN;
        ext.assignee = fields.assignee() != null ? fields.assignee().displayName() : null;

        if (fields.labels() != null && !fields.labels().isEmpty()) {
            ext.labels = String.join(",", fields.labels());
        }

        ext.priority = fields.priority() != null ? fields.priority().name() : null;
        ext.createdAt = fields.created() != null ? Instant.parse(fields.created()) : null;
        ext.updatedAt = fields.updated() != null ? Instant.parse(fields.updated()) : null;
        return ext;
    }

    private TaskStatus mapJiraStatus(String statusName) {
        if (statusName == null) return TaskStatus.OPEN;
        String lower = statusName.toLowerCase();
        if (CLOSED_STATUSES.contains(lower)) return TaskStatus.CLOSED;
        if (IN_PROGRESS_STATUSES.contains(lower)) return TaskStatus.IN_PROGRESS;
        return TaskStatus.OPEN;
    }
}
