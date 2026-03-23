package org.acme.services.sync.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraPageResponse(List<JiraIssue> issues, boolean isLast, String nextPageToken) {}
