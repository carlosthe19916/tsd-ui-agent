package org.acme.services.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
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
@ApplicationScoped
public interface RequirementSummarizerService {

    @UserMessage("""
            Task: {{taskTitle}}

            Description:
            {{taskDescription}}

            Comments:
            {{comments}}

            Labels:
            {{labels}}
            """)
    String summarize(String taskTitle, String taskDescription, String comments, String labels);
}
