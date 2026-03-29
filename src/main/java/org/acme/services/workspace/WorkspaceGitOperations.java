package org.acme.services.workspace;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

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

    public void commit(Workspace workspace, String message, Map<String, String> trailers) {
        String fullMessage = message;
        if (trailers != null && !trailers.isEmpty()) {
            StringBuilder sb = new StringBuilder(message);
            sb.append("\n\n");
            for (Map.Entry<String, String> entry : trailers.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            fullMessage = sb.toString().stripTrailing();
        }
        workspace.exec("git", "-c", "user.name=" + gitUserName, "-c", "user.email=" + gitUserEmail, "-c", "commit.gpgsign=false", "commit", "-m", fullMessage);
    }

    public void push(Workspace workspace, String remoteName, String branchName) {
        workspace.exec("git", "push", remoteName, branchName);
    }

    public void pushToUrl(Workspace workspace, String url, String refspec) {
        workspace.exec("git", "push", url, refspec);
    }

    public String getCurrentBranch(Workspace workspace) {
        return workspace.exec("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
    }
}
