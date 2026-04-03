package org.acme.services.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitManagerCloneDirTest {

    @Test
    void cloneDirWithBranch() {
        String result = GitManager.cloneDir("/base", "https://github.com/owner/repo", "main");
        assertTrue(result.endsWith("/repositories/github_com_owner_repo/main"));
    }

    @Test
    void cloneDirWithNullBranch() {
        String result = GitManager.cloneDir("/base", "https://github.com/owner/repo", null);
        assertTrue(result.endsWith("/repositories/github_com_owner_repo/default"));
    }

    @Test
    void cloneDirWithEmptyBranch() {
        String result = GitManager.cloneDir("/base", "https://github.com/owner/repo", "");
        assertTrue(result.endsWith("/repositories/github_com_owner_repo/default"));
    }

    @Test
    void cloneDirWithBlankBranch() {
        String result = GitManager.cloneDir("/base", "https://github.com/owner/repo", "   ");
        assertTrue(result.endsWith("/repositories/github_com_owner_repo/default"));
    }

    @Test
    void cloneDirStripsDotGit() {
        String result = GitManager.cloneDir("/base", "https://github.com/owner/repo.git", "develop");
        assertTrue(result.endsWith("/repositories/github_com_owner_repo/develop"));
    }

    @Test
    void branchDirWithValue() {
        assertEquals("main", GitManager.branchDir("main"));
    }

    @Test
    void branchDirWithNull() {
        assertEquals(GitManager.DEFAULT_BRANCH_DIR, GitManager.branchDir(null));
    }

    @Test
    void branchDirWithEmpty() {
        assertEquals(GitManager.DEFAULT_BRANCH_DIR, GitManager.branchDir(""));
    }

    @Test
    void branchDirWithBlank() {
        assertEquals(GitManager.DEFAULT_BRANCH_DIR, GitManager.branchDir("   "));
    }

    @Test
    void defaultBranchDirConstant() {
        assertEquals("default", GitManager.DEFAULT_BRANCH_DIR);
    }
}
