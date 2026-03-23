package org.acme.services.sync.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssue(
        int number,
        @JsonProperty("html_url") String htmlUrl,
        String title,
        String body,
        String state,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        List<GitHubLabel> labels
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubLabel(String name) {}
}
