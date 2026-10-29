# 0059. The status overview ends with Verification and the claimed tickets, without a batch ledger

Date: 2026-10-29

Status: Accepted (amends [0021](0021-status-overview-and-plan-changes.md))

## Context

[0021](0021-status-overview-and-plan-changes.md) ends every response to the owner with a status overview whose
batch ledger lists Merged into master, Pending CR, Pending User QA and Blocked or other, often with a Cleanup line
after it. On 2026-10-29 Matěj said the ledger is noise to him (RLM-508). He does not read its groups. "None from
this run" says nothing. Claude Code already shows him the sessions that wait for him. He settles blocked work in
his planning chat. Processes stopped and files removed never matter to him. What he does need is missing: a
session's title carries the keys he typed, while a batch may work on others (an L ticket's parts, a pulled-in
dependency, a split-off part), so the keys the run actually claimed should close the response.

Playtest handoffs also told him, almost every time, to start a new world. He tests in one world that already has a
command block, the training dummy, portals and test gear, and needs a new one only rarely, for example when
Undervault's worldgen must generate again.

## Decision

- **No batch ledger:** no Merged into master, Pending CR, Pending User QA or Blocked or other group, and no Cleanup
  line, in any response or skill.
- **Every response ends with Verification:** what ran and passed in this run, one bullet each, never "all checks
  passed" for a partial run. Its last line names the tickets the run claimed or worked on
  (`Tickets: RLM-490, RLM-491 (parts of RLM-35)`), with a few words when they differ from the keys he typed.
- **A manual test keeps its shape:** What to test, Launch commands and how he answers (`qa1`, `qa2`), then
  Verification.
- **What needs his answer before the run can go on** (a decision, an account, a plan change an agent proposes, a
  conflict only he can settle, a player's GitHub issue) is asked in the response, as a question.
- **What only needs recording** goes into Jira comments on the ticket it concerns: stale claims, reconciled
  tickets, split-off parts, priority changes, uncommitted work left behind. Decision tickets and tickets waiting on
  him need nothing new: their tickets say what they need. Follow-ups that could not be ticketed because Jira was
  unreachable stay in the response, the one place left that can hold them.
- **Playtests start in his existing test world.** A handoff asks for a new world only when the change needs one (a
  worldgen or structure change that shows only in newly generated chunks, a change to world creation itself, or
  saved data an existing world cannot carry), and then says why in one line.
- 0021's other rules stay: the overview ends every response, and plan changes carry no label.

## Consequences

- Responses get shorter, and their last line answers which tickets the session really worked on.
- Matěj no longer sees blocked, decision or stale tickets in every response. He finds them in Jira and in his
  planning, and a run asks him only what it cannot go on without.
- More goes into Jira comments, so a later session reading a ticket finds what an earlier run noticed there,
  instead of in a response it cannot see.
- Playtest handoffs lose the new-world boilerplate. When a change needs a new world, the handoff must say why, so a
  missing reason is visible in review.
