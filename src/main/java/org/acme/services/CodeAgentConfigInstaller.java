package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Installs coding agent configuration directories into a workspace.
 *
 * <p>Copies all known config directories ({@code .claude}, {@code .opencode}, {@code .agents})
 * from a config repository into the workspace. Only directories that exist in the config repo
 * are copied.
 *
 * <p>Used for <b>filesystem workspaces only</b>. Devcontainer workspaces use bind-mounts instead.
 *
 * <h3>Source and target</h3>
 * <pre>
 *   source (config repo)                        target (workspace worktree)
 *   ~/.tsd-agent-ui/repositories/               ~/.tsd-agent-ui/repositories/
 *     {config-repo}/default/                      {project-repo}/trees/{alias}/
 *     ├── .claude/      ──── copy ────►           ├── .claude/
 *     ├── .opencode/    ──── copy ────►           ├── .opencode/
 *     └── .agents/      ──── copy ────►           └── .agents/
 * </pre>
 *
 * <h3>Merge behavior</h3>
 * Files already present in the target are <b>not overwritten</b> — only new files from the
 * config repo are added. This preserves any project-specific configuration the target repo
 * already ships.
 *
 * <h3>Git safety</h3>
 * After copying, each config directory is added to {@code .git/info/exclude} in the worktree
 * so it never appears in {@code git status} and cannot be accidentally committed.
 *
 * <h3>Error handling</h3>
 * All failures are non-fatal: warnings are logged and the workspace proceeds without config.
 */
@ApplicationScoped
public class CodeAgentConfigInstaller {

    private static final Logger LOG = Logger.getLogger(CodeAgentConfigInstaller.class);

    public static final List<String> CONFIG_DIRS = List.of(".claude", ".opencode", ".agents");

    /**
     * Copies all agent config directories from a config repository into a workspace worktree.
     *
     * @param worktreePath   target — the workspace worktree where config will be installed
     * @param configRepoPath source — the cloned config repository containing config directories
     */
    public void installConfigFiles(Path worktreePath, Path configRepoPath) {
        for (String configDir : CONFIG_DIRS) {
            Path source = configRepoPath.resolve(configDir);
            if (!Files.isDirectory(source)) {
                continue;
            }

            Path target = worktreePath.resolve(configDir);
            LOG.infof("Installing agent config: %s -> %s", source, target);

            try {
                copyMerging(source, target);
            } catch (IOException e) {
                LOG.warnf(e, "Failed to copy agent config from %s to %s, workspace will proceed without config", source, target);
                continue;
            }

            excludeFromGit(worktreePath, configDir);
        }
    }

    /**
     * Recursively copies {@code source} into {@code target}, preserving symlinks, skipping
     * files that already exist in the target, and skipping {@code .git} subtrees.
     *
     * <p>By default {@code walkFileTree} does not follow symlinks: symlinked directories are
     * reported via {@code visitFile()} rather than being descended into. This lets us recreate
     * the symlink in the target instead of copying the contents it points to.
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
                    if (Files.isSymbolicLink(file)) {
                        Files.createSymbolicLink(dest, Files.readSymbolicLink(file));
                    } else {
                        Files.copy(file, dest);
                    }
                } catch (FileAlreadyExistsException e) {
                    LOG.debugf("Skipping existing path %s", dest);
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
     * Resolves the <b>common</b> git directory for use with {@code info/exclude}.
     *
     * <p>In a regular clone this is {@code .git/}. In a worktree, {@code .git} is a file
     * containing {@code gitdir: <path>} pointing to {@code <common>/.git/worktrees/<alias>}.
     * Git only reads {@code info/exclude} from the common git dir, so we resolve up from
     * the worktree-specific gitdir to {@code <common>/.git}.
     *
     * @return the common git directory path, or {@code null} if it cannot be determined
     */
    private Path resolveGitDir(Path worktreePath, Path gitPath) throws IOException {
        if (Files.isRegularFile(gitPath)) {
            String content = Files.readString(gitPath).trim();
            if (content.startsWith("gitdir: ")) {
                Path worktreeGitDir = Path.of(content.substring("gitdir: ".length()));
                if (!worktreeGitDir.isAbsolute()) {
                    worktreeGitDir = worktreePath.resolve(worktreeGitDir).normalize();
                }
                // Worktree gitdir is <common>/.git/worktrees/<alias>
                // Resolve up to <common>/.git for info/exclude
                Path parent = worktreeGitDir.getParent();
                if (parent != null && parent.getFileName() != null
                        && parent.getFileName().toString().equals("worktrees")) {
                    return parent.getParent();
                }
                return worktreeGitDir;
            }
            LOG.warnf("Unexpected .git file content in %s, skipping git exclude", worktreePath);
            return null;
        }
        return gitPath;
    }
}
