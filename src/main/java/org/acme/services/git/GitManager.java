package org.acme.services.git;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class GitManager {

    private static final Pattern PROTOCOL_PREFIX = Pattern.compile("^(https?|git|ssh)://");
    private static final Pattern SSH_SHORTHAND = Pattern.compile("^([^@]+@)?([^:]+):(.+)$");

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    public static String sanitizeUrl(String url) {
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
        String localPath = Path.of(baseDir, "repositories", UUID.randomUUID().toString(), "default").toString();
        return cloneRepository(url, branch, localPath, null);
    }

    public String cloneRepository(String url, String branch, String targetPath) {
        return cloneRepository(url, branch, targetPath, null);
    }

    public String cloneRepository(String url, String branch, String targetPath, String token) {
        try {
            var cmd = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(new File(targetPath));
            if (branch != null && !branch.isBlank()) {
                cmd.setBranch(branch);
            }
            if (token != null && !token.isBlank()) {
                cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", token));
            }
            cmd.call().close();
        } catch (GitAPIException e) {
            throw new GitException("Failed to clone repository: " + redact(e.getMessage()), e);
        }
        return targetPath;
    }

    public void setRemoteUrl(String localPath, String newUrl) {
        try (Git git = Git.open(new File(localPath))) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", newUrl);
            config.save();
        } catch (IOException e) {
            throw new GitException("Failed to set remote URL: " + redact(e.getMessage()), e);
        }
    }

    public void pullRepository(String workingDir, String branchName) {
        pullRepository(workingDir, branchName, null);
    }

    public void pullRepository(String workingDir, String branchName, String token) {
        try (Git git = Git.open(new File(workingDir))) {
            var cmd = git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName(branchName);
            if (token != null && !token.isBlank()) {
                cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", token));
            }
            cmd.call();
        } catch (GitAPIException | IOException e) {
            throw new GitException("Failed to pull repository: " + redact(e.getMessage()), e);
        }
    }

    public String addWorktree(String mainClonePath, String alias) {
        Path repoRoot = Path.of(mainClonePath).getParent();
        String worktreeDir = repoRoot.resolve("trees").resolve(alias).toString();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "worktree", "add", "--relative-paths", "-b", alias, worktreeDir
            );
            pb.directory(new File(mainClonePath));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitException("Failed to add worktree: " + output.trim());
            }
        } catch (IOException e) {
            throw new GitException("Failed to add worktree: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Worktree creation interrupted", e);
        }

        return worktreeDir;
    }

    public void addForkRemote(String localPath, String forkUrl) {
        try (Git git = Git.open(new File(localPath))) {
            git.remoteAdd()
                    .setName("fork")
                    .setUri(new URIish(forkUrl))
                    .call();
        } catch (GitAPIException | IOException | URISyntaxException e) {
            throw new GitException("Failed to add fork remote: " + redact(e.getMessage()), e);
        }
    }

    public void setForkRemoteUrl(String localPath, String forkUrl) {
        try (Git git = Git.open(new File(localPath))) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "fork", "url", forkUrl);
            config.save();
        } catch (IOException e) {
            throw new GitException("Failed to set fork remote URL: " + redact(e.getMessage()), e);
        }
    }

    public void removeForkRemote(String localPath) {
        try (Git git = Git.open(new File(localPath))) {
            git.remoteRemove()
                    .setRemoteName("fork")
                    .call();
        } catch (GitAPIException | IOException e) {
            throw new GitException("Failed to remove fork remote: " + redact(e.getMessage()), e);
        }
    }

    public static String planBranchName(Long planId) {
        return "plan-" + planId + "-" + UUID.randomUUID().toString().substring(0, 8);
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
        try (Git git = Git.open(new File(workingDir))) {
            return git.getRepository().getBranch();
        } catch (IOException e) {
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

    private static String redact(String message) {
        if (message == null) return null;
        return message.replaceAll("://[^@]+@", "://***@");
    }
}
