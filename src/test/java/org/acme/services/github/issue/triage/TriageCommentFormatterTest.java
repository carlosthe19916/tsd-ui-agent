package org.acme.services.github.issue.triage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriageCommentFormatterTest {

    @Test
    void formatContainsMarker() {
        TriageResult result = new TriageResult("accepted", 0.9, false, null,
                true, true, true, null, "Looks valid");
        String comment = TriageCommentFormatter.format(result);
        assertTrue(comment.contains(TriageCommentFormatter.AI_TRIAGE_MARKER));
    }

    @Test
    void formatShowsTriageDecisionAndConfidence() {
        TriageResult result = new TriageResult("accepted", 0.85, false, null,
                true, true, true, null, "Clear issue");
        String comment = TriageCommentFormatter.format(result);
        assertTrue(comment.contains("`triage/accepted`"));
        assertTrue(comment.contains("85% confidence"));
    }

    @Test
    void formatShowsDuplicateInfo() {
        TriageResult result = new TriageResult("duplicate", 0.95, true, 42,
                false, false, false, null, "Same as #42");
        String comment = TriageCommentFormatter.format(result);
        assertTrue(comment.contains("Possible duplicate"));
        assertTrue(comment.contains("#42"));
    }

    @Test
    void formatShowsMissingInformation() {
        TriageResult result = new TriageResult("needs-information", 0.8, false, null,
                false, false, true, "Missing reproduction steps", "Incomplete report");
        String comment = TriageCommentFormatter.format(result);
        assertTrue(comment.contains("Missing Information"));
        assertTrue(comment.contains("Missing reproduction steps"));
    }

    @Test
    void formatShowsBugReportChecklist() {
        TriageResult result = new TriageResult("needs-information", 0.7, false, null,
                true, false, true, "No expected behavior", "Incomplete");
        String comment = TriageCommentFormatter.format(result);
        assertTrue(comment.contains("[x] Reproduction steps"));
        assertTrue(comment.contains("[ ] Expected behavior"));
        assertTrue(comment.contains("[x] Actual behavior"));
    }

    @Test
    void formatHidesBugChecklistForNonNeedsInformation() {
        TriageResult result = new TriageResult("accepted", 0.9, false, null,
                true, true, true, null, "Valid");
        String comment = TriageCommentFormatter.format(result);
        assertFalse(comment.contains("Bug report completeness"));
    }

    @Test
    void formatShowsReasoning() {
        TriageResult result = new TriageResult("support", 0.75, false, null,
                false, false, false, null, "This is a usage question");
        String comment = TriageCommentFormatter.format(result);
        assertTrue(comment.contains("This is a usage question"));
    }

    @Test
    void formatShowsOverrideFooter() {
        TriageResult result = new TriageResult("accepted", 0.9, false, null,
                true, true, true, null, "Valid");
        String comment = TriageCommentFormatter.format(result);
        assertTrue(comment.contains("/triage"));
    }
}
