package org.acme.services.changerequest.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectResponse(Long id, @JsonProperty("default_branch") String defaultBranch) {}
