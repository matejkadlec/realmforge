---
name: flow1
description: Autonomously select and deliver a coherent batch of 1-N tickets from the active RLM Jira sprint (Next first, then To Do), pulling unfinished dependencies into the batch and keeping Next filled by priority. Claims the tickets, implements the batch in its own git worktree and validates it. An AI-only batch is published as one ready pull request and carried to its merge with monitor-pr; a gameplay batch is pushed without a pull request and handed to Matěj for a playtest, which he answers with qa1 or qa2. Never implements decision tickets or tickets waiting on Matěj; lists them instead. Takes an optional run location: "flow1 cloud" (the default) selects only work a cloud session can do and stops when none is left; "flow1 local" selects work only a local session can do first, then goes by normal priority. Use when the user says "flow1", "flow1 cloud" or "flow1 local", or asks the agent to pick up and deliver sprint work.
---

# flow1: autonomous batch delivery

Follow `CLAUDE.md` and read [`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md) completely before
selecting work; this skill may not weaken either. Each step names the section of that document that holds its
rules.

`flow1` is one autonomous goal. Do not treat it as a request for a single ticket, and do not stop at a report
before an AI-only batch's ready pull request or a playtest batch's handoff exists, unless the run reaches a
boundary in _Concurrency and run boundaries_ first: no startable batch (everything left waits on Matěj, conflicts
with in-flight work or exceeds a cap, or a `cloud` run finds only local-only work), or a stop for Matěj.

**Run location** (_Run location_): the argument is `cloud` or `local`; `flow1` without one is `flow1 cloud`, and a
cloud session (`CLAUDE_CODE_REMOTE=true`) always runs as `cloud`. `cloud` selects only cloud-capable tickets and,
when Next and To Do hold none that can start, ends without a claim and lists the local-only tickets left. `local`
selects startable local-only tickets first, from Next and To Do alike, and falls back to normal priority when there
are none. A `cloud` run never promotes a local-only ticket to keep Next filled, and when it finds a local-only
step after its claim, it either splits that part and its "Done when" criteria into its own ticket, which alone
gets `Location: local-only`, or releases the batch with `Location: local-only` on every ticket (_Run location_).
Name the mode in the batch announcement.

1. **Intake:** first run _Reconciling merged tickets_; a ticket that cannot be matched never blocks the run. Then
   follow _Intake and conflict control_: inspect the active sprint (Next before To Do), the in-flight tickets,
   open pull requests, branches, worktrees and the main checkout; read every candidate, comments included, with
   the candidate field set from _Reading a ticket_; set aside every ticket under _Tickets waiting on Matěj_; skip
   work that overlaps in-flight ownership or regenerates a generated file an in-flight batch regenerates; and
   check each batch's _Conflict domains_ against the caps in _Concurrency and run boundaries_ before selecting.
2. **Dependencies:** resolve every candidate's unfinished dependencies under _Dependencies_: pull each one from
   To Do, Next or the backlog into the batch, defer a ticket whose dependency is in flight elsewhere, and list one
   that waits on Matěj.
3. **Classify** each candidate AI-only or Playtest under _QA classification_, and local-only or cloud-capable under
   _Run location_. A batch has one QA mode; a pulled-in dependency follows _Dependencies_.
4. **Select and announce** one coherent batch from Next under _Priority and the Next queue_, _Run location_ and
   _Batch selection_, which define the only promotions from To Do and how far to fill an AI-only batch. Priority is a
   recommendation. A playtest batch also needs a free playtest slot; while none is free, select AI-only work. The
   announcement names the tickets, the pulled-in dependencies and why the batch has this size.
5. **Claim** every newly selected ticket through the _Atomic intake claim_ (its `Location` line included) before
   writing any implementation. The batch's one branch and worktree are created and pushed at the first claim that
   holds (_Branches and worktrees_) and reused for every later ticket of the batch.
6. **Implement** in that worktree, following `CLAUDE.md`'s rules. Changes keep `docs/architecture.md`,
   `CLAUDE.md`, the READMEs and the ADRs current in the same batch.
7. **Validate** under _Implementation and verification_: content regenerated from YAML, ruff, pytest,
   `./gradlew build` and the GameTests green, every Gradle task through the _Gradle queue_. Report only the
   commands that actually ran.
8. **Replenish** Next and reassess active-sprint priority before handing off.
9. **AI-only batch: publish** under _Publication_: push, open one ready pull request (a change to
   `docs/roadmap.md` only as Matěj decided it), verify that the remote head is your final commit, record the
   `🤖 Claude QA` summary on each ticket, move the tickets to Pending CR, and hand off with the status overview from
   `CLAUDE.md`. The roadmap holds no status, so a finished task changes only its ticket.
10. **AI-only batch: monitor to the merge, always.** Do not end the turn at the handoff: in the same run, local or
    cloud, run [`monitor-pr`](../monitor-pr/SKILL.md) on that pull request until it merges or reaches one of its
    stops (_After publication_). A batch is not delivered until its pull request has merged.
11. **Playtest batch: hand off and stop** under _Playtest handoff_: push the branch and open **no pull request**,
    record the `🎮 Playtest handoff` on each ticket, move the tickets to Pending User QA, keep the branch and the
    worktree, and give the status overview in its manual-test shape (_What to test_, the _Launch commands_, the
    verification and the batch ledger), closing with how Matěj answers. Stop there: his
    [`qa1`](../qa1/SKILL.md) publishes the batch and carries it to its merge, and his [`qa2`](../qa2/SKILL.md)
    revises it.

The run ends at a boundary in _Concurrency and run boundaries_. Every response of the run, the last one included,
ends with the status overview from `CLAUDE.md`, its batch ledger filled as that section describes (tickets waiting
on Matěj under "Blocked or other"). In a cloud session (`CLAUDE_CODE_REMOTE=true`), _Cloud sessions_ adds what
differs there.
