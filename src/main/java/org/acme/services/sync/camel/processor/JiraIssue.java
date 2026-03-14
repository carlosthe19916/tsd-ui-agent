package org.acme.services.sync.camel.processor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssue(String key, JiraFields fields) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraFields(
            String summary,
            String description,
            JiraStatus status,
            JiraUser assignee,
            List<String> labels,
            JiraPriority priority,
            String created,
            String updated
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraStatus(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraUser(String displayName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraPriority(String name) {}
}
