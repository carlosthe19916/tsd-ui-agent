# AI-Powered Issue Triage

**Date:** 2026-04-02
**Status:** Proposed
**Prerequisite:** [Issue Triage Flow](issue-triage-flow.md)

---

## Overview

This document describes how AI automates the issue lifecycle defined in [Issue Triage Flow](issue-triage-flow.md). It maps each stage of the flow to a specific AI capability, identifies the components needed, and defines how AI suggestions integrate with the existing human-driven slash commands.

### Design Principles

- **Overlay, not replace** — the AI triage system automates the same flow maintainers follow manually. The label state machine and slash commands remain unchanged.
- **Suggest first, apply if confident** — the AI always posts its reasoning as a comment. Labels are auto-applied only above a configurable confidence threshold.
- **Human override** — maintainers can always correct AI decisions using the existing `/triage`, `/kind`, `/priority` commands.

---

## Stage-by-Stage AI Automation

### Stage 1: Intake

> *New issue opened. Automation applies `needs-triage`, `needs-kind`, `needs-priority`.*

**Current state:** Fully automated by `IssueLabelReconciler`.

**AI automation:** None needed. This stage is already complete.

---

### Stage 2: Triage Gate

> *A maintainer reviews the issue and replaces `needs-triage` with exactly one `triage/*` label.*

**Current state:** Manual — requires a maintainer to post `/triage accepted`, `/triage duplicate`, etc.

**AI automation:** This is the core AI decision. When a new issue is opened, the AI analyzes its content and makes the same determination a maintainer would:

| Decision | AI Analysis | Resulting Label | Side Effects |
|----------|-------------|-----------------|--------------|
| **Duplicate** | Compare title + body against titles of recent open issues | `triage/duplicate` | Post comment linking the candidate original issue |
| **Needs information** | Check for repro steps, expected behavior, actual behavior, environment details | `triage/needs-information` | Post comment listing what is missing |
| **Support question** | Detect usage questions ("how do I", "is it possible to", help requests) | `triage/support` | Post comment redirecting to support channel |
| **Not reproducible** | Flag unclear or contradictory reproduction steps | `triage/not-reproducible` | Post comment asking for clarification |
| **Valid issue** | None of the above apply, issue is clear and actionable | `triage/accepted` | Proceed immediately to Stages 3 and 4 |

**Duplicate detection approach:** Fetch the 50 most recent open issues from the same repository and include their titles in the LLM prompt. The LLM compares semantically — no vector database or embeddings needed. This scales to hundreds of open issues; if the project grows beyond that, a future enhancement can add embedding-based pre-filtering.

**Information sufficiency check:** For issues that appear to be bug reports, the AI checks for:
- Reproduction steps
- Expected behavior
- Actual behavior
- Environment or version information

If key elements are missing, the AI suggests `triage/needs-information` and lists what the reporter should add.

---

### Stage 3: Classification Gate

> *For accepted issues, replace `needs-kind` with a `kind/*` label.*

**Current state:** Manual — requires `/kind bug`, `/kind feature`, or `/kind documentation`.

**AI automation:** The AI classifies the issue based on its content:

| Signal | Classification |
|--------|---------------|
| Error reports, broken behavior, regressions, stack traces | `kind/bug` |
| New capability requests, enhancements, "it would be nice if" | `kind/feature` |
| Missing docs, unclear docs, README updates, typos | `kind/documentation` |

This classification happens in the same LLM call as the triage gate — not a separate request. If the triage decision is anything other than `triage/accepted`, kind classification is still provided but not applied (the issue must pass the triage gate first).

---

### Stage 4: Prioritization Gate

> *Replace `needs-priority` with a `priority/*` label.*

**Current state:** Manual — requires `/priority backlog`, `/priority important-soon`, etc.

**AI automation:** The AI suggests a priority level based on severity signals in the issue:

| Signal | Suggested Priority |
|--------|--------------------|
| Security vulnerability, data loss, crash in production | `priority/critical-urgent` or `priority/release-blocker` |
| Broken core functionality, blocking users | `priority/important-soon` |
| Non-critical bug, improvement with clear value | `priority/important-longterm` or `priority/backlog` |
| Idea or suggestion without strong evidence | `priority/awaiting-more-evidence` |

Priority is inherently subjective. The AI provides a suggestion, but the confidence threshold for auto-applying priority labels should be higher than for kind or triage labels — or priority may require explicit human confirmation regardless of confidence.

---

### Stage 5: Ready to Work

> *Issue has `triage/accepted` + `kind/*` + `priority/*`.*

**AI automation:** None needed. The `IssueLabelReconciler` already removes `needs-*` sentinel labels when the corresponding `triage/*`, `kind/*`, `priority/*` labels are present. Once all three are applied (by AI or human), the issue is automatically "Ready to Work".

---

### Stage 6: PR Lifecycle

> *PR opened, reviewed, `lgtm`, merge & close.*

**AI automation:** Out of scope for triage. The existing plan generation and execution pipeline handles code changes. The triage system's job ends when the issue reaches "Ready to Work".

---

## Re-triage Loop

The [flow document](issue-triage-flow.md) shows `triage/needs-information` looping back to Triage Review when the reporter replies:

```
triage/needs-information → stall → reporter replies → re-triage
```

**AI automation:** Observe `issue_comment.created` events. If:
1. The issue has the `triage/needs-information` label, AND
2. The comment author is the original issue reporter

Then re-run the AI triage analysis with the updated context (original issue body + new comment). The AI re-evaluates whether the information gap has been addressed and may transition the issue to `triage/accepted`.

---

## Stale Issue Handling

> *Issues in `triage/needs-information` or `triage/not-reproducible` that receive no response within 30 days should be closed.*

**AI automation:** A `@Scheduled` Quarkus job runs periodically (e.g., daily) and queries issues with `triage/needs-information` or `triage/not-reproducible` that have had no activity for 30 days. It posts a comment explaining the closure reason and closes the issue.

---

## Architecture

### Why Quarkus LangChain4j (not Claude CLI)

Triage is a **text classification task** — it needs structured JSON output from an LLM, not a coding agent with file system access. Quarkus LangChain4j provides:

- `@RegisterAiService` interfaces with `@SystemMessage`/`@UserMessage` annotations
- Automatic JSON schema generation from Java records for structured output
- CDI-native integration (injectable like any other bean)
- Built-in fault tolerance and observability

The existing `CodingAgentService` / `ClaudeCodeService` are designed for long-running code generation in workspaces. Triage needs neither workspaces nor source code access — it operates purely on issue metadata.

A lighter model (Sonnet or Haiku) is preferred over Opus for triage, keeping latency and cost low.

### Pipeline

```
issues.opened webhook
       │
       ▼
IssueLabelReconciler.onOpened()     ← existing, adds needs-* labels
       │
       ▼
IssueTriageService.onOpened()       ← new, fires on same event
       │ (spawns virtual thread)
       ▼
Fetch 50 recent open issues         ← GHRepository.queryIssues()
       │
       ▼
Build prompt context:
  - New issue title + body
  - Valid labels from LabelConfig
  - Recent issue titles (for duplicate detection)
       │
       ▼
IssueTriageAiService.triageIssue()  ← LangChain4j AI Service
       │
       ▼
TriageResult (structured JSON)
       │
       ├─► Format and post comment (always)
       │
       └─► Apply labels if confidence >= threshold
           ├─ triage/* label (Stage 2)
           ├─ kind/* label (Stage 3, only if triage/accepted)
           └─ priority/* label (Stage 4, only if triage/accepted)
```

### Components

| Component | Purpose |
|-----------|---------|
| `IssueTriageAiService` | LangChain4j `@RegisterAiService` interface. Single method `triageIssue()` returns `TriageResult`. Prompt templates reference valid labels and recent issues. |
| `TriageResult` | Java record matching the stage decisions: triage decision, kind classification, priority suggestion, duplicate info, information sufficiency, confidence scores per axis, and reasoning. |
| `IssueTriageService` | `@ApplicationScoped` orchestrator. Listens to `@Issue.Opened` webhook, spawns virtual thread (following `PlanService` pattern), calls AI service, posts comment, applies labels. |
| `TriageCommentFormatter` | Formats `TriageResult` into a Markdown GitHub comment with suggested labels, reasoning, and (if applicable) duplicate links or missing information checklist. |

### Integration with Existing Code

| Existing Component | How AI Triage Uses It |
|--------------------|----------------------|
| `IssueLabelReconciler` | Runs independently on the same `@Issue.Opened` event. No modification needed — CDI dispatches to both observers. |
| `LabelConfig` | Injected into `IssueTriageService` to get valid label names for the LLM prompt. |
| `PlanService.runWithRequestContext()` | Pattern reused for virtual thread + CDI request context activation. |
| `IssueLabelCommandHandler` | Unchanged. Human `/triage`, `/kind`, `/priority` commands remain the override mechanism. |
| `GHEventPayload.Issue` | From quarkiverse-githubapp — provides `GHIssue` and `GHRepository` for GitHub API access. |

### Configuration

```properties
# AI Triage
tsd-agent.triage.ai.enabled=false                # toggle AI triage on/off
tsd-agent.triage.auto-apply-threshold=0.8         # confidence threshold for auto-applying labels
tsd-agent.triage.recent-issues-count=50            # number of recent issues for duplicate detection

# LangChain4j Anthropic
quarkus.langchain4j.anthropic.chat-model.model-name=claude-sonnet-4-20250514
quarkus.langchain4j.anthropic.chat-model.temperature=0.1
quarkus.langchain4j.anthropic.chat-model.max-tokens=1024
```

---

## Confidence and Human Override

The AI produces a confidence score (0.0–1.0) for each classification axis:

| Axis | Auto-apply behavior |
|------|-------------------|
| Triage (`triage/*`) | Apply if confidence >= threshold |
| Kind (`kind/*`) | Apply if confidence >= threshold AND triage is `accepted` |
| Priority (`priority/*`) | Apply if confidence >= threshold AND triage is `accepted` |

Regardless of confidence, the AI **always posts a comment** with:
- Suggested labels and confidence percentages
- Reasoning for each decision
- Duplicate candidate link (if applicable)
- Missing information checklist (if applicable)
- A note that maintainers can override with `/triage`, `/kind`, `/priority` commands

If a maintainer overrides an AI-applied label (e.g., changes `kind/bug` to `kind/feature` via `/kind feature`), the AI does not re-run or contest the override.

---

## Prior Art

| Tool | Key Pattern Reused |
|------|--------------------|
| [Dosu](https://dosu.dev) | Configurable automation levels (labeling-only, deduplication, full auto). 66% of Apache Superset issues auto-handled. |
| [Coder Labeler](https://github.com/coder/labeler) | Stateless completion API with recent issues as context — no vector DB. Simple and effective for MVP. |
| [trIAge](https://github.com/trIAgelab/trIAge) | Multi-step analysis: quality control, categorization, duplicate detection, priority — similar to our stage-by-stage approach. |
| [Prow](https://docs.prow.k8s.io) | Command-driven `/triage accepted` flow — already implemented in our `IssueLabelCommandHandler`. |
| [GitHub Agentic Workflows](https://github.github.com/gh-aw/) | Goal-based automation defined in Markdown. AI analyzes content, researches codebase, responds with comment, applies labels. |

---

## Future Enhancements

These are out of scope for the initial implementation but align with the [Future Considerations](issue-triage-flow.md#future-considerations) section of the flow document:

| Enhancement | Flow Document Reference |
|-------------|------------------------|
| `lifecycle/stale` + `lifecycle/rotten` auto-labeling | Future Considerations table |
| `area/*` label classification | Future Considerations table |
| `good-first-issue` / `help-wanted` suggestion | Future Considerations table |
| Embedding-based duplicate detection (pgvector) | Scales beyond the 50-issue context window |
| Workspace-based reproducibility check | Stage 2: `triage/not-reproducible` — AI could attempt to reproduce bugs using source code access |
| Feedback loop from human overrides | Track when maintainers change AI labels to improve prompts over time |
