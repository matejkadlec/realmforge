# Architecture decision records

This directory records Realmforge's significant decisions as short ADRs (Michael Nygard style); see [0001](0001-record-architecture-decisions.md) for the rules. In short:

- One decision per file: `NNNN-kebab-case-title.md` with Date, Status, Context, Decision, Consequences.
- New decisions get the next number and a row in the table below.
- Accepted ADRs are not rewritten. A changed decision gets a new ADR, and the old one is marked `Superseded by NNNN`, never deleted.
- Upgrading any pinned version (Minecraft, NeoForge, ModDevGradle, Gradle, Java, Parchment) requires a new ADR.

Background: [vision](../vision.md) (the owner's goals) and [research notes](../research-notes.md) (reasoning, comparisons, sources). How the decisions fit together in code: [architecture](../architecture.md); the plan: [roadmap](../roadmap.md).

| # | Title | Status |
| --- | --- | --- |
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-hybrid-core-mod-and-modpack.md) | Hybrid: own core mod plus a modpack | Accepted |
| [0003](0003-platform-minecraft-1-21-1-neoforge.md) | Platform: Minecraft 1.21.1 on NeoForge | Accepted |
| [0004](0004-build-rpg-systems-in-house.md) | Build all RPG systems in-house | Accepted |
| [0005](0005-content-pipeline-yaml-single-source-of-truth.md) | Content pipeline: YAML as the single source of truth | Accepted, amended by [0014](0014-core-mod-architecture.md) |
| [0006](0006-quests-ftb-quests-generated-from-data.md) | Quests: FTB Quests generated from data | Accepted |
| [0007](0007-asset-pipeline.md) | Asset pipeline | Accepted |
| [0008](0008-distribution-github-and-curseforge.md) | Distribution: GitHub Releases now, CurseForge later | Accepted |
| [0009](0009-licensing.md) | Licensing | Accepted |
| [0010](0010-testing-strategy.md) | Testing strategy | Accepted |
| [0011](0011-repository-layout-and-workflow.md) | Repository layout and workflow | Accepted |
| [0012](0012-procedural-dungeons-and-dimensions.md) | Procedural dungeons and dimensions | Accepted |
| [0013](0013-modpack-composition.md) | Modpack composition | Accepted (written separately) |
| [0014](0014-core-mod-architecture.md) | Core mod architecture | Accepted |
| [0015](0015-merging-through-monitor-pr.md) | Merging through monitor-pr | Accepted (amends 0011) |
| [0016](0016-jira-batch-delivery.md) | Jira batch delivery with flow1 and flow2 | Accepted (extends 0011) |

## Open questions (not decided)

- Nether and End: overhaul or remove.
- Seasons: explicitly not for now.
