package org.acme.services.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface RequirementAiService {

    @SystemMessage("""
            You are a requirements analyst. Given a task description, comments, and additional context,
            produce a structured requirement document in Markdown format with the following sections:

            ## Summary
            A concise summary of the requirement.

            ## Objectives
            Key objectives to achieve.

            ## Affected Repositories
            List repositories or components involved.

            ## Acceptance Criteria
            Clear, testable acceptance criteria.

            ## Additional Notes
            Any other relevant information.

            Be concise and precise. Use bullet points where appropriate.
            """)
    @UserMessage("""
            Task: {{taskTitle}}
            Source: {{sourceType}}

            Description:
            {{taskDescription}}

            Comments:
            {{comments}}

            Additional Context:
            {{additionalContexts}}
            """)
    String discoverRequirement(String taskTitle, String sourceType, String taskDescription,
            String comments, String additionalContexts);
}
