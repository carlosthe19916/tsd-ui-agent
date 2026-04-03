# Issue Triage Flow

**Date:** 2026-04-02
**Status:** Proposed
**Labels definition:** [`src/main/resources/github-labels.yaml`](../../src/main/resources/github-labels.yaml)

---

## Overview

This document describes the issue lifecycle from creation to resolution, driven by a label-based state machine inspired by CNCF projects (Kubernetes, Prometheus, Envoy). Issues are classified along three independent axes — **triage**, **kind**, and **priority** — each gated by a `needs-*` sentinel label.

---

## Label Axes

### Triage

Determines whether the issue is actionable.

| Label | Description |
|-------|-------------|
| `needs-triage` | Issue has not been reviewed yet |
| `triage/accepted` | Valid issue, ready for classification |
| `triage/needs-information` | Waiting on reporter for more details |
| `triage/not-reproducible` | Cannot reproduce as described |
| `triage/duplicate` | Duplicate of an existing issue |
| `triage/support` | Support question, not a code issue |

### Kind

Categorizes the type of work.

| Label | Description |
|-------|-------------|
| `needs-kind` | Issue has not been categorized yet |
| `kind/bug` | Something is broken |
| `kind/feature` | New capability requested |
| `kind/documentation` | Documentation gap or improvement |

### Priority

Determines urgency and staffing.

| Label | Description |
|-------|-------------|
| `needs-priority` | Issue has not been prioritized yet |
| `priority/critical-urgent` | Top priority — active work required immediately |
| `priority/release-blocker` | Must be resolved before next release |
| `priority/important-soon` | Staff now or very soon |
| `priority/important-longterm` | Important but may span multiple releases |
| `priority/backlog` | Community contributions welcome |
| `priority/awaiting-more-evidence` | Interesting but insufficient signal to act |

---

## Lifecycle Flow

```
┌─────────────┐
│  New Issue   │
│  opened      │
└──────┬───────┘
       │ auto-label: +needs-triage, +needs-kind, +needs-priority
       ▼
┌─────────────────┐
│  Triage Review   │◄──────────────────────────────────┐
└──────┬──────────┘                                    │
       │                                               │
       ├─► triage/duplicate ──────► link & close       │
       ├─► triage/support ────────► redirect & close   │
       ├─► triage/not-reproducible ► ask for steps     │
       ├─► triage/needs-information ► stall ───────────┘
       │                              (re-triage when
       │                               reporter replies)
       ▼
┌─────────────────┐
│  triage/accepted │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  Classify Kind   │  replace needs-kind with kind/*
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  Set Priority    │  replace needs-priority with priority/*
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  Ready to Work   │  has: triage/accepted + kind/* + priority/*
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  PR opened       │  references issue
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  lgtm            │  PR approved
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  Merge & Close   │
└─────────────────┘
```

### State Transitions

1. **Intake** — A new issue is opened. Automation applies `needs-triage`, `needs-kind`, and `needs-priority`.

2. **Triage gate** — A maintainer reviews the issue and replaces `needs-triage` with exactly one `triage/*` label:
   - `triage/accepted` — issue is valid, proceed to classification.
   - `triage/needs-information` — ask reporter for details; issue stalls until they respond, then re-enters triage.
   - `triage/not-reproducible` — could not reproduce; may close if no follow-up.
   - `triage/duplicate` — link to the original issue and close.
   - `triage/support` — redirect to support channel and close.

3. **Classification gate** — For accepted issues, replace `needs-kind` with a `kind/*` label.

4. **Prioritization gate** — Replace `needs-priority` with a `priority/*` label.

5. **Ready to work** — An issue is workable when it carries all three: `triage/accepted`, a `kind/*` label, and a `priority/*` label with at least `backlog` severity.

6. **PR lifecycle** — A contributor opens a PR referencing the issue. On approval, the PR receives `lgtm` and is merged. The issue closes.

### Stale Issue Handling

Issues in `triage/needs-information` or `triage/not-reproducible` that receive no response within a defined window (e.g., 30 days) should be closed with a comment explaining why.

---

## Future Considerations

| Label | Purpose |
|-------|---------|
| `lifecycle/stale` | Auto-applied after 30 days of inactivity |
| `lifecycle/rotten` | Auto-applied after 60 days of inactivity, candidate for closing |
| `good-first-issue` | Suitable for new contributors |
| `help-wanted` | Community contributions actively sought |
| `area/*` | Route to specific teams or components (e.g., `area/api`, `area/ui`) |
| `size/*` | Rough effort estimate (`size/XS` through `size/XXL`) |
