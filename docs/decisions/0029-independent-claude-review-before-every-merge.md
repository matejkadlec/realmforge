# 0029. Independent Claude review before every merge

Date: 2026-10-14

Status: Accepted (supersedes [0015](0015-merging-through-monitor-pr.md); keeps its merge gate and the holds that
[0016](0016-jira-batch-delivery.md), [0017](0017-jira-as-the-source-of-task-status.md),
[0021](0021-status-overview-and-plan-changes.md), [0022](0022-playtest-before-the-pull-request.md) and
[0024](0024-no-github-labels.md) shaped)

## Context

Under [0015](0015-merging-through-monitor-pr.md), `monitor-pr` merged a pull request once ChatGPT Codex was done
with its head: its approval, a completed review whose findings `fix-pr` had closed, or 10 minutes of silence.
The independent Claude review (`finalize-pr` with its read-only `pr-reviewer` subagent) only stood in when Codex
hit its usage limit.

The owner no longer pays for ChatGPT, only for Claude, so Codex reviews nothing. Left as it was, the flow would
wait for a review that never comes and then merge every pull request through the silent window, without any
review. He may buy the lowest ChatGPT tier again for image generation, but not to bring Codex review back.

## Decision

- **`finalize-pr` runs after every pull request** a session publishes or updates as its own task work, without
  exception. Its fresh, read-only `pr-reviewer` subagent is the code review: up to five rounds per pass, each
  with a new subagent; the session verifies every finding, fixes the confirmed P1 and P2 ones, tracks or drops
  P3s with a reason, and pushes once per pass. Each pass leaves a `🤖 Independent review` comment on the pull
  request, the GitHub-side record that Codex's reviews used to be.
- **`finalize-pr` also carries the pull request to its merge**, with everything `monitor-pr` did except waiting
  for Codex: review threads, failing and stuck checks, conflicts with `master`, the Jira tickets, the merge with a
  merge commit, CI on `master` after it, and a linked fix or revert when that CI fails because of the merge.
- **A merge needs a reviewed head:** a Clean pass of that exact head, or the owner's approval of that exact head
  after a Capped or Disagreement pass. Every later head (a conflict resolution, a check repair, a thread fix, a
  `qa2` revision) needs a new pass. Only a head that brought `master` in without changing the pull request's own
  diff (the same `git patch-id`) keeps its review. Resolved threads, green required checks and no hold stay
  required.
- **The holds are unchanged:** a reconfirmation (a ticket in Pending User QA), a changes-requested review, and
  anything the owner asked to hold. A Codex finding outside a thread is no longer a hold, since Codex no longer
  reviews.
- **`monitor-pr` and `fix-pr` are disabled, not deleted:** their `SKILL.md` files are renamed to
  `SKILL.md.disabled`, which Claude Code does not load.
- `--admin`, `--auto` and any other bypass of the ruleset stay forbidden. Nobody writes `@codex` on a pull
  request while the Codex connector may still be installed.

## Consequences

- No pull request merges unreviewed: the silent window is gone.
- Reviews cost Claude usage instead of ChatGPT usage. The five-round cap per pass, the late-rounds churn rule (from
  round 4 on) and the review kept across a clean merge of `master` bound it.
- A Capped or disputed pass waits for the owner's approval of the exact head, where Codex's silent window used to
  merge without asking.
- Bringing Codex back needs a new ADR: rename the two files back and restore the Codex steps in `finalize-pr`,
  `CLAUDE.md` and [the AI development flow](../ai-development-flow.md) (this decision's pull request shows what
  changed). Paying for ChatGPT for images alone changes nothing here.
