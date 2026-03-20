package org.acme.services.changerequest.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MergeRequestResponse(@JsonProperty("web_url") String webUrl) {}
