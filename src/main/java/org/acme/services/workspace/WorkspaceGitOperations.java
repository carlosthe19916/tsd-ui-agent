package org.acme.services.workspace;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WorkspaceGitOperations {

    public void addAll(Workspace workspace) {
        workspace.exec("git", "add", ".");
    }

    public void commit(Workspace workspace, String message) {
        workspace.exec("git", "commit", "-m", message);
    }

    public void push(Workspace workspace, String remoteName, String branchName) {
        workspace.exec("git", "push", remoteName, branchName);
    }

    public void pushToUrl(Workspace workspace, String url, String refspec) {
        workspace.exec("git", "push", "--force", url, refspec);
    }

    public String getCurrentBranch(Workspace workspace) {
        return workspace.exec("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
    }
}
