# Architecture decision records

This directory records Realmforge's significant decisions as short ADRs (Michael Nygard style); see [0001](0001-record-architecture-decisions.md) for the rules. In short:

- One decision per file: `NNNN-kebab-case-title.md` with Date, Status, Context, Decision, Consequences.
- New decisions get the next number and a row in the table below.
- Accepted ADRs are not rewritten. A changed decision gets a new ADR, and the old one is marked `Superseded by NNNN`, never deleted.
- ADRs bind agents, not Matěj ([0030](0030-adrs-bind-agents-not-the-owner.md)). Agents follow them strictly in what they decide on their own. When he asks for a change that conflicts with one, the agent names the ADR and its main consequence once, never refuses, and once he confirms, makes the change with its superseding or amending ADR in the same pull request. A ticket he wrote that asks for the change, or a Done decision ticket, is already his confirmation; a ticket an agent wrote is not ([0060](0060-what-counts-as-the-owners-confirmation-of-an-adr-conflicting-change.md)).
- Upgrading any pinned version (Minecraft, NeoForge, ModDevGradle, Gradle, Java) requires a new ADR.

Background: [vision](../vision.md) (the owner's goals) and [research notes](../research-notes.md) (reasoning, comparisons, sources). How the decisions fit together in code: [architecture](../architecture.md); the plan: [roadmap](../roadmap.md).

| # | Title | Status |
| --- | --- | --- |
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted, amended by [0030](0030-adrs-bind-agents-not-the-owner.md) |
| [0002](0002-hybrid-core-mod-and-modpack.md) | Hybrid: own core mod plus a modpack | Accepted |
| [0003](0003-platform-minecraft-1-21-1-neoforge.md) | Platform: Minecraft 1.21.1 on NeoForge | Superseded by [0026](0026-platform-minecraft-26-1-2-neoforge.md) |
| [0004](0004-build-rpg-systems-in-house.md) | Build all RPG systems in-house | Accepted, amended by [0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md) (third-party gameplay mods, Apotheosis) and [0044](0044-third-party-mods-by-milestone.md) (third-party mods by milestone, world dungeons from mods) |
| [0005](0005-content-pipeline-yaml-single-source-of-truth.md) | Content pipeline: YAML as the single source of truth | Accepted, amended by [0014](0014-core-mod-architecture.md) and [0050](0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md) (set pieces) |
| [0006](0006-quests-ftb-quests-generated-from-data.md) | Quests: FTB Quests generated from data | Accepted |
| [0007](0007-asset-pipeline.md) | Asset pipeline | Accepted, refined by [0023](0023-texture-generator.md), vanilla texture rule narrowed to texture files by [0028](0028-dungeon-rooms-generated-or-imported.md) |
| [0008](0008-distribution-github-and-curseforge.md) | Distribution: GitHub Releases now, CurseForge later | Accepted |
| [0009](0009-licensing.md) | Licensing | Accepted, third-party rooms added by [0028](0028-dungeon-rooms-generated-or-imported.md) |
| [0010](0010-testing-strategy.md) | Testing strategy | Accepted, amended by [0018](0018-pack-boot-tests.md) |
| [0011](0011-repository-layout-and-workflow.md) | Repository layout and workflow | Accepted |
| [0012](0012-procedural-dungeons-and-dimensions.md) | Procedural dungeons and dimensions | Accepted, refined by [0019](0019-dungeon-instances-in-plots.md), its Nether and End question resolved by [0025](0025-remove-the-nether-and-end.md), hand-built rooms replaced by [0028](0028-dungeon-rooms-generated-or-imported.md) |
| [0013](0013-modpack-composition.md) | Modpack composition | Accepted (written separately), composition updated for 26.1.2 by [0026](0026-platform-minecraft-26-1-2-neoforge.md), Apotheosis added by [0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md), JEI / REI Trades added by [0040](0040-coins-and-data-driven-merchant-trades.md), Lootr by [0041](0041-dungeon-containers-hold-loot-through-lootr.md), the mods by milestone by [0044](0044-third-party-mods-by-milestone.md), MmmMmmMmmMmm with a beta Moonlight Lib by [0053](0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md) |
| [0014](0014-core-mod-architecture.md) | Core mod architecture | Accepted, decision 9 amended by [0027](0027-client-bound-payload-handlers-through-the-client-event.md), decision 8 (hand-made rooms) by [0028](0028-dungeon-rooms-generated-or-imported.md), decision 6 (armor formula) by [0033](0033-vanilla-armor-formula-amends-our-own.md), decision 8 (set pieces) by [0050](0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md) |
| [0015](0015-merging-through-monitor-pr.md) | Merging through monitor-pr | Superseded by [0029](0029-independent-claude-review-before-every-merge.md); was Accepted (amends 0011), holds extended by [0016](0016-jira-batch-delivery.md) and [0017](0017-jira-as-the-source-of-task-status.md), `needs-approval` removed by [0021](0021-status-overview-and-plan-changes.md), `needs-playtest` removed by [0024](0024-no-github-labels.md) |
| [0016](0016-jira-batch-delivery.md) | Jira batch delivery with flow1 and flow2 | Accepted (extends 0011), amended by [0017](0017-jira-as-the-source-of-task-status.md), [0021](0021-status-overview-and-plan-changes.md) and [0022](0022-playtest-before-the-pull-request.md) |
| [0017](0017-jira-as-the-source-of-task-status.md) | Jira as the source of task status | Accepted (amends 0015 and 0016), amended by [0021](0021-status-overview-and-plan-changes.md) |
| [0018](0018-pack-boot-tests.md) | Pack boot tests: server and client self tests on every PR | Accepted (amends 0010), adjusted for 26.1.2 by [0026](0026-platform-minecraft-26-1-2-neoforge.md) |
| [0019](0019-dungeon-instances-in-plots.md) | Dungeon instances in plots | Accepted (refines 0012), amended by [0031](0031-instance-builder-background-chunks-and-shared-budget.md) |
| [0020](0020-cast-animations-and-melee-while-casting.md) | Cast animations through playerAnimator, no melee while casting | Accepted, amended by [0026](0026-platform-minecraft-26-1-2-neoforge.md) (Player Animation Library) and [0039](0039-a-cast-slows-its-caster-instead-of-moving-interrupting-it.md) (a cast slows instead of moving interrupting it) |
| [0021](0021-status-overview-and-plan-changes.md) | A status overview ends every response; plan changes without a label | Accepted (amends 0015, 0016 and 0017), amended by [0022](0022-playtest-before-the-pull-request.md) and [0059](0059-the-status-overview-ends-with-verification-and-the-claimed-tickets.md) (no batch ledger; Verification and the claimed tickets close every response) |
| [0022](0022-playtest-before-the-pull-request.md) | Playtest before the pull request: qa1 and qa2 | Accepted (amends 0016 and 0021), amended by [0024](0024-no-github-labels.md) |
| [0023](0023-texture-generator.md) | Texture generator: Retro Diffusion Fast, cleaned to the project palette | Accepted (refines 0007) |
| [0024](0024-no-github-labels.md) | No GitHub labels: playtests run on qa1 and qa2 alone | Accepted (amends 0015 and 0022) |
| [0025](0025-remove-the-nether-and-end.md) | Remove the Nether and End; the Overworld is the starting area | Accepted (resolves the open question of 0012), amended by [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md), [0034](0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md) and [0037](0037-elytra-from-chapter-3s-final-boss-amends-the-nether-and-end-removal.md) (elytra source); amended by [0044](0044-third-party-mods-by-milestone.md) (strongholds off from M1, so the End is unreachable from M1) |
| [0026](0026-platform-minecraft-26-1-2-neoforge.md) | Platform: Minecraft 26.1.2 on NeoForge | Accepted (supersedes 0003) |
| [0027](0027-client-bound-payload-handlers-through-the-client-event.md) | Client-bound payload handlers through NeoForge's client event | Accepted (amends 0014) |
| [0028](0028-dungeon-rooms-generated-or-imported.md) | Dungeon rooms are generated or imported, not built by hand | Accepted (amends 0007, 0009, 0012 and 0014) |
| [0029](0029-independent-claude-review-before-every-merge.md) | Independent Claude review before every merge | Accepted (supersedes 0015) |
| [0030](0030-adrs-bind-agents-not-the-owner.md) | ADRs bind agents, not the owner | Accepted (amends 0001), amended by [0060](0060-what-counts-as-the-owners-confirmation-of-an-adr-conflicting-change.md) (what counts as his confirmation) |
| [0031](0031-instance-builder-background-chunks-and-shared-budget.md) | Instance builder: background chunk loads, a shared budget, clear-only cleanup | Accepted (amends 0019) |
| [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md) | The v1 realm layout amends the Nether and End removal | Accepted (amends 0025) |
| [0033](0033-vanilla-armor-formula-amends-our-own.md) | Vanilla's armor formula amends "our own formula" | Accepted (amends 0014) |
| [0034](0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md) | Ancient merchants and the Wither amend the Nether and End removal | Accepted (amends 0025) |
| [0035](0035-room-galleries-show-every-marker-with-its-label.md) | Room galleries show every marker with its label | Accepted |
| [0036](0036-room-lights-and-decorations-form-patterns.md) | Room lights and decorations form patterns | Accepted |
| [0037](0037-elytra-from-chapter-3s-final-boss-amends-the-nether-and-end-removal.md) | Elytra from chapter 3's final boss amend the Nether and End removal | Accepted (amends 0025) |
| [0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md) | Apotheosis carries gear rarity, affixes, gems and enchanting | Accepted (amends 0004 and 0013) |
| [0039](0039-a-cast-slows-its-caster-instead-of-moving-interrupting-it.md) | A cast slows its caster instead of moving interrupting it | Accepted (amends 0020), the slow raised to 50 % by [0047](0047-the-cast-slow-halves-the-casters-speed.md) |
| [0040](0040-coins-and-data-driven-merchant-trades.md) | Coins and data-driven merchant trades | Accepted (amends 0013) |
| [0041](0041-dungeon-containers-hold-loot-through-lootr.md) | Dungeon containers hold loot through Lootr | Accepted (amends 0013) |
| [0042](0042-the-dev-runs-load-the-whole-pack.md) | The dev runs load the whole pack | Accepted (amends 0020) |
| [0043](0043-default-keys-and-key-search-in-the-pack.md) | Default keys and key search in the pack | Accepted (amends 0013), amended by [0046](0046-spell-bar-default-keys-amend-the-pack-defaults.md) (the spell bar's keys, chat on Enter, Dark Vision on ¨) |
| [0044](0044-third-party-mods-by-milestone.md) | Third-party mods by milestone | Accepted (amends 0004, 0013 and 0025), WorldEdit for building only by [0053](0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md) |
| [0045](0045-asgard-and-atlantis-amend-the-v1-realm-layout.md) | Asgard and Atlantis amend the v1 realm layout | Accepted (amends 0032), the names amended by [0054](0054-skyhearth-and-deepholm-amend-asgard-and-atlantis.md) |
| [0046](0046-spell-bar-default-keys-amend-the-pack-defaults.md) | The spell bar's default keys amend the pack's default keys | Accepted (amends 0043) |
| [0047](0047-the-cast-slow-halves-the-casters-speed.md) | The cast slow halves the caster's speed | Accepted (amends 0039), amended by [0057](0057-a-cast-that-slows-its-caster-stops-its-sprint.md) (the cast stops the sprint) |
| [0048](0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md) | Better Key Binds: our own client mod, independent of Realmforge | Accepted (amends 0008, 0011 and 0013) |
| [0049](0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md) | Player hits bypass the hurt cooldown in our damage pipeline, not through Iron's RPG Tweaks | Accepted (amends 0044), amended by [0051](0051-player-melee-passes-the-hurt-cooldown-only-at-full-strength.md) (melee only at full attack strength) |
| [0050](0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md) | Set pieces: hand-built NBT, placed at dungeon generation | Accepted (amends 0005 and 0014) |
| [0051](0051-player-melee-passes-the-hurt-cooldown-only-at-full-strength.md) | A player's melee hit passes the hurt cooldown only at full attack strength | Accepted (amends 0049) |
| [0052](0052-spells-aim-with-the-crosshair.md) | Spells aim with the crosshair: bolts fly where it points, ground spells clamp to their range | Accepted, amended by [0055](0055-aim-assistance-for-a-players-enemy-spells.md) (aim assistance) |
| [0053](0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md) | Target dummies in the pack, WorldEdit for building only, and no FTB Teams parties | Accepted (amends 0013 and 0044) |
| [0054](0054-skyhearth-and-deepholm-amend-asgard-and-atlantis.md) | Skyhearth and Deepholm amend Asgard and Atlantis | Accepted (amends 0045) |
| [0055](0055-aim-assistance-for-a-players-enemy-spells.md) | Aim assistance for a player's enemy spells: a cone around the crosshair, homing bolts, a sticky target | Accepted (amends 0052) |
| [0056](0056-players-break-an-allowlist-of-blocks-in-instances.md) | Players break an allowlist of blocks in instances | Accepted |
| [0057](0057-a-cast-that-slows-its-caster-stops-its-sprint.md) | A cast that slows its caster stops its sprint | Accepted (amends 0047) |
| [0059](0059-the-status-overview-ends-with-verification-and-the-claimed-tickets.md) | The status overview ends with Verification and the claimed tickets, without a batch ledger | Accepted (amends 0021) |
| [0060](0060-what-counts-as-the-owners-confirmation-of-an-adr-conflicting-change.md) | What counts as the owner's confirmation of an ADR-conflicting change | Accepted (amends 0030) |

## Open questions (not decided)

- Seasons: explicitly not for now.
