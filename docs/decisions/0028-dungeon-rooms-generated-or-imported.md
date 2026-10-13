# 0028. Dungeon rooms are generated or imported, not built by hand

Date: 2026-10-13

Status: Accepted (amends the hand-built rooms of [0012](0012-procedural-dungeons-and-dimensions.md) and decision 8 of
[0014](0014-core-mod-architecture.md), adds third-party rooms to [0009](0009-licensing.md), and narrows the vanilla
texture rule of [0007](0007-asset-pipeline.md) to texture files)

## Context

[0012](0012-procedural-dungeons-and-dimensions.md) and decision 8 of [0014](0014-core-mod-architecture.md) assumed
hand-made rooms: "hand-built rooms live in the room library", built in creative with structure blocks
([architecture 15.4](../architecture.md#154-rooms)), and the roadmap had Matěj build the key rooms (A4, W6). On
2026-10-12 Matěj decided that nobody builds rooms by hand (Jira RLM-50; its comments hold his words and the research
this ADR rests on).

The research on RLM-50 looked at two ways to get rooms without building them:

- **Ready-made rooms we may copy.** No permissive "dungeon room kit" exists, but some structure mods ship their
  templates under permissive licenses: Stonevaults (MIT; 104 jigsaw pieces, nearly all within 16×16, vanilla blocks
  only), DungeonZ with its Desert addon (MIT), Dungeon Now Loading (CC0 in its jar), and under LGPL-3.0 YUNG's Better
  Dungeons, Moog's Voyager and End structures, and Mo' Structures. Most other sources are All Rights Reserved,
  non-commercial or GPL (Planet Minecraft and similar sites, research datasets, GPL dungeon mods), and Stardust Labs'
  license bans use "as part of any AI project".
- **Generation.** Language models build best by writing code that places blocks (MC-Bench), and `rf gen` already
  writes structure NBT (the spike rooms). Generated interiors still look worse than hand-built ones (the 2026 GDMC
  winner placed hand-built templates by algorithm), so generated rooms mix with ready-made pieces.

Two older rules stand in the way. [0009](0009-licensing.md) puts every asset in the jar under CC BY 4.0, which
copied third-party rooms cannot be. [0007](0007-asset-pipeline.md) and CLAUDE.md rule 8 forbid vanilla textures
"not even as AI input"; read literally, that also forbids vanilla blocks in our rooms and agents judging renders of
them, which Matěj never meant: dungeons may of course use vanilla blocks, especially in the Overworld, and
netherrack, nether bricks, end stone or purpur in the fire and Void realms.

## Decision

1. **Rooms are generated or imported.** No task waits for a hand-built room; a room built by hand (structure
   blocks, saved as NBT with its YAML sidecar) stays possible.
   - **Generated:** room specs in YAML (kind, footprint, connectors, features, a seed, blocks named by neutral roles
     that themes swap) become structure NBT through our room generator in `rf gen` (A4d, RLM-121). The NBT is
     generated output, never edited by hand.
   - **Imported:** a baseline of third-party rooms, taken in by `rf rooms import` (A4b, RLM-119), remapped to
     vanilla blocks or our neutral roles, retagged into our slot types and given our YAML sidecars (A4c, RLM-120).
   - **Review:** Matěj judges both in batches in the room gallery (A4e, RLM-122) without launching the game; a kept
     generated candidate's seed is frozen.
2. **What may be copied:** rooms under MIT, CC0 or LGPL-3.0, starting with the sets above, each license checked
   again at the exact version imported (A4c). Nothing All Rights Reserved, non-commercial, GPL or of unclear license
   is copied, which rules out Planet Minecraft, Grabcraft and similar sites, research datasets and GPL dungeon mods.
   **Stardust Labs content is avoided entirely:** it is neither copied nor used as input for our tools, because its
   license bans use in AI projects. Using another mod's structures at runtime, as a pack dependency, is a question of
   pack composition ([0013](0013-modpack-composition.md)), not of this decision.
3. **One folder per source:** `content/rooms/third_party/<source>/` holds the source's rooms, its license text
   (`LICENSE`) and a `NOTICE` (project, version, URL, copyright holders, and what we changed: remapped blocks,
   retagging, dropped entities). Rooms under different licenses never share a folder. The folder is a license
   boundary, not a room pool: the rooms' sidecars name the pools they join.
4. **LGPL-3.0 rooms stay LGPL-3.0**, our changes to them included. They keep their own folder in the repository and
   in the jar (the jar's structure folders mirror `content/rooms/`), so they stay separate from our MPL-2.0 code
   and CC BY 4.0 assets. The jar carries the GPL-3.0 text beside the LGPL-3.0 text, as LGPL-3.0 requires. The NBT
   files are their own source form, so the repository is the corresponding source.
5. **The jar carries every notice:** once the first imported room ships, the `jar` task copies each source's
   `LICENSE` and `NOTICE` into `META-INF/third_party/<source>/`, next to `LICENSE_realmforge`;
   `neoforge.mods.toml`'s `license` names the added licenses, and the README's license section lists the sources.
6. **Vanilla templates are referenced, never copied.** A room or a prototype may use a vanilla template by its id at
   runtime (trial chamber pieces, for example), but no vanilla `.nbt` file is copied into `content/` or the jar
   (Minecraft EULA). Imported pieces that copy vanilla templates are dropped (A4c).
7. **Vanilla blocks are fine; vanilla texture files are not** (rule 8, reworded). Our rooms and worldgen may use
   any vanilla block, and renders or screenshots of them are fine for review, by Matěj or by an agent. What stays
   forbidden: vanilla texture files or recolours of them in our assets, and vanilla textures as input for
   generating our textures ([0007](0007-asset-pipeline.md), [0023](0023-texture-generator.md)).
8. **Licensing:** the mod's assets stay CC BY 4.0 ([0009](0009-licensing.md)), except the imported rooms, which
   keep their own licenses (MIT, CC0, LGPL-3.0) under points 3 to 5.

## Consequences

- A4 (RLM-50) is split into A4a (this ADR, RLM-118), A4b (NBT array tags and `rf rooms import`, RLM-119), A4c (the
  baseline import, RLM-120), A4d (the room generator, RLM-121) and A4e (the gallery, RLM-122). W6 assembles its
  dungeon types from these rooms.
- Room validation ([architecture 15.4](../architecture.md#154-rooms)) holds for every room. A source's own blocks
  must be remapped on import, because references stay inside the ID universe (vanilla plus ours, CLAUDE.md rule 3)
  until the full-pack registry dump exists.
- Themes need per-state care: a vanilla `rule` processor writes a fixed block state, so swapping stairs or slabs
  loses their facing and half ([architecture 15.2](../architecture.md#152-dungeon-types)).
- 0012's consequence "room quality depends on hand-built rooms reviewed by the owner" now reads: on generated and
  imported rooms that the owner reviews in the gallery. Generated filler mixes with imported pieces for variety.
- Imported rooms come from older Minecraft versions (1.20.1, 1.21.1). The importer records each source's data
  version, and the game upgrades templates when it loads them.
- Notices are upkeep: re-importing a newer version of a source updates its `NOTICE`, and every new source brings
  its own folder, license text and notice.
- Matěj's time no longer limits how many rooms exist; his review time in the gallery does.
