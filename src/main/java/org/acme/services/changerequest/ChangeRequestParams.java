package org.acme.services.changerequest;

public record ChangeRequestParams(
        String gitUrl,
        String forkUrl,
        String token,
        String ownerRepo,
        String branchName,
        String baseBranch,
        String title,
        String description
) {}
