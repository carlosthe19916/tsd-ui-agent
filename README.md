# TSD UI Agent

A Quarkus-based backend that manages software development tasks imported from external trackers (GitHub Issues, Jira)
and orchestrates AI-assisted code changes via Claude CLI or OpenCode.

## Prerequisites

### Core Requirements (All Modes)

- **Git** - Version control
- **JDK 25** - Java Development Kit
- **Maven** - Build tool (wrapper included via `./mvnw`)
- **Docker | Podman** - Install using https://github.com/devcontainers/cli
- **Devcontainer CLI** - Install using https://github.com/devcontainers/cli

## Quick Start

## Development Mode

Run the application in dev mode with live coding (hot reload):

```shell
./mvnw quarkus:dev
```

The server is running at http://localhost:8080

## UI

You need NodeJS 22

```shell
cd ui
npm ci
npm run start:dev
```

- UI available at http://localhost:3000

## Configuration

### Application Properties

Edit `src/main/resources/application.properties` to customize the app.

#### Google Vertex AI (Claude Devcontainers)

When running Claude Code via Google Vertex AI (instead of the direct Anthropic API), two properties are required:

- **`tsd-agent.devcontainer.claude.env-passthrough`** — forwards Vertex AI environment variables into the devcontainer:
  `CLAUDE_CODE_USE_VERTEX`, `ANTHROPIC_VERTEX_PROJECT_ID`, `CLOUD_ML_REGION`, `GOOGLE_CLOUD_PROJECT`. These tell Claude
  Code to authenticate through Vertex AI.
- **`tsd-agent.devcontainer.claude.mounts.gcloud`** — bind-mounts the host's gcloud Application Default Credentials (
  ADC) file (`~/.config/gcloud/application_default_credentials.json`) into the container so the Vertex AI SDK can
  authenticate.

#### GitHub App Integration

The following envs are needed to integrate with github app:

| Environment Variable                | Description                                          |
|-------------------------------------|------------------------------------------------------|
| `QUARKUS_GITHUB_APP_APP_ID`         | Your GitHub App's numeric ID                         |
| `QUARKUS_GITHUB_APP_APP_NAME`       | Your GitHub App's Name                               |
| `QUARKUS_GITHUB_APP_PRIVATE_KEY`    | PEM-encoded private key for the GitHub App           |
| `QUARKUS_GITHUB_APP_WEBHOOK_SECRET` | Webhook secret configured in the GitHub App settings |

In dev mode you will also need to set `QUARKUS_GITHUB_APP_WEBHOOK_PROXY_URL` as described at https://docs.quarkiverse.io/quarkus-github-app/dev/create-github-app.html

#### AI Triage (Gemini)

The AI-powered issue triage feature uses Google AI Gemini via LangChain4j. The API key is configured as:

```properties
quarkus.langchain4j.ai.gemini.api-key=${GOOGLE_AI_GEMINI_API_KEY:dummy}
```

Set the `GOOGLE_AI_GEMINI_API_KEY` environment variable with a valid Google AI Gemini API key to enable this feature.
Without it, the value defaults to `dummy` and triage calls will fail.
