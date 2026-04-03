package org.acme.services.github.issue.triage;

import dev.langchain4j.model.output.structured.Description;

public record TriageResult(
        @Description("Triage decision: accepted, needs-information, duplicate, not-reproducible, or support")
        String suggestedTriage,

        @Description("Confidence score for the triage decision, between 0.0 and 1.0")
        double confidence,

        @Description("Whether this issue is a duplicate of an existing issue")
        boolean isDuplicate,

        @Description("The issue number of the original issue if this is a duplicate, or null")
        Integer duplicateOfIssueNumber,

        @Description("Whether the issue contains reproduction steps (for bug reports)")
        boolean hasReproSteps,

        @Description("Whether the issue describes expected behavior (for bug reports)")
        boolean hasExpectedBehavior,

        @Description("Whether the issue describes actual behavior (for bug reports)")
        boolean hasActualBehavior,

        @Description("Human-readable description of what information is missing, or null if nothing is missing")
        String missingInformation,

        @Description("Brief reasoning for the triage decision")
        String reasoning
) {}