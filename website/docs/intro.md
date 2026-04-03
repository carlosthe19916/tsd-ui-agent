---
sidebar_position: 1
slug: /
---

# Introduction

TSD UI Agent is a **Quarkus**-based backend that manages software development tasks imported from external trackers (GitHub Issues, Jira) and orchestrates AI-assisted code changes via coding agents like Claude CLI or OpenCode.

## What it does

1. **Connects to your project** — Link GitHub or Jira repositories using credentials
2. **Imports issues as tasks** — Syncs issues from connected projects into a unified task list
3. **Generates implementation plans** — An AI coding agent analyzes your codebase and produces a step-by-step plan for each task
4. **Executes plans** — The coding agent implements the plan inside an isolated git worktree
5. **Creates pull requests** — Submits the changes as a PR/MR back to your repository

## Tech stack

- **Quarkus 3.32.3** — Java backend framework
- **JPA (Panache Active Record)** — Database entities with public fields
- **Apache Camel** — Integration routes for GitHub/Jira APIs and git operations
- **SSE streaming** — Real-time plan execution output
- **Devcontainers** — Isolated workspaces for coding agents

## Getting started

### Prerequisites

- JDK 25
- PostgreSQL 14–17
- Claude CLI or OpenCode

### Running the backend

```bash
./mvnw quarkus:dev
```

This starts the backend in dev mode with live reload on the default Quarkus port (8080).
