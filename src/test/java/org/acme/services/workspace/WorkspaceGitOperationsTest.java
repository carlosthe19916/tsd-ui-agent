package org.acme.services.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceGitOperationsTest {

    private WorkspaceGitOperations workspaceGit;
    private Workspace workspace;

    @BeforeEach
    void setup() {
        workspaceGit = new WorkspaceGitOperations();
        workspaceGit.gitUserName = "Test User";
        workspaceGit.gitUserEmail = "test@example.com";
        workspace = mock(Workspace.class);
        when(workspace.exec(any(String[].class))).thenReturn("");
    }

    @Test
    void commitWithTrailersAppendsTrailerToMessage() {
        workspaceGit.commit(workspace, "Fix login bug", Map.of("Assisted-by", "Claude Code (opus)"));

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(workspace).exec(captor.capture());

        String[] args = captor.getValue();
        String commitMessage = args[args.length - 1];

        assertTrue(commitMessage.startsWith("Fix login bug"), "Message should start with the original text");
        assertTrue(commitMessage.contains("\n\n"), "Message should have a blank line before trailers");
        assertTrue(commitMessage.contains("Assisted-by: Claude Code (opus)"),
                "Message should contain the Assisted-by trailer");
    }

    @Test
    void commitWithMultipleTrailersAppendsAll() {
        Map<String, String> trailers = new LinkedHashMap<>();
        trailers.put("Assisted-by", "Claude Code (opus)");
        trailers.put("Task-ID", "42");

        workspaceGit.commit(workspace, "Refactor auth", trailers);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(workspace).exec(captor.capture());

        String[] args = captor.getValue();
        String commitMessage = args[args.length - 1];

        assertTrue(commitMessage.contains("Assisted-by: Claude Code (opus)"));
        assertTrue(commitMessage.contains("Task-ID: 42"));
    }

    @Test
    void commitWithNullTrailersUsesPlainMessage() {
        workspaceGit.commit(workspace, "Plain commit", (Map<String, String>) null);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(workspace).exec(captor.capture());

        String[] args = captor.getValue();
        String commitMessage = args[args.length - 1];

        assertEquals("Plain commit", commitMessage);
    }

    @Test
    void commitWithEmptyTrailersUsesPlainMessage() {
        workspaceGit.commit(workspace, "Plain commit", Collections.emptyMap());

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(workspace).exec(captor.capture());

        String[] args = captor.getValue();
        String commitMessage = args[args.length - 1];

        assertEquals("Plain commit", commitMessage);
    }

    @Test
    void commitWithTrailersHasNoTrailingWhitespace() {
        workspaceGit.commit(workspace, "Fix bug", Map.of("Assisted-by", "OpenCode (anthropic/claude-opus-4-6)"));

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(workspace).exec(captor.capture());

        String[] args = captor.getValue();
        String commitMessage = args[args.length - 1];

        assertEquals(commitMessage, commitMessage.stripTrailing(),
                "Commit message should not have trailing whitespace");
    }

    @Test
    void commitWithoutTrailersMethodUnchanged() {
        workspaceGit.commit(workspace, "Simple commit");

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(workspace).exec(captor.capture());

        String[] args = captor.getValue();
        String commitMessage = args[args.length - 1];

        assertEquals("Simple commit", commitMessage);
    }
}
