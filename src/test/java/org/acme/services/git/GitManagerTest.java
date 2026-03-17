package org.acme.services.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitManagerTest {

    @Test
    void testExtractOwnerRepoHttpsWithGitSuffix() {
        assertEquals("owner/repo", GitManager.extractOwnerRepo("https://github.com/owner/repo.git"));
    }

    @Test
    void testExtractOwnerRepoHttpsWithoutGitSuffix() {
        assertEquals("owner/repo", GitManager.extractOwnerRepo("https://github.com/owner/repo"));
    }

    @Test
    void testExtractOwnerRepoSshWithGitSuffix() {
        assertEquals("owner/repo", GitManager.extractOwnerRepo("git@github.com:owner/repo.git"));
    }

    @Test
    void testExtractOwnerRepoSshWithoutGitSuffix() {
        assertEquals("owner/repo", GitManager.extractOwnerRepo("git@github.com:owner/repo"));
    }

    @Test
    void testExtractOwnerRepoGitLabHttps() {
        assertEquals("owner/repo", GitManager.extractOwnerRepo("https://gitlab.com/owner/repo.git"));
    }

    @Test
    void testExtractOwnerRepoGitLabSsh() {
        assertEquals("owner/repo", GitManager.extractOwnerRepo("git@gitlab.com:owner/repo.git"));
    }

    @Test
    void testExtractOwnerRepoGitLabSubgroups() {
        assertEquals("group/subgroup/repo", GitManager.extractOwnerRepo("https://gitlab.com/group/subgroup/repo"));
    }
}
