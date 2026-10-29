# 0038. Apotheosis carries gear rarity, affixes, gems and enchanting

Date: 2026-10-19

Status: Accepted (amends [0004](0004-build-rpg-systems-in-house.md) and [0013](0013-modpack-composition.md)); amended by
[0058](0058-player-systems-before-world-content.md) (Apotheosis is M1's, not M2's), 2026-10-29

## Context

[0004](0004-build-rpg-systems-in-house.md) builds every RPG system in the core mod: third-party mods only for QoL,
UI, performance and libraries, plus four big mods taken unchanged, and RPG frameworks such as Apotheosis only as
inspiration. M2's outline therefore planned our own rarity tiers, affixes with item levels, gems and a reforging
station, and M5 an enchanting system without RNG.

Matěj turned this around:

- **2026-10-15 (RLM-201):** M2's rarity, affixes, gems and reforging build on Apotheosis, with Apothic Attributes
  (its required attribute library) and Apothic Enchanting. Apotheosis's progression is fitted to our realms and mob
  levels in the [Apotheosis design](../design/apotheosis.md) (RLM-199, RLM-204).
- **2026-10-16 (RLM-205):** the reason is that building everything is far too much work; a weapons mod and more
  mods are being looked for to replace what was meant to be custom. "Third-party mods only for QoL" no longer holds.
  Apotheosis data is generated from our YAML by the content pipeline, not written as a hand-made data pack.

0004 is accepted, so this ADR records the change instead of rewriting it.

## Decision

- **The custom core** is realms (custom dimensions), quests, races, classes, spells and professions, plus
  semi-custom dungeons, generated from existing pieces with a bit of hand-built work. The main effort goes into
  realms, quests and overall balance. Third-party mods may carry other gameplay systems.
- **Apotheosis 9.1.0, Apothic Attributes 3.1.0 and Apothic Enchanting 2.1.0 are in the pack**, from Modrinth, on
  both sides, with Placebo, their library, which the pack already has. They carry:
  - gear rarity: Apotheosis's five rarities, the fifth (mythic) shown as Legendary;
  - affixes, gems and sockets, reforging, salvaging and augmenting;
  - enchanting with less RNG than vanilla's.
- **We build none of these ourselves:** no rarity component and no extension of vanilla's `Rarity`, no
  `realmforge:affixes` component, no item level, no reforging station.
- **Out of the pack for now** (Matěj: "not now"): Apothic Spawners, Gateways to Eternity and Patchouli. Apotheosis
  gets a chapter in our in-game guide instead of Patchouli's book.
- **They fit our systems, not the other way round:**
  - **Our stats stay the one attribute model.** Apothic Attributes' crit and dodge never fire: every entity type
    gets its crit chance 0 and crit damage 1.0, and its crit damage is capped at 1.0 and its dodge chance at 0
    (`compat/apothic_attributes`, [architecture 19.4](../architecture.md#194-optional-integrations)).
  - **Vanilla's formulas:** its protection and armor formulas are vanilla's (`config/apotheosis/`), and our damage
    pipeline computes armor itself anyway ([0033](0033-vanilla-armor-formula-amends-our-own.md)).
  - **No second stats screen:** its attributes panel is off.
  - **Its stats point at ours:** Apotheosis data that grants an Apothic Attributes stat is remapped to ours, or
    switched off where it would be useless (RLM-272).
  - **Their data comes from `content/`** like all our content ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)):
    the pipeline generates the overrides (`content/apotheosis_affixes/` from RLM-271 on, the rest with RLM-272).
  - **Their recipes stay at their defaults**, with the Nether and End materials from the merchants and the Wither
    ([0034](0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md)).
- **Everything else in 0004 stands:** Spell Engine and Pufferfish's Skills stay inspiration only, Better Combat,
  Distant Horizons and Iris stay as they are, and the libraries are unchanged.

## Consequences

- **M2 shrinks** to fitting Apotheosis: its data from our YAML, our stats in its affixes, loot categories for our
  wands and curio slots, world tiers set by our realms ([Apotheosis design](../design/apotheosis.md)). The roadmap's
  M2 outline loses "affixes with item levels", and M5's enchanting becomes "enchanting with less RNG".
- **Two attribute sets on every entity:** Apothic Attributes registers about 25 attributes. Those with an
  equivalent of ours stay neutral or point at ours, and GameTests check that its crit and dodge never fire
  (`ApothicAttributesGameTests`). A later mod that brings combat stats of its own gets the same treatment.
- **The dev runs and the GameTests load the three mods**, with Enchantment Descriptions, whose texts Apothic
  Enchanting shows, and every dev run copies `pack/config/` into its config folder before it starts, so they play
  with the pack's settings.
- **Our data replaces theirs:** the core mod loads after Apotheosis and Apothic Enchanting, so a file of ours wins
  at the same path. `content/apotheosis_affixes/` generates Apotheosis's affixes, starting with Lucky, whose charm
  category exists only with Curios (without Curios Apotheosis skipped the whole affix); a core mod resource retires
  Apothic Enchanting's stale list of loot modifiers. Our curio categories wait for Curios and our curio items
  (RLM-273).
- **Licenses:** Apotheosis, Apothic Attributes and Apothic Enchanting are MIT code; Apotheosis's assets are ARR,
  and we never reuse them. The pack only references their jars.
- **Docs:** the [vision](../vision.md), [research notes, section 3](../research-notes.md#3-take-as-is-vs-build),
  [architecture](../architecture.md) 8.4, 13, 19.3 and 19.4, the [roadmap](../roadmap.md) (M2, M5), the
  [pack README](../../pack/README.md) and `CLAUDE.md` follow this decision (RLM-271).
