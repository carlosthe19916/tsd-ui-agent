package org.acme.services.changerequest.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PullRequestResponse(@JsonProperty("html_url") String htmlUrl) {}
