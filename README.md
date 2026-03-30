# TSD UI Agent

## Hola Mundo

A Quarkus-based backend that manages software development tasks imported from external trackers (GitHub Issues, Jira) and orchestrates AI-assisted code changes via Claude CLI or OpenCode.

## Prerequisites

The application supports three workspace types, selectable per-workspace from the UI: **FILESYSTEM** (local development), **DOCKER** (devcontainer-based), and **KUBERNETES** (Eclipse Che/K8s). Prerequisites vary by type.

### Core Requirements (All Modes)
- **Git** - Version control
- **JDK 25** - Java Development Kit
- **Maven** - Build tool (wrapper included via `./mvnw`)

### Filesystem Mode (Local Development)
- **Coding Agent** - Claude CLI or OpenCode (see Coding Agent section below)

Note: PostgreSQL is automatically provided by Quarkus Dev Services in dev mode - no manual installation required.

### Docker Mode (Default)
- **Docker or Podman** - Container runtime
- **Devcontainer CLI** - Install with `npm install -g @devcontainers/cli`

### Kubernetes Mode
- **Kubernetes Cluster** - With Eclipse Che or Devfile support

#### Coding Agent
Choose one:

- **Claude CLI**
  ```shell
  curl -fsSL https://claude.ai/install.sh | bash
  claude --version
  ```

- **OpenCode CLI**
  ```shell
  curl -fsSL https://opencode.ai/install | bash
  opencode --version
  ```

Configure the agent in `application.properties`:
```properties
tsd-agent.coding-agent=CLAUDE  # or OPENCODE
```

## Quick Start

### Validate Prerequisites

Before starting, you can validate that all required prerequisites are installed:

```shell
./scripts/validate-prerequisites.sh
```

This script will check:
- Common requirements (Git)
- Filesystem mode requirements (JDK 25, PostgreSQL, coding agents)
- Docker mode requirements (Docker/Podman, Docker Compose)

## Development Mode

Run the application in dev mode with live coding (hot reload):

```shell
./mvnw quarkus:dev
```

**Dev mode features:**
- Live reload on code changes
- Dev UI available at http://localhost:8080/q/dev
- Continuous testing with `./mvnw quarkus:test`
- Debugging on port 5005

## UI

You need NodeJS 22

```shell
cd ui
npm ci
npm run start:dev
```

- UI available at http://localhost:3000/q/dev

## Configuration

### Application Properties

Edit `src/main/resources/application.properties` to customize the app
