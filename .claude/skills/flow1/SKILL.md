---
name: flow1
description: Autonomously select and deliver a coherent batch of 1-N tickets from the active RLM Jira sprint (Next first, then To Do), pulling unfinished dependencies into the batch and keeping Next filled by priority. Claims the tickets, implements the batch in its own git worktree, validates it, publishes one ready pull request and carries it to its merge with monitor-pr (a gameplay batch waits there for Matěj's playtest). Never implements decision tickets or tickets waiting on Matěj; lists them instead. Use when the user says "flow1" or asks the agent to pick up and deliver sprint work.
---

# flow1: autonomous batch delivery

Follow `CLAUDE.md` and read [`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md) completely before
selecting work; this skill may not weaken either. Each step names the section of that document that holds its
rules.

`flow1` is one autonomous goal. Do not treat it as a request for a single ticket, and do not stop at a report
before a ready pull request exists, unless the run reaches a boundary in _Concurrency and run boundaries_ first:
no startable batch (everything left waits on Matěj, conflicts with in-flight work or exceeds a cap), or a stop
for Matěj.

1. **Intake:** first run _Reconciling merged tickets_; a ticket that cannot be matched never blocks the run. Then
   follow _Intake and conflict control_: inspect the active sprint (Next before To Do), the in-flight tickets,
   open pull requests, branches, worktrees and the main checkout; read every candidate, comments included, with
   the candidate field set from _Reading a ticket_; set aside every ticket under _Tickets waiting on Matěj_; skip
   work that overlaps in-flight ownership or regenerates a generated file an in-flight batch regenerates; and
   check each batch's _Conflict domains_ against the caps in _Concurrency and run boundaries_ before selecting.
2. **Dependencies:** resolve every candidate's unfinished dependencies under _Dependencies_: pull each one from
   To Do, Next or the backlog into the batch, defer a ticket whose dependency is in flight elsewhere, and list one
   that waits on Matěj.
3. **Classify** each candidate AI-only or Playtest under _QA classification_. A batch has one QA mode; a pulled-in
   dependency follows _Dependencies_.
4. **Select and announce** one coherent batch from Next under _Priority and the Next queue_ and _Batch
   selection_, which define the only promotions from To Do and how far to fill an AI-only batch. Priority is a
   recommendation. A playtest batch also needs a free playtest slot; while none is free, select AI-only work. The
   announcement names the tickets, the pulled-in dependencies and why the batch has this size.
5. **Claim** every newly selected ticket through the _Atomic intake claim_ before writing any implementation.
   The batch's one branch and worktree are created and pushed at the first claim that holds (_Branches and
   worktrees_) and reused for every later ticket of the batch.
6. **Implement** in that worktree, following `CLAUDE.md`'s rules. Changes keep `docs/architecture.md`,
   `CLAUDE.md`, the READMEs and the ADRs current in the same batch.
7. **Validate** under _Implementation and verification_: content regenerated from YAML, ruff, pytest,
   `./gradlew build` and the GameTests green, every Gradle task through the _Gradle queue_. Report only the
   commands that actually ran.
8. **Replenish** Next and reassess active-sprint priority before handing off.
9. **Publish** under _Publication_: push, open one ready pull request (`needs-playtest` for a playtest batch,
   `needs-approval` for any change to `docs/roadmap.md`), verify that the remote head is your final commit, record
   the `🤖 Claude QA` summary (and the `🎮 Playtest handoff`) on each ticket, move the tickets to Pending CR
   (AI-only) or Pending User QA (playtest), and hand off leading with _What to look at_, plus the _Playtest
   commands_ for a playtest batch. The roadmap holds no status, so a finished task changes only its ticket.
10. **Monitor to the merge, always.** Do not end the turn at the handoff: in the same run, local or cloud, run
    [`monitor-pr`](../monitor-pr/SKILL.md) on that pull request until it merges or reaches one of its stops
    (_After publication_). A playtest batch waits there until Matěj removes `needs-playtest`; a failed playtest
    follows _Playtest handoff_. A batch is not delivered until its pull request has merged.

The run ends at a boundary in _Concurrency and run boundaries_ and closes with the batch ledger defined there,
"Waiting for Matěj" included. In a cloud session (`CLAUDE_CODE_REMOTE=true`), _Cloud sessions_ adds what differs
there.
