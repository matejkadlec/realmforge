# 0044. Third-party mods by milestone

Date: 2026-10-20

Status: Accepted (amends [0004](0004-build-rpg-systems-in-house.md) and [0013](0013-modpack-composition.md);
answers the stronghold question of [0025](0025-remove-the-nether-and-end.md) and amends its "M1 does not change them")

## Context

[0004](0004-build-rpg-systems-in-house.md) kept third-party mods to QoL, UI, performance and libraries, and
[0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md) opened it for Apotheosis because building
every system is too much work. On 2026-10-18 Claude researched about 130 mods with NeoForge 26.1.2 builds (Modrinth
API) for Matěj, including the Overworld structure and light dungeon mods that RLM-221 asked for, and he marked each
one Yes, No or Undecided. On 2026-10-19 he decided where their structures go and what moves between milestones, and
answered the open questions the same night (RLM-264). The candidates and every pick are in the
[research note](../research/third-party-mods.md).

0004 and 0013 are accepted, so this ADR records the change instead of rewriting them.

## Decision

- **Third-party scope** (Matěj, 2026-10-18): the original "minimal third-party mods" vision is gone; fully custom
  would take absurd time.
  - **Stays custom:** quests, story, NPCs and dialogues; classes, specializations and spells; mounts, pets and the
    in-game guide; realms and instanced dungeons.
  - **Talents stay dropped for now:** classes and specializations only, talents may come later (Matěj, 2026-10-16,
    RLM-28; confirmed 2026-10-18). Glyphs are postponed too.
  - **Third-party allowed:** world-generated dungeons and their mobs, including other mods' dungeons placed into our
    realms with swapped blocks (Lithostitched processors, Structurify), and boss-like mobs if they are lightweight.
    Full boss fights stay ours.
- **Mods by milestone**, all with NeoForge 26.1.2 builds on 2026-10-18 (the
  [research note](../research/third-party-mods.md) lists them with their notes and tickets):
  - **M1:** Structurify, Inventory Sorting, Advanced Loot Info with Advanced Core Info, Just Enough Professions,
    Controlling with Searchables (in since [0043](0043-default-keys-and-key-search-in-the-pack.md)), Clumps, CleanCut
    or Cut Through (only if Better Combat does not already hit through grass), Crafting Tweaks, Item Highlighter, Pick
    Up Notifier, RightClickHarvest, Comforts, Traveler's Titles, Macaw's building mods, Better ModList, BetterF3,
    Lootr ([0041](0041-dungeon-containers-hold-loot-through-lootr.md)) and Iron's RPG Tweaks, combat module only.
  - **M2:** Artifacts, Immersive Armors, Legendary Tooltips, Item Borders, YDM's Weapon Master, YUNG's suite (Better
    Dungeons included, Better Strongholds not), Dungeons and Taverns (not its Stronghold splinter), Structory,
    Structory: Towers, MVS, Battle Towers, Dungeons Enhanced, Underground Worlds, Golem Dungeons, Illager Invasion, It
    Takes a Pillage, Illager Warship, Ender Zoology, ChoiceTheorem's Overhauled Village, Village Taverns, Villager
    Names.
  - **M3:** Ranged Weapon API and More Bows and Arrows, with the ranged play style.
  - **M4:** YUNG's Cave Biomes, The Lost Castle, Alex's Mobs Continued (its Nether and End mobs off or moved), Just
    Enough Resources, LambDynamicLights, Effectual, Falling Leaves, Fresh Animations with ETF and EMF, Fusion (in M1
    instead if Undervault needs connected textures).
  - **M5:** Aquaculture 2 (the fishing profession) and Better Archeology (the seed of an archaeology profession).
  - **Later realms:** MSS – Moog's Soaring Structures (Unmade Isles), Grim Kingdoms (Quagmire), Dungeons and Taverns'
    Ancient City Overhaul (the late Overworld questline).
  - **M8 (v2), after v1:** Shoulder Surfing Reloaded and Camera Overhaul (both off by default, switched on in an
    optional-features screen), Apothic Spawners, Trims Overhaul, Gateways to Eternity, Yggdrasil.
  - **Watchlist:** Supplementaries with Amendments, Polymorph, Relics and Farmer's Delight add-ons join when they reach
    26.1.2; Enhanced Boss Bars, Simply Swords and Mowzie's Mobs too, with our own solutions until then. Re-checked when
    each milestone starts.
  - **Rejected:** every mod Matěj marked No (the research note lists them), among them Explorer's Compass and
    Nature's Compass (our own Structure Compass instead), Immersive Paintings (our own painting variants instead),
    Corpse and GraveStone (keepInventory stays on) and the RPG Series gear.
- **Placement** (Matěj, 2026-10-19):
  - **each structure only in its realm, never also in Overworld;** the [realm design](../design/realms.md#structures-from-other-mods)
    lists which structure goes where;
  - **vanilla strongholds are off from M1**, through Structurify: they do not stay as an Overworld dungeon;
  - Ancient Cities stay, slightly modified, for a later Overworld questline with a Warden of level 40 and more, which
    needs a structure-bound mob level floor (RLM-240);
  - paintings are chosen per dungeon theme, never random across themes.
- **Hit invulnerability:** player damage never loses anything to vanilla's 10-tick hurt cooldown, melee included,
  because attack speed 3 and more will be reachable. Iron's RPG Tweaks carries it with its combat module alone (its
  XP, durability, hunger and sleep modules off). If it misbehaves, the fallback is our data pack putting player
  attacks, arrows and our spell damage types into `#minecraft:bypasses_cooldown`.
- **Licences:** CC BY-NC-ND mods are used unmodified: packwiz downloads the original jar, and every change is our own
  config, data pack or processor (Matěj accepts this reading). Nothing of theirs is copied into our repository, as
  rule 8 of `CLAUDE.md` already requires.
- **Catalogues first:** Artifacts, Immersive Armors and More Bows and Arrows each get a catalogue (every item with its
  effect, a proposed minimum realm or level, early unique reward candidates) before they are enabled, and Matěj
  decides what stays.

## Consequences

- **The plan moves** ([roadmap](../roadmap.md)): M1 gets the pack tasks PK1 to PK4; the Overworld chapter's content
  (its dungeons, the illager questline, the raid quests and the Illusioner tower with Overworld's final boss), the
  world-structure dungeons of Overworld and Undervault and our own Structure Compass move from M4 to M2; M4 keeps the
  twin realms' structures and gets open-air instanced dungeons (a second instance dimension with sky light,
  amending [0019](0019-dungeon-instances-in-plots.md) when it is built); a new M8 (v2) holds the post-v1 mods.
- **0013's list of small UI mods to build ourselves is settled:** Inventory Sorting replaces Lightweight Inventory
  Sorting, Advanced Loot Info and Just Enough Professions replace EMI Trades' role next to JEI / REI Trades
  ([0040](0040-coins-and-data-driven-merchant-trades.md)), and our combat text covers damage numbers. Nothing is left
  of RLM-61.
- **More mods, more checks:** each one goes in with its own ticket, its side, a pack boot, the pack README and the
  0013 list; worldgen mods add a structure-placement check and a spark profile, and every mob mod meets our mob
  levels (RLM-240) and our stats as the one attribute model (0038).
- **The End is unreachable from M1 on.** Without strongholds no End portal generates, so players cannot reach the
  End from M1, long before M4 removes the dimension; [0025](0025-remove-the-nether-and-end.md) said M1 leaves both
  dimensions untouched, and this amends that. Items only the End gives (end stone, purpur, chorus fruit, end rods,
  dragon's breath, the End's shulker shells) have no source until [0034](0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md)'s
  merchants or M4's removal task gives them one, and the advancements "Eye Spy" and "The End?" become unreachable
  early; elytra already wait for chapter 3's final boss ([0037](0037-elytra-from-chapter-3s-final-boss-amends-the-nether-and-end-removal.md)).
  Whether M1 needs an earlier source for any of them is open for Matěj. The Nether is unchanged.
- **Our damage pipeline meets Iron's RPG Tweaks:** PK4 checks it against the hurt-cooldown handling of
  [architecture 9.1](../architecture.md#91-damage-pipeline) and keeps fire, lava, cactus and crowds from hitting every
  tick.
- **The custom core shrinks again:** talents and glyphs wait, world dungeons and their mobs may come from mods, and
  the effort goes into realms, quests, classes, spells, instanced dungeons and balance.
- **Docs:** the [vision](../vision.md), the [research notes](../research-notes.md#owner-overrides), the
  [realm design](../design/realms.md), the [roadmap](../roadmap.md) and [architecture](../architecture.md) 12 and
  15.6 follow this decision (RLM-264, RLM-221).
