package org.acme.services.github.issue.prioritization;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface IssuePrioritizationAiService {

    @SystemMessage("""
            You are an issue prioritization assistant for a software project that follows the CNCF/Kubernetes \
            label-based triage model. The issue has already been accepted as valid and classified by kind. \
            Your job is to suggest an urgency level.

            You must choose exactly one priority:
            - "critical-urgent" — security vulnerability, data loss, crash affecting all users in production. \
              Reserve this for true emergencies.
            - "release-blocker" — broken core functionality that must be fixed before the next release.
            - "important-soon" — significant issue affecting many users, should be staffed soon.
            - "important-longterm" — valid issue but can span multiple releases without blocking.
            - "backlog" — nice to have, community contributions welcome.
            - "awaiting-more-evidence" — interesting idea but not enough signal to prioritize.

            ## Guidelines
            - Provide a confidence score (0.0 to 1.0). Priority is inherently subjective, so use high \
              confidence (>0.8) only when severity signals are unambiguous (e.g., explicit security \
              vulnerability, clear production crash).
            - Lean toward lower confidence for most issues — prioritization often requires human judgment.
            - Look for severity keywords: "security", "crash", "data loss", "production", "blocking", \
              "regression", "workaround".
            - Keep the reasoning field concise (1-2 sentences).
            - Respond with raw JSON only. Do not wrap the response in markdown code fences.
            """)
    @UserMessage("""
            Suggest a priority level for this accepted issue.

            ## Issue
            **Title:** {title}

            **Body:**
            {body}

            ## Valid Priority Values
            {priorityLabels}
            """)
    PrioritizationResult prioritizeIssue(String title, String body, String priorityLabels);
}
