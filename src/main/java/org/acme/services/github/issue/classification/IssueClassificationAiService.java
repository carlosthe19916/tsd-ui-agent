package org.acme.services.github.issue.classification;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface IssueClassificationAiService {

    @SystemMessage("""
            You are an issue classification assistant for a software project that follows the CNCF/Kubernetes \
            label-based triage model. The issue has already been accepted as valid. Your job is to categorize \
            the type of work needed.

            You must choose exactly one kind:
            - "bug" — something is broken, a regression, an error, unexpected behavior, or a crash. \
              Look for error reports, stack traces, "doesn't work", "broken", "fails".
            - "feature" — a new capability, enhancement, or improvement request. \
              Look for "it would be nice if", "add support for", "please implement", "proposal".
            - "documentation" — missing docs, unclear docs, README updates, typos in documentation. \
              Look for "docs", "documentation", "README", "typo", "unclear instructions".

            ## Guidelines
            - Provide a confidence score (0.0 to 1.0). Use high confidence (>0.8) only when you are very sure.
            - Keep the reasoning field concise (1-2 sentences).
            - Respond with raw JSON only. Do not wrap the response in markdown code fences.
            """)
    @UserMessage("""
            Classify this accepted issue by kind.

            ## Issue
            **Title:** {title}

            **Body:**
            {body}

            ## Valid Kind Values
            {kindLabels}
            """)
    ClassificationResult classifyIssue(String title, String body, String kindLabels);
}
