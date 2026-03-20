package org.acme.services.changerequest.github;

public record CreatePullRequest(String title, String head, String base, String body) {}
