# 0004. Build all RPG systems in-house

Date: 2026-10-11

Status: Accepted

## Context

The main value of Realmforge is that its systems (races, classes, leveling, stats, spells, talents, gear, professions, pets, mobs, bosses, dungeons, dimensions) work together as one ecosystem. Third-party RPG frameworks each bring their own attributes and assumptions, which would fragment that. Comparison tables (take as-is, libraries, build ourselves, inspirations): [research notes, section 3](../research-notes.md#3-take-as-is-vs-build).

## Decision

- **All RPG systems are built in-house** in the core mod.
- **Third-party mods only for QoL, UI, performance and libraries**, plus four big mods taken unchanged: Better Combat, MineColonies, Distant Horizons, Iris. The pack only references them; nothing is forked.
- **Libraries our code builds on:** GeckoLib 4 (models and animations), Curios (curio slots), Lithostitched (jigsaw depth, structure tweaks), TerraBlender (only if we add Overworld biomes), FTB Quests (quest book in v0.1, see [0006](0006-quests-ftb-quests-generated-from-data.md)).
- **Frameworks are inspiration only**, never dependencies: Spell Engine (best spell data format), Apotheosis (affixes, gems, reforging), Pufferfish's Skills; likewise Iron's Spells 'n Spellbooks, Passive Skill Tree, AutoLeveling, Explorer's Compass, Dave's Potioneering, Dummmmmmy and the others listed in the vision.
- Small UI mods missing on NeoForge 1.21.1 are built by us later (current list in [0013](0013-modpack-composition.md), modpack composition).

## Consequences

- More code to write, but one consistent attribute model and full control over balance.
- License cautions when taking inspiration:
  - Spell Engine is GPL-3.0-only: do not copy its code into our MPL-2.0 files.
  - Apotheosis: code MIT, assets ARR; never reuse its assets.
  - Explorer's Compass is CC BY-NC-SA: none of its code.
  - Curios and TerraBlender are LGPL-3.0, GeckoLib and Lithostitched MIT: fine as dependencies.
  - FTB Quests is ARR and CurseForge-only: depend on it, never copy or redistribute it.
- Better Combat on 1.21.1 depends on the abandoned playerAnimator; our spell-casting animations must coexist with it (technical spike 4).
