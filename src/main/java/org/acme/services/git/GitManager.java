package org.acme.services.git;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class GitManager {

    private static final Pattern PROTOCOL_PREFIX = Pattern.compile("^(https?|git|ssh)://");
    private static final Pattern SSH_SHORTHAND = Pattern.compile("^([^@]+@)?([^:]+):(.+)$");

    @Inject
    ProducerTemplate template;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    static String sanitizeUrl(String url) {
        String result = url;

        // Strip protocol prefix
        result = PROTOCOL_PREFIX.matcher(result).replaceFirst("");

        // Normalize SSH shorthand (git@host:path → host/path)
        var sshMatcher = SSH_SHORTHAND.matcher(result);
        if (sshMatcher.matches()) {
            result = sshMatcher.group(2) + "/" + sshMatcher.group(3);
        }

        // Strip leading slashes
        result = result.replaceFirst("^/+", "");

        // Strip trailing .git
        if (result.endsWith(".git")) {
            result = result.substring(0, result.length() - 4);
        }

        // Replace non-alphanumeric chars (except -, _, /) with _
        result = result.replaceAll("[^a-zA-Z0-9\\-_/]", "_");

        // Replace / with _
        result = result.replace('/', '_');

        return result;
    }

    public String cloneRepository(String url, String branch) {
        String localPath = Path.of(baseDir, UUID.randomUUID().toString(), "default").toString();

        var headers = new java.util.HashMap<String, Object>();
        headers.put("localPath", localPath);
        headers.put("remotePath", url);
        if (branch != null && !branch.isBlank()) {
            headers.put("branch", branch);
        }

        try {
            template.requestBodyAndHeaders("direct:git-clone", null, headers);
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to clone repository: " + e.getMessage(), e);
        }

        return localPath;
    }

    public void setRemoteUrl(String localPath, String newUrl) {
        try {
            template.requestBodyAndHeaders("direct:git-remote-set-url", null, Map.of(
                    "workingDir", localPath,
                    "remotePath", newUrl
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to set remote URL: " + e.getMessage(), e);
        }
    }

    public String addWorktree(String mainClonePath, String alias) {
        Path repoRoot = Path.of(mainClonePath).getParent();
        String worktreeDir = repoRoot.resolve("trees").resolve(alias).toString();

        try {
            template.requestBodyAndHeaders("direct:git-worktree-add", null, Map.of(
                    "workingDir", mainClonePath,
                    "worktreeDir", worktreeDir,
                    "branchName", alias
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to add worktree: " + e.getMessage(), e);
        }

        return worktreeDir;
    }

    public void removeWorktree(String mainClonePath, String worktreeDir) {
        try {
            template.requestBodyAndHeaders("direct:git-worktree-remove", null, Map.of(
                    "workingDir", mainClonePath,
                    "worktreeDir", worktreeDir
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to remove worktree: " + e.getMessage(), e);
        }
    }

    public void addForkRemote(String localPath, String forkUrl) {
        try {
            template.requestBodyAndHeaders("direct:git-remote-add-fork", null, Map.of(
                    "workingDir", localPath,
                    "remotePath", forkUrl
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to add fork remote: " + e.getMessage(), e);
        }
    }

    public void setForkRemoteUrl(String localPath, String forkUrl) {
        try {
            template.requestBodyAndHeaders("direct:git-remote-set-url-fork", null, Map.of(
                    "workingDir", localPath,
                    "remotePath", forkUrl
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to set fork remote URL: " + e.getMessage(), e);
        }
    }

    public void removeForkRemote(String localPath) {
        try {
            template.requestBodyAndHeaders("direct:git-remote-remove-fork", null, Map.of(
                    "workingDir", localPath
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to remove fork remote: " + e.getMessage(), e);
        }
    }

    public void addAll(String workingDir) {
        try {
            template.requestBodyAndHeaders("direct:git-add", null, Map.of(
                    "workingDir", workingDir
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to git add: " + e.getMessage(), e);
        }
    }

    public void commit(String workingDir, String message) {
        try {
            template.requestBodyAndHeaders("direct:git-commit", null, Map.of(
                    "workingDir", workingDir,
                    "commitMessage", message
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to git commit: " + e.getMessage(), e);
        }
    }

    public void push(String workingDir, String remoteName, String branchName) {
        try {
            template.requestBodyAndHeaders("direct:git-push", null, Map.of(
                    "workingDir", workingDir,
                    "remoteName", remoteName,
                    "branchName", branchName
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to git push: " + e.getMessage(), e);
        }
    }

    public void pushToUrl(String workingDir, String url, String refspec) {
        try {
            template.requestBodyAndHeaders("direct:git-push-url", null, Map.of(
                    "workingDir", workingDir,
                    "pushUrl", url,
                    "refspec", refspec
            ));
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to git push to URL: " + e.getMessage(), e);
        }
    }

    public static String extractHost(String gitUrl) {
        // HTTPS: https://github.com/owner/repo.git → github.com
        var protocolMatcher = PROTOCOL_PREFIX.matcher(gitUrl);
        if (protocolMatcher.find()) {
            String withoutProtocol = protocolMatcher.replaceFirst("");
            int slashIdx = withoutProtocol.indexOf('/');
            return slashIdx >= 0 ? withoutProtocol.substring(0, slashIdx) : withoutProtocol;
        }
        // SSH: git@github.com:owner/repo.git → github.com
        var sshMatcher = SSH_SHORTHAND.matcher(gitUrl);
        if (sshMatcher.matches()) {
            return sshMatcher.group(2);
        }
        return gitUrl;
    }

    public String getCurrentBranch(String workingDir) {
        try {
            Object result = template.requestBodyAndHeaders("direct:git-rev-parse", null, Map.of(
                    "workingDir", workingDir
            ));
            return result.toString().trim();
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Failed to get current branch: " + e.getMessage(), e);
        }
    }

    public static String extractOwnerRepo(String gitUrl) {
        String result = gitUrl;

        // Strip protocol prefix
        result = PROTOCOL_PREFIX.matcher(result).replaceFirst("");

        // Normalize SSH shorthand (git@host:path → host/path)
        var sshMatcher = SSH_SHORTHAND.matcher(result);
        if (sshMatcher.matches()) {
            result = sshMatcher.group(2) + "/" + sshMatcher.group(3);
        }

        // Strip leading slashes
        result = result.replaceFirst("^/+", "");

        // Strip trailing .git
        if (result.endsWith(".git")) {
            result = result.substring(0, result.length() - 4);
        }

        // Remove host part (e.g. "github.com/owner/repo" → "owner/repo")
        int firstSlash = result.indexOf('/');
        if (firstSlash >= 0) {
            result = result.substring(firstSlash + 1);
        }

        return result;
    }

    public void deleteClonedDirectory(String localPath) {
        Path repoParentDir = Path.of(localPath).getParent();
        if (repoParentDir == null || !Files.exists(repoParentDir)) {
            return;
        }
        try {
            Files.walkFileTree(repoParentDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete cloned directory: " + repoParentDir, e);
        }
    }

}
