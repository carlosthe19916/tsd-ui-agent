package org.acme.services.sync.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssue(String key, JiraFields fields) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraFields(
            String summary,
            JsonNode description,
            JiraStatus status,
            List<String> labels,
            String created,
            String updated
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraStatus(String name) {}
}
