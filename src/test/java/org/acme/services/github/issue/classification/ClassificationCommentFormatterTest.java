package org.acme.services.github.issue.classification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationCommentFormatterTest {

    @Test
    void formatContainsMarker() {
        ClassificationResult result = new ClassificationResult("bug", 0.9, "Stack trace present");
        String comment = ClassificationCommentFormatter.format(result);
        assertTrue(comment.contains(ClassificationCommentFormatter.AI_CLASSIFICATION_MARKER));
    }

    @Test
    void formatShowsKindAndConfidence() {
        ClassificationResult result = new ClassificationResult("feature", 0.75, "Enhancement request");
        String comment = ClassificationCommentFormatter.format(result);
        assertTrue(comment.contains("`kind/feature`"));
        assertTrue(comment.contains("75% confidence"));
    }

    @Test
    void formatShowsReasoning() {
        ClassificationResult result = new ClassificationResult("documentation", 0.85, "README update needed");
        String comment = ClassificationCommentFormatter.format(result);
        assertTrue(comment.contains("README update needed"));
    }

    @Test
    void formatShowsOverrideFooter() {
        ClassificationResult result = new ClassificationResult("bug", 0.9, "Valid");
        String comment = ClassificationCommentFormatter.format(result);
        assertTrue(comment.contains("/kind"));
    }
}
