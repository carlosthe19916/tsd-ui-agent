package org.acme.services.workspace;

public interface WorkspaceToolsService {

    void openIDE(Workspace workspace);

    void openTerminal(Workspace workspace);

    String openClaude(Workspace workspace, Long taskId, String requirement, String planApiUrl, String existingSessionId);
}
