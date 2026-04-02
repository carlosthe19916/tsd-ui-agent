# ADR-0001: Fullsend Gap Analysis

**Status:** Proposed
**Date:** 2026-03-31
**Context:** Gap analysis between the [fullsend](https://github.com/fullsend) design document and the current TSD-UI-Agent implementation.

---

## Summary

**Fullsend** is a living design document exploring fully autonomous agentic software development for GitHub-hosted organizations. It defines how agents should triage issues, implement solutions, review code, and merge to production autonomously — with humans participating only at strategic intent and guarded paths (CODEOWNERS).

**TSD-UI-Agent** is a working platform that orchestrates AI-assisted code changes via a multi-phase pipeline (requirement enrichment, plan generation, plan execution, change request creation).

This document maps fullsend's requirements against tsd-ui-agent's current capabilities and proposes prioritized work sections.

---

## 1. Fullsend Requirements Overview

### 1.1 Core Workflow — Three Agent Roles

| Role | Responsibilities |
|------|-----------------|
| **Triage Agent** | Duplicate detection, info sufficiency check, reproducibility testing, label assignment |
| **Implementation Agent** | Branch, implement, test iteratively, handle CI failures, self-review, open/update PR |
| **Review Agents** (N parallel) | Decomposed by concern: correctness, intent alignment, security, injection defense, style; zero-trust; coordinator aggregates verdicts |

### 1.2 Label-Driven State Machine

Labels drive the workflow: `duplicate` → `not-ready` → `not-reproducible` → `ready-to-implement` → `ready-for-review` → `ready-for-merge` / `requires-manual-review`.

Slash commands (`/triage`, `/implement`, `/review`) trigger transitions and reset downstream labels.

### 1.3 Architecture Components (13)

1. Agent Infrastructure — where agents run
2. Agent Sandbox — isolation boundary
3. Agent Harness — config, skills, codebase context
4. Agent Runtime — LLM + tool loop
5. Agent Identity Provider — per-agent credentials
6. Work Coordinator — triggers to tasks, conflict prevention
7. Policy Store — autonomy levels, review rules, escalation
8. Intent Source — authorized intent for changes
9. Observability — logging, tracing, audit
10. Agent Registry — catalog of agent roles/configs
11. Webhook + dispatch service — GitHub event normalization
12. Label state machine guard — valid label transitions
13. Coordinator merge algorithm — aggregate review verdicts

### 1.4 Security & Governance

- Prompt injection defense (highest threat priority)
- Zero-trust between agents
- CODEOWNERS always human-owned; agents cannot modify guardrails
- Autonomy earned via graduation criteria (test coverage, CI maturity, history)
- Shadow/probationary mode before full autonomy

### 1.5 Supporting Capabilities

- **Repo readiness assessment** — `agentready` diagnostic tooling
- **Org-level configuration** — `<org>/.fullsend` repo with inheritance (defaults < org < per-repo)
- **Intent representation** — tiered (Tier 0: standing rules through Tier 3: architectural changes)
- **Code review decomposition** — sub-agents for correctness, intent alignment, security, injection defense, style
- **Production feedback loop** — signal monitoring, andon cord, auto-close or escalate
- **Agent testing** — golden-set evaluation, behavioral contract testing, drift detection
- **Codebase context** — layered model (code → CLAUDE.md → architecture docs → external refs)
- **Architectural invariants** — per-PR enforcement, periodic drift detection

---

## 2. Coverage Checkpoint

### 2.1 Already Covered

| # | Fullsend Requirement | Coverage | Notes |
|---|---|---|---|
| 1 | Issue import from external trackers | **Full** | GitHub + Jira sync with query support |
| 2 | Task/issue management | **Full** | CRUD, filtering, pagination, sorting |
| 3 | Implementation agent (plan + execute) | **Full** | Plan generation + execution via Claude CLI / OpenCode |
| 4 | PR/MR creation (change request) | **Full** | GitHub PR + GitLab MR |
| 5 | Multi-execution environments | **Full** | Filesystem, Docker devcontainer, Kubernetes |
| 6 | Pluggable coding agent | **Full** | CodingAgent interface with Claude + OpenCode |
| 7 | Real-time output streaming | **Full** | SSE for plan execution, provisioning |
| 8 | Credential management | **Full** | Token-based, encrypted |
| 9 | Git operations | **Full** | Clone, branch, commit, push, diff, worktree |
| 10 | Fork-based workflows | **Full** | Fork URL support for PRs |
| 11 | Agent harness/context | **Partial** | CLAUDE.md supported; no BOOKMARKS.md or layered context |
| 12 | Work coordination | **Partial** | Tasks assigned to workspaces; no conflict prevention |
| 13 | Multi-step pipeline | **Partial** | 4 phases (requirement → plan → execute → CR); no triage or review |

### 2.2 Not Covered

| # | Fullsend Requirement | Gap | Description |
|---|---|---|---|
| 14 | Triage Agent | **Major** | No duplicate detection, info sufficiency, reproducibility testing |
| 15 | Review Agents (sub-agent swarm) | **Major** | No automated code review or decomposed sub-agents |
| 16 | Label-driven state machine | **Major** | No label management or state transitions |
| 17 | Webhook/event dispatch | **Major** | No GitHub webhook ingestion or event-driven triggers |
| 18 | Prompt injection defense | **Major** | No input sanitization or multi-agent verification |
| 19 | Zero-trust between agents | **Major** | Agents are trusted; no cross-verification |
| 20 | Autonomy/governance model | **Major** | No autonomy levels, graduation criteria, or policy store |
| 21 | CODEOWNERS enforcement | **Major** | No CODEOWNERS parsing or enforcement |
| 22 | Review verdict aggregation | **Major** | No merge coordinator algorithm |
| 23 | Repo readiness assessment | **Medium** | No readiness checks (tests, CI, CLAUDE.md) |
| 24 | Org-level configuration | **Medium** | No `.fullsend` repo convention or config inheritance |
| 25 | Intent representation (tiered) | **Medium** | No tiered intent or architectural invariant enforcement |
| 26 | Production feedback loop | **Medium** | No signal monitoring, andon cord, or auto-close |
| 27 | Agent testing/evaluation | **Medium** | No golden-set testing or capability drift detection |
| 28 | Observability/audit trail | **Medium** | SSE output exists but no structured logging or audit |
| 29 | Agent registry | **Low** | Two agents hardcoded; no dynamic registry |
| 30 | Agent sandbox/isolation | **Low** | Devcontainers provide some isolation; no formal model |
| 31 | Agent identity provider | **Low** | Shared credentials; no per-agent identity |
| 32 | Shadow/probationary mode | **Low** | No shadow mode for new repos |
| 33 | Iterative CI failure handling | **Low** | Plan execution exists but no automated CI retry loop |
| 34 | Human factors tooling | **Low** | No domain expertise tracking or review fatigue management |

---

## 3. Work Sections (Priority Order)

### Section A — Extend the Pipeline (Triage + Review)

> Builds directly on existing pipeline infrastructure. Highest impact.

#### A.1 — Triage Phase

Add a triage phase before the current pipeline. The triage agent would:

- Receive a synced issue (title, body, attachments)
- Check for duplicates against existing tasks
- Assess information sufficiency (does the issue have enough detail?)
- Attempt reproducibility testing (if applicable)
- Assign outcome labels: `duplicate`, `not-ready`, `not-reproducible`, or `ready-to-implement`

**Touches:** `PlanService`, `PlanEntity` (new phase fields), triage prompt template, GitHub/Jira label API calls via existing sync clients.

#### A.2 — Review Phase

Add a review phase after plan execution, before change request creation. The review agent would:

- Analyze the generated diff against the original requirement
- Check for correctness, security issues, and style
- Produce a verdict (approve, request changes, escalate)
- Optionally block CR creation if review fails

**Touches:** `PlanService`, `PlanEntity` (new phase fields), review prompt template, verdict model.

#### A.3 — Label-Driven State Machine

Introduce label management to drive task lifecycle:

- Define label enum and valid transitions
- Enforce that phase triggers reset downstream labels
- Sync labels bidirectionally with GitHub/Jira
- Add slash command support (`/triage`, `/implement`, `/review`)

**Touches:** New `TaskLabel` entity/enum, `TaskEntity` (label relationship), `TaskSyncService` (label sync), transition validation logic.

---

### Section B — Event-Driven Automation

> Enables reactive automation instead of manual sync triggers.

#### B.1 — GitHub Webhook Ingestion

- Create a webhook endpoint to receive GitHub events (issue opened, issue updated, comment created, PR status)
- Parse events and map to task creation/updates
- Trigger triage automatically on new issues
- Trigger re-review on PR pushes

**Touches:** New `WebhookResource`, event parser, integration with `TaskSyncService` and `PlanService`.

---

### Section C — Security & Trust

> Essential for production use with real repositories.

#### C.1 — Prompt Injection Defense

- Sanitize all external input (issue titles, bodies, comments) before passing to AI agents
- Normalize Unicode to prevent steganographic attacks
- Strip or escape known injection patterns
- Log sanitization actions for audit

**Touches:** New `InputSanitizer` utility, integration points in `PlanService` and triage/review prompts.

#### C.2 — Observability & Audit Trail

- Add structured logging for all agent actions (phase start/end, decisions, errors)
- Create an audit table recording: who triggered what, when, with what inputs, what outcome
- Expose audit history via API

**Touches:** New `AuditEntity`, `AuditResource`, logging interceptors across services.

#### C.3 — CODEOWNERS Enforcement

- Parse CODEOWNERS files from target repositories
- When creating change requests, check if modified files fall under human-owned paths
- Flag or block auto-merge for guarded paths
- Surface CODEOWNERS information in the UI

**Touches:** New `CodeownersParser` utility, integration in `ChangeRequestService`.

---

### Section D — Governance & Configuration

> Enables multi-repo, multi-org deployment with proper controls.

#### D.1 — Autonomy Levels per Repository

- Define autonomy levels (e.g., `manual`, `assisted`, `autonomous`)
- Per-repo configuration controlling what agents can do without human approval
- Graduation criteria checks (test coverage, CI maturity, successful review history)

**Touches:** New `RepositoryPolicy` entity, policy evaluation in `PlanService`.

#### D.2 — Org-Level Configuration

- Support a `.fullsend` (or equivalent) configuration repository per organization
- Config inheritance: platform defaults < org config < per-repo overrides
- Store: agent selection, autonomy defaults, guardrails, skill definitions

**Touches:** New `OrgConfigService`, config resolution logic, `GitEntity` extension for config repos.

#### D.3 — Repo Readiness Assessment

- Check target repos for: test coverage, CI configuration, CLAUDE.md presence, CODEOWNERS
- Surface readiness score in UI
- Optionally gate agent work on minimum readiness

**Touches:** New `ReadinessService`, integration with `GitService`, UI readiness indicator.

---

### Section E — Advanced Review & Feedback

> Sophisticated multi-agent review and production monitoring.

#### E.1 — Decomposed Review Sub-Agents

- Split review into specialized sub-agents: correctness, intent alignment, security, injection defense, style
- Run sub-agents in parallel
- Aggregate verdicts via configurable composition (unanimous, weighted, veto-based)

**Touches:** Extends Section A.2 review phase, new sub-agent prompt templates, verdict aggregation logic.

#### E.2 — Zero-Trust Agent Verification

- Agents verify each other's outputs (implementation agent's code reviewed by independent review agents)
- No agent trusts another's claims without verification
- Separate credentials per agent role

**Touches:** Agent identity model, credential scoping, cross-verification protocols.

#### E.3 — Production Feedback Loop

- Ingest deployment signals (error rates, latency, failures)
- Auto-create issues when regressions detected post-merge
- Andon cord: halt agent changes if deployment increases failure rate
- Auto-close issues when signals return to normal

**Touches:** New `FeedbackService`, signal ingestion endpoints, integration with task creation.

#### E.4 — Agent Testing & Evaluation Framework

- Golden-set evaluation: known test cases with expected outcomes
- Behavioral contract testing: abstract specifications agents must satisfy
- Capability drift detection: periodic checks that agent capabilities haven't degraded
- Production monitoring: track agent decision quality over time

**Touches:** New `AgentEvalService`, test case storage, evaluation runner.

---

### Section F — Future Considerations (Lower Priority)

| Item | Description |
|------|-------------|
| Agent Registry | Dynamic catalog of available agent roles and configurations |
| Per-Agent Identity | Issue separate credentials per agent role |
| Shadow/Probationary Mode | Run agents in observation mode before granting autonomy |
| Iterative CI Retry Loop | Automatically retry failed CI with agent-generated fixes |
| Intent Tiering (Tier 0-3) | Formal tiered intent model with different authorization per tier |
| Human Factors Tooling | Track domain expertise, manage review fatigue, preserve motivation |
