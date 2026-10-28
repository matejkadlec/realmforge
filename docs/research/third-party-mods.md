# Third-party mods by milestone

> Matěj's picks from about 130 mod candidates with NeoForge 26.1.2 builds, and where their structures go. Research
> by Claude on 2026-10-18 (Modrinth API) in Matěj's private Claude doc "Realmforge – Third-Party Mod Candidates";
> Matěj marked each mod Yes, No or Undecided with comments, decided placement and the plan change on 2026-10-19 and
> answered the open questions the same night. Source tickets: RLM-264 (the picks, the plan change) and RLM-221 (the
> Overworld structure research it answers). Decision: [ADR 0044](../decisions/0044-third-party-mods-by-milestone.md).

Every mod below had a NeoForge 26.1.2 build on 2026-10-18 unless the line says otherwise. A pick is a plan, not a pack
change: each mod goes in through its ticket, with its side, a pack boot, the [pack README](../../pack/README.md) and
the [ADR 0013](../decisions/0013-modpack-composition.md) list.

## Scope

- **The original "minimal third-party mods" vision is gone** (Matěj, 2026-10-18): fully custom would take absurd time.
- **Stays custom:** quests, story, NPCs and dialogues; classes, specializations and spells; mounts, pets and the
  in-game guide; realms and instanced dungeons. Talents stay dropped for now (RLM-28).
- **Third-party allowed:** world-generated dungeons and their mobs, including other mods' dungeons placed into our
  realms with swapped blocks (Lithostitched processors, Structurify); boss-like mobs only if they are lightweight,
  never full boss fights. This loosens RLM-221's first rule of 2026-10-15 ("no bosses").
- **Licences:** CC BY-NC-ND mods are used unmodified (packwiz downloads the original jar); every change is our own
  config, data pack or processor. Matěj accepts this reading.

## Picks by milestone

| Milestone | Mods | Notes | Tickets |
| --- | --- | --- | --- |
| M1 | Structurify | switches vanilla strongholds off | PK3, RLM-310 |
| M1 | Inventory Sorting, Advanced Loot Info + Advanced Core Info, Just Enough Professions, Clumps, CleanCut or Cut Through, Crafting Tweaks, Item Highlighter, Pick Up Notifier, RightClickHarvest, Comforts, Traveler's Titles, Better ModList, BetterF3 | Inventory Sorting's default key `;` works beside Open Quests (one with the inventory open, one closed; Matěj confirmed it on 2026-10-19). CleanCut or Cut Through only if Better Combat does not already hit through grass. Advanced Loot Info and Advanced Core Info are betas | PK1, RLM-308 |
| M1 | Macaw's Furniture, Doors, Windows, Roofs, Bridges, Lights and Lamps, Fences and Walls, Paths and Pavings, Trapdoors, Stairs | | PK2, RLM-309 |
| M1 | Iron's RPG Tweaks, combat module only | marked No on 2026-10-18, then taken on 2026-10-19 (below) | PK4, RLM-311 |
| M1 | Controlling + Searchables | in the pack since RLM-279 ([ADR 0043](../decisions/0043-default-keys-and-key-search-in-the-pack.md)) | done |
| M1 | Lootr | moved from M2 to M1 on 2026-10-19 ([ADR 0041](../decisions/0041-dungeon-containers-hold-loot-through-lootr.md)) | RLM-288 |
| M1 | MmmMmmMmmMmm (target dummies) + Moonlight Lib | added on 2026-10-23, after this review: target dummies for everyone, and Maren's Trial (RLM-397) dresses one. Moonlight Lib is a pinned beta, a release-channel exception: every 26.1.2 build of it is a beta ([ADR 0053](../decisions/0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md)) | RLM-393 |
| M2 | Artifacts, Immersive Armors | **Artifacts is held:** its items are curios, which ADR 0025 keeps out of the pack unless they are ours (RLM-7), so Matěj decides with its catalogue. A catalogue each first (every item with its effect, a proposed minimum realm or level, early unique reward candidates); Matěj decides what stays | RLM-312, RLM-313, RLM-314 |
| M2 | Legendary Tooltips, Item Borders, YDM's Weapon Master | | RLM-314 |
| M2 | YUNG's suite: Better Mineshafts, Desert Temples, Jungle Temples, Witch Huts, Ocean Monuments, Bridges, Extras, Better Caves, Better Dungeons | not Better Strongholds. Better Dungeons is in (open question 1, below) | RLM-315 |
| M2 | Dungeons and Taverns, Structory, Structory: Towers, MVS – Moog's Voyager Structures, Battle Towers, Dungeons Enhanced, Underground Worlds | not D&T's Stronghold splinter. **Structory and Structory: Towers are held:** they are Stardust Labs mods under the Stardust Labs License, whose AI clause ADR 0028 avoids, so they wait for Matěj's reading (ADR 0044). Dungeons Enhanced's 26.1.2 build is 6.4.1 beta | RLM-315 |
| M2 | Golem Dungeons | Abandoned Factory in Undervault; its golems join raids from wave 5 (a config switch), decided before the raid quests are tuned | RLM-316 |
| M2 | Illager Invasion, It Takes a Pillage, Illager Warship, Ender Zoology, ChoiceTheorem's Overhauled Village, Village Taverns, Villager Names | Village Taverns' pieces go into CTOV's village pools (Lithostitched `add_template_pool_elements`). Villager Names with a custom name list that leaves out our story NPCs' names (it has no blacklist) | RLM-318 |
| M3 | Ranged Weapon API, More Bows and Arrows | with the ranged play style; More Bows and Arrows gets a catalogue first | RLM-323, RLM-324 |
| M4 | YUNG's Cave Biomes, The Lost Castle, Alex's Mobs Continued | Alex's Mobs Continued with its Nether and End mobs off or moved | RLM-325 |
| M4 | Just Enough Resources (alpha), LambDynamicLights, Effectual, Falling Leaves, Fresh Animations (a resource pack) + Entity Texture Features + Entity Model Features, Fusion | Fusion moves to M1 if Undervault needs connected textures | RLM-326 |
| M5 | Aquaculture 2 (the fishing profession), Better Archeology (the seed of an archaeology profession, WoW-inspired) | | RLM-328 |
| Later realms | MSS – Moog's Soaring Structures (Unmade Isles), Grim Kingdoms (Quagmire), Dungeons and Taverns: Ancient City Overhaul (the late Overworld) | with their realms or questline | none yet |
| M8 (v2) | Shoulder Surfing Reloaded, Camera Overhaul | both off by default, switched on in an optional-features screen | RLM-329 |
| M8 (v2) | Apothic Spawners, Trims Overhaul, Gateways to Eternity, Yggdrasil | Apothic Spawners: modified spawners give vanilla XP and mob loot, never class XP, and their loot is balance-checked | RLM-330 |

**Watchlist** (re-checked when each milestone starts; Matěj expects most 1.21.1-only mods never to reach 26.1.2):
Supplementaries with Amendments, Polymorph, Relics (SSKirillSS's, Modrinth `relics-mod`) and Farmer's Delight add-ons
join when they reach 26.1.2; for Enhanced Boss Bars, Simply Swords and Mowzie's Mobs we build our own until they arrive.
Undecided on the watchlist: Every Compat, and Handcrafted, Another Furniture or Chipped.

**Rejected (No):** Epic Knights, Basic Weapons, the RPG Series gear (Arsenal, Armory, ZsoltMolnarrr's Relics
`relics-rpg`, Jewelry: too much overlap, against "not overmodded"), TslatEntityStatus (we have damage numbers and Neat),
Combat Roll (classes get their own teleport, dash or roll), DeCubed Dungeons, Explorify and Formations Overworld (they
overlap MVS), Medieval Buildings, ATi Structures, Variants&Ventures, Friends&Foes, Mutant Monsters, The Conjurer,
Hunter's Return, Champions-Unofficial, Guard Villagers, EarthMobs, Explorer's Compass and Nature's Compass (our own
Structure Compass instead), Corpse and GraveStone (keepInventory stays on), Tectonic, Little Joys, Snow! Real Magic!,
Paragliders and Hang Glider, Food Effect Tooltips (our own tooltips), Pufferfish's Skills, Cooking for Blockheads
(modern look), Storage Delight, Crate Delight, Starcatcher, WorldEdit (for the pack; Matěj builds with it in his dev
client, `-PwithWorldEdit`, and his Prism instance, [ADR
0053](../decisions/0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md)), Rechiseled,
Stoneworks, Fetzi's Asian Decoration, Building Wands, Construction Sticks, Dark Paintings, Dynamic RPG Resource Bars,
Visible Traders, Advancement Plaques, Ping Wheel, Simple Voice Chat, KubeJS + LootJS, AmbientSounds (ElevenLabs sounds
instead), Sound Physics Remastered, Subtle Effects, Particular Reforged, Not Enough Animations, 3D Skin Layers, Spawn
Animations, Sounds, and Immersive Paintings (open question 3). Watchlist No: When Dungeons Arise, IDAS, Born in Chaos,
Kaleidoscope Cookery, Creeper and Enderman Overhaul, Critters and Companions, Kobolds, TorchMaster, Valhelsia
Structures, In Control!, Naturalist, Presence Footsteps.

**Not reviewed:** Repurposed Structures, which RLM-221 asked for, has a 26.1 build that Modrinth does not tag 26.1.2.

## Placement

Matěj, 2026-10-19: **each structure only in its realm, never also in Overworld.** The per-realm list is in the
[realm design](../design/realms.md#structures-from-other-mods). Facts for the placement work:

- **Dungeons Enhanced** gives each structure its own biome tag (`tags/worldgen/biome/has_structure/<structure>.json`);
  loot goes through loot tables or Global Loot Modifiers, spawners through `ametrin_structures/spawner_profile` and
  `trial_spawner` data ([customizing guide](https://github.com/Ametrin-Studios/DungeonsEnhanced/blob/main/customizing.md)).
- **Flying Dutchman** spawns 64+ blocks above the surface in the mod; our data pack overrides its start height so it
  floats 20–40 blocks above Deepholm's seabed (open question 2; details on RLM-265).
- **Ocean Monuments** (vanilla, through YUNG's Better Ocean Monuments) generate only in Deepholm, never in Overworld
  (Matěj, 2026-10-19, RLM-265), so prismarine and Apothic Enchanting's seashelves come only from there.
- **Strongholds** are off completely from M1 (Structurify), and neither YUNG's Better Strongholds nor D&T's
  Stronghold splinter comes in.
- **Ancient Cities** stay, modified at least slightly (D&T's Ancient City Overhaul), for a later Overworld questline
  with a Warden of level 40 and more, which the Overworld chapter notes as extremely strong. Their mobs need a
  structure-bound level floor, because RLM-240's open-world mobs follow the nearest player.
- **Paintings** are chosen per dungeon theme, never random across themes (no kitten painting in catacombs): our own
  painting variants, a list per theme (RLM-322).

## The Overworld chapter

Matěj, 2026-10-19; the plan moves it to M2 ([roadmap](../roadmap.md#m2-gear-and-loot)):

- **Main questline:** 2–3 zombie, skeleton or spider dungeons first, then pillagers, illagers and evokers until the
  end of the chapter. Other dungeons are optional quests, or mandatory where they fit the progression (RLM-320).
- **Final boss:** an elite Illusioner at level 8 in an Illusioner tower (a world structure, not an instance), the
  tower's other mobs at about level 6; our own AI and phases, no Blindness (RLM-321).
- **Raid quests:** "Defeat the first raid wave" … "Defeat the Nth raid wave", then "Defend a village from a raid", so a
  failed raid still pays. Once the raid quest unlocks, "Receive Bad Omen" repeats up to about 3 times. Raids have
  3/5/7 waves on Easy/Normal/Hard plus one with Raid Omen above I, and since 1.21 Bad Omen comes from an Ominous
  Bottle. FTB Quests has no raid-wave or Bad Omen task, so our mod adds both as triggers that FTB reads through
  advancements ([ADR 0006](../decisions/0006-quests-ftb-quests-generated-from-data.md)).
- **Realmforge Trials:** a quest chapter group for every realm's non-story dungeons and bosses, with no dependencies
  between them except that the realm is unlocked: "These dungeons are not part of the main storyline, however,
  completing them helps you progress, both XP-wise and gear-wise."

## Answers of 2026-10-19

Matěj's answers to RLM-264's open questions, and two more decisions (the comment of 2026-10-19 night on RLM-264):

1. **YUNG's Better Dungeons: in.** "A very common dungeon mod, but good and lightweight." RLM-221 had ruled it out on
   2026-10-15 as filler.
2. **Flying Dutchman:** in Deepholm, as a ghost ship floating 20–40 blocks above the seabed (above).
3. **Immersive Paintings: No.** We draw our own painting variants; no player-uploaded textures are needed.
4. **Camera Overhaul: Yes**, with Shoulder Surfing Reloaded in M8 (v2). **Villager Names: Yes**, in M2.

- **Hit invulnerability:** player damage must never lose anything to vanilla's 10-tick damage ticks, melee included,
  because attack speed 3 and more will be reachable (a hit every 6–7 ticks). Matěj prefers a mod: Iron's RPG Tweaks,
  combat module only (its XP module would even ignore keepInventory). Check that fire, lava, cactus and crowds of
  mobs do not hit every tick. The lighter alternative, if the mod misbehaves: player attacks, arrows and our spell
  damage types in `#minecraft:bypasses_cooldown`.
- **Open-air instanced dungeons are wanted** (like WoW's Stratholme or Hytale's dungeons: an open area under the sky,
  closed in by mountains or cliff walls). The instance dimension of
  [ADR 0019](../decisions/0019-dungeon-instances-in-plots.md) has no sky light and a fixed night, so they need a
  second one. Proposed and planned for M4 (RLM-327).
- **The post-v1 bucket is M8 (v2).**

## RLM-221's research (2026-10-15)

RLM-221 asked for candidates in three groups: mods that overhaul the Overworld's vanilla structures, Repurposed
Structures-style simple structures, and light boss-free dungeons. Where they ended up:

- **Vanilla structure overhauls:** YUNG's suite (mineshafts, desert and jungle temples, witch huts, ocean monuments),
  ChoiceTheorem's Overhauled Village with Village Taverns, Dungeons and Taverns. Whether vanilla's jungle temple
  still generates in 26.1.2 (RLM-221's question) is checked when RLM-315 places YUNG's Better Jungle Temples.
- **Simple structures:** MVS, and Structory with Structory: Towers if Matěj clears their Stardust Labs License (ADR 0044); Repurposed Structures was not reviewed (above).
- **Light dungeons:** Dungeons Enhanced (the ships, the pillager camp), Battle Towers, Underground Worlds, Golem
  Dungeons, Illager Warship; bosses at most lightweight. L_Ender's Cataclysm, Aquamirae, When Dungeons Arise and
  Stalwart Dungeons stay out, as RLM-221 said.
