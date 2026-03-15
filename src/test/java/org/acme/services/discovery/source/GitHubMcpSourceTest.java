package org.acme.services.discovery.source;

import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubMcpSourceTest {

    @Test
    void testExtractOwner() {
        assertEquals("owner", GitHubMcpSource.extractOwner("https://github.com/owner/repo/issues/123"));
    }

    @Test
    void testExtractRepo() {
        assertEquals("repo", GitHubMcpSource.extractRepo("https://github.com/owner/repo/issues/123"));
    }

    @Test
    void testExtractIssueNumber() {
        assertEquals("123", GitHubMcpSource.extractIssueNumber("123"));
        assertEquals("42", GitHubMcpSource.extractIssueNumber("GH-42"));
    }

    @Test
    void testSupportsGitHub() {
        GitHubMcpSource source = new GitHubMcpSource();
        source.enabled = true;

        TaskEntity githubTask = new TaskEntity();
        githubTask.type = SourceType.GITHUB;
        assertTrue(source.supports(githubTask));

        TaskEntity jiraTask = new TaskEntity();
        jiraTask.type = SourceType.JIRA;
        assertFalse(source.supports(jiraTask));
    }

    @Test
    void testSupportsDisabledReturnsFalse() {
        GitHubMcpSource source = new GitHubMcpSource();
        source.enabled = false;

        TaskEntity task = new TaskEntity();
        task.type = SourceType.GITHUB;
        assertFalse(source.supports(task));
    }

    @Test
    void testPriority() {
        GitHubMcpSource source = new GitHubMcpSource();
        assertEquals(10, source.priority());
    }
}
