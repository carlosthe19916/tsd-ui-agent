---
sidebar_position: 3
---

# Configuration

## Coding agent

The coding agent is selected via the `tsd-agent.coding-agent` property in `application.properties`:

```properties
tsd-agent.coding-agent=CLAUDE   # or OPENCODE
```

- **CLAUDE** — Uses Claude CLI (`@anthropic-ai/claude-code`)
- **OPENCODE** — Uses OpenCode

Each agent runs inside a devcontainer workspace with its own configuration, volume mounts, and lifecycle commands.

## API endpoints

| Endpoint | Description |
|---|---|
| `GET /projects` | List all projects |
| `POST /projects` | Create a project connection |
| `GET /tasks` | List all tasks |
| `POST /tasks/{id}/plan` | Generate a plan for a task |
| `PUT /tasks/{id}/plan` | Execute a plan |
| `GET /tasks/{id}/plan/output` | SSE stream of execution output |
