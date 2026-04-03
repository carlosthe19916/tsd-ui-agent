package org.acme.services.github.issue.prioritization;

import dev.langchain4j.model.output.structured.Description;

public record PrioritizationResult(
        @Description("Priority suggestion: critical-urgent, release-blocker, important-soon, important-longterm, backlog, or awaiting-more-evidence")
        String suggestedPriority,

        @Description("Confidence score for the priority suggestion, between 0.0 and 1.0")
        double confidence,

        @Description("Brief reasoning for the priority decision")
        String reasoning
) {}
