# TSD UI Agent

## Pre requisites

- JDK 25
- Ollama (Local dev)

```shell
curl -fsSL https://ollama.com/install.sh | sh
ollama pull granite3.3:8b
ollama serve
```

## Dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

Currently /{taskId}/plan/enrich-requirement can be called and we will use Quarkus Lanchain4j for enriching the plan, what if I want to use the Anthopic models?


The current ui, task list page, has a series of step, one of the being "Plan", when I click there I want it to be similar to the "Requirement" step where I am asked to "Generate plan" with AI or just do it manually like it is doing right now.
- For any action against the Plan Step to be enabled the git repository step should be valid. The Plan step requires a git repository to be selected otherwise the step should be fully visible but disable so the user cannot add a plan, neither manually nor by ai
- If AI is asked to generate a plan, the backend should be able to use claude sdk for java in order to generate the plan in markdown and then eventually save the plan in PlanEntity
- The plan needs to be executed against the git repository configured together with the "requirement" definition defined in PlanEntity. Manually it is similar to my terminal command "claude {requirement} planMode"
- Design the solution in a way that it can be replaced by OpenCode in case Claude Code is not available for the user


claude --resume 467bab9d-40f6-4e3b-84c9-337825b66921