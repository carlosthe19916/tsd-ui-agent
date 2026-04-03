package org.acme.services.github.issue.classification;

import dev.langchain4j.model.output.structured.Description;

public record ClassificationResult(
        @Description("Kind classification: bug, feature, or documentation")
        String suggestedKind,

        @Description("Confidence score for the classification, between 0.0 and 1.0")
        double confidence,

        @Description("Brief reasoning for the classification decision")
        String reasoning
) {}
