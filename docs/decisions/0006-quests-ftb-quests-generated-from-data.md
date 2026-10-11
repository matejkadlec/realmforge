# 0006. Quests: FTB Quests generated from data

Date: 2026-10-11

Status: Accepted

## Context

The vision wants 500+ quests and questlines, chapter-locked, with repeatable quests. Otherworld showed what goes wrong with hand-edited FTB Quests: wrong IDs, mismatched boss levels, non-retroactive kill tasks. Known FTB Quests SNBT facts: IDs are 16-digit hex; a choice reward references its reward table by a decimal long; files are read on world start or via `/ftbquests reload`. Details: [research notes, section 3](../research-notes.md#quests) and [section 9](../research-notes.md#procedural-quests-no-mans-sky-style).

## Decision

- **v0.1 uses FTB Quests**, with all quest files generated from `content/` by the pipeline ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)).
- **Stable IDs:** quest, chapter and reward-table IDs are derived deterministically from the quest key, so regeneration never breaks saved player progress.
- **No custom FTB task types** via FTB's internal API (they could break on FTB updates).
- **Our mod grants advancements and stats** (e.g. "profession at level 5"); FTB reads them with its standard tasks, which are retroactive.
- Quest texts (boss levels, locations, recipes) are generated from the same data as the game.
- **Procedural "contracts"** (job board, guild ranks, profession orders, "dungeon of the day") come later in our own quest system, because FTB Quests has one shared quest set and cannot generate quests per player at runtime. Repeatable trade quests start in FTB Quests and move to contracts later.

## Consequences

- FTB Quests (with FTB Library and FTB Teams) is ARR and CurseForge-only, which constrains distribution ([0008](0008-distribution-github-and-curseforge.md)).
- The pipeline must implement SNBT writing and ID derivation and test it.
- Heracles (Odyssey Quests, MIT) remains a possible alternative if FTB Quests becomes a problem; its 1.21.1 release only came out on 2026-10-04.
