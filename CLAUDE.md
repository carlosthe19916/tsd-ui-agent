# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

### Backend

```shell
./mvnw quarkus:dev          # Dev mode with live reload
./mvnw test                 # Run all unit tests
./mvnw test -Dtest=ClassName#methodName  # Run a single test
```

**Prerequisites:** JDK 25, PostgreSQL, Claude CLI or OpenCode.

### Frontend

```shell
npm ci    # Install dependencies, executed only once
npm run format:fix && npm run lint:fix && npm run lint # Lint and format validation
```

## Architecture

This is a **Quarkus 3.32.3** backend that manages software development tasks imported from external trackers (GitHub Issues, Jira) and orchestrates AI-assisted code changes via Claude CLI.

### Core Domain Flow

1. **Projects** (`/projects`) - Connect to GitHub/Jira via credentials and sync issues
2. **Tasks** (`/tasks`) - Issues imported from projects; each task can have a Plan
3. **Plans** (`/tasks/{id}/plan`) - Multi-step workflow per task:
   - **Requirement**: populated from task title and description
   - **Plan Generation**: Claude CLI analyzes codebase and produces implementation plan
   - **Plan Execution**: Claude CLI implements the plan in a git worktree
   - **Change Request**: Creates PR/MR via Camel git routes

### Key Patterns

- **Panache Active Record**: Entities use public fields, extend `PanacheEntityBase`. **No Lombok.**
- **Camel Routes** for external integrations: HTTP calls to GitHub/Jira APIs (`services/sync/camel/`), git operations (`services/git/camel/`)
- **Async operations**: Long-running AI/CLI tasks use `TransactionSynchronization` to trigger work after commit, with `isXxxInProgress` boolean guards for concurrency
- **SSE streaming**: Plan execution output streamed via `ExecutionOutputBroadcaster` at `GET /tasks/{id}/plan/output`
- **CodingAgentService interface**: Abstraction over Claude CLI (`ClaudeCodeService`) designed to be swappable (e.g., OpenCode)

### Package Layout

```
org.acme
  models/jpa/entity/   # JPA entities (Panache Active Record, public fields)
  dto/                  # Request/response DTOs
  mapper/               # Entity <-> DTO mappers (manual, no MapStruct)
  resources/            # JAX-RS endpoints
  services/             # Business logic
    sync/               # External issue sync (GitHub, Jira)
      camel/            # Camel route builders and processors
    git/                # Git operations (clone, worktree, PR creation)
      camel/            # Camel routes for git commands
    agent/              # AI coding agent abstraction (Claude CLI)
  validation/           # Custom Bean Validation constraints
```

### Coding Agent Configuration

- Claude CLI or OpenCode selected via `tsd-agent.coding-agent` property (`CLAUDE` or `OPENCODE`)
- Plan generation and execution delegated to the configured coding agent running inside workspaces
