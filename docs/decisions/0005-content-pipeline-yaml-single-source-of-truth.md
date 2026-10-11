# 0005. Content pipeline: YAML as the single source of truth

Date: 2026-10-11

Status: Accepted; amended by [0014](0014-core-mod-architecture.md) (dungeon rooms are NBT in `content/rooms/`)

## Context

The vision calls for 500+ quests, 100+ structures, 5-10+ dimensions and hundreds of items. In Otherworld, hand-maintained content led to quests referencing mobs that were not in the pack, boss levels that differed between quest and config, and unobtainable or unrepairable rewards. Python is the owner's strength and AI writes it reliably. Details: [research notes, section 4](../research-notes.md#4-content-pipeline).

## Decision

- **All content lives in `content/` as YAML.** Exception (later): dimension worldgen is written directly as JSON in `content/worldgen/`; the pipeline validates and copies it ([0012](0012-procedural-dungeons-and-dimensions.md)).
- **Java holds behaviour types** (spell type, affix type, boss phase, dungeon generator); **data holds instances** (Fireball = projectile spell, damage 12, burn). Items, blocks and entities are registered via generated Java constants; spells, talents, affixes, gems, dungeon types and boss definitions are datapack registries with codecs (validated on world load; `/reload` does not reload them).
- **Python in `tools/`** validates and generates. Schemas are pydantic models, which also emit JSON Schema for editor autocompletion.
- **ID universe:** vanilla reports (`--reports`) plus our own IDs now. **TODO:** add a registry dump of the whole pack via the Registry Dump mod (`/dump registry`).
- **Validation order**, locally and in CI; generators run only on data that passed it:
  1. schema (pydantic);
  2. references: every ID exists in the ID universe;
  3. semantic rules: every item obtainable, every reward usable and repairable in its chapter, boss levels in quest titles match data, every quest says where/how/with what, no orphaned textures or lang keys;
  4. runtime: server boots cleanly and GameTests pass ([0010](0010-testing-strategy.md)).
- **Generators produce** Java registrations, assets (models, blockstates, lang, Xaero icons), data (recipes, loot tables, tags, worldgen, advancements, our registries), FTB Quests SNBT ([0006](0006-quests-ftb-quests-generated-from-data.md)), configs for third-party mods, and documentation.
- **Generated files are committed** under `*/src/generated/` (other generated outputs are marked in `.gitattributes`) and **never edited by hand**. Errors are fixed in `content/` or in the generator.
- **CI regenerates everything and requires a clean `git diff`.**

## Consequences

- A new spell, item or quest usually needs no new Java code, only YAML.
- Typos in IDs fail validation or compilation, not playtests.
- Contributors (human or AI) must run the generators after editing `content/`; CI enforces it.
- Content ports cheaply to a future Minecraft version ([0003](0003-platform-minecraft-1-21-1-neoforge.md)).
- Until the full-pack registry dump exists, references to third-party mod IDs are not validated.
