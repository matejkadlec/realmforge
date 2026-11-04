# 0070. Batches may share generated files

Date: 2026-11-04

Status: Accepted (amends [0016](0016-jira-batch-delivery.md)'s generated-file rule)

## Context

[0016](0016-jira-batch-delivery.md) capped parallel batches so that "two in-flight batches never regenerate the same
generated file". The generated files (`mod/src/generated/`, `qa-mod/src/generated/`, `tools/schemas/`,
`tools/id_universe/`, `pack/index.toml`, `uv.lock`) are written by our tools from the sources, so the rule kept two
branches from conflicting in files nobody edits. In practice nearly every gameplay batch adds player-facing text,
which lands in the one generated `lang/en_us.json`, so the rule let one such batch run at a time, and Matěj kept
approving the overlap case by case (RLM-333 and RLM-557 on 2026-11-04, for one). The scheduler's planner (RLM-587)
already allows it.

Matěj decided on 2026-11-04 (RLM-623): two batches may run at the same time even when both regenerate the same
generated file. Whichever merges second rebases and reruns `uv run rf gen`; that conflict is solved in minutes. He
doesn't want to approve it case by case any more.

## Decision

1. **A shared generated file is no overlap.** Intake, the slot race and the run boundaries in
   [docs/ai-development-flow.md](../ai-development-flow.md) no longer refuse, defer or end on it, and a batch that
   finds during implementation that it regenerates another batch's file goes on.
2. **The claim still lists the generated files** a batch will regenerate (`Generated:`), so the batch that merges
   second knows what to regenerate.
3. **The second merge regenerates.** `finalize-pr` brings `master` into its pull request and reruns the tooling
   (`uv run rf gen`, `uv lock`, `packwiz refresh`, `rf ids`) whenever `master` changed a generated file the pull
   request also changes, with or without a textual conflict, and commits the result before it merges. A generated
   file is never hand-merged.
4. **Sources stay ordinary files.** The YAML, generators and code that produce a generated file are covered by the
   conflict map like any other file; only the generated output is exempt.

## Consequences

- Several batches with new player-facing content can run at once; 0016's consequence "the generated-file rule lets
  only one batch with new player-facing content run at a time" no longer holds.
- The second batch to merge pays a rebase or merge of `master`, a rerun of `uv run rf gen` and a fresh CI run, and
  usually a new review pass (its own diff changed).
- The required checks are not strict, so a clean textual merge could leave a generated file out of date on `master`;
  `finalize-pr`'s merge check against `master`'s generated files catches that before the merge, and `rf gen --check`
  on `master`'s CI after it.
