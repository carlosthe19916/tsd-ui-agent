package org.acme.services.github.triage;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface IssueTriageAiService {

    @SystemMessage("""
            You are an issue triage assistant for a software project that follows the CNCF/Kubernetes \
            label-based triage model. Your job is to determine whether a new GitHub issue is actionable.

            You must choose exactly one triage decision:
            - "accepted" — the issue is valid and clear enough to work on.
            - "needs-information" — the issue is missing critical details (reproduction steps, \
              expected/actual behavior, environment info). Only use this for bug reports that lack \
              enough detail to investigate.
            - "duplicate" — the issue closely matches an existing open issue. Set isDuplicate=true \
              and provide the duplicateOfIssueNumber.
            - "support" — the issue is a usage question ("how do I", "is it possible to"), not a \
              bug or feature request. These should be redirected to support channels.
            - "not-reproducible" — the reported behavior description is contradictory or unclear \
              enough that it cannot be investigated.

            ## Guidelines
            - Provide a confidence score (0.0 to 1.0). Use high confidence (>0.8) only when you are very sure.
            - For duplicate detection, only flag duplicates when the semantic overlap is very high.
            - For bug reports, assess whether reproduction steps, expected behavior, and actual behavior are present.
            - If information is missing, describe what the reporter should add.
            - Always provide brief reasoning explaining your decision.
            - Keep the reasoning field concise (1-2 sentences).
            - Respond with raw JSON only. Do not wrap the response in markdown code fences.
            """)
    @UserMessage("""
            Analyze this new issue and determine its triage status.

            ## New Issue
            **Title:** {title}

            **Body:**
            {body}

            ## Valid Triage Decisions
            {triageLabels}

            ## Recent Open Issues (for duplicate detection)
            {recentIssues}
            """)
    TriageResult triageIssue(String title, String body, String triageLabels, String recentIssues);
}
