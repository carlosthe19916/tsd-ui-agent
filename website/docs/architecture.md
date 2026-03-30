---
sidebar_position: 2
---

# Architecture

## Core domain flow

```
Projects -> Tasks -> Plans -> Code Changes -> Pull Requests
```

- **Projects** (`/projects`) — Connect to GitHub/Jira via credentials and sync issues
- **Tasks** (`/tasks`) — Issues imported from projects; each task can have a Plan
- **Plans** (`/tasks/{id}/plan`) — Multi-step workflow per task

### Plan lifecycle

Each plan goes through these stages:

1. **Requirement** — Populated from the task title and description
2. **Plan Generation** — The coding agent analyzes the codebase and produces an implementation plan
3. **Plan Execution** — The coding agent implements the plan in a git worktree
4. **Change Request** — A PR/MR is created via Camel git routes

## Package layout

```
org.acme
  models/jpa/entity/   # JPA entities (Panache Active Record, public fields)
  dto/                  # Request/response DTOs
  mapper/               # Entity <-> DTO mappers
  resources/            # JAX-RS endpoints
  services/
    sync/               # External issue sync (GitHub, Jira)
      camel/            # Camel route builders and processors
    git/                # Git operations (clone, worktree, PR creation)
      camel/            # Camel routes for git commands
    agent/              # AI coding agent abstraction
  validation/           # Custom Bean Validation constraints
```

## Key patterns

- **Panache Active Record** — Entities use public fields, no Lombok
- **Camel Routes** — HTTP calls to GitHub/Jira APIs, git operations
- **Async operations** — Long-running AI tasks use `TransactionSynchronization` to trigger work after commit
- **SSE streaming** — Plan execution output streamed via `ExecutionOutputBroadcaster`
- **CodingAgentService** — Abstraction over Claude CLI and OpenCode, selected via `tsd-agent.coding-agent` config property
