# TSD UI Agent

Quarkus-based backend that manages git repositories, tasks/plans, and integrates with Claude Code CLI for AI-driven plan generation and execution.

## Stack

- Java 25, Quarkus 3.32.x, Hibernate ORM Panache, Apache Camel, LangChain4j
- Build: Maven (`./mvnw`)
- Database: PostgreSQL

## Commands

- Dev mode: `./mvnw quarkus:dev`
- Run tests: `./mvnw test`
- Build: `./mvnw package`
- Integration tests: `./mvnw verify -DskipITs=false`

## Project Structure

```
src/main/java/org/acme/
  resources/          # REST endpoints (JAX-RS)
  dto/                # Data transfer objects
  models/jpa/entity/  # JPA entities (PanacheEntity active record)
  mapper/             # DTO <-> Entity mappers
  services/           # Business logic (@ApplicationScoped CDI beans)
  services/ai/        # LangChain4j AI services
  services/agent/     # Coding agent (CodingAgentService / ClaudeCodeService)
  services/git/       # Git operations + Camel routes
  services/sync/      # GitHub/Jira sync + Camel routes
  validation/         # Custom Bean Validation
src/main/resources/
  application.properties
src/test/java/org/acme/
specs/                # Playwright E2E test specs
.github/workflows/    # CI pipelines (build, E2E, image)
.claude/agents/       # Claude Code agent definitions (Playwright test agents)
.mcp.json             # MCP server config (PatternFly, Playwright, Camel)
```

## Code Patterns

- Entities extend `PanacheEntity` (active record pattern)
- Services are `@ApplicationScoped` CDI beans
- Long-running operations use `Thread.startVirtualThread`
- Transactions use `QuarkusTransaction.requiringNew()` for short-lived units
- Git operations are delegated through Camel routes via `ProducerTemplate`
- Configuration via MicroProfile Config (`@ConfigProperty`)
- Worktrees are used for isolated plan execution branches
- Claude CLI is invoked as a subprocess with `--output-format stream-json`

## Testing

- Unit tests: `@QuarkusTest` with REST Assured
- Mocking: `quarkus-junit-mockito`
- Async: Awaitility
- E2E: Playwright (specs in `specs/`, CI in `.github/workflows/ci-e2e*.yaml`)

## Important Notes

- Do not commit `.env` files (contains secrets)
- `tsd-agent.git.base-dir` controls where repositories are cloned
- MCP servers are configured in `.mcp.json`
