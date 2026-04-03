package org.acme.services.github.issue.implement;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidateReadyToWorkTest {

    private final IssueImplementationService service = new IssueImplementationService();

    @Test
    void readyToWorkWithAllLabels() {
        Set<String> labels = Set.of("triage/accepted", "kind/bug", "priority/backlog");
        assertNull(service.validateReadyToWork(labels));
    }

    @Test
    void missingTriageAccepted() {
        Set<String> labels = Set.of("kind/bug", "priority/backlog");
        String result = service.validateReadyToWork(labels);
        assertNotNull(result);
        assertTrue(result.contains("triage/accepted"));
    }

    @Test
    void missingKind() {
        Set<String> labels = Set.of("triage/accepted", "priority/backlog");
        String result = service.validateReadyToWork(labels);
        assertNotNull(result);
        assertTrue(result.contains("kind/*"));
    }

    @Test
    void missingPriority() {
        Set<String> labels = Set.of("triage/accepted", "kind/feature");
        String result = service.validateReadyToWork(labels);
        assertNotNull(result);
        assertTrue(result.contains("priority/*"));
    }

    @Test
    void hasNeedsLabels() {
        Set<String> labels = Set.of("triage/accepted", "kind/bug", "priority/backlog", "needs-kind");
        String result = service.validateReadyToWork(labels);
        assertNotNull(result);
        assertTrue(result.contains("needs-*"));
    }

    @Test
    void missingMultipleLabels() {
        Set<String> labels = Set.of();
        String result = service.validateReadyToWork(labels);
        assertNotNull(result);
        assertTrue(result.contains("triage/accepted"));
        assertTrue(result.contains("kind/*"));
        assertTrue(result.contains("priority/*"));
    }

    @Test
    void differentKindLabelsAccepted() {
        assertNull(service.validateReadyToWork(Set.of("triage/accepted", "kind/feature", "priority/important-soon")));
        assertNull(service.validateReadyToWork(Set.of("triage/accepted", "kind/documentation", "priority/critical-urgent")));
    }

    @Test
    void wrongTriageLabelNotAccepted() {
        Set<String> labels = Set.of("triage/needs-information", "kind/bug", "priority/backlog");
        String result = service.validateReadyToWork(labels);
        assertNotNull(result);
        assertTrue(result.contains("triage/accepted"));
    }
}
