Refactoring Plan: Multi-Environment Execution (Filesystem, Docker, Kubernetes)

Context

The app currently runs all git operations and Claude CLI commands as local processes on the host filesystem, using git worktrees for task isolation. This refactoring introduces a Workspace abstraction to support 3 execution environments:

- Filesystem: Current behavior (worktrees, local processes)
- Docker: Each task runs in a devcontainer (devcontainer CLI)
- Kubernetes: Each task runs in a pod managed via devfile.io / Eclipse Che

The execution mode is selected per-workspace by the user from the UI. Implementation is incremental: Phase 1 (abstractions + Filesystem), Phase 2 (Docker/devcontainers), Phase 3 (Kubernetes/devfile).

 ---
Phase 1: Core Abstractions + Filesystem Refactor

1.1 Create Workspace interface

New file: src/main/java/org/acme/services/workspace/Workspace.java

public interface Workspace {
String id();                    // worktree path, container ID, or pod name
String workingDirectory();      // repo root inside the workspace
boolean isAlive();

     String exec(String... command) throws WorkspaceException;
     void execStreaming(Consumer<String> lineConsumer, String... command) throws WorkspaceException;
     String execWithStdin(byte[] stdin, String... command) throws WorkspaceException;
     void execWithStdinStreaming(byte[] stdin, Consumer<String> lineConsumer, String... command) throws WorkspaceException;
}

1.2 Create WorkspaceManager interface

New file: src/main/java/org/acme/services/workspace/WorkspaceManager.java

public interface WorkspaceManager {
Workspace provision(WorkspaceRequest request) throws WorkspaceException;
Workspace reconnect(String workspaceId) throws WorkspaceException;
void destroy(String workspaceId) throws WorkspaceException;
boolean exists(String workspaceId);
}

1.3 Supporting types

New files in org.acme.services.workspace:
- ExecutionMode.java — enum: FILESYSTEM, DOCKER, KUBERNETES
- WorkspaceRequest.java — record with gitUrl, branch, forkUrl, branchName, credentialToken, environment (Map)
- WorkspaceException.java — extends RuntimeException

1.4 Filesystem implementation

New files in org.acme.services.workspace.filesystem:

- FilesystemWorkspace.java — wraps a worktree directory path, implements Workspace via ProcessBuilder
- FilesystemWorkspaceManager.java — delegates to existing GitManager for clone/worktree operations
    - provision(): calls GitManager.cloneRepository() if needed, then GitManager.addWorktree(), returns FilesystemWorkspace
    - reconnect(): verifies directory exists, returns FilesystemWorkspace
    - destroy(): calls GitManager.removeWorktree()

1.5 CDI producer for mode selection

New file: src/main/java/org/acme/services/workspace/WorkspaceManagerProducer.java

Replaced by WorkspaceManagerResolver, which resolves the correct WorkspaceManager per-workspace based on the ExecutionMode stored on each WorkspaceEntity.

1.6 Refactor CodingAgentService interface

Modify: src/main/java/org/acme/services/agent/CodingAgentService.java

Change signature from String workdir to Workspace workspace:
String generatePlan(Workspace workspace, String requirement, Long taskId);
void executePlan(Workspace workspace, String planText, Long taskId);

1.7 Refactor ClaudeCodeService

Modify: src/main/java/org/acme/services/agent/ClaudeCodeService.java

Replace ProcessBuilder usage with workspace.execWithStdinStreaming(). The claude command name still comes from config. The streaming + JSON parsing + broadcaster logic stays the same — only the process execution mechanism changes.

1.8 Create WorkspaceGitOperations

New file: src/main/java/org/acme/services/workspace/WorkspaceGitOperations.java

Utility that runs git commands through Workspace.exec():
- addAll(workspace) → workspace.exec("git", "add", ".")
- commit(workspace, message) → workspace.exec("git", "commit", "-m", message)
- push(workspace, remoteName, branchName) → workspace.exec("git", "push", ...)
- pushToUrl(workspace, url, refspec) → workspace.exec("git", "push", "--force", ...)
- getCurrentBranch(workspace) → workspace.exec("git", "rev-parse", "--abbrev-ref", "HEAD")

1.9 Refactor PlanService

Modify: src/main/java/org/acme/services/PlanService.java

- Inject WorkspaceManager instead of WorktreeService
- In doPlanGeneration and doPlanExecution: replace worktreeService.ensureWorktree() with workspaceManager.provision() or workspaceManager.reconnect() based on whether plan.workspaceId is set
- Store workspace.id() in plan.workspaceId
- Pass Workspace to codingAgentService instead of String worktreePath

1.10 Refactor ChangeRequestService

Modify: src/main/java/org/acme/services/ChangeRequestService.java

- Inject WorkspaceManager and WorkspaceGitOperations
- Replace gitManager.addAll/commit/push/pushToUrl calls with WorkspaceGitOperations equivalents using Workspace
- Reconnect to workspace via workspaceManager.reconnect(plan.workspaceId)
- getCurrentBranch for the main clone path still uses GitManager (needed only for Filesystem mode fallback when branch not set)

1.11 Refactor WorktreeService

Modify: src/main/java/org/acme/services/WorktreeService.java

- Remove ensureWorktree() — this is now handled by WorkspaceManager
- Keep openVSCode(), openTerminal(), openClaude() — these are desktop integration features
- In Phase 2/3, these will be extended for Docker/K8s (devcontainer open, Eclipse Che URL)

1.12 Update PlanEntity

Modify: src/main/java/org/acme/models/jpa/entity/PlanEntity.java

- Add workspaceId field (String column)
- Keep worktreePath temporarily for backward compatibility during migration
- FilesystemWorkspaceManager sets workspaceId to the worktree path (same value)

1.13 Configuration

Modify: src/main/resources/application.properties

(Execution mode is now per-workspace, selected by the user from the UI)

1.14 Update existing tests

Modify: Tests that reference WorktreeService.ensureWorktree() or CodingAgentService method signatures:
- src/test/java/org/acme/services/PlanServiceTest.java
- src/test/java/org/acme/services/ChangeRequestServiceTest.java

 ---
Phase 2: Docker / Devcontainers

2.1 Devcontainer approach

Use the devcontainer CLI (@devcontainers/cli) to manage containers:
- devcontainer up — create and start a container from a devcontainer.json
- devcontainer exec — run commands inside the container
- The app generates a devcontainer.json per workspace in a temp directory
- VS Code can connect to the running devcontainer via "Attach to Running Container"

2.2 Docker implementation

New files in org.acme.services.workspace.docker:

- DevcontainerWorkspace.java — implements Workspace
    - id() returns container ID (from devcontainer up output)
    - exec() / execStreaming() use devcontainer exec --container-id <id> via ProcessBuilder
    - workingDirectory() returns /workspaces/<repo-name>
- DevcontainerWorkspaceManager.java — manages devcontainer lifecycle
    - provision():
      i. Create temp dir with generated devcontainer.json (base image with git + claude + node)
      ii. Clone repo into a Docker volume
      iii. Run devcontainer up --workspace-folder <path>
      iv. Setup branch and remotes via devcontainer exec
      v. Pass ANTHROPIC_API_KEY as container env var
    - destroy(): stop container, remove volume
    - reconnect(): verify container running, return DevcontainerWorkspace

2.3 Container image

New file: Dockerfile.workspace (or use a base image reference in devcontainer.json)

Requirements: git, claude CLI (via npm), node, basic dev tools.

The devcontainer.json template:
{
"image": "tsd-agent-workspace:latest",
"containerEnv": { "ANTHROPIC_API_KEY": "${localEnv:ANTHROPIC_API_KEY}" },
"mounts": ["source=workspace-${uuid},target=/workspaces,type=volume"]
}

2.4 Desktop integration for Docker

Modify: WorktreeService (or new DesktopIntegrationService)
- openTerminal(): open host terminal running docker exec -it <container-id> /bin/bash
- openVSCode(): run devcontainer open or code --folder-uri vscode-remote://attached-container+<hex-id>/workspaces/<name>

2.5 Configuration

tsd-agent.docker.devcontainer-cli=devcontainer
tsd-agent.docker.image=tsd-agent-workspace:latest

 ---
Phase 3: Kubernetes / Devfile / Eclipse Che

3.1 Approach

Use devfile.io format to define workspaces and Eclipse Che (or a lightweight devfile operator) to manage them:
- Devfile defines the container, volumes, and IDE configuration
- Eclipse Che provides web-based IDE (Theia/VS Code) accessible via URL
- The app generates devfile YAML per workspace and uses the Kubernetes API to create resources

3.2 Kubernetes implementation

New files in org.acme.services.workspace.kubernetes:

- KubernetesWorkspace.java — implements Workspace
    - id() returns namespace/pod-name
    - exec() uses Fabric8 client.pods().exec()
    - execStreaming() uses Fabric8 exec watch API with ExecListener
    - workingDirectory() returns /projects/<repo-name> (devfile convention)
- KubernetesWorkspaceManager.java — manages pod lifecycle
    - provision():
      i. Generate devfile YAML for the workspace
      ii. Create PVC for workspace storage
      iii. Create Pod from devfile spec (or use Eclipse Che workspace API)
      iv. Wait for pod Ready
      v. Exec git clone + branch setup inside pod
      vi. Mount ANTHROPIC_API_KEY from K8s Secret
    - destroy(): delete pod + PVC
    - reconnect(): find pod, verify running

3.3 Dependencies

 <dependency>
     <groupId>io.quarkus</groupId>
     <artifactId>quarkus-kubernetes-client</artifactId>
 </dependency>

3.4 Desktop integration for Kubernetes

- openVSCode() / openTerminal(): return Eclipse Che workspace URL for browser-based IDE
- The Che URL is derived from the workspace/devfile configuration

3.5 Configuration

tsd-agent.kubernetes.namespace=tsd-agent-workspaces
tsd-agent.kubernetes.image=tsd-agent-workspace:latest
tsd-agent.kubernetes.storage-class=standard
tsd-agent.kubernetes.storage-size=5Gi
tsd-agent.kubernetes.che-url=https://che.example.com

 ---
Package Structure (final)

org.acme.services.workspace/
ExecutionMode.java
Workspace.java
WorkspaceManager.java
WorkspaceRequest.java
WorkspaceException.java
WorkspaceGitOperations.java
WorkspaceManagerProducer.java
filesystem/
FilesystemWorkspace.java
FilesystemWorkspaceManager.java
docker/
DevcontainerWorkspace.java
DevcontainerWorkspaceManager.java
kubernetes/
KubernetesWorkspace.java
KubernetesWorkspaceManager.java

Files Modified (Phase 1)

┌────────────────────────────────────────┬──────────────────────────────────────────────────────────────────┐
│                  File                  │                              Change                              │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ services/agent/CodingAgentService.java │ Change String workdir → Workspace workspace                      │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ services/agent/ClaudeCodeService.java  │ Use workspace.execWithStdinStreaming() instead of ProcessBuilder │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ services/PlanService.java              │ Use WorkspaceManager instead of WorktreeService.ensureWorktree() │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ services/ChangeRequestService.java     │ Use WorkspaceGitOperations + Workspace instead of GitManager     │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ services/WorktreeService.java          │ Remove ensureWorktree(), keep desktop integration methods        │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ models/jpa/entity/PlanEntity.java      │ Add workspaceId field                                            │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ resources/application.properties       │ (execution mode removed — now per-workspace)                     │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ services/PlanServiceTest.java          │ Update for new interfaces                                        │
├────────────────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ services/ChangeRequestServiceTest.java │ Update for new interfaces                                        │
└────────────────────────────────────────┴──────────────────────────────────────────────────────────────────┘

Existing Code Reused

- GitManager — continues to handle clone/worktree lifecycle in Filesystem mode; FilesystemWorkspaceManager wraps it
- GitRoutes (Camel) — unchanged, still used by GitManager for Filesystem mode
- ExecutionOutputBroadcaster — unchanged, all modes publish to it
- ChangeRequestProvider (GitHub/GitLab) — unchanged, PR/MR creation is always HTTP API calls from the app
- GitManager.planBranchName(), extractOwnerRepo(), extractHost() — static utilities reused as-is

Deliverables

- Save this design as docs/multi-environment-design.md in the project (tracked in git)
- Implementation code per phase

Verification

1. Phase 1: Run ./mvnw test — all existing tests must pass after refactor
2. Phase 1: Manual test: create a task, generate plan, execute plan, create PR — same workflow as before in Filesystem mode
3. Phase 2: Manual test: create a workspace with "Container" type from the UI, verify container created via docker ps, plan generation works inside container, devcontainer exec runs git/claude commands
4. Phase 3: Manual test: create a workspace with Kubernetes type, verify pod created via kubectl get pods, plan works inside pod