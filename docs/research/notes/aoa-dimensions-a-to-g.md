# Advent of Ascension 3.x (MC 1.12.2): the A–G dimensions, one profile each

> Appendix to [the reference-game research](../reference-games.md) (RLM-94), written on 2026-10-12 by an AI research agent. Local citations name their source (the Craft to Exile 2 1.1.3 instance and the jars it ships, the Mine and Slash and Advent of Ascension source trees, the CTE2 GitHub changelogs); those files are not in this repository. Mechanics and numbers only: never reuse names, assets or code of the reference games.


> **Citation shorthand.** `S12/` = `AoA 1.12.2 source: source/`, `L12/` = `AoA 1.12.2 source: lang/`, `C12/` = `AoA 1.12.2 source: changelogs/`, `D21/` = `AoA 1.21 source: data/aoa3/`, `C21/` = `AoA 1.21 source: changelogs/`, `RF/` = `repo: `. `CFG` = `S12/utils/ConfigurationUtil.java` (StructureConfig defaults about lines 900–1100, OreConfig lines 261–900). `SPAWN` = `S12/common/registration/EntitySpawnRegister.java#getDimensionSpawns` (lines 258–465), written as `w` = spawn weight. All numbers come from the local 1.12.2 source snapshot, whose changelogs end at 3.3.6, unless a line says otherwise. AoA's source is view-only: these notes record mechanics and numbers, never code or assets. The fandom wiki usually documents the 1.16.5-era 3.5+ mod. Its Crystevia page mentions respawn anchors, which do not exist in 1.12, so wiki-only facts are flagged as possibly version-specific.
>
> **Density method.** Each dimension's `populate()` rolls structures per chunk as `1/n` (n from `CFG`). Most structures sit in an `else if` chain (at most one chained structure per chunk). Rune shrines, the Eye Bulb Grotto and the Crystal Transfer Hut are rolled independently. Per-structure probability = `(1/n_i) · Π_{j<i}(1 − 1/n_j)`. "Per km²" = per 1,000 × 1,000 blocks = 3,906 chunks. Every placement also needs a valid spot (top block, air, water), so all counts are **upper bounds**.

## 1. Which A–G dimensions exist, and how do they compare?

### Takeaway
The 1.12.2 source has exactly 11 dimensions from A to G: Abyss, Ancient Cavern, Barathos, Candyland, Celeve, Creeponia, Crystevia, Deeplands, Dustopia, Gardencia and Greckon. The list in the brief is complete; the source also has `nether` and `overworld` folders, but those are vanilla worlds with AoA additions. Most of these dimensions are a single biome. Most roll one structure per 50–250 chunks and have no ambient music. Six of the eleven have no skylight, so they are permanently dark. Every one has at least one hostile-only roster or palette problem. Even so, each has a working loop: dimension materials → a boss-summon item → a boss altar inside a landmark structure.

### Cited Findings
- **List verified:** `S12/dimension/` contains `abyss, ancientcavern, barathos, candyland, celeve, creeponia, crystevia, deeplands, dustopia, gardencia, greckon` (A–G) plus `haven … voxponds` and `nether`/`overworld` folders — `AoA 1.12.2 source: source/dimension/`. The same 11 biomes are blacklisted as "non-overworld" in `SPAWN#getOverworldBiomes` (lines 498–526).
- **One biome per dimension:** every A–G generator writes a single biome ID into the whole chunk, e.g. `biomeArray[i] = Biome.getIdForBiome(BiomeRegister.DEEPLANDS)` — [S12/dimension/deeplands/ChunkGenDeeplands.java L111–116]; the same pattern holds in the other `ChunkGen*` classes, each with one `biomes/Biome*.java` file.
- **Shared portal, non-consumable key:** a portal frame is 13 Ancient Rock plus exactly one each of the Carved Runes of Travel, Reality, Space and Direction. Its interior is 3 wide × 4 tall — [S12/library/misc/AoAPortalFrame.java]. Right-clicking a carved rune with a dimension's Realmstone lights the portal. The code never shrinks the realmstone stack, and relighting an existing portal prints "The portal remains lit, but it doesn't seem to require your key again." — [S12/block/functional/misc/CarvedRunicPortalBlock.java L26–46], [L12/en_us.lang `message.feedback.teleporterFrame.existing`]. Each dimension has its own activation line, e.g. Deeplands "A cool chill comes upon you as your feet start to feel wet." — [L12/en_us.lang `message.feedback.teleporterFrame.*`].
- **Realmstone acquisition ("Deity Challenges", charge a Blank Realmstone):**

  | Dimension | Challenge |
  |---|---|
  | Abyss | "Summon a Wither in The Nether" |
  | Ancient Cavern | "Kill 5 Different Crystal Constructs in 30 Seconds" |
  | Barathos | "Kill a Husk With a Blank Realmstone" |
  | Candyland | "Eat a Heart Fruit in Precasia" |
  | Celeve | "Kill a Clown With a Lelyetian Greatblade" |
  | Creeponia | "Survive a Blast From a Creeper With a Primed TNT Nearby" |
  | Crystevia | "Use a Deep Crystal on a Case Construct" |
  | Deeplands | "Mine an Ore Block Down at Bedrock Level" |
  | Dustopia | "Summon Shadowlord While Night Vision is Active" |
  | Gardencia | "Throw a Blank Realmstone Into a Carrot Crop in Precasia, Then Defeat Your Creation" |
  | Greckon | "Use Some Nightmare Flakes on Elusive" |

  Source: [L12/en_us.lang L1703–1727 `gui.realmstoneMenu.hover.*`, title "Deity Challenges"], [L12/en_us.lang L2084–2085 Blank Realmstone "Needs to be charged by the gods"].
- **Tiers (wiki, 3.5+ era):**

  | Tier | Dimension (prerequisite) |
  |---|---|
  | 1 | Barathos, Creeponia, Deeplands (all from the Overworld) |
  | 2 | Abyss (Nether), Candyland (Precasia), Celeve (Lelyetia), Crystevia (Deeplands), Gardencia (Precasia) |
  | 3 | Dustopia, Greckon (both Abyss) |

  The wiki adds: "The only order to dimensions is the tier system… the player is free to visit the dimensions in any order". Ancient Cavern is not listed: 3.5 removed it from the realmstone GUI — [wiki Dimensions](https://adventofascension.fandom.com/wiki/Dimensions), [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
- **Light and sky:**
  - No skylight (`hasSkyLight=false`, permanently dark without block light): Abyss, Ancient Cavern, Crystevia, Deeplands, Dustopia, Greckon.
  - Skylight with a fixed celestial angle of 0.0 (permanent noon): Barathos, Candyland, Celeve, Creeponia, Gardencia.
  - Thick distance fog (`doesXZShowFog=true`): Abyss and Barathos only.
  - Every provider returns `canDoRainSnowIce=false` (no snow or ice forms), and every biome except Gardencia calls `setRainDisabled()`. No respawning in any of the 11 (`canRespawnHere=false`).
  - Source: [S12/dimension/*/WorldProvider*.java], [S12/dimension/*/biomes/Biome*.java].
- **No ambient music:** all 11 `WorldProvider#getMusicType()` return `Enums.NULL_MUSIC`, a custom "null" music type — [S12/dimension/*/WorldProvider*.java], [S12/library/Enums.java L16]. The only dimension-specific tracks in `SoundsRegister` are boss themes (`KROR_MUSIC`, `CRYSTOCORE_MUSIC`, `SHADOWLORD_MUSIC`, …) — [S12/common/registration/SoundsRegister.java L806–835].
- **Structure density** (upper bounds; method in the header):

  | Dimension | Per 1,000 chunks | Per km² | 1 per N chunks | Boss-altar structures per km² | NPC structures per km² |
  |---|---|---|---|---|---|
  | Abyss | 18.8 | 73 | 53 | Illusion Tree 6.5 + Shadowlord Platform 9.2 | Lotto Hut 8.8 |
  | Barathos | 5.4 | 21 | 184 | Baroness Arena 2.8 + Hive Nest 4.3 | Baroness House (lottoman) 4.0 |
  | Candyland | 8.0 | 31 | 125 | none (altar not placed by any structure) | Lotto Platform 6.5 |
  | Celeve | 6.4 | 25 | 155 | Gyro Platform 4.0 | Lotto Balloon 6.0, Toy Tower 6.5 |
  | Creeponia | 11.4 | 45 | 88 | Creeper HQ 5.6 | Lotto Stand 13, Explosives Tower 13, Bank 12.9 |
  | Crystevia | 24.7 (2.5 without huts) | 96 (9.8 without huts) | 40 (400 without huts) | Power Station ≈2.4 | Trading Post ≈2.4, Lotto Overlook ≈2.4 |
  | Deeplands | 6.1 | 24 | 163 | Kror Pillars 6.1 | Lotto Shelter 6.1 |
  | Dustopia | 22.6 | 88 | 44 | Primordial Shrine 11.2 | Village 19.2, Lotto Cage 19.3 |
  | Gardencia | 11.4 | 45 | 88 | Floro Castle 2.3 | Lotto Sky Flower 7.8 |
  | Greckon | 10.5 | 41 | 95 | none (boss summoned anywhere with an idol) | Lotto Rock 7.8 |

  Computed from `CFG` StructureConfig defaults and the `populate()` chains in [S12/dimension/*/ChunkGen*.java].
- **Decoration attempts per chunk:**

  | Dimension | Small plants | Trees, large plants | Other |
  |---|---|---|---|
  | Abyss | 20 Tubeicles + 4 shrub features | 2 trees from 11 types | — |
  | Barathos | 0 | 0 | 1 small + 1/15 large "Baron Rock" |
  | Candyland | 100 candy grass + 10 candy features | 1 from 12 types | 1/3 chocolate bar |
  | Celeve | 60 | 2 Celevus trees | — |
  | Creeponia | 60 | 7 Creep trees | — |
  | Crystevia | 15 crystal plants | ≈1.8 crystal clusters (loop bound re-rolled each iteration) | 1/10 ceiling water source |
  | Deeplands | 30 plants, only at y = 20 | 1/3 Deepshroom | 1/25 Deep Spire, 1/6 fossil, 1 Deep Crystal lamp |
  | Dustopia | 33 | 5 Dawnwood trees | 1/5 Dawn Cage |
  | Gardencia | 59, only above y 67 | ≈1 giant flower | 2 lakes |
  | Greckon | 15 Haunted Flowers | 1 Haunted Tree | 1/4 lake |

  Source: the `Biome*Decorator` inner classes in [S12/dimension/*/biomes/Biome*.java].
- **Spawn rosters:**

  | Dimension | Natural hostile types (`SPAWN`) | Structure- or trap-only hostiles | Passive or ambient | NPCs |
  |---|---|---|---|---|
  | Abyss | 7 | 2 | Shining Pixon | Butchery Master, Undead Herald, Abyssal Lottoman |
  | Barathos | 11 | none (Baron Castle adds a Tharafly spawner; Hive Workers are Hive King minions) | none | Creation Master, Herald, Baron Lottoman |
  | Candyland | 5 | 3 | 2 snails + Ambient Pixon | Herald, Candied Lottoman |
  | Celeve | 9 | 0 | none | Innervation Master, Herald, Celevian Lottoman, Toy Merchant |
  | Creeponia | 9 (incl. the vanilla creeper) | 0 | Creep Cow | Herald, Lottoman, Explosives Expert, Creep Banker |
  | Crystevia | 7 | 0 | none | Herald, Crystal Lottoman, Crystal Trader |
  | Deeplands | 7 | Arocknid, Nipper | Shik | Extraction Master, Herald, Rocky Lottoman |
  | Dustopia | 8 | 3 + Dust Strider | Radiant Pixon | Herald, Dustopian Lottoman, 5 Primordial villagers |
  | Gardencia | 7 | 3 | none | Herald, Floro Lottoman |
  | Greckon | 7 | 1 | none | Infusion Master, Herald, Haunted Lottoman |

  Source: [SPAWN L300–451], structure entity spawns in [S12/structure/*/].
- **Undead Herald:** this w1 wandering trader appears in every dimension. It trades 10 dimension tokens for that dimension's banner (e.g. 10 Deeplands Tokens → Deep Banner) — [S12/entity/npcs/trader/EntityUndeadHerald.java].
- **Lottomen:** each dimension's lottoman sells loot boxes for that dimension's tokens. Deeplands and Crystevia use identical prices: 28 tokens → Lotto Totem, 15 → Weapons Case, 10 → Rune Box, 21 → Treasure Box — [S12/entity/npcs/lottoman/EntityRockyLottoman.java], [EntityCrystalLottoman.java].
- **Owner context:** the vision lists "older versions of Advent of Ascension (MC 1.12; the newer versions are terribly cut down)" as an inspiration — [RF/docs/vision.md L113].

### Inferences
- The structure counts are modest, and the structures are mostly single-purpose: altars, lotto stands, spawner huts. The densest dimensions (Dustopia, Crystevia) get their numbers from one repeated cheap structure (Lotto Cage, Transfer Hut).
- Almost every dimension has exactly one biome, one ground block and one music state (silence). Inside a dimension the player sees the same thing everywhere. This matches the "empty" complaints in section 13 better than raw structure counts do.

### Gaps
- `MUSIC_NULL` is the custom sound behind `NULL_MUSIC`. Its asset is not in the repo, so silence is likely but not verified.
- Spawn caps, light rules and AoA's custom spawn checks were not traced. The tables give spawn-list composition, not observed mob density.

## 2. Abyss

### Takeaway
A tier-2 "evil" dimension: dark, red-fogged, hilly wasteland with flesh, eye and tentacle flora. It is fairly dense in structures (2 bosses, 2 spawner huts) and has one harvestable passive creature. Players lump it with Dustopia and Greckon as interchangeable "dark place with scary mobs you have a hard time seeing".

### Cited Findings
- **Terrain:** overworld-style noise generator with Abyss Grass on Abyss Stone, Dimensional Fabric at y 0, sea level 0 (no seas), plus a custom cave carver — [S12/dimension/abyss/ChunkGenAbyss.java], [CaveGenAbyss.java], [biomes/BiomeAbyss.java]. Biome name "Abyssal Wastelands"; types DEAD, COLD, WASTELAND, SPOOKY; red water and sky — [BiomeAbyss.java].
- **Light and atmosphere:** no skylight; red fog (R 0.414, G 0.021, B 0.03 × factor); thick XZ fog on — [S12/dimension/abyss/WorldProviderAbyss.java].
- **Flora (per chunk):** 20 Tubeicles; 4 rolls of Blood Pine (2/5), Eye Shrub (2/5) or Blood Spikes (1/5); 2 tree rolls among 7 Tentacle Trees, Bloodtwister, Shadow Tree, Eyebush and Eye Hanger — [BiomeAbyss.java#BiomeAbyssDecorator]. The red tentacle-eye wood drops a **Staring Eye** 5% of the time, the Elusive summon item — [S12/block/generation/wood/TentaclesEyeRed.java L18].
- **Hostile mobs (w20 unless noted):**
  - Apparition: 82 HP, Wither on hit.
  - Bloodsucker: 109 HP, Slowness III and heals 2× its damage.
  - Distorter: 95 HP, 0 damage. Pulses Slowness and Speed on players within 2 blocks.
  - Fiend: 90 HP, Blindness III.
  - Occulent: 98 HP. Randomly spawns illusory "mirage" copies that die when hit.
  - Web Reaper: 107 HP, ranged. Each Nightmare Flake raises its "stage" (up to 10) and fully heals it. At stage > 1, right-clicking it with an enchanted book turns the book into the **Book of Shadows** and adds +5 stages. From stage 15 its shots inflict Wither II.
  - Slimer: w1, 120 HP, Poison XVI.
  - Structure-only: Flesh Eater (95 HP, Hunger XVI; Flesh Temple spawner), Jawe (93 HP; Jawe Hut spawner).
  - Source: [SPAWN L311–320], [S12/entity/mobs/abyss/*.java], [L12/en_us.lang `advancement.aoa.abyss.the_dark_book`].
- **Non-hostile:** Shining Pixon (creature, w12, groups 1–3). "When siphoned with a stone or diamond bowl, they drop … shining infusion stones" — [SPAWN L311], [L12/bestiary/en_us/shining_pixon.txt]. Butchery Master (w1) and Undead Herald (w1) — [SPAWN L312–313]. 3.5 later added Anemia as a natural spawn — [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
- **Structures:**
  - Illusion Tree: 19×80×20, holds the Illusion Altar; 1/600.
  - Jawe Hut: 11×10×11, Jawe spawner; 1/520.
  - Flesh Temple: 13×10×13, Flesh Eater spawner; 1/520.
  - Abyssal Lotto Hut: floats 20 above ground, Abyssal Lottoman; 1/440.
  - Shadowlord Platform: 5×7×5, floats 5–9 above ground, Shadow Altar plus bloodstone lamps; 1/420.
  - Eye Bulb Grotto: independent roll, 1/140.
  - Wither Rune Shrine: floats +12, a Runation rune post; 1/650.
  - None contains loot chests.
  - Source: [ChunkGenAbyss.java#populate], `CFG` SubCategoryAbyss, [S12/structure/abyss/*.java]. AbyssalArch, AbyssalTree, ShadeTree, ShadeBush, EyeCane and BulbStock templates exist in `S12/structure/abyss/`, but no code outside the structure package names them, so they are apparently unused.
- **Bosses:**
  - **Shadowlord:** 2000 HP, flying, explosive projectiles. Summoned by using the Book of Shadows on a Shadow Altar. Summoning it while under Night Vision and holding a Blank Realmstone also yields the **Dustopia Realmstone** — [S12/entity/boss/shadowlord/EntityShadowlord.java L82], [S12/block/functional/altar/ShadowAltar.java].
  - **Elusive:** 2000 HP, 15 damage. Spawns a clone every 300 ticks and teleports. Clones (30 HP) blind the player and explode on death. Summoned with a Staring Eye on the Illusion Altar, which spawns Elusive plus a clone about 10 blocks behind the player — [S12/entity/boss/elusive/*.java], [S12/block/functional/altar/IllusionAltar.java].
- **Materials:** Bloodstone ore: 4 veins/chunk × 6–12 blocks at y 45–49 in Abyss Stone — [BiomeAbyss.java#doOreGen], `CFG` Bloodstone.
- **Access and role:** tier 2 (after the Nether); key from summoning a Wither in the Nether. Leads to Dustopia (via Shadowlord) and Greckon (Nightmare Flakes on Elusive) — [L12/en_us.lang L1703, L1711, L1713], [wiki Dimensions](https://adventofascension.fandom.com/wiki/Dimensions).
- **Perception:**
  - "Dimensions like Greckon, Dustopia, and the Abyss all fill this niche of…" an "evil dark place with scary mobs you have a hard time seeing"; the issue proposes merging two of the three — [GitHub issue #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
  - A responder agreed with this merge only: "Abyss is tier 2 while Greckon/Dustopia are tier 3" — [same issue](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
- **1.21:** "primer worldgen" only (3.7.7). The `abyssal_valley` biome has no spawns or features yet — [C21/3.7.7.md L12–18], [D21/worldgen/biome/abyssal_valley.json].

### Inferences
- Abyss is the most "full" of the dark dimensions: weird flora at 26 attempts/chunk, the second-highest structure rate, a passive creature with a gathering use. It still reads as samey: everything is dark, red-fogged and hostile.
- The boss items come from local interactions (eye wood drops, empowering a mob). That ties the bosses to exploring the dimension.

### Gaps
- Shadowlord's and Elusive's drop tables are not in the repo (1.12.2 loot JSONs absent); the wiki boss pages were not fetched.

## 3. Ancient Cavern

### Takeaway
Not a world but a **single hand-built boss complex** in a void dimension: rooms 1–6 plus four deity boss rooms. It is the end-game arena for four 3000–4000 HP "champions", gated by skill level 70 and 200 deity tribute. It is the closest thing in AoA 1.12 to a persistent "boss-rush instance".

### Cited Findings
- **Generation:** `populate()` returns unless the chunk is (0,0). It then builds `AncientCavern` once at (−20, 15, −20) from Rooms 1–6 and the Coniferon, Horon, Goldorth and Penumbra rooms. The code logs that it will cause "cascading worldgen" warnings and that "It only generates once" — [S12/dimension/ancientcavern/ChunkGenAncientCavern.java#populate], [S12/structure/ancientcavern/AncientCavern.java]. Every arrival lands at (0, 18, 0) — [AncientCavernTeleporter.java#findExistingPortal].
- **World:** VOID biome; top and filler blocks are air; `getSpawningChance()=0`; black fog; no skylight — [biomes/BiomeAncientCavern.java], [WorldProviderAncientCavern.java].
- **Contents:**
  - Room 3: Bloodsucker and Flowerface spawners.
  - Room 5: Angelica and Rock Critter spawners.
  - Room 6: the exit portal (Ancient Cavern Portal).
  - All rooms: Ancient Light blocks.
  - Deity shrines: one in each boss room, plus Pluton and Selyan shrines in Room 1 and Erebon and Luxon shrines in Room 6. The shrine code rejects activation outside certain coordinates, so only the shrines in the right rooms summon.
  - Source: [S12/structure/ancientcavern/AncientCavern*Room*.java], [S12/block/functional/utility/AncientCavernShrine.java].
- **Bosses (summoned at deity shrines):**

  | Boss | Deity | Requirement | Stats and mechanics |
  |---|---|---|---|
  | Penumbra | Erebon | Hunter 70 + 200 Erebon tribute | 3000 HP, ranged 18. Each hit summons a random mob from other dimensions. |
  | Horon | Luxon | Runation 70 + 200 Luxon tribute | 3000 HP, 24 dmg. Drains energy, creation and soul resources; random explosions; occasional Weakness IV. |
  | Goldorth | Pluton | Foraging 70 + 200 Pluton tribute | 3000 HP, 17 dmg, speed 0.329. |
  | Coniferon | Selyan | Infusion 70 + tribute (assumed 200, not checked) | 4000 HP, 25 dmg. Slowness on hit; periodic poison with an overlay. |

  Source: [S12/block/functional/utility/AncientCavernShrine.java], [S12/entity/boss/{penumbra,horon,goldorth,coniferon}/*.java].
- **Access:** "Kill 5 Different Crystal Constructs in 30 Seconds" (Crystevia mobs) — [L12/en_us.lang L1704]. A changelog moved the Ancient Cavern realmstone drop "to Angelicas from Automatons" — [C12/ changelogs, grep "Ancient Cavern realmstone"]. 3.5 removed it from the realmstone GUI — [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
- **Advancement flavour:** root "Challenger's Arena"; bosses are "champion of Pluton/Selyan/Luxon/Erebon" — [L12/en_us.lang `advancement.aoa.ancientcavern.*`].
- **Perception:**
  - TV Tropes files it under "Forced Level-Grinding" (each boss needs skill 70) and calls Penumbra a "Flunky Boss" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension).
  - Issue #1895 says "Ancient cavern has great bosses that could easily fit in" the Deeplands — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
  - "Fixed the Ancient Cavern bosses not dropping loot" — [C12/ changelog line].
- **1.21:** no `ancient_cavern` dimension in `D21/dimension/` — [D21/dimension/].

### Inferences
- Structurally this is a fixed boss hub, shared by all players and generated once. Realmforge's plot-based instances solve its weak points: one copy for the whole server and cascading worldgen.
- Requiring 70 in an unrelated skill makes access depend on grinding a skill that has nothing to do with the boss.

### Gaps
- Room layouts and sizes are written with a different builder (the `addBlock` count is 0), so room dimensions were not measured. Boss drop tables are not in the repo.

## 4. Barathos

### Takeaway
A tier-1 hot, barren "baron" desert. It has permanent noon, fog, a hellstone underworld layer, 11 mob types and 2 bosses, but **zero vegetation** and very sparse structures (1 per ~184 chunks). Players say it "feels like more of a template than it's own dimension". The 1.21 rework turned it into the best AoA template for a desert realm with oases.

### Cited Findings
- **Terrain:** overworld-style noise with Baron Ground on Baron Stone and a Dimensional Fabric floor. Baron Stone below y≈25–28 is converted to **Barathos Hellstone**, and caves are carved through Baron Stone — [S12/dimension/barathos/ChunkGenBarathos.java L113–130], [CaveGenBarathos.java].
- **Biome:** types HOT, SPARSE, DEAD, WASTELAND, DRY; temperature 2.0; orange sky — [biomes/BiomeBarathos.java].
- **Light and atmosphere:** skylight on; celestial angle fixed at 0 (permanent noon); brown-orange thick XZ fog — [WorldProviderBarathos.java].
- **Decoration:** no plant or tree generation at all. Per chunk: 1 Small Baron Rock (4 variants) and 1/15 large Baron Rock (8 variants, up to 15×13×11) — [BiomeBarathos.java#doMiscGen].
- **Mobs (`SPAWN` L322–334):**
  - Cryptid (w30): y<27; sets fire when standing on hellstone.
  - Arkback (w20): 200 HP, armour 6.
  - Echodar (w20): flying; y<20.
  - Eilosapien (w1): screen overlay on hit.
  - Nospike (w1).
  - Emperor Beast (w20): 150 HP; Nausea plus knock-up.
  - Parasect (w20): y<50; Nausea V.
  - Keeler (w20): y<50. Fully heals whenever it drops below 8 HP, so it must be finished in one hit.
  - Squiggler (w1): y<27; ignites its target within 20 blocks.
  - Tharafly (w15): flying.
  - Ramradon (w20): heals 15 when its target is below 50% HP.
  - Source: [S12/entity/mobs/barathos/*.java].
- **Non-hostile:** Creation Master (w1), Undead Herald (w1); no animals — [SPAWN L322–323].
- **Structures:**
  - Baroness Arena: 31×29×31, Baroness Altar; 1/1400.
  - Hive Nest: 19×12×19, Hive Spawner; 1/900.
  - Baron Castle: 14×37×15, Tharafly spawner, Mineralization Station, **8 loot chests**; 1/950.
  - Baroness House: 20×19×19, Baron Lottoman; 1/975.
  - Power Rune Shrine: +20 in the air; 1/650.
  - Source: [ChunkGenBarathos.java#populate], `CFG` SubCategoryBarathos, [S12/structure/barathos/BaronCastle.java L2581 `assignLootChests`].
- **Bosses:**
  - **Baroness:** 2000 HP, ranged 8. Throws bombs, has stage changes and invulnerability windows. Summoned with a Warlock Gem on the Baroness Altar — [S12/entity/boss/baroness/EntityBaroness.java], [S12/block/functional/altar/BaronessAltar.java].
  - **Hive King:** 2500 HP, 20 dmg. Grows from 0 to 100% before it fights; damage taken while it grows makes it despawn. Summoned with a Hive Egg on the Hive Spawner. A Hive Egg is made by hitting a Tharafly with a Hive Chunk — [S12/entity/boss/hiveking/EntityHiveKing.java], [S12/block/functional/altar/HiveSpawner.java], [S12/item/misc/HiveChunk.java].
- **Materials:** Varsium (2 veins × 4–8, y 25–62), Baronyte (3 × 4–8, y 25–34), Blazium (2 × 4–8 in hellstone, y 0–19) — [BiomeBarathos.java#doOreGen], `CFG`.
- **Access and role:** tier 1; "Kill a Husk With a Blank Realmstone". Prerequisite of Iromine — [L12/en_us.lang L1705], [wiki Dimensions](https://adventofascension.fandom.com/wiki/Dimensions).
- **Perception:**
  - "Barathos could benefit from some more personality, it feels like more of a template than it's own dimension"; the issue suggests L'Borean-style oases — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
  - A responder suggested "adding oases to Barathos" — [same](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
- **1.21 rework (shipped):**
  - Four biomes: `barathos_flats`, `barren_oasis`, `the_burning_pits`, `the_spears`.
  - Flats spawn Emperor Beast (w25) and Arkback (w100) as **creatures** and Tharafly as a monster.
  - Features: loose sand patches, "false baron oasis", "baron fossil sites", "dried oasis lake", "hellstone lava lake", fire patches, "baron stone pillars", three shrub types.
  - Structure `baron_resource_pit` (random spread, spacing 28, separation 20).
  - A **world event system** whose first event is a Barathos **sandstorm**.
  - Source: [D21/dimension/barathos.json], [D21/worldgen/biome/barathos_flats.json, barren_oasis.json, the_burning_pits.json, the_spears.json], [D21/worldgen/structure_set/baron_resource_pits.json], [C21/3.7.x changelogs "Added a world event system … sandstorm in Barathos"].

### Inferences
- The 1.12 version fails on identity: one biome, no plants, rocks as the only decoration. The 1.21 version fixes this almost exactly as the community asked, with sub-biomes, (dried and false) oases, fossils, pillars, a lava-pit zone and a weather event. That is direct evidence for the owner's "one style, many sub-biomes" realm rule.

### Gaps
- 1.12 Barathos loot-chest contents and boss drops are not in the repo.

## 5. Candyland

### Takeaway
A tier-2 candy world: flat, bright, permanent noon. It has heavy prop decoration (canes, lollipops, chocolate bars) and real passive life (two snails and a pixon). Every hostile is candy that explodes or shoots. It is memorable for its theme, but "an assault on the eyes", and its boss altar does not generate in the world.

### Cited Findings
- **Terrain and light:** overworld-style generator with Candyland Grass on Candyland Dirt; sea level 63 (seas and water); skylight; noon fixed; pink fog; yellow sky; reddish water — [S12/dimension/candyland/ChunkGenCandyland.java], [WorldProviderCandyland.java], [biomes/BiomeCandyland.java].
- **Water:** candied water slows players, and Gardencian mobs soaked in it can be killed for the L'Borean realmstone. Changelogs: "Fixed candyland sticky water message appearing in spectator", "Fixed water in Candyland still slowing players" — [C12/ changelogs], [L12/en_us.lang L1717].
- **Decoration (per chunk):**
  - 100 Candy/Blue Candy Grass.
  - 10 rolls of Candy Tube, Candy Stack, Green or Red Peppermint Stack, or a Candycane block.
  - 1 "tree" roll among 4 Candy Canes, 2 Cotton Candy Trees, 2 Aqua Cotton Candy Trees, 2 Lollypops and 2 Swirl Pops.
  - 1/3 chance of a (white, dark) chocolate bar.
  - Source: [BiomeCandyland.java#BiomeCandylandDecorator].
- **Mobs:**
  - Airhead (w20): flying, 2 HP, ranged 14.
  - Candy Corny (w20): 83 HP.
  - Cherry Barrager (w1): 130 HP, explosive shots.
  - Lollypopper (w20): explodes on death.
  - Cherry Blaster (w20): explosive shots.
  - Structure-only: Gingerbird (Aviary spawner), Gingerbread Man (house spawner), Cane Bug (Infested Candy Cane spawner).
  - Most heal 0.4/tick in water.
  - Source: [SPAWN L336–343], [S12/entity/mobs/candyland/*.java].
- **Non-hostile:** Peppermint Snail and Spearmint Snail (creatures, w20, 25 HP); Ambient Pixon (w12, 1–3). TV Tropes: "the only passive mobs are slugs" — [SPAWN L342–343, L424], [S12/entity/passive/EntityPeppermintSnail.java], [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension).
- **Structures:**
  - Cotton Candy Tower: 9×20×9; 1/850.
  - Gingerbread House: 10×11×10, Gingerbread Man spawner, 2 loot chests; 1/740.
  - Gingerbird Aviary: 10×12×10, spawner; 1/700.
  - Candy Lotto Platform: 13×12×13, Candied Lottoman; 1/600.
  - Infested Candy Cane: 12×8×12, Cane Bug spawner; 1/420.
  - Source: [ChunkGenCandyland.java#populate], `CFG`, [S12/structure/candyland/GingerbreadHouse.java L439].
- **Boss, Cotton Candor:** 3000 HP, flying, ranged 35 with 5-power explosions. Changes stage every 100 ticks. Summoned with a Treat Bag on a **Candy Block**. No Candyland structure places the Candy Block — [S12/entity/boss/cottoncandor/EntityCottonCandor.java], [S12/block/functional/altar/CandyBlock.java]; grep of `S12/structure/` finds no CANDY_BLOCK. TV Tropes: he "can be harmed only by a handful of magic staves" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension).
- **Access:** tier 2 (prerequisite Precasia); "Eat a Heart Fruit in Precasia" — [L12/en_us.lang L1706], [wiki Dimensions](https://adventofascension.fandom.com/wiki/Dimensions).
- **Perception:**
  - "I'm sorry but candyland is an assault on the eyes and Celeve is a monstrosity"; the issue proposes merging both into a theme-park dimension — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
  - A responder disagreed that Candyland is empty — [same](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
- **1.21:** no Candyland dimension in `D21/dimension/` — [D21/dimension/].

### Inferences
- Candyland shows that dense, varied props (5+ object families placed every chunk) plus a few harmless creatures make a dimension feel inhabited even with few structures. The cost is a saturated single palette.

### Gaps
- No Candy Block or Treat Bag recipe in the repo (recipes are JSON assets).

## 6. Celeve

### Takeaway
A tier-2 clown/toy "circus" on End-style floating islands, permanently at noon. Nine clown mobs, no passive life, few structures (1 per ~155 chunks); the boss is bought from an NPC. Players call it "a monstrosity".

### Cited Findings
- **Terrain:** floating islands over the void: a custom noise field plus `FloatingDimChunk`, a chunk class with its own skylight map for empty or partial chunks; End-like in shape, by inference. Celeve Grass on Celeve Dirt; biome types MOUNTAIN and MAGICAL — [S12/dimension/celeve/ChunkGenCeleve.java L79–190], [S12/dimension/FloatingDimChunk.java], [biomes/BiomeCeleve.java]. Structures are placed in mid-air: the Gyro Platform at y 40–59, the Lotto Balloon at y 10–29 — [ChunkGenCeleve.java#populate].
- **Light:** skylight; noon fixed; dark-red fog; green sky — [WorldProviderCeleve.java], [BiomeCeleve.java].
- **Decoration:** 60 plant rolls per chunk (blue, red, white and purple Celevians, plus the Celebulb) and 2 Celevus tree rolls — [BiomeCeleve.java#BiomeCeleveDecorator].
- **Mobs (w20 unless noted):**
  - Bobo: 95 HP, launches its target upward.
  - Chocko.
  - Jumbo (w2): 100 HP, 10.5× horizontal knockback.
  - Koko: Blindness.
  - Kranky.
  - Snappy.
  - Sticky: Slowness.
  - Stitches.
  - Tipsy.
  - Source: [SPAWN L394–404], [S12/entity/mobs/celeve/*.java].
- **Non-hostile:** Innervation Master (w1), Undead Herald (w1). No animals. 3.5 later made the "Clown" (renamed Happy) spawn in Celeve — [SPAWN L394–395], [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
- **Structures:**
  - Gyro Platform: 19×9×19, floats at y 40–59, Toy Box altar; 1/980.
  - Celevian Lotto Balloon: 9×18×9 at y 10–29; 1/650.
  - Toy Tower: 11×46×11, **Toy Merchant** NPC; 1/600.
  - Compass Rune Shrine: 1/450, independent.
  - Source: [ChunkGenCeleve.java#populate], `CFG`, [S12/structure/celeve/*.java].
- **Toy Merchant trades:** 5 Circus Coins → 10 Balloons; 46 → Gravitator; **15 → Toy Gyrocopter**; 128 → Smiley Upgrade Kit; 15 → Confetti Cluster; 25 → Balloon Bomber — [S12/entity/npcs/trader/EntityToyMerchant.java].
- **Boss, Gyro:** 2500 HP, flying ranged. Using a Toy Gyrocopter on the Toy Box releases a gyrocopter that rises for 200 ticks and turns into Gyro — [S12/entity/boss/gyro/EntityGyro.java, EntityGyrocopter.java], [S12/block/functional/altar/ToyBox.java].
- **Access:** tier 2 (prerequisite Lelyetia); "Kill a Clown With a Lelyetian Greatblade". 3.5 changed it to a Lelyetian warrior killed with an exploding firework — [L12/en_us.lang L1707], [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
- **Perception:** "Celeve is a monstrosity"; "candy and clowns just screams for a theme park dimension" — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895). Advancement title "Funny, or Scary?" — [L12/en_us.lang `advancement.aoa.celeve.root.title`].
- **1.21:** primer worldgen; fixed biome `floating_big_top` with an empty generator (`aoa3:empty`) — [D21/dimension/celeve.json], [C21/3.7.7.md].

### Inferences
- Celeve is the only A–G sky-island dimension. A boss whose summon item is sold for a local currency is a clean "NPC sells the key" loop.

### Gaps
- Island size and spacing were not measured from the noise.

## 7. Creeponia

### Takeaway
A tier-1 creeper forest: dense trees, permanent noon, black fog. Nine explosive mob types and a joke passive cow; the NPC density is the highest of any A–G dimension (bank, explosives shop, lotto). Players still say it has "a serious lack of mobs".

### Cited Findings
- **Terrain:** overworld-style generator with Creeponia Grass on Creeponia Dirt, sea level 0, green water. Caves are carved through Creep Stone, with **Primed Stone** and **Unstable Stone** layers — [S12/dimension/creeponia/ChunkGenCreeponia.java], [CaveGenCreeponia.java], [biomes/BiomeCreeponia.java]. Biome types COLD and FOREST; yellow sky — [BiomeCreeponia.java].
- **Light:** skylight; noon fixed; **black** fog colour (0, 0, 0) — [WorldProviderCreeponia.java].
- **Decoration:** 60 Creep Grass or Creep Flowers and **7 Creep Tree rolls per chunk** (the densest tree generation of A–G) — [BiomeCreeponia.java].
- **Mobs:**
  - Bone Creeper (w40).
  - Cave Creepoid (w25): y<30; 1/3 chance to explode when hit.
  - **Vanilla Creeper** (`net.minecraft…EntityCreeper`, w40).
  - Creeperlock (w25): teleports onto the player 1/120 ticks; explosive shots.
  - Creepird (w15): flying, y>50. Poisoning it while holding a Blank Realmstone gives the Mysterium realmstone.
  - Creepuple (w40).
  - King Creeper (w1).
  - Magical Creeper (w25): magic shots.
  - Winged Creeper (w30): leaps toward players above it.
  - Source: [SPAWN L412–421], [S12/common/registration/EntitySpawnRegister.java L5 import], [S12/entity/mobs/creeponia/*.java].
- **Non-hostile:**
  - Creep Cow (creature w5, 20 HP). Milking it with a bucket explodes it; the advancement is "Worst. Farmer. Ever." — [SPAWN L422], [S12/entity/animals/EntityCreepCow.java], [L12/en_us.lang `advancement.aoa.creeponia.worst_farmer_ever`].
  - Explosives Expert: sells grenades, TNT and fireworks; Omni armour for Gemenyte + Unstable Gunpowder — [S12/entity/npcs/trader/EntityExplosivesExpert.java].
  - Creep Banker (coin bank) — [S12/structure/creeponia/CreeponiaBank.java].
- **Structures:**
  - Creeper HQ: 24×56×24 tower with C.R.E.E.P altars; 1/700.
  - Creeponian Lotto Stand; 1/300.
  - Explosives Tower: 8×29×8; 1/300.
  - Creeponia Bank: 12×16×12; 1/300.
  - Source: [ChunkGenCreeponia.java#populate], `CFG`.
- **Boss, C.R.E.E.P. ("Creepy Raging Entity of Extreme Power"):** 3000 HP. Lobs Creep Bombs (2.5× projectile damage) about every 10 ticks. Summoned with Explosive Gems on a ring of C.R.E.E.P altars (the code checks the four adjacent altar blocks) — [S12/entity/boss/creep/EntityCreep.java], [S12/block/functional/altar/CreepAltar.java], [L12/en_us.lang `advancement.aoa.creeponia.fizzled_out.desc`].
- **Materials:** Ornamyte (4 veins × 2–6, y 3–14), Gemenyte and Jewelyte (4 × 2–6 each, y 22–36), all in creep, primed or unstable stone — [BiomeCreeponia.java#doOreGen], `CFG`.
- **Access:** tier 1; "Survive a Blast From a Creeper With a Primed TNT Nearby". Prerequisite of Mysterium and Vox Ponds — [L12/en_us.lang L1708], [wiki Dimensions](https://adventofascension.fandom.com/wiki/Dimensions).
- **Perception:** "that place has a serious lack of mobs", though the issue calls the style unique — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895). TV Tropes "Action Bomb": "just about every Creeponia mob" explodes — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension).
- **1.21:** not present in `D21/dimension/`.

### Inferences
- The roster is large by weight (total hostile weight 251) but one-note: everything is a creeper variant. "Lack of mobs" most likely means a lack of *kinds of life*, not a low spawn rate.

### Gaps
- Primed and Unstable Stone behaviour (whether they explode when mined) was not read.

## 8. Crystevia

### Takeaway
A tier-2 **Nether-style cavern** in purple-fogged crystal stone (no lava, almost no water). It is very ore-dense: 24 gemstone veins per chunk in six colours. Crystal currency runs through a dense web of tiny "transfer huts" (≈87/km²). It has 7 construct mob types, **no passive life**, one trader and one boss. The economy loop is coherent but grindy; players called its boss "one of the most boring and uninteresting fights in the game".

### Cited Findings
- **Terrain:**
  - Nether noise (`InitNoiseGensEvent.ContextHell`), 17 vertical cells with a cosine vertical profile, solid crust near top and bottom. All stone is Crystevia Stone.
  - Dimensional Fabric at y ≤ 2 and y ≥ 125; height 128.
  - Sea level 0, so no lava or water sea.
  - 1/10 chunks: a water source is placed in the ceiling at y 110–119 and the column below is cleared, giving a ceiling waterfall.
  - Source: [S12/dimension/crystevia/ChunkGenCrystevia.java L79–307], [biomes/BiomeCrystevia.java#doMiscGen L199–215].
- **Light:**
  - No skylight; bright **magenta fog** (R 0.3, G 0, B 0.3 × factor 1.5–5) — [WorldProviderCrystevia.java].
  - The crystal-plant class sets no light level in this snapshot — [S12/block/…/CrysteviaCrystalPlant.java, grep `setLightLevel`]. Crystal block lighting changed twice. 3.1: "Gave crystevia crystal blocks a small light level" — [C12/3.1.md]. Then 3.1.2: "Removed luminosity from crystevia crystal blocks. This should improve performance in Crystevia greatly" — [C12/3.1.2.md].
  - Wiki (later version): ambient light 0.0, fixed time 18000, "Clocks and compasses spin wildly" — [wiki Crystevia](https://adventofascension.fandom.com/wiki/Crystevia).
  - TV Tropes: "rather dim in comparison to the Nether" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension).
- **Decoration:** 15 crystal-plant rolls per chunk (6 colours, on any stone surface found by scanning down from a random y 20–99), and ≈1.8 crystal-cluster rolls (6 shapes × 6 colours = 36 templates) — [BiomeCrystevia.java#doPlantGen, #doTreeGen].
- **Mobs (Constructs; w20 except Mind w2):**
  - Strength: 69 HP, 15 dmg.
  - Range: 70 HP, ranged 10.5.
  - Terror: flying ranged 9.5.
  - Speed: 58 HP, speed 0.31.
  - Resistance: 80 HP, armour 15.
  - Flight: flying melee.
  - Mind: 74 HP.
  - All regenerate 0.1 HP/tick.
  - Source: [SPAWN L345–352], [S12/entity/mobs/crystevia/*.java].
- **Non-hostile:** only the Undead Herald (w1) as a natural spawn. NPCs live only in structures: Crystal Trader (Trading Post) and Crystal Lottoman, who "hides up in crystal lotto overlooks, keeping distant from the hostile creations of the dimension" — [SPAWN L345], [L12/bestiary/en_us/crystal_lottoman.txt].
- **Structures:**
  - Crystal Transfer Huts: 11×10–16×11, one per colour, each with a **Crystal Creator**; independent 1/45 per chunk (≈22 per 1,000 chunks, ≈87/km²).
  - One "crystal building" roll at 1/400, split evenly among:
    - Crystal Extension Station: 11×12×11, Crystal Extension Shrine.
    - Crystal Lotto Overlook: 9×7×9, Lottoman.
    - Power Station: 7×5×7, Crystocore altar.
    - Crystal Trading Post: 9×3×9, Crystal Trader.
    - Each ≈2.4/km².
  - All placed in air at y 20–99; none has loot chests.
  - Source: [ChunkGenCrystevia.java#populate L309–385], `CFG` SubCategoryCrystevia, [S12/structure/crystevia/*.java].
- **Economy loop (1.12.2):**
  1. Gemstone ores: 6 colours × **4 veins × 9–15 blocks** per chunk at y 10–109, harvest level 4, 4–7 XP.
  2. Ore drops **Gemstones**.
  3. A Crystal Creator of the matching colour converts gemstones to **Crystals 1:1**.
  4. The Crystal Trader gives 1 crystal → 3 Crystevia Tokens; 16 crystals → a Druse of that colour; 12 Rainbow Druse → Crystal Maul or Crystal Greatblade.
  5. Crystal Extension Shrine: Rainbow Druse + 10 crystals within 5 blocks → **Giant Crystal**.
  6. Giant Crystal on the Power Station → **Crystocore**.
  - Source: [BiomeCrystevia.java#doOreGen], `CFG` (Blue…Yellow Crystal: 4 veins, 9–15), [S12/common/registration/BlockRegister.java L1462 `OreBlock("BlueGemstoneOre", …, 4, 4, 7)`], [S12/block/functional/utility/CrystalCreator.java], [S12/entity/npcs/trader/EntityCrystalTrader.java], [S12/block/functional/utility/CrystalExtensionShrine.java], [S12/block/functional/altar/PowerStation.java].
  - In later versions the transfer conversion became a 12.5% (1/8) chance per gemstone and the token trade became "1 crystal for 1 copper coin" — [wiki Crystevia](https://adventofascension.fandom.com/wiki/Crystevia), [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
  - "Crystevia's crystal plants now have a very small chance to drop Druse of its respective colour when broken" — [C12/ changelogs].
- **Boss, Crystocore:** 3000 HP, 15 dmg, flying melee. Every 220 ticks it switches colour state and applies a random debuff (poison, blindness, …) — [S12/entity/boss/crystocore/EntityCrystocore.java].
- **Access and role:**
  - Tier 2 (prerequisite Deeplands). Key: "Use a Deep Crystal on a Case Construct" while carrying a Blank Realmstone — [L12/en_us.lang L1709], [S12/entity/mobs/deeplands/EntityCaseConstruct.java#processInteract], [wiki Crystevia Realmstone](https://adventofascension.fandom.com/wiki/Crystevia_Realmstone).
  - Its constructs gate Ancient Cavern ("Kill 5 Different Crystal Constructs in 30 Seconds") and Immortallis ("Take a Hit From a Construct of Strength Affected by a Strength Buff") — [L12/en_us.lang L1704, L1715].
- **Perception:**
  - Issue "Crystevia Rework" (#543) calls the item chain tedious: several structures, many gemstones, 1/8 odds in that version. It calls Crystocore "one of the most boring and uninteresting fights in the game" and the rewards rarely used. It proposes buying crystals directly and replacing the Power Station with a 6-table "Crystal Charging Station" — [GitHub #543](https://github.com/Tslat/Advent-Of-Ascension/issues/543).
  - Advancement title "The Dazzling Underground" — [L12/en_us.lang].
- **1.21:** primer worldgen; single `crystal_caverns` biome with its own music track (`aoa3:biome.crystevian_caverns.music`) but no spawns or features yet — [D21/worldgen/biome/crystal_caverns.json], [C21/3.7.7.md].

### Inferences
- Crystevia has plenty of *resources* (24 ore veins/chunk is extreme) but no *life*: no animals, no ambient critters, only constructs. Its many structures are one repeated utility hut. The colour variety comes from six recolours of the same plant, cluster and ore.

### Gaps
- Exact Nether-noise cavern proportions (open volume share, shelf heights) were not simulated.

## 9. Deeplands

### Takeaway
A tier-1 enclosed cave world that maps closely onto Realmforge's planned first realm. It is a **single flat floor at y = 20** under a rock mass whose underside sits at roughly y 55–70, with a Dimensional Fabric ceiling at y 128–130. It is built from **one block type** ("Deep Rock"), lit only by one Deep Crystal per chunk, silent, and has 4 structure types (≈24/km² at most). Its resources sit in the ceiling rock **above** the walkable space, mixed with disguised trap blocks. One community voice called its look "amazing", but there is little to do there apart from Kror.

### Cited Findings
- **Terrain:**
  - Overworld-style noise with an **inverted** height variation (`heightVariation = −1` in the weight loop). Air is forced below the average height and solid above it.
  - Every block below y 20 is forced solid, so the floor is flat at y = 20.
  - Fabric at y 0–2 and y 128–130; dimension height 128; sea level 4.
  - The rock above uses Deeplands Stone as both top and filler block.
  - Source: [S12/dimension/deeplands/ChunkGenDeeplands.java L122–329], [biomes/BiomeDeeplands.java L16–17], [WorldProviderDeeplands.java#getActualHeight].
  - The roof's existence is confirmed by the changelog "Fixed Shik spawning on the roof of Deeplands" — [C12/ changelogs].
- **Ceiling height (inference from the noise constants):** an average terrain height of ≈7.7–8.1 noise cells of 8 blocks puts the underside of the rock at about y 61–65. Floor-to-ceiling landmarks agree: Kror Pillars are 41 tall from y 20 (to y 61) and Deep Spires 49–51 tall (to about y 70) — [ChunkGenDeeplands.java L151–193], [S12/structure/deeplands/KrorPillars.java, DeepSpire1/2.java].
- **One-block palette:** the lang names `DeeplandsStone` "Deep Rock". The three trap blocks are also named "Deep Rock": `DeeplandsTrapExplosion`, `…Lava` and `…Nipper`. They drop nothing and on harvest explode, release lava or spawn a Nipper — [L12/en_us.lang `tile.DeeplandsStone.name`, `tile.DeeplandsTrap*.name`], [S12/block/generation/misc/DeeplandsTrap*.java].
- **Light and atmosphere:** no skylight; fog `0.3 × clamp(…, 0, 0.23)`, i.e. near-black; biome types WASTELAND, SPOOKY, SPARSE; `NULL_MUSIC` — [WorldProviderDeeplands.java], [BiomeDeeplands.java]. The only worldgen light is **one Deep Crystal per chunk** set into the floor at y 19 (light 0.8 → level 12) — [BiomeDeeplands.java#doMiscGen], [S12/common/registration/BlockRegister.java L1874 `LightBlock("DeepCrystal", …, 0.8f, …)`].
- **Decoration (all at y = 20, only where air):**
  - 30 rolls of Deep Grass or Deep Blooms.
  - 1/3 Deepshroom: 7×10×7 or 9×14×9 giant mushroom.
  - 1/25 Deep Spire.
  - 1/6 Fossilised Ribs (10×6×13) or Bone Circle (7×4×7).
  - Source: [BiomeDeeplands.java#doPlantGen, #doTreeGen, #doMiscGen].
  - `GiantDeepshroom1–3` exist in `S12/structure/deeplands/` but no generator references them (unused).
- **Ores and loot blocks, all at y 70–114, inside the rock above the floor:**
  - Charged Runium: 5 veins × 12–18.
  - **Deep Case**, a loot-table block whose drops scale with Fortune: 4 veins × 5.
  - **Trap veins: 25 per chunk × 20 blocks**, a random mix of explosion, lava and Nipper traps.
  - Source: [BiomeDeeplands.java#doOreGen, #doMiscGen], `CFG` ChargedRunium, [S12/block/functional/misc/DeepCase.java], [S12/hooks/jer/JerHooks.java L77 "DistributionSquare(4, 5, 70, 114)"].
- **Mobs:**
  - Dweller (w20): 50 HP.
  - Cave Creep (w20): 65 HP; heals 25 at random.
  - Rockbiter (w20): 60 HP.
  - Rock Critter (w20): 75 HP; climbs.
  - Rock Crawler (w20): 70 HP; climbs.
  - Case Construct (w1): 90 HP.
  - Doubler (w1): 70 HP; blinds players within 10 blocks.
  - Arocknid (structure spawner): 75 HP; Weakness; climbs.
  - Nipper (trap only): 45 HP.
  - All natural spawns are limited to `posY < 120`.
  - Source: [SPAWN L354–363], [S12/entity/mobs/deeplands/*.java].
- **Non-hostile:**
  - **Shik** (ambient w5, groups 0–3, 5 HP): "small crab-like creatures … found hiding inside rocky outcrops, skittering away once found … extremely timid … briefly performing a greeting or dance" when meeting other Shiks — [SPAWN L363], [S12/entity/passive/EntityShik.java], [L12/bestiary/en_us/shik.txt].
  - **Extraction Master** (w1) "roams around the Deeplands looking to sell special Extraction armour"; "Reduced extraction master spawn rate in Deeplands" — [SPAWN L354], [L12/bestiary/en_us/extraction_master.txt], [C12/ changelogs].
  - Rocky Lottoman (Deep Lotto Shelter) — [S12/structure/deeplands/DeepLottoShelter.java L227].
- **Structures (`else if` chain, ≈1 per 163 chunks, ≈24/km²):**
  - Arocknid Cave: 15×9×15, Arocknid spawner; 1/700.
  - **Charging Station**: 15×8×15, holds the Charging Table, decayed glass and water; 1/635.
  - **Kror Pillars**: 15×41×6, Kror Altar; 1/635.
  - Deep Lotto Shelter: 14×4×14; 1/635.
  - All need air at y 20. **No loot chests** in any Deeplands structure.
  - Source: [ChunkGenDeeplands.java#populate L331–382], `CFG` SubCategoryDeeplands, [S12/structure/deeplands/*.java] (no `assignLootChests` call).
- **Boss, Kror ("the stone giant"):**
  - 2200 HP, 60 melee damage, full knockback resistance. Ranged damage halved; Slowness III on hit; at random pulls nearby non-sneaking players toward himself.
  - Summoning: place a **Charging Table** on the Kror Altar, then use a **Boulder Dash**. The altar "begins draining your energy", and walking away cancels it ("You moved too far from the altar!") — [S12/entity/boss/kror/EntityKror.java], [S12/block/functional/altar/KrorAltar.java], [L12/en_us.lang `message.mob.kror.*`, `message.feedback.krorAltar.chargingTable`].
  - Wiki (version unstated): "The player's energy must be full". Drops include Charged Runium Chunks (3–9), Deep Case (1–2), Deeplands Tokens (5–32), Dense Rock, Armour Plating, coins, a weapon pool (Boulder Bomber 16.6%, Subterranean Greatblade 14.5%) and a Kror Trophy — [wiki Kror](https://adventofascension.fandom.com/wiki/Kror).
- **Skill link:** "Some of these more powerful runes will require charged runium, which can be found in the Deeplands as charged runium ore" (Runation) — [L12/guides/en_us/runation.txt L9].
- **Access and role:**
  - Tier 1 (from the Overworld). Key: "Mine an Ore Block Down at Bedrock Level"; "Fixed redstone ore not counting for deeplands' realmstone task" — [L12/en_us.lang L1710], [C12/ changelogs], [wiki Dimensions](https://adventofascension.fandom.com/wiki/Dimensions).
  - It gives Crystevia its key: Deep Crystal + Case Construct. The Case Construct is only 1 of 102 hostile spawn weight (≈1%) — [SPAWN L356–362].
- **Perception:**
  - "The way the Deeplands looks is amazing"; the issue suggests moving Ancient Cavern's bosses there — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
  - The project owner finds AoA's "Stoneblock-like" dimension barren (brief).
  - "The cave generation has changed" in 3.5 (1.16.5) — [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
- **1.21:** single biome `the_depths` with a music track (`aoa3:biome.cavern_depths.music`), no spawns or features yet — [D21/dimension/deeplands.json], [D21/worldgen/biome/the_depths.json].

### Inferences
- The Deeplands are not cramped: a 40-plus-block-tall open layer that is effectively infinite. They feel barren for other reasons, listed in section 13.

### Gaps
- No screenshots were examined. The ceiling height is inferred from the noise constants, not measured in-game. Boulder Dash and Charging Table sources (recipe or drop) are not in the repo.

## 10. Dustopia

### Takeaway
A tier-3 dark "Dustopian Forest": no skylight, grey fog, black sky and water. It is the **densest A–G dimension in structures** (≈88/km²) and the only one with a **village of friendly NPCs** next to its boss shrine. Its hostiles lean on screen-darkening and resource-draining tricks. Players see it as part of the interchangeable dark trio.

### Cited Findings
- **Terrain and light:** overworld-style generator with Dustopia Grass on Dustopia Dirt; no skylight; grey fog; black sky; black water; types WASTELAND, SPOOKY, FOREST; no ores — [S12/dimension/dustopia/ChunkGenDustopia.java], [WorldProviderDustopia.java], [biomes/BiomeDustopia.java]. A 1.21 changelog mentions "Dustopia's visual shader", now built into AoA's post-processing — [C21/ changelogs].
- **Decoration (per chunk):** 8 Dawn Bush or Dawn Flower, 25 Dawn Grass, 5 Dawnwood tree rolls, 1/5 Dawn Cage — [BiomeDustopia.java].
- **Mobs (w20 unless noted):**
  - Basilisk: 119 HP.
  - Devourer: 135 HP; heals 40 on hit.
  - Duston: flying; periodically spawns Dust Striders, whose hits darken the screen.
  - Lost Soul: drains 30 soul resource.
  - Dusteiva.
  - Stalker: 138 HP. Freezes while the player looks at it; teleports up to ~64 blocks when hit at close range.
  - Stalker Prime (w1).
  - Lurker: 140 HP; Wither III.
  - Structure spawners: Arkzyne, Crusilisk (blindness and a bloody overlay), Merkyre (poison cloud when hit).
  - Source: [SPAWN L300–309], [S12/entity/mobs/dustopia/*.java].
- **Non-hostile:** Radiant Pixon (w12, groups 1–3; "black and lustreless … hard to spot"), Undead Herald — [SPAWN L300–301], [L12/bestiary/en_us/radiant_pixon.txt]. 3.5 made Urka a passive natural spawn — [wiki 3.5](https://adventofascension.fandom.com/wiki/3.5).
- **Structures:**
  - Primordial Shrine: 5×7×11; 1/350.
  - Crusilisk Cave: 10×10×20, spawner; 1/305.
  - Merkyre Tower: 15×48×15, spawner; 1/300.
  - Arkzyne Outpost: 15×13×15, spawner; 1/295.
  - Lotto Cage; 1/200.
  - **Dustopian Village**: 35×11×23 with **Primordial Banker, Guide, Merchant, Spellbinder and Wizard**; 1/200 — [ChunkGenDustopia.java#populate], `CFG`, [S12/structure/dustopia/DustopianVillage.java].
  - Village trades include Primordial armour (Darkly Powder + coins), a Daybreaker Bow, beef, torches, and Storm, Power, Wither and Compass runes (1 copper coin each) — [S12/entity/npcs/trader/EntityPrimordial*.java].
- **Boss, the Primordial Five:** the shrine needs 8 lit Dustopian Lamps; each summon switches one random lamp off. It spawns a chain of five bosses, each spawning the next on death:

  | Order | Boss | HP | Attack |
  |---|---|---|---|
  | 1 | Kajaros | 1750 | 23 melee, blindness and nausea |
  | 2 | Miskel | 1300 | ranged 14, heals 40 on hit |
  | 3 | Harkos | 1300 | regenerates, leaps |
  | 4 | Raxxan | 1000 | nausea |
  | 5 | Okazor | 1200 | 50 melee, full heal after 3 quick hits |

  6,550 HP in total — [S12/block/functional/altar/PrimordialShrine.java], [S12/entity/boss/primordialfive/*.java].
- **Access:** tier 3 (prerequisite Abyss); summon Shadowlord while under Night Vision — [L12/en_us.lang L1711], [S12/block/functional/altar/ShadowAltar.java].
- **Perception:** part of the dark trio judged repetitive ("find the boss items, fight the boss") — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895). TV Tropes "Dark Is Not Evil": the Primordials are friendly while "everything else … is committed to draining what little light you can find" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension).
- **1.21:** primer worldgen with a fixed biome `the_silent_forest` and music; no spawns or features yet — [D21/dimension/dustopia.json], [D21/worldgen/biome/the_silent_forest.json].

### Inferences
- Dustopia shows that an NPC settlement plus a boss shrine at high frequency gives a dimension "places". The darkness and screen-obscuring mobs work against that.

### Gaps
- How players relight the Dustopian Lamps was not traced.

## 11. Gardencia

### Takeaway
A tier-2 bright, hilly, lake-filled garden of **giant flowers** with castles. Every mob is a hostile walking vegetable or flower, and it has no ores and no passive life. Players called it "one of the most empty dimensions" despite its giant landmarks: a 150-block Lotto Sky Flower and a 48-block water castle.

### Cited Findings
- **Terrain and light:** overworld-style generator with Gardencia Grass on Gardencia Dirt; sea level 66; 2 lake rolls per chunk; skylight; noon fixed; green fog and sky; types MAGICAL, WET, HILLS — [S12/dimension/gardencia/ChunkGenGardencia.java], [WorldProviderGardencia.java], [biomes/BiomeGardencia.java].
- **Decoration:** 59 rolls of Daileers, Lylips or Garden Grass, only above y 67. 2 tries at 50% each for a giant flower (Rose Tree, 2 Blue Starflowers, 2 Rose Starflowers, 2 Sunflowers, 3 Tulips) — [BiomeGardencia.java].
- **Mobs (w20 unless noted):**
  - Archvine: 105 HP.
  - Broccohead.
  - Carrotop.
  - Flowerface.
  - Squasher: random jumps.
  - Sunny (w1): heals under open sky.
  - Corny.
  - Structure-only: Daysee, Pod Plant (Slowness), Vine Wizard (ranged 12).
  - All heal in water or rain. Gardencia is the only A–G biome that does not call `setRainDisabled()`. Whether it actually rains there was not verified; the provider overrides `canDoRainSnowIce` and `canDoLightning`.
  - Source: [SPAWN L444–451], [S12/entity/mobs/gardencia/*.java], [biomes/BiomeGardencia.java], [WorldProviderGardencia.java L45, L61].
- **Non-hostile:** Undead Herald only; no animals and no skill master — [SPAWN L444].
- **Structures:**
  - **Floro Castle**: 25×48×25, 3 parts, placed in water at y 63, **9 loot chests**, Vinocorne Shrine; 1/1700.
  - Garden Castle: 35×23×21, 3 chests, Petal Crafting Station, Pod Plant spawner; 1/1650.
  - Lotto Sky Flower: 8×**150**×8, Floro Lottoman; 1/500.
  - Wizard Flower: Vine Wizard spawner; 1/300.
  - Daysee Flower: spawner; 1/300.
  - Kinetic Rune Shrine: 1/650.
  - Source: [ChunkGenGardencia.java#populate], `CFG`, [S12/structure/gardencia/*.java, FloroCastle.java L3022, GardenCastle.java L2789].
- **Boss, Vinocorne:** 2500 HP. Spawns a random flower minion every 70 ticks (30–120 HP each). Summoned by using Petals on the Vinocorne Shrine, which grows a Living Growth block that hatches the boss after 40 ticks. Petals also buy random Hydrangic armour pieces at the Petal Crafting Station — [S12/entity/boss/vinocorne/*.java], [S12/block/functional/altar/VinocorneShrine.java], [S12/block/functional/utility/PetalCraftingStation.java].
- **Access:** tier 2 (prerequisite Precasia): throw a Blank Realmstone into a carrot crop in Precasia, then defeat the creature it creates. Gardencia is a prerequisite of L'Borean — [L12/en_us.lang L1712, L1717], [wiki Dimensions](https://adventofascension.fandom.com/wiki/Dimensions).
- **Perception:** "I think Gardencia is one of the most empty dimensions"; the issue proposes moving its plant mobs and structures into Shyrelands — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895). Advancement title "You Are What You Eat" — [L12/en_us.lang].
- **1.21:** not present.

### Inferences
- Gardencia has big landmarks but very rare destinations: castles at 1/1650–1/1700, about 2.3 per km². The only "life" is hostile produce, so open hills full of flowers still read as empty. Landmarks without inhabitants or reasons to stop do not fix emptiness.

### Gaps
- Castle chest contents and the Vinocorne drop table are not in the repo.

## 12. Greckon

### Takeaway
A tier-3 permanently dark haunted land. Its seven mobs specialise in jump-scares, invisibility and silence. The boss can be summoned anywhere with an idol. It is the clearest AoA case of a horror realm with limited visibility.

### Cited Findings
- **Terrain and light:**
  - Overworld-style generator with Greckon Grass on Greckon Dirt; caves; a lake at a random y in 1/4 chunks.
  - No skylight; reddish fog; purple water; types MOUNTAIN, SPOOKY, MAGICAL.
  - Source: [S12/dimension/greckon/ChunkGenGreckon.java#populate], [CaveGenGreckon.java], [WorldProviderGreckon.java], [biomes/BiomeGreckon.java].
- **Decoration:** 15 Haunted Flowers and 1 Haunted Tree roll per chunk — [BiomeGreckon.java].
- **Mobs (w20 unless noted):**
  - Grillface: 131 HP, 16 dmg; scare sound plus full-screen overlay.
  - Shifter: turns invisible every 8 s.
  - Silencer (w1): mutes sound client-side.
  - Skull Creature.
  - Sugarface.
  - Valkyrie: flying ranged.
  - Hunter.
  - Faceless Floater (Faceless Tree spawner): purple fog overlay for 15 s.
  - Source: [SPAWN L384–392], [S12/entity/mobs/greckon/*.java].
- **Non-hostile:** Infusion Master (w1), Undead Herald (w1), Haunted Lottoman (Lotto Rock); no animals — [SPAWN L384–385].
- **Structures:**
  - Haunted Maze: 25×12×16 with a Haunting Table; 1/700.
  - Haunted Lotto Rock: floating +25; 1/500.
  - Faceless Tree: 9×13×9, spawner; 1/180.
  - Storm Rune Shrine: 1/650.
  - Source: [ChunkGenGreckon.java#populate], `CFG`.
- **Boss, Bane:** 1750 HP. Turns invisible when he hits, summons 6 one-HP clones, and sometimes a big clone that explodes with power 6. The Haunted Idol works anywhere in Greckon ("Bane can only be summoned within the Greckon.") — [S12/entity/boss/bane/*.java], [S12/item/misc/summon/HauntedIdol.java], [L12/en_us.lang `message.mob.bane.incorrectDimension`].
- **Materials:** Ghastly ore (2 veins × 3–7, y 3–27), Ghoulish ore (2 × 3–7, y 30–59) — [BiomeGreckon.java#doOreGen], `CFG`.
- **Access:** tier 3 (prerequisite Abyss); use Nightmare Flakes on Elusive — [L12/en_us.lang L1713].
- **Perception:**
  - Dark trio, "scary mobs you have a hard time seeing" — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
  - TV Tropes: before 3.2, a Greckon portal in Mysterium could trap players. The Beamer was valued because "lots of mobs punish you for coming too close" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension).
- **1.21:** not present.

### Inferences
- Greckon's design depends on hiding information from the player (darkness, invisibility, silence, overlays). The owner rules out exactly this kind of realm.

### Gaps
- What the Haunting Table does was not checked.

## 13. Deeplands and Crystevia vs the planned Realmforge stone realm: why AoA's caves feel barren

### Takeaway
AoA's caves have enough space: the Deeplands are a 40-plus-block-tall endless hall, and Crystevia is a Nether-sized cavern. They feel barren because of **sameness and missing life**:
- one stone block everywhere;
- one flat walking layer;
- near-total darkness and silence;
- no passive or neutral life apart from a timid 5-HP crab (Deeplands) and none at all (Crystevia);
- 1 structure per ~160 chunks (Deeplands), or one repeated utility hut (Crystevia);
- no loot chests and no settlement;
- resources placed above the walkable layer, behind disguised traps.

Each point is fixable, and each maps onto a decision the owner has already made.

### Cited Findings
| Aspect | AoA Deeplands (1.12.2) | AoA Crystevia (1.12.2) | Realmforge first realm (owner decisions 2026-10-11/12, brief) |
|---|---|---|---|
| Enclosure | Fabric floor and ceiling, height 128 [S12/dimension/deeplands/ChunkGenDeeplands.java L284–286] | Fabric ≤2 / ≥125 [ChunkGenCrystevia.java L262–303] | Bedrock floor and ceiling, "Stoneblock-like but not cramped" |
| Walkable layout | **One flat floor at y 20**; rock above y ≈ 61–65 (inferred) [ChunkGenDeeplands.java L151–239] | Nether-style shelves and caverns [ChunkGenCrystevia.java L120–194] | Big walkable spaces |
| Block palette | 1 block ("Deep Rock"), traps look identical [L12/en_us.lang `tile.DeeplandsStone/Trap*`] | 1 stone + 6 recoloured ores, plants and clusters [BiomeCrystevia.java] | Many custom stone variants; no single-texture world |
| Light | 1 lamp block per chunk (level 12), black fog [BiomeDeeplands.java#doMiscGen] | No skylight; plants unlit; magenta fog [WorldProviderCrystevia.java] | No horror realm with limited visibility |
| Vegetation, oases | 2 plant types + 1/3 mushroom, floor level only [BiomeDeeplands.java] | Crystal plants and clusters only | Grassy pockets and oases |
| Water, lava | None in worldgen (water only inside the Charging Station) | 1/10 chunks ceiling waterfall [BiomeCrystevia.java L199–215] | Not specified |
| Passive or neutral life | Shik (ambient w5, 5 HP, flees) [SPAWN L363] | **None** [SPAWN L345–352] | Animals and neutral mobs, e.g. a stone cow variant |
| Hostile roster | 5 common + 2 rare melee rock mobs, Arocknid, Nipper [SPAWN L356–362] | 7 constructs, all regenerating [S12/entity/mobs/crystevia] | Stone-themed mobs, animated statues |
| NPCs and trade | Extraction Master (w1, rate reduced), Rocky Lottoman, Herald [SPAWN L354–355; C12/] | Crystal Trader, Crystal Lottoman (in structures) [S12/structure/crystevia/] | Mines with friendly NPCs trading for dungeon keys |
| Structures | 4 types, ≤24/km², **0 loot chests** [CFG; S12/structure/deeplands/] | 6-colour huts ≈87/km² + 4 buildings ≈2.4/km² each [CFG] | Dwarf-fortress dungeon, catacombs, boss arena, mines, small POIs |
| Dungeons | None (Arocknid spawner room only) | None | 2–4 per realm; instanced ones open with crafted keys |
| Ores, Y rules | Charged Runium y 70–114, Deep Case y 70–114, all in the ceiling mass [BiomeDeeplands.java#doOreGen] | 6 gemstones, 24 veins/chunk, y 10–109 [BiomeCrystevia.java#doOreGen] | 3+ ores and gems with Y-range rules; mining is the main profession |
| Mining risk | 25 trap veins × 20 blocks/chunk in the same rock [BiomeDeeplands.java L80–94] | None | Mining is meant to be rewarding |
| Boss | Kror via Kror Pillars, Charging Table, Boulder Dash, energy [KrorAltar.java] | Crystocore via a 5-step crystal chain [section 8] | Stone boss arena |
| Music | None (`NULL_MUSIC`) | None | Not specified |
| Respawn and home | `canRespawnHere=false` | `canRespawnHere=false` | Persistent shared world (respawn not specified) |

- Community: "every dimension is super simple, boring, and empty"; "it is a bit spread out"; proposes cutting to "12 or 15" dimensions — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895). Yet "The way the Deeplands looks is amazing" — [same](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
- Crystevia: tedious item chain; "one of the most boring and uninteresting fights in the game" — [GitHub #543](https://github.com/Tslat/Advent-Of-Ascension/issues/543).

### Inferences: what exactly was missing
1. **Variation over distance.** One biome, one block, one floor height and a structure every ~163 chunks: walking 200 blocks in the Deeplands looks the same as standing still. No sub-zones ("grassy pockets/oases" in RF terms) and no water or lava features.
2. **Verticality in the walkable space.** Every plant and structure is placed at exactly y = 20, so the Deeplands are a 2D plane with a lid. There are no ledges, chasms, multi-level halls or reasons to climb, even though the rock above is where everything valuable is.
3. **Light as a design tool.** One level-12 lamp per chunk and near-black fog give no readable landmarks and push the player toward darkness. The owner rules this out.
4. **Life that is not trying to kill you.** The Shik is the only non-hostile creature, and it is tiny and flees. Crystevia has none. There are no animals, no neutral mobs and no settlements; the only NPCs are rare wanderers (Extraction Master w1, Undead Herald w1) and lotto stands.
5. **Rewards where the player walks.** No loot chests in any Deeplands structure. Ores and the Deep Case "treasure ore" sit 50+ blocks above the floor, inside rock laced with disguised traps, so mining is punished.
6. **Places to stop.** Four structure types, three of them single-purpose: a spawner room, a charging table and a lotto stand. No fortress, no mine, no village. Crystevia's many huts are one repeated converter.
7. **Sound.** No ambient music; 1.21 AoA adds biome music to both dimensions. This suggests the author also saw a gap here.
8. **Purpose density.** Deeplands' progression role is three things (Kror, charged runium, the Crystevia key) behind rare spawns: the Case Construct is ≈1% of hostile weight. Crystevia's purpose is one long conversion chain.

### Gaps
- No player screenshots or videos were reviewed (search returned none). "Barren" is diagnosed from code, numbers and two GitHub issues. AoA 3.5's changed Deeplands caves were not examined.

## 14. Relevance to Realmforge

### Takeaway
AoA 1.12 gives Realmforge a proven **skeleton**:
- a shared portal frame with a non-consumable, per-realm key;
- an unlock chained from the previous realm's content;
- a boss altar in a landmark structure, summoned with an item built from local materials;
- per-realm tokens and NPC vendors;
- Y-band ores;
- configurable structure rates.

It also gives a precise list of what makes a realm feel empty (section 13). Realmforge should copy the loops and reject the density, palette and lighting choices. No names, code or assets may be reused.

### Adopt
- **Shared portal + non-consumable realm key (matches the owner decision).** AoA uses one frame (13 base blocks + 4 rune blocks, 3×4 interior), right-clicked with a per-dimension key that is never consumed. Relighting gives a "doesn't require your key again" message, and each realm has a one-line activation message — [S12/library/misc/AoAPortalFrame.java], [S12/block/functional/misc/CarvedRunicPortalBlock.java], [L12/en_us.lang `message.feedback.teleporterFrame.*`]. For RF: the selector block plus realm item, with a themed portal colour **and** a one-line flavour message per realm.
- **Unlock chained from the previous realm's content.** Example: the Crystevia key is Deep Crystal + Case Construct from the Deeplands — [L12/en_us.lang L1709]. RF: the second realm's unlock item uses materials or a boss drop from the stone realm. Show it in the quest log, not as a hidden challenge.
- **Boss altar inside a landmark + summon item crafted from local materials.** Kror Pillars + Kror Altar + Charging Table + Boulder Dash; Power Station + Giant Crystal from the Extension Shrine — [S12/block/functional/altar/KrorAltar.java, PowerStation.java], [S12/block/functional/utility/CrystalExtensionShrine.java]. RF: the stone boss arena portal sits in a visible landmark (pillars, a hall) and opens with a crafted key, which matches the instanced-dungeon decision.
- **"Treasure ore" blocks** like the Deep Case: a stone block with its own loot table that respects Fortune — [S12/block/functional/misc/DeepCase.java]. RF: cache or geode blocks in the mining Y bands, a reward that is native to mining.
- **Per-realm tokens + vendors.** Mobs drop realm tokens; lotto NPCs sell boxes at fixed prices (28/15/10/21); a wanderer trades tokens for cosmetics — [EntityRockyLottoman.java], [EntityUndeadHerald.java]. RF: realm currency spent at the mine NPCs that sell dungeon keys.
- **Ambient creatures with a gathering use**, like the Pixons: siphon with a bowl → infusion stones — [L12/bestiary/en_us/shining_pixon.txt]. RF: neutral "crystal mites" or the stone cow that give a profession material without being killed.
- **Ore Y-bands as data**, e.g. Charged Runium y 70–114, Varsium y 25–62, Blazium y 0–19 in a sub-layer — [BiomeDeeplands.java], [BiomeBarathos.java]. RF: Y-range rules per ore in content YAML, shown in-game (AoA had JER hooks: [S12/hooks/jer/JerHooks.java]).

### Adapt
- **The Deeplands' inverted noise (floor plus rock mass above)** → keep a huge open layer, but give it several floor heights. Add ledges, chasms and multi-level halls with density functions ([RF/docs/research-notes.md L388–392]). Put ore bands **in the walls and pillars of the walkable layers**, not in an unreachable roof.
- **Crystevia's Nether-style shelves and ceiling waterfalls** → use them as one sub-zone (a crystal grotto pocket), with lit crystals as navigation landmarks. Do not use them as a whole realm. Note AoA's own performance trouble with luminous crystal blocks — [C12/ "Removed luminosity from crystevia crystal blocks…"].
- **Construct family** (seven role variants: strength, ranged, terror, speed, resistance, flight, mind; all regenerate) → an "animated statue" family with readable roles and visual tells. Drop constant regeneration, which only lengthens fights — [S12/entity/mobs/crystevia/*.java].
- **Rock mobs that climb (Rock Crawler, Arocknid) and the timid Shik that hides in outcrops** → good behaviours for a living cave: climbers on pillars, critters that flee into rock — [S12/entity/mobs/deeplands/EntityRockCrawler.java], [L12/bestiary/en_us/shik.txt].
- **Trap blocks** → only inside dungeons and catacombs, telegraphed, never in open mining stone where they look identical to it — [S12/block/generation/misc/DeeplandsTrap*.java].
- **Primordial Five chain** (5 bosses, each spawning the next) → shape it as miniboss → miniboss → boss inside one instance, with breathers between phases — [S12/entity/boss/primordialfive/*.java].
- **Ancient Cavern's one-time hand-built boss complex** → an instanced boss wing in a plot, never a shared single copy — [S12/structure/ancientcavern/AncientCavern.java].
- **1.21 Barathos** (4 biomes: flats, dried and false oases, fossil sites, stone-pillar "spears", lava pits; spacing-28 resource pits; sandstorm world event) → a direct template for the M4 desert realm with big oases — [D21/worldgen/biome/*.json], [C21/ sandstorm lines].
- **Dustopian Village next to the boss shrine** → a friendly mine or outpost hub near the stone boss arena — [S12/structure/dustopia/DustopianVillage.java].

### Avoid
- **One filler block for the whole realm** (Deeplands top and filler = "Deep Rock") — [BiomeDeeplands.java L16–17].
- **Placing everything on one Y level** (every Deeplands feature at y 20) — [BiomeDeeplands.java].
- **Sparse, purely random structure rolls.** Deeplands: 1 per ~163 chunks from a 4-entry `else if` chain of independent 1/n rolls ([CFG], [ChunkGenDeeplands.java#populate]). Use spacing-based structure sets with a small-POI layer.
- **Hostile-only rosters** (Crystevia, Gardencia, Celeve, Greckon have no passive mobs) — [SPAWN].
- **Permanent darkness and screen-obscuring mobs** (6 of 11 dimensions without skylight; Greckon overlays, invisibility, silence) — [WorldProvider*.java], [S12/entity/mobs/greckon/*.java]. This conflicts with "no horror realm with limited visibility".
- **Silent realms** (`NULL_MUSIC` everywhere) — [S12/library/Enums.java L16].
- **Resources behind punishment**: the ore bands share the rock with 25 trap veins per chunk — [BiomeDeeplands.java#doMiscGen]. This conflicts with "mining is the main profession".
- **Rare-spawn gates and long conversion chains**: the Case Construct is ≈1% of spawn weight, and Crystevia's chain runs gemstones → crystals → druse → Giant Crystal → boss; players called it tedious — [SPAWN L356], [GitHub #543](https://github.com/Tslat/Advent-Of-Ascension/issues/543).
- **Structures with no loot** (zero chests in the Deeplands) and **one cheap structure inflating density** (Crystevia huts) — [S12/structure/deeplands/], [CFG].
- **Single saturated palettes** ("candyland is an assault on the eyes") and **thematic duplicates** (the dark trio) — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895).
- **No respawn or home in a persistent realm** (`canRespawnHere=false` in all 11) — [WorldProvider*.java]. Inference for RF: offer a respawn point at the friendly mine hub.

### Gaps
- No target structure density for Realmforge is proposed here (it needs playtesting). The AoA numbers only show where "too sparse" lies: ≈24 structures/km² in the Deeplands, against ≈88/km² in Dustopia, the densest A–G dimension.
- AoA 1.12 loot tables and recipes are not in the local repo, so reward balance per dimension is unverified.
