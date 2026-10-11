# 0015. Merging through monitor-pr

Date: 2026-10-11

Status: Accepted (amends the merge step of [0011](0011-repository-layout-and-workflow.md))

## Context

[0011](0011-repository-layout-and-workflow.md) has the owner play and merge every pull request. Most pull
requests are small agent changes (tooling, docs, pipeline) that the owner does not need to play, so waiting for
a manual merge slows every session down. The `master` ruleset already enforces the gate: pull requests only,
every review thread resolved, and the four CI jobs as required checks. ChatGPT Codex reviews each pull request
when it opens.

## Decision

- The `monitor-pr` skill (`.claude/skills/monitor-pr/SKILL.md`) merges a pull request, with a merge commit, once
  Codex is done with its head (its approval, a completed review whose findings `fix-pr` has all closed, or
  10 minutes of silence after the push), every review thread is resolved, every required check is green, and no
  hold applies.
- Holds that wait for the owner:
  - the `needs-playtest` label, which the publishing session adds when the owner should play the change first
    (gameplay, visuals, balance) and which only the owner removes;
  - a changes-requested review;
  - a Codex finding outside a review thread;
  - anything the owner asked to hold.
- When Codex has hit its usage limit, an independent `finalize-pr` review of the exact head replaces the Codex
  review.
- After the merge, `monitor-pr` follows CI on `master`; a failure caused by the merge gets a linked fix or revert
  pull request.
- `--admin`, `--auto` and any other bypass of the ruleset are never used.

## Consequences

- The owner playtests what matters (labelled pull requests) and everything else merges without waiting.
- A head can merge without its own Codex review when Codex stays silent for 10 minutes (in this repository Codex
  usually reviews new commits, but on PR #1 it reviewed none of the later pushes). The required checks and
  resolved threads remain the gate.
- The owner's "plays and merges" step in [0011](0011-repository-layout-and-workflow.md) now applies only to
  pull requests with the `needs-playtest` label.
