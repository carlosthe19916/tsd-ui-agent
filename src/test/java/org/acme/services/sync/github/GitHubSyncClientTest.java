package org.acme.services.sync.github;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubSyncClientTest {

    @Test
    void extractOwnerRepoFromReposPath() {
        assertEquals("owner/repo", GitHubSyncClient.extractOwnerRepo("/repos/owner/repo"));
    }

    @Test
    void extractOwnerRepoFromReposPathWithTrailingSlash() {
        assertEquals("owner/repo", GitHubSyncClient.extractOwnerRepo("/repos/owner/repo/"));
    }

    @Test
    void extractOwnerRepoFromPlainPath() {
        assertEquals("owner/repo", GitHubSyncClient.extractOwnerRepo("/repos/owner/repo"));
    }
}
