# TSD UI Agent

A Quarkus-based backend that manages software development tasks imported from external trackers (GitHub Issues, Jira) and orchestrates AI-assisted code changes via Claude CLI or OpenCode.

## Prerequisites

The application supports three workspace types, selectable per-workspace from the UI: **FILESYSTEM** (local development), **DOCKER** (devcontainer-based), and **KUBERNETES** (Eclipse Che/K8s). Prerequisites vary by type.

### Core Requirements (All Modes)
- **Git** - Version control
- **JDK 25** - Java Development Kit
- **Maven** - Build tool (wrapper included via `./mvnw`)

### Database
- **PostgreSQL 17+** - Required for production deployments. In dev mode, Quarkus Dev Services automatically starts a PostgreSQL container — no manual installation needed.

### Filesystem Mode (Local Development)
- **Coding Agent** - Claude CLI or OpenCode (see Coding Agent section below)

Note: In dev mode, PostgreSQL is provided automatically by Quarkus Dev Services (see Database section above).

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

### Database

The application requires **PostgreSQL 17** or later. The default Docker Compose setup uses the `postgres:17` image (configurable via `POSTGRESQL_IMAGE` in `.env`).

| Mode | Database Setup |
|------|---------------|
| **Dev mode** (`quarkus:dev`) | Automatic via Quarkus Dev Services — no setup needed |
| **Docker Compose** | Started automatically by `docker-compose.yaml` |
| **Production** | Provide connection details via `QUARKUS_DATASOURCE_JDBC_URL` environment variable |

An **H2 profile** is also available for standalone/offline use:
```shell
./mvnw quarkus:dev -Dquarkus.profile=h2
```

### Application Properties

Edit `src/main/resources/application.properties` to customize the app
