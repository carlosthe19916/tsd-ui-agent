package org.acme.services.workspace;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WorkspaceGitOperations {

    @ConfigProperty(name = "tsd-agent.git.user-name")
    String gitUserName;

    @ConfigProperty(name = "tsd-agent.git.user-email")
    String gitUserEmail;

    public void addAll(Workspace workspace) {
        workspace.exec("git", "add", ".");
    }

    public void commit(Workspace workspace, String message) {
        workspace.exec("git", "-c", "user.name=" + gitUserName, "-c", "user.email=" + gitUserEmail, "-c", "commit.gpgsign=false", "commit", "-m", message);
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
