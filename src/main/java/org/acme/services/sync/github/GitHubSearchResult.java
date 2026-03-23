package org.acme.services.sync.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubSearchResult(List<GitHubIssue> items) {}
