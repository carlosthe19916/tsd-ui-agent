package org.acme.services.sync.camel.processor;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.sync.ExternalIssue;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GitHubIssueMapper implements Processor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        List<GitHubIssue> items = exchange.getIn().getBody(List.class);
        List<ExternalIssue> result = items.stream().map(this::toExternalIssue).toList();
        exchange.getIn().setBody(result);
    }

    private ExternalIssue toExternalIssue(GitHubIssue gh) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = String.valueOf(gh.number());
        ext.url = gh.htmlUrl();
        ext.title = gh.title();
        ext.description = gh.body();
        ext.status = "open".equals(gh.state()) ? TaskStatus.OPEN : TaskStatus.CLOSED;
        ext.assignee = gh.assignee() != null ? gh.assignee().login() : null;

        if (gh.labels() != null && !gh.labels().isEmpty()) {
            ext.labels = gh.labels().stream().map(GitHubIssue.GitHubLabel::name).collect(Collectors.joining(","));
        }

        ext.priority = null;
        ext.createdAt = gh.createdAt() != null ? Instant.parse(gh.createdAt()) : null;
        ext.updatedAt = gh.updatedAt() != null ? Instant.parse(gh.updatedAt()) : null;
        return ext;
    }
}
