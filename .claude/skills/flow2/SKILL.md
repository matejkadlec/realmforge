---
name: flow2
description: Turn Matěj's task description into RLM Jira tasks in the active sprint (the existing ticket format - parent milestone epic, labels, a "Done when" section, English), claim them (In Progress) and implement them right away through the flow1 lifecycle, pulling in unfinished dependencies (an AI-only batch ends in a pull request, a gameplay batch in a playtest handoff for qa1 or qa2). Use when the user describes a task and says "flow2".
---

# flow2: capture then deliver

Follow `CLAUDE.md` and read [`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md) completely before
creating or selecting work; this skill may not weaken either.

1. Start with _Reconciling merged tickets_, so a ticket whose pull request already merged reads as Done in the
   duplicate search. Then analyze every requested item and search `RLM` for duplicates, reading each candidate,
   comments included, with the candidate field set from _Reading a ticket_.
2. Create or update tickets in the current active sprint under _Issues_: Task type, the parent milestone epic (or
   the epic that covers work outside the roadmap), labels, priority, a description in one of the existing shapes
   with a "Done when" section, in English, and a `Blocks` link for each dependency. Preserve all explicitly
   requested work; merge only truly trivial duplicates. A comment on an existing ticket may already record the
   scope, correction or rejection that decides between creating, updating and merging. Never update a duplicate
   that is in flight (_In flight_ in the flow document): it belongs to another batch, so create a follow-up
   ticket linked to it for the new part, or tell Matěj that the requested work is already under way there.
3. Set sprint-relative priority and classify each ticket AI-only or Playtest under _QA classification_. A
   requested item that is Matěj's own decision gets the `decision` label and is listed, not implemented
   (_Tickets waiting on Matěj_).
4. Route new or newly selected work through To Do -> Next (the `flow2` exception in _Priority and the Next queue_
   lets it in whatever else Next holds), replenishing Next under the same section. The step 6 claim owns the
   Next -> In Progress move; do not make it here, or the claim's "still in Next" precondition is false on every
   run.
5. Inspect in-flight ownership, conflicts and dependencies before implementing, exactly as `flow1` steps 1 and 2
   do, including the conflict domains, the generated files, the pending-batch slots and the playtest limit. Pull
   unfinished dependencies into the batch under _Dependencies_.
6. Claim every ticket you will implement through the atomic intake claim before writing any implementation, as
   `flow1` step 5 requires. A ticket this run just created is claimed the same way; creating it grants no
   ownership.
7. Implement as much of the safe coherent scope as current precision allows, then follow `flow1` steps 6 to 11
   for validation, the ticket statuses, concurrency and the stop rules: an AI-only batch is published and monitored
   to its merge; a playtest batch is pushed without a pull request and handed to Matěj (the status overview with
   its _Launch commands_) for `qa1` or `qa2`.
8. Leave blocked tickets in the appropriate non-complete state with the exact dependency recorded (a `Blocks`
   link and a comment).
