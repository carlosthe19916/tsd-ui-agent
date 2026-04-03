# PR Lifecycle — Stage 6: AI-Powered Implementation

**Date:** 2026-04-03
**Status:** Proposed
**Prerequisite:** [Issue Triage Flow](issue-triage-flow.md), [AI Issue Triage](ai-issue-triage.md)

---

## Overview

This document describes how an issue that has reached "Ready to Work" (Stage 5) is automatically implemented by an AI coding agent, producing a pull request. It builds on the existing plan/execute/change-request pipeline already in the application and defines how it integrates with the label-driven triage flow.

### Design Principles

- **Label-driven trigger** — the pipeline starts when an issue reaches "Ready to Work" (has `triage/accepted` + `kind/*` + `priority/*`, no `needs-*` labels remaining).
- **Reuse existing infrastructure** — workspaces, coding agent abstraction (`CodingAgentService`), and change request providers (`GitHubChangeRequestProvider`) are already built.
- **Agent-agnostic** — Claude Code, OpenCode, or any future agent can be used via the existing `CodingAgentService` interface.
- **Human approval required** — AI opens a PR but never merges. Maintainers review, request changes, and merge.
- **Feedback loop** — when a reviewer comments on the PR, the agent can iterate.

---

## Trigger: When Does Implementation Start?

### Option A: Automatic (label-based)

When `IssueLabelReconciler` removes the last `needs-*` label, the issue is "Ready to Work". A new listener observes `@Issue.Unlabeled` events and checks:

1. No `needs-triage`, `needs-kind`, or `needs-priority` labels remain
2. Issue has `triage/accepted` + a `kind/*` + a `priority/*`
3. Issue is not already being implemented (no workspace linked yet)

If all conditions met, the implementation pipeline starts automatically.

### Option B: Manual (slash command)

A maintainer posts `/implement` on the issue to explicitly trigger the pipeline. This gives human control over which accepted issues get AI implementation.

### Recommendation

**Option B for initial implementation**, with Option A as a future enhancement. Auto-implementation of every accepted issue is aggressive — maintainers should choose which issues to delegate to AI. The `/implement` command integrates naturally with the existing slash command framework (`IssueLabelCommandHandler`).

---

## Pipeline

The existing 4-phase pipeline in `PlanService` + `ChangeRequestService` already handles the core flow. Stage 6 wraps it with GitHub issue integration:

```
Issue reaches "Ready to Work"
       │
       ▼ maintainer posts /implement
       │
┌──────────────────────────────────┐
│ 1. Provision Workspace           │
│    - Clone repo into worktree    │
│    - Install coding agent config │
│    - Create feature branch       │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ 2. Requirement Enrichment        │
│    - Issue title + description   │
│    - Issue comments              │
│    - Issue labels (kind, etc.)   │
│    → Build rich requirement text │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ 3. Plan Generation               │
│    - Coding agent analyzes code  │
│    - Produces implementation plan│
│    - Plan posted as issue comment│
│      for human review (optional) │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ 4. Plan Execution                │
│    - Coding agent implements     │
│    - Code changes in worktree    │
│    - Tests run inside workspace  │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ 5. Change Request                │
│    - git add, commit, push       │
│    - Open PR via GitHub API      │
│    - PR references the issue     │
│    - Post PR link on the issue   │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ 6. Review Feedback Loop          │
│    - Reviewer comments on PR     │
│    - Agent re-executes with      │
│      feedback as new requirement │
│    - Push updated commits        │
│    - Repeat until approved       │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ 7. Merge & Close                 │
│    - Human merges PR             │
│    - Issue auto-closes via       │
│      "Closes #N" in PR body      │
│    - Workspace destroyed         │
└──────────────────────────────────┘
```

---

## Reuse of Existing Components

| Existing Component | How Stage 6 Uses It |
|--------------------|---------------------|
| `WorkspaceService` | Provisions a workspace (filesystem worktree, Docker devcontainer, or K8s pod) for the target repo |
| `CodingAgentService` | `generatePlan()` and `executePlan()` — agent-agnostic interface (Claude Code or OpenCode) |
| `PlanService` | `doFullPipeline()` — orchestrates requirement → plan → execution → change request |
| `ChangeRequestService` | `doChangeRequest()` — git add/commit/push, create PR via GitHub API |
| `GitHubChangeRequestProvider` | Creates the PR via `POST /repos/{owner}/{repo}/pulls` |
| `ExecutionOutputBroadcaster` | Streams real-time output to the UI via SSE |
| `LabelConfig` | Validates issue labels to confirm "Ready to Work" state |
| `IssueLabelCommandHandler` | Framework for the `/implement` slash command |

### What Needs to Be Built

| Component | Purpose |
|-----------|---------|
| `/implement` command handler | Slash command that triggers the pipeline from a GitHub issue comment |
| `IssueImplementationService` | Orchestrator: creates workspace + task + plan entities, links them, triggers `PlanService.triggerFullPipeline()` |
| Requirement builder | Enriches the requirement with issue comments, labels, and linked issues (beyond just title + description) |
| PR body formatter | Generates PR body that references the issue (`Closes #N`), includes the plan summary, and links to the AI triage analysis |
| PR feedback listener | Observes `issue_comment.created` on PRs, feeds reviewer comments back to the agent for iteration |

---

## Requirement Enrichment

The current `PlanService.doRequirementEnrichment()` simply concatenates `task.title + task.description`. For Stage 6, the requirement should include:

```
## Issue
Title: {issue.title}
Labels: triage/accepted, kind/bug, priority/important-soon

## Description
{issue.body}

## Discussion
{issue comments, excluding bot comments}

## AI Triage Analysis
{AI triage comment reasoning}
{AI classification comment reasoning}
```

This gives the coding agent full context about what was discussed, what information was provided during triage, and what kind of issue it is.

---

## Feature Branch Naming

```
ai/{issue-number}-{slugified-title}
```

Example: `ai/169-app-crashes-on-submit`

This convention:
- Makes it clear the branch was created by AI
- Links back to the issue number
- Is human-readable in PR lists

---

## PR Body Template

```markdown
## Summary

AI-generated implementation for #{issueNumber}.

## Changes

{plan summary from Phase 2}

## Issue Context

- **Kind:** `kind/{kind}`
- **Priority:** `priority/{priority}`
- **Triage reasoning:** {from AI triage comment}

## Testing

{testing approach from generated plan}

---

Closes #{issueNumber}

*Generated by AI coding agent. Human review required before merge.*
```

The `Closes #N` keyword ensures the issue auto-closes when the PR is merged.

---

## Review Feedback Loop

When a reviewer posts a comment on the AI-generated PR:

1. **Observe** `issue_comment.created` on PRs (GitHub treats PR comments as issue comments)
2. **Filter**: only react to comments from maintainers (not the bot itself)
3. **Feed back**: append reviewer feedback to the requirement and re-run `executePlan()` in the same workspace
4. **Push**: commit and push the updated changes to the same branch — the PR updates automatically
5. **Notify**: post a comment on the PR acknowledging the feedback and listing what was changed

This mirrors the approach used by GitHub Copilot Workspace and Devin, where the feedback loop is automated.

### Scope Limitation

For the initial implementation, the feedback loop can be manual — a maintainer posts `/implement-feedback` on the PR to trigger re-execution. Fully automated feedback handling is a follow-up.

---

## Coding Agent Selection

The existing `CodingAgentService` abstraction supports multiple agents:

| Agent | Config Value | Best For |
|-------|-------------|----------|
| Claude Code | `tsd-agent.coding-agent=CLAUDE` | Complex multi-file changes, architecture decisions |
| OpenCode | `tsd-agent.coding-agent=OPENCODE` | Alternative agent with Vertex AI support |

The agent is selected globally via `tsd-agent.coding-agent` config. A future enhancement could allow per-issue agent selection (e.g., simple issues use a faster/cheaper agent).

---

## Workspace Lifecycle

| Event | Action |
|-------|--------|
| `/implement` command | Provision workspace (clone + worktree + agent config) |
| Pipeline completes | Workspace stays alive for feedback iterations |
| PR merged | Destroy workspace (cleanup worktree/container) |
| PR closed without merge | Destroy workspace |
| Pipeline fails | Workspace stays alive for debugging, with error posted on issue |

---

## Prior Art

| Tool | Key Pattern | Relevance |
|------|-------------|-----------|
| [GitHub Copilot Workspace](https://github.blog/ai-and-ml/github-copilot/from-idea-to-pr-a-guide-to-github-copilots-agentic-workflows/) | Issue assignment → sandbox → plan (editable) → implement → draft PR → feedback loop | Closest model to our pipeline. They also separate plan from execution. |
| [Sweep AI](https://github.com/sweepai/sweep) | Label-based trigger (`sweep` label) → sandbox → implement → PR | Simple trigger model. Label-based is easy to adopt. |
| [SWE-agent](https://github.com/SWE-agent/SWE-agent) | CLI-driven → Docker sandbox → iterative test-and-fix → PR | Test-driven iteration loop is valuable — agent runs tests and self-corrects. |
| [Devin](https://devin.ai/) | Assignment/webhook → proprietary sandbox → implement → PR → **Autofix** (auto-handles CI failures and reviewer comments) | Feedback loop automation is the key differentiator. |
| [OpenHands](https://openhands.dev/) | Label-based (`fix-me`) → Docker/K8s → implement → PR with debugging insights | Open-source, flexible sandbox, posts actionable debugging info on failure. |

### Common Patterns Across All Tools

1. **Sandboxed execution** — all tools use ephemeral, isolated environments (Docker, GHA, K8s). We already have this via `WorkspaceManager`.
2. **Never auto-merge** — AI opens a PR but humans always approve merge. Branch protection enforces this.
3. **Test suites as validation** — agents that run tests and self-correct produce better results. Our pipeline should ensure tests pass before creating the PR.
4. **Direct PR to main repo** — all major tools push branches to the main repo (not forks). Forks add complexity without benefit when branch protection is in place.
5. **Feedback loops are the differentiator** — Copilot and Devin closing the reviewer feedback loop was the major advance in 2025-2026.

---

## Future Enhancements

| Enhancement | Description |
|-------------|-------------|
| Auto-trigger on "Ready to Work" | Option A from trigger section — automatically implement when all labels are present |
| Automated feedback loop | Auto-handle PR reviewer comments without `/implement-feedback` command |
| CI failure auto-fix | When CI fails on the PR, re-run the agent with the failure output |
| Per-issue agent selection | Choose Claude Code vs OpenCode based on issue complexity or kind |
| Cost tracking | Track LLM token usage per issue for budget management |
| Implementation quality metrics | Track PR acceptance rate, iteration count, time-to-merge |
