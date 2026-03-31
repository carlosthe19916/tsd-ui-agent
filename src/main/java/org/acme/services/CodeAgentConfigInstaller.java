package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.services.codeagent.CodingAgentType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Installs coding agent configuration (e.g. {@code .claude/} or {@code .opencode/}) into a workspace.
 *
 * <p>Used for <b>filesystem workspaces only</b>. Devcontainer workspaces use bind-mounts instead.
 *
 * <h3>Source and target</h3>
 * <pre>
 *   source (config repo)                        target (workspace worktree)
 *   ~/.tsd-agent-ui/repositories/               ~/.tsd-agent-ui/repositories/
 *     {config-repo}/default/                      {project-repo}/trees/{alias}/
 *     └── .claude/          ──── copy ────►       └── .claude/
 *          ├── settings.json                           ├── settings.json  (skipped if exists)
 *          ├── agents/                                 ├── agents/
 *          └── rules/                                  └── rules/
 * </pre>
 *
 * <h3>Merge behavior</h3>
 * Files already present in the target are <b>not overwritten</b> — only new files from the
 * config repo are added. This preserves any project-specific configuration the target repo
 * already ships.
 *
 * <h3>Git safety</h3>
 * After copying, the config directory is added to {@code .git/info/exclude} in the worktree
 * so it never appears in {@code git status} and cannot be accidentally committed.
 *
 * <h3>Error handling</h3>
 * All failures are non-fatal: warnings are logged and the workspace proceeds without config.
 */
@ApplicationScoped
public class CodeAgentConfigInstaller {

    private static final Logger LOG = Logger.getLogger(CodeAgentConfigInstaller.class);

    @ConfigProperty(name = "tsd-agent.coding-agent")
    CodingAgentType codingAgentType;

    /**
     * Copies the agent config directory from a config repository into a workspace worktree.
     *
     * @param worktreePath   target — the workspace worktree where config will be installed
     * @param configRepoPath source — the cloned config repository containing the config directory
     */
    public void installConfigFiles(Path worktreePath, Path configRepoPath) {
        Path source = configRepoPath.resolve(codingAgentType.configDir);
        if (!Files.isDirectory(source)) {
            LOG.infof("No %s directory found in config repo %s, skipping", codingAgentType.configDir, configRepoPath);
            return;
        }

        Path target = worktreePath.resolve(codingAgentType.configDir);
        LOG.infof("Installing agent config: %s -> %s", source, target);

        try {
            copyMerging(source, target);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to copy agent config from %s to %s, workspace will proceed without config", source, target);
            return;
        }

        excludeFromGit(worktreePath, codingAgentType.configDir);
    }

    /**
     * Recursively copies {@code source} into {@code target}, skipping files that already exist
     * in the target and skipping {@code .git} subtrees.
     */
    private void copyMerging(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(file));
                try {
                    Files.copy(file, dest);
                } catch (FileAlreadyExistsException e) {
                    LOG.debugf("Skipping existing file %s", dest);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Adds the config directory to {@code .git/info/exclude} so it is invisible to git.
     * For worktrees, resolves the {@code .git} file to the actual gitdir.
     */
    private void excludeFromGit(Path worktreePath, String dirName) {
        Path gitPath = worktreePath.resolve(".git");
        try {
            Path gitDir = resolveGitDir(worktreePath, gitPath);
            if (gitDir == null) {
                return;
            }

            Path excludeFile = gitDir.resolve("info").resolve("exclude");
            Files.createDirectories(excludeFile.getParent());
            String entry = "/" + dirName + "/";
            String existing = Files.exists(excludeFile) ? Files.readString(excludeFile) : "";
            if (!existing.contains(entry)) {
                Files.writeString(excludeFile,
                        (existing.endsWith("\n") || existing.isEmpty() ? existing : existing + "\n") + entry + "\n");
            }
            LOG.infof("Added %s to git exclude in %s", entry, excludeFile);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to add %s to git exclude in %s", dirName, worktreePath);
        }
    }

    /**
     * Resolves the git directory. In a regular clone this is {@code .git/}. In a worktree,
     * {@code .git} is a file containing {@code gitdir: <path>} pointing to the real gitdir.
     *
     * @return the resolved gitdir path, or {@code null} if it cannot be determined
     */
    private Path resolveGitDir(Path worktreePath, Path gitPath) throws IOException {
        if (Files.isRegularFile(gitPath)) {
            String content = Files.readString(gitPath).trim();
            if (content.startsWith("gitdir: ")) {
                Path gitDir = Path.of(content.substring("gitdir: ".length()));
                return gitDir.isAbsolute() ? gitDir : worktreePath.resolve(gitDir).normalize();
            }
            LOG.warnf("Unexpected .git file content in %s, skipping git exclude", worktreePath);
            return null;
        }
        return gitPath;
    }
}