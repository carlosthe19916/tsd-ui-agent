package org.acme.services.sync;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class JiraIssueFetcher implements IssueFetcher {

    private static final int MAX_RESULTS = 50;
    private static final Set<String> CLOSED_STATUSES = Set.of("done", "closed", "resolved");
    private static final Set<String> IN_PROGRESS_STATUSES = Set.of("in progress");

    @Override
    public SourceType getType() {
        return SourceType.JIRA;
    }

    @Override
    public List<ExternalIssue> fetchIssues(ProjectEntity project) {
        try {
            AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
            URI jiraUri = URI.create(project.url);
            JiraRestClient client = factory.createWithBasicHttpAuthentication(
                    jiraUri, project.credential.username, project.credential.token);

            try {
                String jql = (project.query != null && !project.query.isBlank())
                        ? project.query
                        : "order by updated DESC";

                List<ExternalIssue> result = new ArrayList<>();
                int startAt = 0;

                while (true) {
                    SearchResult searchResult = client.getSearchClient()
                            .searchJql(jql, MAX_RESULTS, startAt, null)
                            .claim();

                    for (Issue issue : searchResult.getIssues()) {
                        ExternalIssue ext = new ExternalIssue();
                        ext.externalId = issue.getKey();
                        ext.url = jiraUri + "/browse/" + issue.getKey();
                        ext.title = issue.getSummary();
                        ext.description = issue.getDescription();
                        ext.status = mapStatus(issue.getStatus().getName());
                        ext.assignee = issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : null;
                        ext.labels = issue.getLabels() != null
                                ? StreamSupport.stream(issue.getLabels().spliterator(), false)
                                        .collect(Collectors.joining(","))
                                : null;
                        ext.priority = issue.getPriority() != null ? issue.getPriority().getName() : null;
                        ext.createdAt = issue.getCreationDate() != null
                                ? Instant.ofEpochMilli(issue.getCreationDate().getMillis())
                                : null;
                        ext.updatedAt = issue.getUpdateDate() != null
                                ? Instant.ofEpochMilli(issue.getUpdateDate().getMillis())
                                : null;
                        result.add(ext);
                    }

                    int fetched = startAt + MAX_RESULTS;
                    if (fetched >= searchResult.getTotal()) {
                        break;
                    }
                    startAt = fetched;
                }

                return result;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            throw new SyncException("Failed to fetch Jira issues: " + e.getMessage(), e);
        }
    }

    private TaskStatus mapStatus(String statusName) {
        if (statusName == null) {
            return TaskStatus.OPEN;
        }
        String lower = statusName.toLowerCase();
        if (CLOSED_STATUSES.contains(lower)) {
            return TaskStatus.CLOSED;
        }
        if (IN_PROGRESS_STATUSES.contains(lower)) {
            return TaskStatus.IN_PROGRESS;
        }
        return TaskStatus.OPEN;
    }
}
