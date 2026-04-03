package org.acme.services.github.issue.prioritization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrioritizationCommentFormatterTest {

    @Test
    void formatContainsMarker() {
        PrioritizationResult result = new PrioritizationResult("backlog", 0.8, "Low severity");
        String comment = PrioritizationCommentFormatter.format(result);
        assertTrue(comment.contains(PrioritizationCommentFormatter.AI_PRIORITIZATION_MARKER));
    }

    @Test
    void formatShowsPriorityAndConfidence() {
        PrioritizationResult result = new PrioritizationResult("critical-urgent", 0.95, "Security issue");
        String comment = PrioritizationCommentFormatter.format(result);
        assertTrue(comment.contains("`priority/critical-urgent`"));
        assertTrue(comment.contains("95% confidence"));
    }

    @Test
    void formatShowsReasoning() {
        PrioritizationResult result = new PrioritizationResult("important-soon", 0.7, "Affects many users");
        String comment = PrioritizationCommentFormatter.format(result);
        assertTrue(comment.contains("Affects many users"));
    }

    @Test
    void formatShowsOverrideFooter() {
        PrioritizationResult result = new PrioritizationResult("backlog", 0.6, "Nice to have");
        String comment = PrioritizationCommentFormatter.format(result);
        assertTrue(comment.contains("/priority"));
    }
}
