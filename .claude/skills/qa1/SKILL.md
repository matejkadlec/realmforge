---
name: qa1
description: Record Matěj's passed playtest of a batch in Pending User QA, publish its preserved branch as one ready pull request, move its tickets to Pending CR and carry the pull request through its independent review to its merge with finalize-pr. Also confirms a reconfirmation on a published pull request. Only playtest batches use it. Use when the user says "qa1" or approves a playtest.
---

# qa1: playtest passed, publish

Follow `CLAUDE.md` and read [`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md) completely before
publishing; this skill may not weaken either. _qa1: playtest passed_ there holds the rules.

1. **Find the batch** Matěj passed: the one this session handed off, the ticket keys, branch or pull request he
   names, or the only batch waiting for a playtest (its tickets in Pending User QA, with or without a pull
   request). With several and no hint, ask which one, listing each with its tickets, branch or pull request.
   Nothing waiting for a playtest: say so and stop. When a `qa2` left a
   decision question open on the batch's tickets, ask Matěj to answer it first, or to confirm that his `qa1`
   accepts the batch as it is; publish nothing before that.
2. **Take the preserved branch:** `git fetch origin`, then reuse the worktree that holds it or create one from
   `origin/<branch>` (_Branches and worktrees_). Then bring the local branch up to `origin/<branch>` before
   anything else: `git merge --ff-only origin/<branch>`, or `git merge origin/<branch>` when both sides have
   commits. Commits Matěj pushed from his own clone belong to the batch and are never pushed over; every later
   push is leased on that fetched head (`--force-with-lease=<branch>:<sha>`). His uncommitted edits follow
   `CLAUDE.md`: fold small ones that belong to the batch into the next commit, and ask about anything larger.
   Anything after the handed-off head that changes gameplay (his edits, his pushed commits) was played only if he
   says so in his `qa1`; otherwise it goes back to him for another playtest, as `qa2` does, instead of being
   published.
3. **Finish:** commit the coherent batch. When `origin/master` moved (for a stacked batch: its dependency
   branch), rebase onto it and regenerate generated files with `uv run rf gen`. Whenever the head differs from the
   handed-off head the gate last passed on (his commits or edits, a rebase), run the full gate again
   (_Implementation and verification_, Gradle through the _Gradle queue_). A rebase whose conflict resolutions
   touch anything a player sees (_QA classification_: code, content, assets, pack configuration) changes what
   Matěj played: push it and hand it back for another playtest, as `qa2` does, instead of publishing.
4. **Publish** under _Publication_: push (leased as in step 2), open one ready pull request to `master` (a
   stacked batch: to its dependency branch, until that merges), and read the remote head back: it must be your
   final commit. The description carries the line
   `Playtest passed: played <sha>, published <sha>`, the GitHub-side record `finalize-pr` falls back on when a
   Jira write fails.
5. **Record and move:** on each ticket, the `🎮 Playtest passed` (the played head, the head it is published as,
   Matěj's words if he gave any)
   and the `🤖 Claude QA` summary (what was verified, the pull request, the head); then move every ticket of the
   batch from Pending User QA to Pending CR. Never leave a passed ticket in Pending User QA.
6. **Hand off and monitor:** the status overview from `CLAUDE.md`, then in the same run, local or cloud,
   [`finalize-pr`](../finalize-pr/SKILL.md) on the pull request until it merges or reaches one of its stops. The
   playtest was Matěj's last required step. This session has taken the batch over: its worktree and local branch
   count as created by this session, so `finalize-pr` removes them after the merge.

**A pull request that waits for a reconfirmation** (_Reconfirmation_): `qa1` publishes nothing new. It passes only
the head Matěj played, the one the latest `🎮 Playtest handoff` names: when the pull request moved on since then and
the new commits change gameplay (_QA classification_), it hands those back for another playtest instead. Otherwise
it first rewrites the pull request description's `Playtest passed: played <sha>, published <sha>` line to name that
head (`gh api -X PATCH repos/matejkadlec/realmforge/pulls/<n> -F body=@<file>`), the record that survives a failed
Jira write, then records the `🎮 Playtest passed` for that head on each ticket, moves the tickets to Pending CR, and
the `finalize-pr` run continues to the merge. One `finalize-pr` run owns a pull request: when the answer comes in the
session whose run waits for this playtest, that run carries on. In any other session, ask Matěj first whether that
run has stopped; only then does `qa1` continue `finalize-pr` here, and otherwise it changes nothing and points him to
the waiting session.
