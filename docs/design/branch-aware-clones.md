# Branch-Aware Clone Directories

**Date:** 2026-04-03
**Status:** Implemented

---

## Overview

Each (repo URL, branch) pair gets its own clone directory. Workspaces are always git worktrees created from these clones — never the clone directory itself. Enrichment and devcontainer config are generated per-branch because project structure (pom.xml, .devcontainer, package.json) can differ between branches.

---

## Directory Structure

### Repositories (clones)

```
{baseDir}/repositories/{sanitizedUrl}/
  ├── default/         ← clone (no branch specified — used by /implement and UI with empty branch)
  ├── main/            ← clone (UI created GitEntity with branch="main")
  ├── develop/         ← clone (UI created GitEntity with branch="develop")
  └── trees/
      ├── a1b2c3d4/    ← worktree (workspace, created from any of the above clones)
      └── e5f6g7h8/    ← worktree (another workspace)
```

### Devcontainer configs

```
{baseDir}/devcontainers/{sanitizedUrl}/
  ├── default/
  │   ├── devcontainer.json       ← base config for default branch
  │   └── {worktreeAlias}/
  │       └── devcontainer.json   ← per-workspace patched config
  └── main/
      ├── devcontainer.json       ← base config for main branch
      └── {worktreeAlias}/
          └── devcontainer.json   ← per-workspace patched config
```

---

## Clone Path Resolution

`GitManager.cloneDir(baseDir, gitUrl, branch)`:
- Sanitizes the URL (strips protocol, `.git` suffix, normalizes)
- Uses branch name as directory name, or `"default"` if branch is null/empty

```
cloneDir(baseDir, "https://github.com/owner/repo", "main")    → {sanitized}/main/
cloneDir(baseDir, "https://github.com/owner/repo", null)       → {sanitized}/default/
cloneDir(baseDir, "https://github.com/owner/repo", "")         → {sanitized}/default/
cloneDir(baseDir, "https://github.com/owner/repo", "develop")  → {sanitized}/develop/
```

---

## Invariant: Workspaces Are Always Worktrees

No workspace ever runs directly in the clone directory. The flow is:

1. **Clone** (or pull latest) to `{sanitized}/{branch}/`
2. **Pull latest** before creating worktree (ensures fresh start)
3. **Create worktree** via `gitManager.addWorktree(cloneDir, alias)` → `{sanitized}/trees/{alias}/`
4. **Workspace** points to the worktree path

---

## Fork URL: No Conflict By Design

- `forkUrl` only affects the **push target** in `ChangeRequestService`, controlled by `PipelineContext.forkUrl()`
- `/implement` sets `forkUrl = null` → pushes to origin
- UI sets `forkUrl` from `GitEntity.forkUrl` → pushes to fork
- An extra `fork` remote in the clone is harmless if unused

---

## End-to-End Flows

### UI: Create GitEntity with branch="main"

```
POST /gits { url: "https://github.com/owner/repo", branch: "main" }
  → GitService.doProvision():
    1. Clone to {sanitized}/main/
    2. Add fork remote to main/ clone if forkUrl set
    3. Enrichment + devcontainer config from {sanitized}/main/

POST /workspaces { git: { id: 1 }, executionMode: "DOCKER" }
  → FilesystemWorkspaceManager.createWorktree():
    1. Look for clone in {sanitized}/main/ ← found
    2. Pull latest main branch
    3. Create worktree at {sanitized}/trees/{alias}/
  → DevcontainerWorkspaceManager:
    4. Read base config from devcontainers/{sanitized}/main/devcontainer.json
    5. Run devcontainer up on worktree
```

### UI: Create GitEntity with no branch

```
POST /gits { url: "https://github.com/owner/repo" }
  → GitService.doProvision():
    1. Clone to {sanitized}/default/ (repo HEAD)
    2. Enrichment + devcontainer config from {sanitized}/default/

POST /workspaces { git: { id: 1 }, executionMode: "DOCKER" }
  → FilesystemWorkspaceManager.createWorktree():
    1. Look for clone in {sanitized}/default/ ← found
    2. Pull latest
    3. Create worktree at {sanitized}/trees/{alias}/
  → DevcontainerWorkspaceManager:
    4. Read base config from devcontainers/{sanitized}/default/devcontainer.json
    5. Run devcontainer up on worktree
```

### /implement (always default branch)

```
/implement on issue #169
  → IssueImplementationService.doImplement():
    1. Clone to {sanitized}/default/ (or pull if exists)
    2. Enrichment + devcontainer config from {sanitized}/default/
  → FilesystemWorkspaceManager.createWorktree():
    3. Pull latest
    4. Create worktree at {sanitized}/trees/{alias}/
  → DevcontainerWorkspaceManager:
    5. Read base config from devcontainers/{sanitized}/default/devcontainer.json
    6. Run devcontainer up on worktree
  → PlanService.doFullPipeline():
    7. Generate plan → execute plan → create PR
```

---

## Shared Clone Safety

When `/implement` and the UI share `{sanitized}/default/` (both use no branch):
- Both create **independent worktrees** — no file-level conflict
- Fork remotes added by UI are harmless to `/implement` (it pushes to origin)
- Pull operations are additive — pulling latest doesn't break existing worktrees
- Each worktree has its own branch (`git worktree add -b {alias}`)
