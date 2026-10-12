---
name: qa1
description: Record Matěj's passed playtest of a batch in Pending User QA, publish its preserved branch as one ready pull request (no needs-playtest), move its tickets to Pending CR and carry the pull request to its merge with monitor-pr. Also confirms a reconfirmation on a published pull request. Only playtest batches use it. Use when the user says "qa1" or approves a playtest.
---

# qa1: playtest passed, publish

Follow `CLAUDE.md` and read [`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md) completely before
publishing; this skill may not weaken either. _qa1: playtest passed_ there holds the rules.

1. **Find the batch** Matěj passed: the one this session handed off, the ticket keys, branch or pull request he
   names, or the only batch waiting for a playtest (tickets in Pending User QA, or an open pull request with
   `needs-playtest`, one outside the Jira flow included). With several and no hint, ask which one, listing each
   with its tickets, branch or pull request. Nothing waiting for a playtest: say so and stop.
2. **Take the preserved branch:** `git fetch origin`, then reuse the worktree that holds it or create one from
   `origin/<branch>` (_Branches and worktrees_). Then bring the local branch up to `origin/<branch>` before
   anything else: `git merge --ff-only origin/<branch>`, or `git merge origin/<branch>` when both sides have
   commits. Commits Matěj pushed from his own clone belong to the batch and are never pushed over; every later
   push is leased on that fetched head (`--force-with-lease=<branch>:<sha>`). His uncommitted edits follow
   `CLAUDE.md`: fold small ones that belong to the batch into the next commit, and ask about anything larger.
3. **Finish:** run the checks for anything that changed since the played head and commit the coherent batch.
   When `origin/master` moved (for a stacked batch: its dependency branch), rebase onto it, regenerate generated
   files with `uv run rf gen`, and run the full gate (_Implementation and verification_, Gradle through the
   _Gradle queue_). A rebase whose conflicts touched game code changes what Matěj played: push it and hand it back
   for another playtest, as `qa2` does, instead of publishing.
4. **Publish** under _Publication_: push (leased as in step 2), open one ready pull request to `master` (a
   stacked batch: to its dependency branch, until that merges) without `needs-playtest`, its description naming the
   head Matěj played, and read the remote head back: it must be your final commit.
5. **Record and move:** on each ticket, the `🎮 Playtest passed` (the played head, the head it is published as,
   Matěj's words if he gave any)
   and the `🤖 Claude QA` summary (what was verified, the pull request, the head); then move every ticket of the
   batch from Pending User QA to Pending CR. Never leave a passed ticket in Pending User QA.
6. **Hand off and monitor:** the status overview from `CLAUDE.md`, then in the same run, local or cloud,
   [`monitor-pr`](../monitor-pr/SKILL.md) on the pull request until it merges or reaches one of its stops. The
   playtest was Matěj's last required step.

**A pull request that already waits for a playtest** (a reconfirmation, _Reconfirmation_, or a gameplay pull
request outside the flow with `needs-playtest`): `qa1` publishes nothing new. It records the `🎮 Playtest passed`
for the pull request's current head on each of its tickets (one outside the flow may have none: the label's
removal then records the playtest), removes `needs-playtest` for Matěj (his `qa1` is his answer;
`gh api -X DELETE repos/matejkadlec/realmforge/issues/<n>/labels/needs-playtest`), moves the tickets to Pending
CR, and continues `monitor-pr` on it.
