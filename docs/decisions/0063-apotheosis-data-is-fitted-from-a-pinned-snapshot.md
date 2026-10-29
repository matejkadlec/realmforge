# 0063. Apotheosis's data is fitted from a pinned snapshot

Date: 2026-10-29

Status: Accepted

## Context

Matěj decided on 2026-10-16 (RLM-205) that Apotheosis's data is generated from our YAML by the content pipeline
([0005](0005-content-pipeline-yaml-single-source-of-truth.md)), not hand-written as a data pack
([0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)). RLM-271 started with one content type,
`content/apotheosis_affixes/`, whose entry held a whole attribute affix in a strict model. RLM-272 has to point every
affix, gem and tier augment that grants an Apothic Attributes stat at ours, and RLM-232 builds on it (30 affixes off,
value and rarity changes on affixes of many kinds, a rarity's loot rules, a tier augment off).

Apotheosis reads a file of ours at the same path instead of its own and merges nothing, so every file we write must
hold the whole entry. The stat remaps alone touch 9 affixes, 11 gems (Apotheosis's gems use 14 kinds of bonus) and
5 tier augments: copying each into YAML by hand, with a strict model for every affix kind and bonus kind, would
duplicate about a thousand lines of Apotheosis's data, and every Apotheosis update would leave them silently stale.
The pipeline also could not check that an override names an entry Apotheosis has (RLM-272's review finding: a typo
writes a second affix next to the untouched original).

## Decision

- **A snapshot of Apotheosis's own data is the base.** `uv run rf ids apotheosis` downloads the jar
  `pack/mods/apotheosis.pw.toml` pins (sha512-checked) and writes `tools/id_universe/apotheosis-<version>.json`: its
  affixes, gems, tier augments and rarities as shipped, and the ids of its recipes. It is committed, never edited by
  hand, and names Apotheosis's source and MIT license. A pytest fails while it names another jar than the pack's.
- **`content/` says what changes, not whole entries:**
  - `apotheosis_affixes/`, `apotheosis_gems/`, `apotheosis_tier_augments/`, `apotheosis_rarities/`: one entry,
    switched off (`disabled: true`) or with top-level fields replaced (`replace:`, in Apotheosis's format, each
    field whole and of the same JSON type). The entry must be in the snapshot.
  - `apotheosis_stat_remaps/`: an Apothic Attributes stat replaced by ours, or switched off, wherever an affix, a gem
    bonus or a tier augment grants it; `source_base` converts a stat whose scale differs from ours.
  - `recipe_removals/`: recipes of vanilla or Apotheosis that no longer exist.
- **The pipeline fits and writes:** an entry's own change first, then the remaps; every changed entry is written to
  Apotheosis's own path, a switched-off entry or removed recipe as a file whose `neoforge:never` condition makes
  NeoForge's loaders (and Placebo's, Apotheosis's) skip it without an error. Validation reports an unknown entry
  (with close matches), a field the entry lacks, a remapped stat left anywhere a remap cannot reach, and a remap
  nothing grants.
- Lucky's existing fix moves to the new form (`replace: categories`), with the same generated file.

## Consequences

- RLM-232 and later tuning write a few lines per change, and the pipeline checks them against Apotheosis's real data.
- An Apotheosis update in the pack needs `rf ids apotheosis` and `rf gen`; the regenerated files then carry the new
  release's other changes to the entries we fit, and the validation names any remap the update broke.
- Field values in `replace:` are checked only for their JSON type and for loot categories, rarities, affix ids and
  attributes, not against every Apotheosis codec: the pack boot and `rf gametest-log` catch a file Apotheosis refuses.
- Other mods' data (elites, invaders, Apothic Enchanting) is not in the snapshot; a later need adds its kind there.
