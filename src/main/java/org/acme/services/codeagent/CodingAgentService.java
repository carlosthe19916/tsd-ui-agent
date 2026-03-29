package org.acme.services.codeagent;

import org.acme.services.workspace.Workspace;

public interface CodingAgentService {

    String PLAN_GENERATION_PROMPT = """
            Analyze this codebase and generate a detailed implementation plan in Markdown format \
            for the following requirement:

            %s

            Output ONLY the plan in Markdown. Include: Overview, affected files and components, \
            step-by-step implementation instructions, and testing approach.
            """;

    /**
     * Returns a label identifying this coding agent and its model,
     * suitable for use in git commit trailers.
     * Example: "Claude Code (opus)" or "OpenCode (anthropic/claude-opus-4-6)"
     */
    String agentLabel();

    String generatePlan(Workspace workspace, String requirement, Long taskId);

    void executePlan(Workspace workspace, String planText, Long taskId);
}
