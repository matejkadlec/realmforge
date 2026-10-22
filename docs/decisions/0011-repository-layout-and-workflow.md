# 0011. Repository layout and workflow

Date: 2026-10-11

Status: Accepted (merge step amended by [0015](0015-merging-through-monitor-pr.md), now
[0029](0029-independent-claude-review-before-every-merge.md); the `better-key-binds/` subproject added by
[0048](0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md), 2026-10-22)

## Context

AI agents do most of the work in many small sessions, locally and in the cloud; the owner reviews, playtests and merges. The owner works on Windows with WSL. Details: [research notes, section 7](../research-notes.md#7-repository-and-workflow) and [section 8](../research-notes.md#8-ai-limits).

## Decision

- **One monorepo on GitHub**, default branch `master`:

  ```text
  CLAUDE.md            rules for agents, pinned versions, commands
  content/             YAML: the single source of truth
  tools/               Python: validation, generators, simulation, asset pipeline
  mod/                 NeoForge core mod (ModDevGradle)
  qa-mod/              dev-only QA mod, never released
  pack/                packwiz modpack definition, third-party mod configs
  art/                 prompts, palettes, asset sources
  docs/                vision, research notes, decisions (ADRs)
  .github/workflows/   CI and release
  ```

- **Develop in WSL** on the Linux filesystem; **never build via `/mnt/c`** (the Windows-WSL file bridge is much slower). `runClient` in WSLg is fine for quick checks.
- **Play on Windows** via Prism Launcher with packwiz-installer (pack served by `packwiz serve` in WSL or from GitHub); a Gradle task copies our jar into the instance.
- `.gitattributes` enforces LF line endings.
- **Feature branches and small PRs**, one feature or one batch of content each. **Conventional Commits.**
- **Tests run before every PR** ([0010](0010-testing-strategy.md)); CI runs them again.
- **Agent workflow:** scoped issue, branch and session; a Claude Code hook validates content after each edit in `content/` or `tools/` (fast, no Gradle; compilation and tests run before the PR); review by a second agent, CI, then the owner plays and merges.
- **CLAUDE.md and `docs/` are kept current** with every change that affects them.
- **Agents verify APIs against sources** (decompiled Minecraft sources, NeoForge docs) instead of guessing.
- Everything in the repo is in English; communication with the owner is in English.

## Consequences

- Small PRs keep each session's context manageable and the owner's reviews short.
- Stale CLAUDE.md or docs mislead future sessions, so updating them is part of done.
- Pinned versions plus source verification are the main defense against API hallucinations.
