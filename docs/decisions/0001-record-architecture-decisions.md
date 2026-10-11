# 0001. Record architecture decisions

Date: 2026-10-11

Status: Accepted

## Context

Realmforge is vibe-coded: AI agents do most of the work across many separate sessions, and each session starts without the memory of the previous ones. The phase 1 research and the owner's approval of its recommendations happened in private, Czech-language documents that are not part of the repository. Without a durable, English record in the repo, future sessions would re-open settled questions or silently drift away from them.

## Decision

- We record architecture decisions as ADRs (Michael Nygard style) in `docs/decisions/`.
- File name: `NNNN-kebab-case-title.md`, numbered sequentially. Content: `# NNNN. Title`, `Date`, `Status`, then Context / Decision / Consequences. Keep them short.
- Every new significant decision gets a new numbered ADR. An accepted ADR's decision is not rewritten.
- When a decision changes, the new ADR states what it supersedes, and the old ADR's status becomes `Superseded by NNNN` (with a link). Superseded ADRs are never deleted.
- Version upgrades of pinned tooling (Minecraft, NeoForge, ModDevGradle, Gradle, Java, Parchment) require a new ADR.
- `docs/decisions/README.md` holds the index; add a row for every new ADR.
- Background reasoning, comparisons and sources stay in [research-notes.md](../research-notes.md); the owner's goals stay in [vision.md](../vision.md). ADRs link to them instead of repeating them.

## Consequences

- Agents read the ADR index before proposing structural changes and can check whether a question is already decided.
- Changing course costs one short document, which keeps changes deliberate and traceable.
- The ADR set has to be kept current; an undocumented decision is treated as not made.
