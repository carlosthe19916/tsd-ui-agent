package org.acme.services.agent;

import org.acme.services.workspace.Workspace;

public interface CodingAgentService {
    String generatePlan(Workspace workspace, String requirement, Long taskId);

    void executePlan(Workspace workspace, String planText, Long taskId);
}
