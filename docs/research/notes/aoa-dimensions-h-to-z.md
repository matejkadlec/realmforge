# Advent of Ascension (AoA 3.x, MC 1.12.2): dimensions H–Z, plus AoA content in the vanilla Overworld and Nether

> Appendix to [the reference-game research](../reference-games.md) (RLM-94), written on 2026-10-12 by an AI research agent. Local citations name their source (the Craft to Exile 2 1.1.3 instance and the jars it ships, the Mine and Slash and Advent of Ascension source trees, the CTE2 GitHub changelogs); those files are not in this repository. Mechanics and numbers only: never reuse names, assets or code of the reference games.


Scope: per-dimension profiles of Haven, Immortallis, Iromine, L'Borean, Lelyetia, Lunalus, Mysterium, Nowhere, Precasia, Runandor, Shyrelands, Vox Ponds, and what AoA adds to the Overworld and the Nether, with comparisons for Realmforge's candidate realms. Cross-dimension systems (skills, tribute, economy) appear only where they explain a dimension.

How to read the citations and numbers:
- **A12** = `research sources: aoa_1.12.2` (view-only AoA source for 1.12.2; the README says it mirrors "the latest public release only" and the newest changelog there is `changelogs/3.3.6.md`, so this is AoA 3.3.6). **A21** = `research sources: aoa_1.21` (1.21.1 branch; newest changelog `changelogs/3.7.16.1.md`).
- **SC** = `A12/source/utils/ConfigurationUtil.java`, class `StructureConfig.SubCategory<Dim>` (default structure chances, written as "1/n per chunk").
- **ESR** = `A12/source/common/registration/EntitySpawnRegister.java`, method `getDimensionSpawns()` (natural spawn entries: weight, min–max group, creature type). MONSTER = hostile, CREATURE = passive or NPC.
- **Mob stats** (HP, damage, immunities) come from each entity class (`getBaseMaxHealth()`, `getBaseMeleeDamage()`, `MobProperties`) under `A12/source/entity/...`.
- **Structure sizes** come from the `//StructureSize: AxBxC` comment and the `addBlock` calls in `A12/source/structure/<dim>/<Name>.java`. Spawner, chest and NPC counts were counted from those calls with a read-only script.
- **"Effective" frequencies**: in every 1.12.2 chunk generator, the main structures sit in one `if / else if` chain (SC comment: "One structure per chunk, with the exception of rune shrines"). I computed P(i) = Π(1 − 1/n_j for the earlier entries) × 1/n_i, then "1 per X chunks" and an equivalent grid spacing of √X × 16 blocks. These are generation *attempts*. Most attempts also check the ground block first, so real counts are lower. All numbers are AoA defaults.

---

## 0. Which H–Z dimensions exist (list verification)

### Takeaway
AoA 3.3.6 for 1.12.2 has 11 H–Z dimensions: Haven, Immortallis, Iromine, L'Borean, Lelyetia, Lunalus, Mysterium, Precasia, Runandor, Shyrelands and Vox Ponds. **Nowhere does not exist in 1.12.2.** It was added in 3.5 (the 1.16.5 era), replaced the removed Immortallis and Ancient Cavern, and is still in the 1.21 branch. Of the H–Z set, the 1.21 data ships only L'Borean, Lelyetia, Nowhere and Precasia, and only Precasia and Nowhere have content.

### Cited Findings
- The 1.12.2 dimension folders are abyss, ancientcavern, barathos, candyland, celeve, creeponia, crystevia, deeplands, dustopia, gardencia, greckon, haven, immortallis, iromine, lborean, lelyetia, lunalus, mysterium, nether, overworld, precasia, runandor, shyrelands and voxponds. There is no `nowhere` folder, and the 1.12.2 Java source and lang files contain no "nowhere" string — [A12 source/dimension/; grep over A12 source and lang/en_us.lang]
- The 1.12.2 dimension IDs are 800–821. The H–Z ones are haven 811, immortallis 812, iromine 813, lborean 814, lelyetia 815, lunalus 816, mysterium 817, precasia 818, runandor 819, shyrelands 820 and voxPonds 821 — [A12 ConfigurationUtil.java `SubCategoryDimensionIds`, lines ~135–200]
- AoA's world generator only adds content to dimension 0 (Overworld) and −1 (Nether). It has no End hook — [A12 source/dimension/WorldGen.java]
- Nowhere is "an unfinished structure dimension added in version 3.5" — [AoA wiki: Nowhere](https://adventofascension.fandom.com/wiki/Nowhere)
- The wiki says Immortallis and Ancient Cavern were removed because "the developer did not want to have multiple structure dimensions". It also cites technical and quality reasons and their reliance on Tribute, and says "Nowhere, a much larger structure dimension, succeeded them" — [AoA wiki: Immortallis](https://adventofascension.fandom.com/wiki/Immortallis). NamuWiki says they were "removed after version 3.4.9" — [NamuWiki: Nevermine](https://en.namu.wiki/w/Nevermine)
- A21 has `dimension/*.json` for abyss, barathos, celeve, crystevia, deeplands, dustopia, lborean, lelyetia, nowhere and precasia. A21 `AoADimensions.java` still declares ResourceKeys for Haven, Iromine, Lunalus, Mysterium, Runandor, Shyrelands and Vox Ponds, but these have no dimension JSON — [A21 data/aoa3/dimension/; A21 source/common/registration/worldgen/AoADimensions.java]
- In A21, `lborean_deep.json` and `lelyetian_plains.json` have empty spawner and feature lists. The 13 Precasia biomes have full spawner and feature lists — [A21 data/aoa3/worldgen/biome/*.json]
- Tiers (introduced as official in 3.2): Tier 1 = Creeponia, Nether, Lelyetia, Deeplands, Barathos, Precasia. Tier 2 = Vox Ponds, Mysterium, Abyss, Celeve, Haven, Crystevia, Iromine, Gardencia, Candyland. Tier 3 = Runandor, Greckon, Dustopia, Lunalus, L'Borean. Tier 4 = Shyrelands. Nowhere has no tier. "A dimension's tier is one more than that of the highest tiered dimension that must be visited to reach it" — [AoA wiki: Dimensional Tiers](https://adventofascension.fandom.com/wiki/Dimensional_Tiers). The Immortallis page lists it as Tier 3 (prerequisite Crystevia) — [AoA wiki: Immortallis](https://adventofascension.fandom.com/wiki/Immortallis)

### Inferences
- The HP ranges of the main hostiles I extracted (excluding outliers such as the 30-HP Explodot, 1-HP disguised Arc Flowers and the 999-HP Destructor) follow those tiers: Lelyetia 50–85, Precasia 60–120, Haven 70–120, Iromine 75–120, Mysterium 45–110, Vox Ponds 74–112, L'Borean 112–135, Runandor 109–132, Lunalus 110–150, Shyrelands 140–180. This is a consistent difficulty ladder. The structure of the realms (themes, density) did not scale with it.
- The existing Realmforge note says "only 2 of its 20+ dimensions came back" after the rewrite. That matches the H–Z picture: only Precasia and Nowhere are real content in A21.

### Gaps
- I did not check whether any other H–Z dimension was scrapped before release. The wiki has a "Scrapped features" page that I did not read.

---

## 1. Cross-cutting facts that explain why 1.12.2 AoA dimensions feel empty

### Takeaway
The 1.12.2 engine itself pushes every H–Z realm toward emptiness. Each realm has one biome, no music, no weather, a fixed time of day (except Precasia), no respawn point, mostly hostile spawns, and synthetic terrain that is often aligned to the 16-block chunk grid. Each signature structure is about as rare as a vanilla village (1 per ~300–1,700 chunks). The only "filler" is trees, flowers and a few decorative structures.

### Cited Findings
- **One biome per dimension.** Each WorldType uses `BiomeProviderSingle(BiomeRegister.<DIM>)`, e.g. `WorldTypeHaven.getBiomeProvider` — [A12 source/dimension/haven/WorldTypeHaven.java]. Every 1.12.2 H–Z dimension folder has exactly one `biomes/Biome<Dim>.java` — [A12 source/dimension/*/biomes/]
- **Silence.** Every H–Z `WorldProvider<Dim>.getMusicType()` returns `Enums.NULL_MUSIC`, a music type bound to `SoundsRegister.MUSIC_NULL` ("ambient.music.null"). AoA registers music only for bosses (`BANE_MUSIC` … `VOXXULON_MUSIC`) — [A12 WorldProviderHaven.java etc.; A12 library/Enums.java line 16; A12 common/registration/SoundsRegister.java lines 18, 806–839, 932]. A21 later adds per-biome music, e.g. `aoa3:biome.lelyetian_plains.music` and `aoa3:biome.nowhere.music`, and vanilla tracks in Precasia — [A21 data/aoa3/worldgen/biome/lelyetian_plains.json, nowhere.json, precasian_forest.json]
- **No respawn, no weather.** All H–Z providers return `canRespawnHere() = false` and disable rain, snow and lightning — [A12 WorldProvider<Dim>.java, all 11 files]
- **Time of day frozen** (celestial angle): Haven, L'Borean, Lelyetia, Runandor and Shyrelands 0.0 (permanent noon); Vox Ponds 0.35; Lunalus 0.4; Immortallis 0.4; Mysterium 0.05; Iromine 1.0. Precasia does not override it (`isSurfaceWorld() = true`, so it has a normal cycle) — [A12 WorldProvider<Dim>.calculateCelestialAngle]
- **No skylight** in Immortallis, Lunalus and Mysterium (`hasSkyLight = false`). Sky colours: Haven green, Lunalus pink, Mysterium red, Vox Ponds green, Iromine black; L'Borean, Lelyetia, Runandor, Shyrelands and Immortallis blue — [A12 WorldProvider<Dim>.init; Biome<Dim>.getSkyColorByTemp]
- **Passive or ambient life is close to nonexistent.** CREATURE entries in ESR for H–Z are only Halycon (Haven, weight 5), Trotter (Lelyetia, 20), Coratee (L'Borean, 20), Meganeuropsis (Precasia, 5, group 1–2), the pixons (Mysterium, Lelyetia, Runandor, L'Borean: 12, groups 1–3; Ambient Pixon in Haven, Runandor, Candyland, Shyrelands) and NPCs at weight 1 (skill masters, Undead Herald, Metalloid) — [ESR]
- Pixons are "animated energy" that you siphon with an Infusion bowl for dimension-specific infusion stones. Harvesting is gated by Infusion level — [A12 lang/bestiary/en_us/ambient_pixon.txt; A12 source/entity/misc/pixon/EntityPixon.java lines 93–108]
- **Structure mobs come from spawner blocks inside structures**, not from natural spawning. Examples: Dawnlight, Enforcer, Mechyon, Mermage, Amphibior, Zhinx, Paravite, Rawbone, Exohead, Refluct, Baumba, Zarg, Zorp, Inmates, Fungock, Mushroom Spider, Runic Golem, Runic Guardian, Spectral Wizard, Arc Wizard, Soulscorne, Lightwalker, Luxocron, Shyre Troll, Nightwing — [A12 source/structure/<dim>/*.java, `*_SPAWNER` blocks]
- **Total structure attempts per chunk** (all chains, including decorative groups), from SC defaults: Vox Ponds 1 per 4.9 chunks (lamp posts dominate), Shyrelands 1 per 20.4 (wall decorations dominate), Haven 1 per 30.2, Iromine 1 per 30.3, Mysterium 1 per 63.8, Lelyetia 1 per 66.0, Runandor 1 per 88.9, Precasia 1 per 94.6, Lunalus 1 per 96.2, L'Borean 1 per 121 — [computed from SC and each ChunkGen<Dim>.populate() order]
- A community GitHub issue titled "Minimizing Dimensional Emptiness" summarises the AoA loop as finding boss items, fighting the boss, shopping and moving on. It asks for "more varying creatures" and "more types of structures," and notes that turning up the mob and structure spawn configs makes AoA worlds feel livelier — [GitHub issue #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895)
- In the 1.21 branch the developer was still tuning density: "Testing a reduction of mob spawns in Precasia. Feedback will help this grow into a place we're happy with, hopefully" and "Gave Precasia its own Pixon spawn list, making it more common than other t1 dimensions" — [A21 changelogs, grep "Precasia"]

### Inferences
- The owner's complaint that AoA realms are "empty and lifeless" follows directly from the code: one biome, no music, a frozen sky, almost no passive life, and main structures spaced 300–660 blocks apart.
- Five of the 11 dimensions (Lelyetia, Mysterium, Runandor, Shyrelands, Vox Ponds) build terrain from hand-coded per-chunk blocks instead of noise. That makes a visible 16×16 tiling, which probably adds to the "artificial and repetitive" feel.

### Gaps
- I found no reachable Reddit discussion (Reddit is blocked from this environment) and no dated review that rates the H–Z dimensions one by one. Perception evidence below is limited to wikis, TV Tropes, NamuWiki, one GitHub issue and one Minecraft Forum thread.

---

## 2. Haven (sky islands)

### Takeaway
Haven in 1.12.2 is an End-style floating-island realm. Its strongest assets are an 83-block "Guardian Tower" dungeon that you climb, floating mini-dungeons below the islands, colourful flora, and the void used as a puzzle element (Lunalus key, Rockrider summon). It is undermined by a frozen noon, mostly flying hostiles, and a placement bug on its main dungeon. From 3.5 on, Haven became massive, close-together islands with mostly passive mobs.

### Cited Findings
**Terrain and atmosphere (1.12.2)**
- The terrain is a 3D noise field (min, max and main Perlin octaves) of Haven Stone, with an offset of −8. Density fades to −30 in the lowest 8 noise cells (each cell is 4 blocks, so nothing below ~y32) and fades progressively toward the top (32 cells, so y0–128). The surface is `haven_grass` over `haven_dirt`. Sea level is 0, so there is no water — [A12 dimension/haven/ChunkGenHaven.java `generateNoiseField`, `generateBiomeTerrain`; biomes/BiomeHaven.java]
- It uses `FloatingDimChunk` (custom skylight for chunks with void) — [A12 dimension/FloatingDimChunk.java]. A changelog entry reads "Fixed Haven being dark" — [A12 changelogs, grep "Haven"]
- Sky colour is `Color.GREEN`. Fog is purple (0.19, 0.02, 0.29 × brightness). Celestial angle is 0 (always noon). Clouds use the vanilla renderer at y128. There is no rain — [A12 WorldProviderHaven.java; BiomeHaven.java]
- Decoration per chunk: 50 plant attempts (4/7 Haven grass plant, 1/7 each blue, yellow and pink Daylooms), 5 "Havendales" flower-structure attempts (blue, pink, yellow), and 3 tree attempts. Tree structures come in Blue, Pink, Purple, Red, Turquoise and Yellow variants — [A12 BiomeHaven.java decorator; A12 structure/haven/]

**Mobs**
- Natural spawns: Automaton 20, Angelica 15, Orbiter 15, Seeker 15, Surveyor 5, Volar 3, Rainicorn 2, Voliant 1. Passive Halycon 5 (20 HP animal). Ambient Pixon 12. Undead Herald NPC 1 — [ESR; A12 entity/animals/EntityHalycon.java]
- Stats: Angelica 84 HP, flying, status-immune, applies Levitation and Weakness. Automaton 108 HP, 10.5 dmg, 0.9 knockback resistance. Orbiter 83 HP, poison and slowness. Seeker 70 HP, flying, ranged-immune. Surveyor 97 HP, flying. Volar 86 HP, flying ranged. Rainicorn 85 HP. Voliant 120 HP, flying ranged, 13 dmg. Dawnlight 87 HP (spawner only) — [A12 entity/mobs/haven/*.java]
- The Angelica will "glide towards [victims] and apply an uncontrollable levitation effect, rising up with its target until its death or falling demise" — [A12 lang/bestiary/en_us/angelica.txt]

**Structures (1.12.2 defaults, effective rates)**
- Dawnlight Dungeon: 1/650. Size 20×15×10, floats at y10–34 below the islands, 2 Dawnlight spawners, 2 loot chests (`structureDawnlightDungeon`).
- Floating Lotto Fountain: 1/651. Size 7×17×7, floats at y10–49, Twinkling Lottoman NPC.
- Guardian Tower: nominal 1/35 (effective 1/35). Size 29×83×29 in 3 parts, spawners for Angelica, Seeker, Volar and 2 Surveyors, 3 chests, Guardian Altar at the top.
- Strike Rune Shrine: 1/650, 7×4×7, floats 20 blocks above the ground.
- Sources: [SC `SubCategoryHaven`; A12 ChunkGenHaven.populate(); A12 structure/haven/*.java]
- **Bug:** the Guardian Tower ground check reads `pos.setPos(x + 14, y - 1, x + 14)`, using x twice instead of z — [A12 ChunkGenHaven.java line 278]
- `RockriderBoulder` (11×5×11 with a Rockrider Shrine) is registered and has a config key (`rockriderBoulderSpawnChance = 17`), but no world-gen code calls it — [A12 structure/haven/RockriderBoulder.java; StructuresHandler.java line 390; grep shows no caller]

**Bosses**
- **Four Guardians**: use a Voliant Heart on the Guardian Altar. It spawns 4 ranged bosses with 750 HP each (Blue inflicts blindness, Red slowness; Yellow has 15 projectile damage, the others 20). Only the last guardian killed returns the loot table. Advancement "kill_four_guardians" — [A12 block/functional/altar/GuardianAltar.java; entity/boss/fourguardians/EntityBlueGuardian.java]
- **Rockrider**: 1,500 HP, 30 dmg. The Heavy Boulder works only in Haven. Rockrider switches form every 300 ticks: one form ignores gun damage, the other ignores melee — [A12 item/misc/summon/HeavyBoulder.java; entity/boss/rockrider/EntityRockRider.java lines 37–168]
- Getting the Heavy Boulder: a Floating Stone dropped into the void (below y−10) is teleported to y257 and turned into a Heavy Boulder. The Rockrider Shrine also converts a Floating Stone — [A12 item/misc/FloatingStone.java; block/functional/altar/RockriderShrine.java]

**Materials**
- Crystallite ore: 8 veins per chunk, 8–12 ores per vein, y30–69. It smelts to Crystallite, and the lang lists a Crystallite Sword — [A12 BiomeHaven decorator; OreConfig `SubCategoryCrystallite`; RecipeRegister.java line 18; en_us.lang]
- Haven Tokens are the dimension's currency — [A12 en_us.lang]

**Access and progression**
- Haven key: "Kill a Flye With Melee While High up in The Lelyetian Trees." A changelog lowered the height requirement to Y=80 from 100 — [A12 en_us.lang `gui.realmstoneMenu.hover.haven`; A12 changelogs]
- Haven gives the Lunalus key: "Jump Over The Void In Haven With a Gravitator." The Gravitator is bought from Celeve's Toy Merchant for 46 Circus Coins — [A12 en_us.lang; A12 entity/npcs/trader/EntityToyMerchant.java line 53; A12 item/misc/Gravitator.java line 48]
- Portal colour is BRIGHT_TURQUOISE. Tier 2 (prerequisite Lelyetia) — [A12 BlockRegister.java line 2238; AoA wiki: Dimensional Tiers](https://adventofascension.fandom.com/wiki/Dimensional_Tiers)

**Later versions**
- 3.5+ wiki description: "a heavenly sky-island dimension". The islands are "massive", mountainous, with tall cliffs and overhangs, and "generate fairly close together". There is "open access to the void". Time is fixed at 6000, ambient light 0.75. Islands are Weightless Stone, with Crystallite "in enormous quantities". "As of the 3.5 update, almost all mobs in the Haven are passive." The hostile mobs are Spirit Guardian and Spirit Protector, which appear when an Eeo is attacked — [AoA wiki: The Haven](https://adventofascension.fandom.com/wiki/The_Haven)
- Haven is not shipped in A21 data — [A21 data/aoa3/dimension/]

**Perception**
- "A dimension made up of floating islands," compared with Aether 2. Valued for the Gravitator and its armour — [NamuWiki](https://en.namu.wiki/w/Nevermine)
- TV Tropes records a bug: firing a Skydriver Bow over Haven's void froze the game's AI — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
- The GitHub emptiness thread proposes merging Haven with Lelyetia — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895)

### Inferences
- Between ~y32 and ~y100 the 1.12.2 islands are noise blobs like the End's, with no guarantee they connect. AoA's own move in 3.5 to "massive… fairly close together" islands supports the owner's "big, well-connected islands" brief.
- The Guardian Tower bug probably makes the main dungeon much rarer than 1/35, which leaves the realm with only rare floating micro-dungeons. I have not verified this in-game.

### Gaps
- Exact island heights and coverage per chunk; Floating Stone and Voliant Heart drop rates (loot JSON is not in the A12 copy).

---

## 3. Immortallis (structure-only progression gauntlet)

### Takeaway
Immortallis is a fixed, void-surrounded, 9-room linear dungeon built once at chunk (0,0). You cannot build or mine there. Rooms alternate between fetch objectives ("collect gold, deposit it"), kill quotas of 25 and four boss duels, and are gated by coins and Tribute thresholds. It was memorable as a set piece but was removed in 3.5, partly because the developer did not want several structure dimensions. Nowhere replaced it.

### Cited Findings
- **Generation:** `populate()` returns unless the chunk is (0,0), where it builds `ImmortallisDungeon` at (−15, 17, −40). The dungeon is Rooms 1–9 built in order. The log message says: "Is this about to cause some 'cascading worldgen' warnings in the console? Yes." — [A12 dimension/immortallis/ChunkGenImmortallis.java line 53; structure/immortallis/ImmortallisDungeon.java]
- **Footprint:** the rooms extend from x+41 (Room 1) to x+255 (Room 9), are 9–26 blocks tall and up to 86 deep. Each room has ~2,100–3,060 blocks of Archaic tiles, lights and glass — [scan of A12 structure/immortallis/ImmortallisRoom1–9.java]
- **Rules:** no skylight, black fog, no spawning (`getSpawningChance() = 0`). Only torches can be placed or mined, and block items, buckets, armour stands and hanging entities cannot be used — [A12 WorldProviderImmortallis.java `canPlaceBlock`, `canMineBlock`, `canInteractWith`; BiomeImmortallis.java]
- **Sequence** of the 9 Progressor blocks — [A12 block/functional/utility/ImmortallisProgressor.java; en_us.lang `message.feedback.immortallisProgression.*`]:
  1. Starting Coin. Gives a Return Crystal: "Collect the gold from the Goldums. Deposit it into the gold accumulators!"
  2. Pluton tribute ≥100. Spawns **Klobber** (1,000 HP, 20 dmg).
  3. Progress Coin 1. "Slay 25 skeletal spirits for Erebon!"
  4. Erebon tribute ≥100. Spawns **Proshield** (500 HP).
  5. Progress Coin 2. Collect pure gold from Goldus.
  6. Pluton = 200. Spawns **Mirage** (750 HP, ranged).
  7. Progress Coin 3. "Slay 25 evil spirits for Erebon!"
  8. Erebon = 200. Spawns **Flash** (1,000 HP, speed 0.329).
  9. Progress Coin 4. Teleports you to (0,20,0), consumes the Return Crystal and resets tribute.
- **Mobs (spawner only):** Goldum and Goldus (1 HP gold carriers), Fenix (1 HP, 15 dmg), Reaver 40 HP, Urioh 35, Visage 60, Urv 75, Ghastus 15, Shavo 125, Skeledon 120, Skelekyte 115. Skeledon and Skelekyte have invisibility — [A12 entity/mobs/immortallis/*.java; ImmortallisRoom*.java spawner placement]
- **NPCs and rewards:** the Dungeon Keeper sells the Starting Coin for 5 Silver Coins and Archaic building blocks for Dungeon Tokens. The Token Collector sells the Vulcane plus augments (5–25 tokens), the Vulcammer Maul (28) and the Meteor Staff (26). Advancements also ask you to craft gold and ornate statues of each boss — [A12 entity/npcs/trader/EntityDungeonKeeper.java, EntityTokenCollector.java; en_us.lang `advancement.aoa.immortallis.*`]
- **Access:** "Take a Hit From a Construct of Strength Affected by a Strength Buff" (a Crystevia mob). Welcome message: "Erebon and Pluton welcome you." — [A12 en_us.lang]
- **Wiki account:** the wiki describes the run as collecting 5 Impure Gold, killing 25 spirits and collecting 5–10 Pure Gold. Weapons in the second shop unlock only after every challenge — [AoA wiki: Immortallis](https://adventofascension.fandom.com/wiki/Immortallis)
- **Perception:** "a preset dimension where you play lethal arena-based puzzles and games"; a Creeper bug could "permanently destroy the rooms" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension). The names of the tasks shown when you win "are all very odd" — [NamuWiki](https://en.namu.wiki/w/Nevermine). One Forum post advises saving Immortallis for last because it is a "dungeon dimension that isn't really modifiable" — [Minecraft Forum thread](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/mods-discussion/2917920-how-should-i-order-the-aoa-and-divinerpg)

### Inferences
- The gauntlet maps cleanly to Realmforge's "miniboss → miniboss → boss" instance. Its weak parts are the fetch rooms and the gating on an external system (Tribute). A single shared copy at (0,0) is also bad for multiplayer and griefing. Realmforge's per-player instanced plots avoid that.

### Gaps
- Klobber, Proshield, Mirage and Flash attack patterns were not analysed beyond stats. The exact Dungeon Token drop sources were not traced.

---

## 4. Iromine (golden steampunk lands)

### Takeaway
Iromine is a noise-terrain, golden-palette steampunk realm under a black sky. It has machine mobs, several of which ignore entire damage types. Its points of interest are an NPC lab that sells the boss keys, a boss maze, a boss "charging pad", and small spawner passages. It is functional but sparse, and the damage-type immunities and an item-stealing mob make it more annoying than memorable.

### Cited Findings
- **Terrain:** overworld-style noise generator (coordScale 684.412, heightStretch 12, baseSize 8.5), biome height variation 0.5, Iromine Stone with Iromine grass, its own cave carver, sea level 0, Dimensional Fabric at y0 — [A12 dimension/iromine/ChunkGenIromine.java; biomes/BiomeIromine.java; CaveGenIromine.java]
- **Atmosphere:** sky colour 0 (black), clouds at −1000 (none), black fog, celestial angle 1.0, water tint (255,249,173), portal TANGERINE_YELLOW — [A12 WorldProviderIromine.java; BiomeIromine.java; BlockRegister.java line 2240]
- **Decoration:** 40 plant attempts (Iro grass, Lurchians, Irotops) and 3 trees (Irogold or Irodust). In 1/50 chunks a floating "Iro Doubler" or "Iro Floater" ornament sits 15–19 blocks above the ground — [A12 BiomeIromine decorator]
- **Ore:** Lyon, 2 veins per chunk, 3–7 ores, y45–64. It smelts to a Lyon Ingot, used for the Lyonic armour set, greatblade and staff — [A12 BiomeIromine; OreConfig `SubCategoryLyon`; RecipeRegister line 27; en_us.lang]
- **Mobs:** Mechamaton 20 (120 HP, **immune to melee and ranged**), Polytom 20 (flying ranged), Quickpocket 20 (75 HP), Voltron 20 (blaster-immune), Mechachron 1 (rare, 114 HP). NPCs: Metalloid trader 1, Foraging Master 1, Undead Herald 1. Spawner only: Enforcer (89 HP, immune to melee and ranged) and Mechyon (slowness) — [ESR; A12 entity/mobs/iromine/*.java]
- **Structures (effective):** IroMaze 1/1,300 (40×26×47, 2 parts, **Silverfoot altar** inside). Charging Pads 1/1,301 (17×10×17, **Mechbot altar**). Professor's Lab 1/601 (Professor NPC). Iro Pillar 1/602 (Golden Lottoman). "Iro passage" group 1/151, split four ways between Enforcer Tower (12×17×12, 2 Enforcer spawners, 1 chest), Mechyon Temple (11×11×12, 2 Mechyon spawners, 1 chest), and Iro Passage 1 and 2 (Enforcer and Mechyon spawners, 1–2 chests each). Energy Rune Shrine 1/650 — [SC `SubCategoryIromine`; A12 ChunkGenIromine.populate(); structure/iromine/*.java]
- **Bosses:** **Silverfoot** (1,400 HP, applies slowness; summoned with a Silvro Coin). **Mechbot** (2,500 HP, explosion-immune; drops "MechFall" shells, 4 extra below 1,250 HP, and fires faster below 1,000 HP; summoned with a Gold Spring) — [A12 block/functional/altar/SilverfootAltar.java, MechBotAltar.java; entity/boss/mechbot/EntityMechbot.java lines 86–105]
- **Key economy:** the Professor sells the Silvro Coin (for 5 silver coins plus 2 magnet shards) and the Gold Spring (for an Iro Crate). He also trades a Mecha Gear (gold coin plus 5 Lyon ingots), discharge capsules, cannon upgrades and the Demolisher (3 silver coins plus 7 Lyon ingots). The Metalloid buys and sells most AoA ingots and gems for copper coins — [A12 entity/npcs/trader/EntityProfessor.java, EntityMetalloid.java]
- **Access:** "Use a Blank Realmstone on a Mineralization Station." Tier 2 — [A12 en_us.lang; AoA wiki: Dimensional Tiers](https://adventofascension.fandom.com/wiki/Dimensional_Tiers)
- **Perception:** a steampunk dimension with mechanical mobs. "There are no hunter mobs in this dimension!" The page warns you may lose items to the Quickpocket — [NamuWiki](https://en.namu.wiki/w/Nevermine). The GitHub emptiness thread proposed merging it with Immortallis; a reply said they don't fit — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895)
- Iromine is not in A21 data — [A21 data/aoa3/dimension/]

### Inferences
- "Buy the boss key from a realm NPC" turns bosses into a currency sink. It works, but it replaces exploration with shopping, which is the loop the GitHub thread criticises.

### Gaps
- Where the Mineralization Station comes from; the Quickpocket theft mechanic was not read in code.

---

## 5. L'Borean (water realm)

### Takeaway
In 1.12.2 L'Borean is **not underwater**. It is a flat land of coral "trees" and many ponds (biome "L'Borean Ponds"), with no ores. Its best ideas are water as a boss mechanic (Hydrolisk heals in water, Dracyon floods the ground), a boss key obtained by fishing, an aquatic castle dungeon and giant corals. From 3.5 on it became a true ocean (floor y31 to a barrier ceiling at y127). In 1.21 the sea level is the world top, and arrival is protected by an air bubble around the portal.

### Cited Findings
**1.12.2**
- **Terrain:** biome name "L'Borean Ponds", height variation 0.05 (very flat), Borean Stone with Borean grass and dirt. Below the default sea level the generator places **Borean grass, not water**. Each chunk makes 8 `WorldGenLakes(WATER)` attempts — [A12 dimension/lborean/ChunkGenLBorean.java lines ~249–252, 362–369; biomes/BiomeLBorean.java]
- **Decoration:** 1 coral structure per chunk (Green 1 and 2, Pink 1, Orange, White, Yellow). 65 plant attempts (Bureal stocks, green, white and yellow waterweeds, blue Ocealites; red waterweeds 1/6,500). **There is no ore generation** — [A12 BiomeLBorean decorator]
- **Atmosphere:** blue sky, blue-green fog, permanent noon, clouds at y128, portal IRIS_BLUE — [A12 WorldProviderLBorean.java; BlockRegister.java line 2229]
- **Mobs:** Angler 20 (112 HP), Coralon 20 (120), Muncher 20 (135), Sea Viper 20 (116, poison), Neptuno 1 (rare, 132). **Coratee 20 is a passive animal** (45 HP). Glowing Pixon 12. Hauling Master 1 and Undead Herald 1 (NPCs). Spawner only: Mermage (130 HP, ranged) and Amphibior (122 HP) — [ESR; A12 entity/mobs/lborean/*.java; entity/animals/EntityCoratee.java]
- **Lore:** the bestiary calls the Angler a two-headed walrus that now lives "primarily [as] a land animal". The Amphibior heals quickly when semi-submerged and creates pools of water at its feet when it attacks — [A12 lang/bestiary/en_us/angler.txt, amphibior.txt]
- **Structures (effective):** Aquatic Castle 1/1,700 (24×49×25, 2 parts; 2 Mermage and 2 Amphibior spawners; 5 chests; a Declogging Table). Drowned Lotto Stand 1/1,701 (Boreic Lottoman; **bug:** it rolls the Aquatic Castle chance, 1,700, instead of its own 550). Dracyon Fountain 1/451 (8×8×8, Dracyon altar). Hydro Platform 1/301 (7×13×7, Hydro Table). Water Rune Shrine 1/650 — [SC `SubCategoryLborean`; A12 ChunkGenLBorean.java line 379; structure/lborean/*.java]
- **Bosses:**
  - **Dracyon**: 1,700 HP, flying. Summoned with a **Call of the Drake, which a fishing catch in L'Borean yields with a 1/10 chance** — [A12 event/PlayerEvents.java ~line 499; block/functional/altar/DracyonAltar.java; entity/boss/dracyon/EntityDracyon.java]. TV Tropes: Dracyon converts terrain into water-like puddles that heal him — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
  - **Hydrolisk**: 609 HP. Summoned with a Pure Water Stone at the Hydro Table. Heals 1 HP per tick while in water and spawns 45-HP Hydrolons — [A12 entity/boss/hydrolisk/EntityHydrolisk.java lines 100–105]
  - **Corallus**: 1,800 HP. Summoned by throwing 5 Amphibiyte Lungs into water; works anywhere, and Amphibiytes come from Overworld coves — [A12 item/misc/AmphibiyteLung.java]
- **Materials:** Coral Stone feeds the coral weapon line (Archergun, Cannon, Clogger, Greatblade, Staff, Coralstone Maul, Coralstorm Sword). The Corby minion slab drops here — [A12 en_us.lang; changelog "except corby, which is dropped in lborean"]
- **Access:** "Let a Gardencian Creature Soak In Candied Water, Then Kill it" (requires Gardencia and Candyland). Tier 3 — [A12 en_us.lang; AoA wiki: L'Borean](https://adventofascension.fandom.com/wiki/L%27Borean)

**Later versions**
- 3.5+ wiki: "a massive ocean" filled with water from the floor (y31–32) to a **barrier ceiling at y127–128**, up to 96 blocks deep, with a flat coral-grass floor. Passive life: Cod, Coratee, Salmon, Squid, Tropical Fish. Structures sit on the ocean floor — [AoA wiki: L'Borean](https://adventofascension.fandom.com/wiki/L%27Borean). TV Tropes: "a blockade of Barriers directly above the water that prevent you from surfacing" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
- 1.21 data: `sea_level: 384` (water up to the top of the world), default block `aoa3:coral_rock`, aquifers enabled. The biome `lborean_deep` has no spawns or features yet — [A21 data/aoa3/worldgen/noise_settings/lborean.json; worldgen/biome/lborean_deep.json]
- 1.21 arrival: the portal builds a **radius-3 air bubble enclosed by bubble-coral blocks out to radius 4** — [A21 source/content/block/functional/portal/LboreanPortalBlock.java]. Changelog: "Prevented L'Borean's portal from spawning on the roof, and gave it a bubble"; "Adjusted how the sky renders in L'Borean" — [A21 changelogs, grep "Borean"]
- **Perception:** "supposed to be completely underwater" and "full of lakes that make mobs there heal to full health quickly" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension). "A dimension where giant corals grow" — [NamuWiki](https://en.namu.wiki/w/Nevermine)

### Inferences
- AoA took two attempts at an underwater realm and settled on a hard ceiling plus a fully flooded column. The owner's idea of void damage near the surface is a softer version of the same barrier idea.
- Water-linked boss mechanics (healing in water, flooding) are the most distinctive L'Borean design element worth carrying over.

### Gaps
- The 3.5 underwater mob behaviour (swimming AI) was not checked in code; oxygen handling in the 3.5 L'Borean is unknown.

---

## 6. Lelyetia (two-sided paper-thin world)

### Takeaway
Lelyetia is a thin 5-block slab of land with grass on both faces and the void above and below. Trees grow up from the top face and hang upside-down from the bottom. Under the slab, gravity pushes you back up. Spire-like towers hold the dimension's NPC hub, and dungeons hang in the void below. It is one of AoA's most original layouts, but annoying flyers that knock you off ledges and a 27% share of holed chunks make it tense more than lively.

### Cited Findings
- **Terrain:** every chunk is a slab y55–59 (Lelyetia "down grass" at y55, stone y56–58, grass y59). With 3/11 probability (`rand.nextInt(11) > 7`) a chunk uses one of 7 frame or grid patterns with holes instead of the full slab — [A12 dimension/lelyetia/ChunkGenLelyetia.java lines 60–213]
- **Two-sided decoration** per chunk: 51 grass plants on top and 51 hanging under, 11 Lelyetian stems or "wigglers" up and 11 inverted below, 2+2 Churry and Achony trees on top and 2+2 inverted trees under the slab — [A12 biomes/BiomeLelyetia.java decorator]
- **Gravity rule:** below y54 fall distance is cancelled and the player is pushed upward (+0.05 motion per tick, no gravity while falling) unless a **Distorting Artifact** is in the hotbar. Above y57 gravity returns — [A12 event/dimension/LelyetiaEvents.java]. Changelog: "Added a failsafe to restart player trajectory in lelyetia if they get stuck between layers" — [A12 changelogs]
- **Atmosphere:** blue sky, orange fog (0.614, 0.321, 0.13), permanent noon, clouds at y128. **There are no ores** — [A12 WorldProviderLelyetia.java; BiomeLelyetia.java]
- **Mobs:** Flye 20 (50 HP, flying), Lelyetian Caster 20 (ranged), Lelyetian Warrior 20, Tracker 20, Grobbler 1 (rare, 0.9 knockback resistance). **Trotter 20 is a passive animal** (45 HP). Glaring Pixon 12. Logging Master 1 and Undead Herald 1. Spawner only: Zhinx, Paravite, Rawbone and Exohead — [ESR; A12 entity/mobs/lelyetia/*.java; entity/passive/EntityTrotter.java]
- **Structures (effective):** Lelyetian Tower 1/760 (14×61×14 on top of the slab, with **Hunter Master, Lelyetian Banker, Lelyetian Lottoman and Lelyetian Trader NPCs and 6 chests**). Zhinx Enclave 1/381 (11×40×13, hangs at y15 below the slab). Paravite Hive 1/382 (13×19×13 at y36). Boney Dungeon 1/383 (10×48×10 at y31, Rawbone and Exohead spawners). Graw Pillar 1/168 (5×20×5, Graw altar) — [SC `SubCategoryLelyetia`; A12 ChunkGenLelyetia.populate(); structure/lelyetia/*.java]
- **Boss:** **Graw**, 2,500 HP, flying ranged, magic-immune, explosive shots. Summoned with a Guardian's Eye. Orange or Yellow Spores used on the altar spawn a Flye instead — [A12 block/functional/altar/GrawAltar.java; entity/boss/graw/EntityGraw.java]
- **Trader:** the Lelyetian Trader trades a Gauge Rifle for 10 silver coins plus 5 yellow or orange spores, buys Zhinx Dust for 4 copper coins, and sells Lelyetian Glass — [A12 entity/npcs/trader/EntityLelyetianTrader.java]
- **Access:** "Take a Fall of at Least 26 Blocks" (Tier 1). Lelyetia then gives the Haven key (melee-kill a Flye high in the trees) and the Celeve key ("Kill a Clown With a Lelyetian Greatblade") — [A12 en_us.lang realmstone hovers]
- **Later versions:** in A21 the Lelyetia dimension type has `has_ceiling: true` and `has_skylight: false`. Its biome has music but no spawns or features. Changelog: "Tweaked Lelyetia Worldgen" — [A21 data/aoa3/dimension_type/lelyetia.json; worldgen/biome/lelyetian_plains.json; changelogs 3.7.x]
- **Perception:** NamuWiki notes the upper and lower areas split by a floor with holes, with upside-down trees in the lower space that slow falls. TV Tropes lists the Flyes as "Goddamned Bats" because their hitbox "extends so abnormally far" and they "can knock you off high structures". It also mentions "the paper-thin world" — [NamuWiki](https://en.namu.wiki/w/Nevermine); [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)

### Inferences
- Lelyetia is the best AoA precedent for "a whole island as one dungeon with paths woven vertically and horizontally". It shows a top face, a bottom face, and structures hanging into the void that you can reach because gravity pushes you back up.

### Gaps
- The exact shapes of the 7 hole patterns were not mapped.

---

## 7. Lunalus (space realm)

### Takeaway
1.12.2 Lunalus is a dark void with tiny islands of 11–15 blocks (about 1 per chunk), a pink sky and floating rooms. It has the most "civilised" settlement in AoA's H–Z set, a Zal village with 8 buildings and 7 NPC roles, but its structures are rare and the mobs mostly fly. **It has no low gravity in 1.12.2.** Low gravity, an oxygen requirement and a void that drops you back from the sky arrived later (3.5+ code, still in A21 source). Players find getting around tedious.

### Cited Findings
**1.12.2**
- **Terrain:** the land is only structures. There are 2 placement attempts per chunk, each with 50% chance, of `LunarIsland1` (15×15×15; Lunasole grass and dirt, Moonlight and Darklight orbs) or `LunarIsland2` (11×11×11; Lunalyte, Sunfire and Lunar orbs), at y36–44 and y61–69 — [A12 dimension/lunalus/ChunkGenLunalus.java lines 79–98; structure/lunalus/LunarIsland1/2.java]
- **Island decoration:** Luntar and Lunalip plants (1/10 per grass block); Lunosso and Lunicia trees (1/25) — [A12 biomes/BiomeLunalus.java]
- **Atmosphere:** `hasSkyLight = false`, black fog, sky `Color.PINK`, celestial angle 0.4 — [A12 WorldProviderLunalus.java; BiomeLunalus.java]
- **Gravity:** the 1.12.2 source has no Lunalus gravity code. Gravity-related lang entries are gear only: Lunar Armour "Jump Higher" / "Reduces Fall Damage Slightly", Lunar Bow "Small Jump Boost While Held", Lunar Archergun "Arrows Have no Gravity" — [grep A12 source for LUNALUS; A12 en_us.lang]
- **Mobs:** Explodot 20 (30 HP, flying), Lunarcher 20 (118 HP, flying ranged, gun-immune), Visular 20 (110 HP, flying), Visulon 1 (rare, 150 HP; "Added lunalus realmstone drop to Visulon"), Undead Herald 1. **There is no passive or ambient life.** Spawner only: Refluct (122 HP, immune to blasters and magic), Baumba (134 HP, stationary ranged), Zarg (120), Zorp (114), Inmate-X (138), Inmate-Y (145) — [ESR; A12 entity/mobs/lunalus/*.java; A12 changelogs]
- **Structures (effective):**
  - Lunar Village 1/1,500, a composite of 8 buildings. Lunar Atom, Lunar Lotto Platform (Lunar Lottoman), Food Market (Zal Grocer; 2 chests), Herbal House (Zal Herbalist; sells Lunacrike, Luna Globe and Lunalon seeds), Spellbinder House (Zal Spellbinder; sells runes, a Rune Shrine, a Mending book for 2 Lunaver coins, a Super→Ultra Cannon upgrade and Soulstone tools; 3 chests), Lunarade Stand (Zal Vendor), Lunar Bank (Zal Banker) and Lunar Fountain, plus Zal Citizens and a Zal Child.
  - Space Arena 1/906 (21×8×22; 2 Refluct and 2 Baumba spawners).
  - Lunar Creation Platform 1/907 (Lunar Creation Table).
  - Zarg Planetoid 1/903 (23×20×25; Zarg and 2 Zorp spawners).
  - **Lunar Maze** 1/904 (18×15×18; Lunar Enrichment Table at the centre).
  - Lunar Prison 1/854 (37×10×21; 2 Inmate-X and 2 Inmate-Y spawners).
  - Lunar Garden 1/809.
  - Observer's Eye 1/810 at y90–99 (Visualent altar). **Bug:** it rolls `lunarGardenSpawnChance` (804) instead of its own 375.
  - Lunar Rune Shrine 1/600.
  - Sources: [SC `SubCategoryLunalus`; A12 ChunkGenLunalus.java line 165; structure/lunalus/*.java; entity/npcs/trader/EntityZal*.java]
- **Boss:** **Visualent**, 2,000 HP, flying. Teleports randomly within ±10 blocks. Summoned with an Observing Eye — [A12 entity/boss/visualent/EntityVisualent.java line 54; block/functional/altar/VisualentAltar.java]
- **Materials:** Lunar ingots (lang includes "Molten Lunar Metal"), the Lunar armour and weapon set, Zargonite armour ("+10% Magic Damage Dealt"), Lunar Tokens — [A12 en_us.lang]
- **Access:** jump over Haven's void with a Gravitator, so it requires Haven and Celeve. Tier 3. Welcome message: "The portal is strangely quiet in your presence." Changelog: "Made Lunalus' portal silent per its description" — [A12 en_us.lang; A12 changelogs; AoA wiki: Lunalus](https://adventofascension.fandom.com/wiki/Lunalus)
- **Overworld tie-in:** the Lunar Invasion event ("The void of Lunalus has seeped its way into the overworld") — [A12 lang/guides/en_us/lunar_invasion.txt]

**Later versions**
- 3.5+ wiki: "a dark, asteroid-filled space dimension". Generation spans y0–256, with a starry custom sky and no sun or moon. Some asteroids are special (ice, basalt, netherrack). Features: Lunar Geode, Molten Core, Cradle of Life. Structures: Infested Asteroid, Lunar Oasis (3 variants), Stranded Lottoman, Zal Prison Camp. Ores: coal, iron, diamond, amethyst, sapphire. "Gravity in Lunalus is lower." Breathing needs armour that "Provides its own oxygen". Falling into the void "eventually teleports the player to a point above y=256". Beds explode — [AoA wiki: Lunalus](https://adventofascension.fandom.com/wiki/Lunalus)
- A21 code (`LunalusEvents`):
  - downward speed is multiplied by 0.85 each tick while falling;
  - a jump adds +0.5 vertical velocity;
  - fall distance is capped at 10;
  - 1 suffocation damage per tick unless the player is environmentally protected;
  - at y ≤ −25 the player is teleported to y350;
  - a Distorting Artifact cancels the gravity changes and the void teleport.
  - Sources: [A21 source/event/dimension/LunalusEvents.java; event/PlayerEvents.java lines 98–107, 176]
- 1.19.4 changelog: the tag `forge:armor/airtight` "Is used for atmospheric protection (Lunalus, Vox Ponds)" and defaults to the Achelos, Oceanus and Sealord helmets and the Face Mask — [A21 changelogs/3.6.26.md]
- Lunalus is not in A21 data — [A21 data/aoa3/dimension/]

**Perception**
- "Dark, with scattered spherical ground, so you need many blocks or jump-boosting armor" — [NamuWiki](https://en.namu.wiki/w/Nevermine)
- TV Tropes describes the Zal as friendly "Starfish Aliens" villagers with a mid-20th-century TV-family society — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
- The GitHub thread proposes a Lunalus/Mysterium merge and using mushrooms as planet parts — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895)

### Inferences
- The tiny islands plus the void make movement the main activity, and settlements are rare (1/1,500). The later move to larger asteroids with features and ores is AoA's own fix.

### Gaps
- When exactly low gravity first appeared (3.5.0 or later) is not confirmed; the drop rate of the Lunalus key from Visulon is unknown.

---

## 8. Mysterium (dark mushroom realm)

### Takeaway
Mysterium is a dark, red-skyed fungal realm. It is laid out as a chessboard of chunk-sized stepped mounds and sunken ponds, decorated with giant mushroom "trees". It has the best mix of points of interest in the H–Z set: a haunted castle, a Gorb village of friendly NPCs, a mushroom house, a spider cave, a golem arena, and a portal platform that leads on to Runandor. It is also the ritual site for the Runandor key.

### Cited Findings
- **Terrain:** for each chunk, with 1/3 chance (`isHole = rand.nextInt(3) == 0`) it is a sunken square of stone up to y10 with water at y11–12. Otherwise it is a stepped mound: stone y1–20, dirt y21, then 3 grass rings at y22–24. There is fabric at y0–2 — [A12 dimension/mysterium/ChunkGenMysterium.java line 60 ff.]
- **Atmosphere:** `hasSkyLight = false`, sky `Color.RED`, purple fog (0.214, 0.221, 0.53), celestial angle 0.05, purple water (88,51,142). Portal TYRIAN_PURPLE — [A12 WorldProviderMysterium.java; BiomeMysterium.java; BlockRegister line 2243]
- **Decoration:** mystic bushes, ferns and 3 rainbow grasses. Mushroom-tree structures (Blue, Green, Orange, Purple, Yellow, Mini Blue) and tiny shrooms — [A12 BiomeMysterium decorator; structure/mysterium/]
- **Ore:** Mystite, 2 veins per chunk, 5–9 ores, y2–21. Smelts to a Mystite ingot — [A12 BiomeMysterium; RecipeRegister line 28]
- **Mobs:** Fungat 20 (flying, poison), Fungback 15, Fungik 15 (ranged, mining fatigue), Eeo 15 (45 HP; attacking it spawns a Spirit Guardian and a Spirit Protector), Fungung 1 (rare). Blooming Pixon 12. Runation Master 1 and Undead Herald 1. Spawner only: Undead Troll, Banshee, Nightmare Spider and Phantom (castle), Fungock, Mushroom Spider, Runic Golem — [ESR; A12 entity/mobs/mysterium/*.java; EntityEeo.java lines ~91–99]
- **Friendly NPC life:** "Gorb Citizens are the friendly denizens of Mysterium… [they] will occasionally offer advice." The Gorb Arms Dealer sells grenades and a laser blaster; the Gorb Engineer sells Phantasm armour and an Ultraflame — [A12 lang/bestiary/en_us/gorb_citizen.txt; entity/npcs/trader/EntityGorbArmsDealer.java, EntityGorbEngineer.java]
- **Structures (effective):** Haunted Castle 1/850 (39×39×29, 3 parts; Undead Troll, Banshee, Nightmare Spider and Phantom spawners; 2 chests plus a top chest). Fungshroom 1/801 (17×18×17; Fungock). **Gorb Village** 1/652 (45×10×20; 3 NPC types). Mystic Lotto Shroom 1/502. Mushroom Spider Cave 1/453. **Mystic Portal Platform** 1/419 (13×31×9; Portal Master NPC and a pre-built **Runandor portal**). Runic Arena 1/318 (15×3×15; 2 Runic Golems). Distortion Rune Shrine 1/500 — [SC `SubCategoryMysterium`; A12 ChunkGenMysterium.populate(); structure/mysterium/*.java]. Changelog: "Removed Realm Travel Ticket requirement from Runandor Portal" — [A12 changelogs]
- **Boss:** **King Shroomus**, 1,800 HP, ranged. Heals 2 HP periodically and applies slowness, poison, nausea and weakness. The Shroom Stone works only in Mysterium — [A12 entity/boss/kingshroomus/EntityKingShroomus.java lines 70–94; item/misc/summon/ShroomStone.java]
- **Access:** "Inflict a Creepird With Poison" (so after Creeponia). Tier 2. Mysterium is also where the Runandor key ritual happens: "Take a Vox Light to Mysterium And Place a Blank Realmstone And Runic Energy on Top of it, Then Activate it With an Active Rune Stone" — [A12 en_us.lang; block/functional/lights/VoxLight.java line 26]
- **Perception:** "Looks very dark but is actually a colorful mushroom dimension," with the Portal Master and haunted castles — [NamuWiki](https://en.namu.wiki/w/Nevermine). TV Tropes records a pre-3.2 bug where the Greckon portal permanently redirected Mysterium's return portal and trapped players — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
- Mysterium is not in A21 data — [A21 data/aoa3/dimension/]

### Inferences
- Mysterium is the H–Z realm closest to the owner's "full" ideal: an NPC village, varied mid-size points of interest and portal links. It is hurt by darkness (no skylight) and the obviously tiled terrain.

### Gaps
- Gorb Village layout details; how often a Gorb Citizen gives advice and what it says.

---

## 9. Nowhere (hub, minigames and boss arenas; 3.5+, not in 1.12.2)

### Takeaway
Nowhere is a non-natural hub dimension at fixed midnight. It has no building and no mob drops. Activity portals lead to tiered parkour courses (your inventory is stored), a boss lobby whose altar sends you to a free pre-built arena (with a queue when all are busy), and a utility lobby. It replaced the removed structure realms and is the clearest AoA precedent for Realmforge's hub plus instanced arenas.

### Cited Findings
- **Dimension type (A21):** `fixed_time: 18000`, `has_skylight: false`, `height: 2032`, `natural: false`, `bed_works: false`, `respawn_anchor_works: false`, effects `aoa3:nowhere` — [A21 data/aoa3/dimension_type/nowhere.json]
- **Fixed-coordinate layout** (`aoa3:single_structure` placement):
  - hub at (0,1500,0); utility lobby at y1000; boss lobby at y500; a disabled boss lobby at y450; parkour lobby at y2;
  - **parkour courses** at z=1000: Ancient Hallways x1000, Mine Madness x1500, Twisted Treetops x2000, Aurora x2500, Nether Tourist x3500;
  - **boss arenas** at negative x and z: Bridge (−1000,−1000), City (−1500,−1000), Colosseum (−2000,−1000), Archaic Hall (−2500,−1000), Archaic Extraction Station (−3000,−1000), Shrine (−3500,−1000), Archaic Aquaduct (−4000,−1000), Desert (−1000,−1500), Swamp (−1500,−1500), Square (−1000,−2000), Spiral (−1500,−2000), Tulip (−2000,−2000).
  - Sources: [A21 data/aoa3/worldgen/structure_set/nowhere_*.json]
- **Regions and rules:**
  - parkour region x,z > 900, where abilities are locked;
  - boss region x,z < −500, where boss music plays;
  - "food-free" region x,z > 0;
  - lobby region within ±250;
  - building is creative-only;
  - falling out of the world returns you to your checkpoint, or to the return portal;
  - first-timers who fall are reset to (17.5, 452.5, 3.5).
  - Sources: [A21 source/event/dimension/NowhereEvents.java]
- **Activity portals:** PARKOUR (stores your inventory), PARKOUR_1…6 (each gives a Return Crystal), BOSSES, DUNGEON (y1501), UTILITY, and RETURN (gives back stored items and resets status) — [A21 source …/NowhereActivityPortal.java `enum Activity`]
- **Parkour tier** = (x − 500)/500, with an optional reward loot table per course. Advancements "Tier-1 Acrobat" … "Tier-6 Acrobat" — [A21 source/content/world/nowhere/NowhereParkourCourse.java lines 34, 58–71, 153; A21 lang/en_us.json]
- **Boss arenas** are data records: `structure_id`, `arena_pos`, a list of `player_spawns` and a list of `boss_spawns` — [A21 source/content/world/nowhere/NowhereBossArena.java CODEC]. Lang strings: "The altar only accepts relics of strong souls"; "Teleporting to the arena in 5 seconds... Move out of the square to cancel"; "No free arenas, please wait until another fight has concluded and try again"; "You have 5 seconds to prepare..."; plus a config "Nowhere Hardcore Boss Deaths" — [A21 lang/en_us.json `message.aoa3.feedback.nowhere.boss.*`]
- **Access:** in 1.21, "Combine a Blank Realmstone with a Shulker Box, a Nether star, Void Scales, a Recovery Compass, and a Redstone Lamp". The 3.5 wiki gives an Enchanted Golden Apple, End Crystal, Beacon and Blank Realmstone. The 3.5 description: "a single room floating in the void", no mobs, most right-click actions blocked, Ender Pearls don't teleport, and "non-player, non-tameable mobs drop no items" — [A21 lang/en_us.json; AoA wiki: Nowhere](https://adventofascension.fandom.com/wiki/Nowhere)
- Archaic Tokens exist (advancement "Tokens From Where?") — [A21 lang/en_us.json]

### Inferences
- Nowhere solves the "multiple structure dimensions" problem by keeping one non-natural dimension and allocating fixed plots for arenas and courses. Realmforge's plot allocator already does this, and the arena data shape can be copied conceptually.

### Gaps
- What the DUNGEON activity contains (it looks unfinished); the parkour reward tables; how arena occupancy is tracked and cleaned up beyond the scheduler calls.

---

## 10. Precasia (prehistoric realm; tier 1, rebuilt for 1.21)

### Takeaway
1.12.2 Precasia is a single-biome, dry and hilly "old forest" full of mostly hostile prehistoric fauna. Its set pieces stand out: a huge buried temple, underground dens, and a 38-wave **Skeletal Army** survival arena. Precasia is also the hub for two other realm keys. In 1.21 the developer rebuilt it as an Overworld-like realm with 13 biomes, herd animals, oceans, tar pits and volcanoes. AoA's own team moved from an empty realm to a full one here.

### Cited Findings
**1.12.2**
- **Terrain:** overworld-style noise (height variation 0.4) with caves (`CaveGenPrecasia`). Layers are High Precasia Stone over Low Precasia Stone, topped with Precasia grass. Sea level is 0, so there is no water. There is a normal day/night cycle (`isSurfaceWorld`), clouds at y200, and no rain — [A12 dimension/precasia/ChunkGenPrecasia.java; WorldProviderPrecasia.java; biomes/BiomePrecasia.java]
- **Decoration:** 25 plant attempts (Lucon grass, tangle thorns) and 3 tree attempts (Stranglewood or Lucalus) — [A12 BiomePrecasia decorator]
- **Ores:** four "bone fragment" ores (skull, foot, leg, chest), 2 veins per chunk each, 2–6 ores per vein, y0–39 in Low Precasia Stone. Each smelts to a **Skeletal Ingot**, used for Skeletal armour, tools, bow and archergun — [A12 BiomePrecasia; RecipeRegister lines 42–45; en_us.lang]
- **Mobs:** Dyrehorn 20, Giant Snail 20 (status-immune), Deinotherium 20 (120 HP, ranged-immune), Sabretooth 20 (90 HP), Tortione 3 (**immune to gun, melee and ranged**), Terradon 1 (rare, 105 HP). **Meganeuropsis 5 is passive** (a giant dragonfly, group 1–2). Undead Herald 1. Spawner or structure only: Kaiyu (ranged), Opteryx, Diocus, Iosaur, Spinoledon, and the Primitive Carrotop (from the Gardencia ritual) — [ESR; A12 entity/mobs/precasia/*.java; entity/passive/EntityMeganeuropsis.java]
- **Structures (effective):**
  - **Kaiyu Temple** 1/1,500. 44×36×50 in 8 parts, placed 30 blocks below the surface, with a Kaiyu spawner.
  - **Skeletal Army Arena** 1/1,001. 30×11×23 with an Army Block and a Skele Elder.
  - Jungle Lotto Hut 1/421 (Precasian Lottoman).
  - Opteryx Nest 1/301 (9×2×9).
  - **Precasian Den** 1/600, independent of the chain. One of the Diocus, Iosaur or Spinoledon dens, each 28×18×30 in 2 parts, underground at y5–19, with 2 spawners.
  - Life Rune Shrine 1/650.
  - Sources: [SC `SubCategoryPrecasia`; A12 ChunkGenPrecasia.populate() lines 363–425; structure/precasia/*.java]
- **Bosses:**
  - **Tyrosaur**: 4,000 HP, immune to guns and ranged. The Bone Horn works only in Precasia — [A12 item/misc/summon/BoneHorn.java line 124; entity/boss/tyrosaur/EntityTyrosaur.java]
  - **Skeletal Army** (wave survival): activating the Army Block starts wave 1. Each wave spawns its mobs plus a **Skele Elder (60 HP); killing that Elder within 50 blocks of the block triggers the next wave**.
  - Wave mobs escalate from 2 Skele Pigs (wave 1, 3 mob types), through Strong variants (waves 12–19; wave 19 mixes 6 normal and 6 Strong), to Elite variants (waves 20–35, rising from 1 to 12 per wave). Wave 36 is a Strong mix, waves 37–38 are an Elder alone, and **wave 39 spawns Skeletron** (1,100 HP, 70 melee damage).
  - The Army Block also re-spawns an idle Skele Elder on random ticks.
  - Sources: [A12 block/functional/altar/ArmyBlock.java; entity/boss/skeletalarmy/EntitySkeleElder.java lines 110–130; EntitySkeletron.java]
- **Hub role:** the Candyland key is "Eat a Heart Fruit in Precasia". The Gardencia key is "Throw a Blank Realmstone Into a Carrot Crop in Precasia, Then Defeat Your Creation" (a Primitive Carrotop) — [A12 en_us.lang; item/food/HeartFruit.java line 32; item/misc/BlankRealmstone.java line 61]. The Precasia key itself is "Combine a Blank Realmstone With One of Each Overworld Sapling" — [A12 en_us.lang]

**1.21 rebuild**
- **13 biomes:** caves, deadlands, desert, forest, jungle, ocean, plains, river, savannah, shore, tall forest, tar pits, volcano — [A21 data/aoa3/worldgen/biome/precasian_*.json]
- **Spawn tables:**
  - creatures: Horndron herds of 2–4 (plains, savannah, tall forest, tar pits); Deinotherium 1–3 (desert, forest, savannah, tar pits); sniffers; frogs and tadpoles; turtles;
  - monsters: Veloraptor packs of 2–3; Smilodon; Spinoledon; Attercopus (most biomes, weight 100); Chomper (river, shore); Dunkleosteus (ocean); Meganeuropsis (jungle); Scolopendis (caves);
  - ocean ambient: cod, salmon, pufferfish, tropical fish, Dark Hatchetfish, Shiny Squid, plus glow squid and squid.
  - Source: [same files]
- **Features:** skeletal remains, desert cocoons, Lucalus and Stranglewood stumps, tar pits, volcano chutes, hot springs, lava and water lakes, a bone-fragment ore, vanilla ores, suspicious cave gravel, and biome music borrowed from vanilla (lush caves, desert, forest, jungle) — [same files]
- **Structures:** Attercopus Nest (random spread spacing 14, separation 6) and Precasian Lotto Hovel (spacing 24, separation 12). Opteryx nests were added — [A21 data/aoa3/worldgen/structure_set/attercopus_nests.json, precasian_lotto_hovels.json; A21 changelogs]
- **New key method:** "Unearth a buried realmstone from within a jungle" (an Overworld brushable jungle gravel feature) — [A21 lang/en_us.json; A21 data/aoa3/neoforge/biome_modifier/ore_brushable_jungle_gravel.json]

**Perception**
- Tier 1, "A dimension with the theme of an old forest," fossil-like minerals combining into a skeletal ingot — [NamuWiki](https://en.namu.wiki/w/Nevermine)
- TV Tropes notes that before 2.5 there were no biomes and the Precasian Forest was the whole dimension — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)

### Inferences
- The 1.21 Precasia spawn tables (herd sizes, predators against grazers, ambient fish) are effectively a ready-made template for "a realm as full as a modded Overworld".
- The 1.12.2 Skeletal Army is a ready-made wave-instance structure.

### Gaps
- How 1.21 Precasia animals behave (herding AI, fleeing) was not read in code; Kaiyu Temple loot.

---

## 11. Runandor (rune realm)

### Takeaway
Runandor is a bright blue "rune" realm whose terrain is made of flat chunk-sized plateaus at heights 11–36, set by a counter that advances with each generated chunk. It has strong hostiles, a boss arena, a rune-reroll station, and a notable **timed "Rune Templar" defend event**. The terrain is the most artificial in AoA, and the realm reads as a combat corridor.

### Cited Findings
- **Terrain:** the generator keeps a `count` field, seeded once with `rand.nextInt(26)` and incremented after every generated chunk (wrapping 25→0). Each chunk is filled solid to y = 11 + count. Chunks with count < 4 are flooded to y15 with water. Runic grass appears on top when the height is > 14 — [A12 dimension/runandor/ChunkGenRunandor.java lines 41–74]
- **Decoration:** 85 plant attempts (Runic bush, Rune bulbs, Magias) and 2 Runic trees (6 variants) — [A12 biomes/BiomeRunandor.java]
- **Ore:** Elecanium, 3 veins per chunk, 2–5 ores, y1–35. Smelts to an Elecanium ingot — [A12 BiomeRunandor; RecipeRegister line 19]
- **Atmosphere:** blue sky and fog, permanent noon. Portal ELECTRIC_BLUE — [A12 WorldProviderRunandor.java; BlockRegister line 2246]
- **Mobs:** Ariel 20 (115 HP), Bouncer 20 (110), Eye Creature 20 (130), Paladin 20 (109 HP, 16 dmg, **immune to melee and ranged**), Runicorn 20 (132), Runicorn Rider 1 (rare). Gleaming Pixon 12 and Ambient Pixon. Anima Master 1 and Undead Herald 1. Spawner only: Runic Guardian (ranged) and Spectral Wizard (immune to guns and ranged) — [ESR; A12 entity/mobs/runandor/*.java]
- **Structures (effective):** **Clunkhead Arena** 1/800 (24×14×20; 2 Runic Guardian spawners; Clunkhead altar). Runic Tower 1/651 (10×37×10; Runic Lottoman). Spectral Cage 1/501 (Spectral Wizard). Rune Randomisation Station 1/317 (Rune Randomizer). **Rune Templar Bunker** 1/302 (15×9×15, underground at y3; 4 Rune Templars) — [SC `SubCategoryRunandor`; A12 ChunkGenRunandor.populate(); structure/runandor/*.java]
- **Rune Templar event:** you activate a Templar with its colour rune plus Runic Energy. It then loses 0.25 HP per tick from 400 HP (**80 s**). While it drains, it spawns Runic Lifeforms (80 HP; 1/125 chance per tick, at most 4 nearby). At 0 it drops loot and resets — [A12 entity/mobs/runandor/templars/EntityRuneTemplar.java lines 60, 78–110; EntityRunicLifeform.java]
- **Boss:** **Clunkhead**, 2,200 HP, ranged-immune. Its projectile damage is random, 1 + rand(35). It heals. Summoned with a Mega Rune Stone — [A12 entity/boss/clunkhead/EntityClunkhead.java; block/functional/altar/ClunkheadAltar.java]
- **Access:** the Vox Light ritual in Mysterium, or the pre-built Runandor portal on Mysterium's Mystic Portal Platform. Tier 3 — [A12 en_us.lang; structure/mysterium/MysticPortalPlatform.java; AoA wiki: Dimensional Tiers](https://adventofascension.fandom.com/wiki/Dimensional_Tiers)
- **Perception:** "A rune-themed dimension full of blue." — [NamuWiki](https://en.namu.wiki/w/Nevermine). The bestiary describes Ariels walking "the Lands of Runandor, defending it from any potential invaders" — [A12 lang/bestiary/en_us/ariel.txt]. The GitHub thread suggests moving Runicorns and Paladins into Shyrelands — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895)
- Runandor is not in A21 data — [A21 data/aoa3/dimension/]

### Inferences
- Because height depends on a per-instance counter and not on coordinates, the terrain depends on the order chunks are generated. It is not reproducible from the seed alone, and it produces staircases with 25-block cliffs.
- Runandor is not a room or maze realm, despite the brief's guess. Its only maze-like elements are bunkers and arenas.

### Gaps
- Rune Templar loot; how the Rune Randomizer works.

---

## 12. Shyrelands (tier-4 light realm; a literal grid of rooms in 1.12.2)

### Takeaway
1.12.2 Shyrelands is an **infinite grid of identical 16×16 walled courtyards, one per chunk**. Each has 25-block walls with archways on all four sides, an open roof, and a cloud layer at y64. About 1 room in 25 holds a feature (NPC stand, spawner ambush, wizard checkpoint, shrine), and rarer dungeons lie below the floor. Wind gusts blow players off the wall tops. It is AoA's only true room-maze realm, and 3.5 replaced it with a bright forest full of ruins.

### Cited Findings
**Terrain and atmosphere**
- Every chunk is built the same way:
  - Shyrelands stone y0–29, grass floor y30 (white brick rim);
  - walls y31–44: yellow brick at x/z 0–3 and 12–15, white pillars at 4 and 11, a white lintel at y44 over x 5–10;
  - a yellow band at y45, glass at y46–54, white brick at y55;
  - random glass "windows" (1/12 per wall block);
  - `buildRandomDoor` decorates each side with one of 5 arch patterns in y40–43.
  - Source: [A12 dimension/shyrelands/ChunkGenShyrelands.java lines 67–150]
- **Clouds:** 1/3 of the columns at y64 get a barrier placeholder, later swapped for `SHYRE_CLOUD` blocks — [A12 ChunkGenShyrelands.java lines 73, 389]
- **Wind:** while a player is at y ≥ 55 with nothing above them, every 80 ticks there is a 1/3 chance of a random horizontal push (0–1 velocity), a chat message and a wind sound — [A12 event/dimension/ShyrelandsEvents.java]
- **Atmosphere:** blue sky, yellowish fog (0.414, 0.421, 0.13), permanent noon. Portal YELLOW. "A blinding light shines through the portal." — [A12 WorldProviderShyrelands.java; BlockRegister line 2247; en_us.lang]
- **Decoration:** 9 inverted Shyre stocks; 64 plant attempts (Shyre stock structures, Shyre weed, Arcbulb, Arcflower, Horizon daisies); Trilliad bloom 1/40 — [A12 biomes/BiomeShyrelands.java]

**Mobs**
- Natural spawns: Sysker 20 (163 HP), Shyre Knight 20 (140 HP, 18 dmg), Axiolight 20 (invisible), Arc Flower 20, Arcbeast 20 (170 HP), Stimulo 20 (blaster-immune), Omnilight 20 (flying ranged), Soulvyre 5 (178 HP, 18.5 dmg), Stimulosus 1 (rare, 180 HP). Ambient Pixon 12. Expedition Master 1 and Undead Herald 1 — [ESR; A12 entity/mobs/shyrelands/*.java]
- An Arc Flower is "actually [an] Arcworm using the ground to hide", with a head growth "that looks very similar to nearby flora". The Arcbeast is cow-like ("citizens of the Shyrelands seem to consider Arcbeasts as such"), "very timid", and attacks out of fear — [A12 lang/bestiary/en_us/arc_flower.txt, arcbeast.txt]

**Structures (effective)**
- Craexxeus Tower 1/600 (39×45×39 in 2 parts, about 2.5×2.5 cells; Craexxeus altar).
- Whitewashing Station 1/275 (Whitewashing Table).
- **Room-feature group** 1/25, placed at the cell's origin (y31). One of: Divine Platform (Divine Station), Arc Wizard Checkpoint (**4 Arc Wizard spawners**), Soulscorne Ambush (**4 Soulscorne spawners**), Strange Shrine, Shyrelands Lottoman stand, Shyre Banker stand, Shyre Archer stand.
- **Underground dungeons** 1/250, at y2–16 inside the stone base: Lightwalker, Luxocron or Shyre Troll dungeon (15×6×15, 1 spawner each).
- Sources: [SC `SubCategoryShyrelands`; A12 ChunkGenShyrelands.populate() lines 421–489; structure/shyrelands/*.java]
- `AncientShrine` (Ancient Altar) is registered, but no world-gen code calls it — [A12 StructuresHandler.java line 122; grep shows no caller]

**Boss, materials, access**
- **Craexxeus**: 3,000 HP, flying ranged, fires "nukes". On death it spawns **Xxeus** (3,000 HP, melee 25), making a two-phase fight. Summoned with an Ancient Ring, which the Shyre Archer sells for 2 Shyregem and 2 Shyrestone ingots — [A12 entity/boss/craexxeus/EntityCraexxeus.java lines 90–117; EntityShyreArcher.java]
- **Ores:** Shyrestone ore and Shyregem ore (1/8 of placements), max(10, rand(19)) = 10–18 single-block placements per chunk, y2–26. They smelt to Shyrestone ingots and Shyregems, which make the Shyre Sword, Staff and Blaster and the Shyregem Bow. The Shyre Archer also sells the Sunshine Bow — [A12 BiomeShyrelands; RecipeRegister lines 39–40; en_us.lang; EntityShyreArcher.java]
- **Access:** the key combines a Blank Realmstone, Magic Mending Solution, Haunted Flower, Glowstone, Darkly Powder and Bloodstone. Tier 4 (prerequisites Greckon and Dustopia) — [A12 en_us.lang; AoA wiki: The Shyrelands](https://adventofascension.fandom.com/wiki/The_Shyrelands)

**Later versions and perception**
- 3.5+ wiki: "a bright forest dimension filled with ruined structures". The surface is above y=100, bright rock runs down to y62, then Shyre crust, with Shyre Pillars down to the void. There is no day/night cycle "despite the presence of a sun". Ruins include the Blade of the Heavens, Ruined Arch, Shyre Mausoleum and Temple of the Sun — [AoA wiki: The Shyrelands](https://adventofascension.fandom.com/wiki/The_Shyrelands)
- "A kingdom-like dimension with an alien atmosphere full of mobs trying to kill you" — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
- Shyrelands is not in A21 data — [A21 data/aoa3/dimension/]

### Inferences
- The grid has no dead ends: every wall keeps a 6-wide opening from y31 to y39. It is a lattice, not a maze. Navigation is trivial but repetitive, which may be why 3.5 replaced it.
- A room-maze realm should vary room sizes and connections and close some doors.

### Gaps
- No player commentary specifically on the 1.12.2 grid layout was found.

---

## 13. Vox Ponds (toxic post-apocalyptic swamp)

### Takeaway
Vox Ponds is a two-layer toxic wasteland: a lower pond floor at y16–17 under an upper crust at y32–38. It drains health constantly unless you wear a Hazmat suit or face mask. Its landmarks are very tall towers (up to 97 blocks), ruined shops and lamp posts every ~6 chunks, plus an "unkillable" Destructor mob. It has the highest structure density of the H–Z set but almost no reward structures (no chests) and no ores. It is atmospheric but punishing.

### Cited Findings
- **Layers:** toxic stone y0–15, water y16–17, air y18–31 (the lower level), toxic stone y32–35. The top is 50% toxic stone and toxic waste (y36–37) and 50% toxic dirt with toxic grass (y36–38). There is fabric at y0–2 — [A12 dimension/voxponds/ChunkGenVoxPonds.java lines 58–90]
- **Decoration:**
  - 1/6 chunks get a lamp post, either hanging at y27 or standing at y16;
  - toxic trees (0–2), 12 toxic tentacles, 6 Vox branches, 8 mini tentacles on toxic waste;
  - support beams 1/16;
  - 2–7 toxic stems hanging under the crust from y28;
  - dead grass and Vox shrooms.
  - Sources: [A12 ChunkGenVoxPonds.populate(); biomes/BiomeVoxPonds.java]
- **Atmosphere:** sky `Color.GREEN`, dark-green fog (0.034, 0.121, 0.06), celestial angle 0.35, toxic water colour (103,147,0). Portal OLIVE. "You begin to feel sick as weird feelings grow inside you." — [A12 WorldProviderVoxPonds.java; BiomeVoxPonds.java; en_us.lang]
- **Hazard:** every tick, armour-bypassing absolute damage of 1 is attempted unless the player wears a full Hazmat set or a Face Mask (or has IC2 protection). Advancement "Oops" — [A12 event/dimension/VoxPondsEvents.java; utils/ItemUtil.java lines 577–585]
- **Mobs:** Toxxulous 20 (status-immune), Grocculate 20 (0.9 knockback resistance), Gadgetoid 20 (gun-immune), Alarmo 20 (0 dmg, applies slowness), Centinel 20 (ranged). Fischer 1 (rare). **Destructor 1 (999 HP, `SPECIAL_COMBAT_ENTITY`)**. Undead Herald 1. Spawner only: Nightwing and Exoid — [ESR; A12 entity/mobs/voxponds/*.java]
- Destructors are "completely unkillable by normal means" and turn aggressive only when an Alarmo alerts them. In 3.5 the Night Watchers moved to Vox Ponds and became passive — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
- **Structures (effective):** Enigma Station 1/866 (Enigma Table, at y18). **Control Tower** 1/507 (11×55×11), **Cell Tower** 1/508 (11×97×11), **Observation Tower** 1/509 (16×30×20). None of the towers has spawners or chests. Vox building group 1/131: Destroyed Store 1 or 2 (16×9×16), Exoid Platform (Exoid Station), or **Voxxulon Beacon** (Voxxulon altar plus a Vox Light). Nightwing Island 1/98 (5×2×5, floating at y18). Vox Lotto Outpost 1/85 (9×22×9). Poison Rune Shrine 1/600 — [SC `SubCategoryVoxPonds`; A12 ChunkGenVoxPonds.populate(); structure/voxponds/*.java]
- **Boss:** **Voxxulon**, 2,000 HP, stationary (speed 0), gun-immune, 30 melee damage. Every tick there is a 1/200 chance it teleports onto its target if that target is within 60 blocks, a 1/200 chance of a poison pulse, and a 1/10 chance of a meteor at each player. Summoned with a Vile Stone — [A12 entity/boss/voxxulon/EntityVoxxulon.java lines 69–79; block/functional/altar/VoxxulonAltar.java]
- **No ores** (the biome has no `doOreGen`). Materials are Vox Ponds Tokens, Toxic Lump and the Vox Cannon — [A12 BiomeVoxPonds.java; en_us.lang]
- **Access:** "Show Any Trader in Creeponia Your Blank Realmstone While Wearing a Hazmat Suit". Tier 2. Vox Ponds provides the Vox Light needed for the Runandor ritual — [A12 en_us.lang; structure/voxponds/VoxxulonBeacon.java]
- **Perception:** a "post-apocalyptic, toxic, mutant-filled world" where "There are mobs that cannot be killed" — [NamuWiki](https://en.namu.wiki/w/Nevermine). TV Tropes: "What is presumed to be a nuclear disaster wiped out an Earth-like world." — [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/AdventOfAscension)
- In A21 the "airtight" tag covers Vox Ponds atmospheric protection, but the dimension is not in A21 data — [A21 changelogs/3.6.26.md; data/aoa3/dimension/]

### Inferences
- Damage is attempted every tick. Vanilla's hurt cooldown likely limits it to about 1 damage per 0.5 s (my reading of vanilla behaviour, not checked in game). Without protection, the realm is a timer.
- Tall towers with no reward or mobs are pure landmarks, so the realm looks busy without giving players a reason to explore.

### Gaps
- What the Enigma Table and Exoid Station do; how the Alarmo–Destructor link is coded.

---

## 14. AoA content in the vanilla Overworld (1.12.2, with 1.21 notes)

### Takeaway
AoA turns the Overworld into its densest "realm": dozens of biome-specific hostile families, 7 random world events (giants, creeper hosts, death champions, ghost re-runs, an Abyss "blood hunt", a Lunalus invasion, a full moon), wandering trader NPCs, 6 ores, 3 small structures (the ruined portal frame starts the realm chain), and 2 summonable bosses. 1.21 cuts this to about 25 spawn entries.

### Cited Findings
- **Always-on spawns across all Overworld biomes:** Bomb Carrier, Everbeast, Faceless Runner, Ghost, Mother Void Walker, Shadow, Trickster, Void Charger, Void Walker, Bugeye, Dicer, Night Reaper, Demon Reaper (1), Clown — [ESR `getOverworldSpawns`]
- **Biome families:**
  - mountains: Goalby, Grunt, Magicke;
  - snowy: Hunch, Polar Ursa, Yeti, Snow Charger;
  - sandy: Desert Charger, Furlion, Sand Golem, Sphinx, Wickett;
  - savanna: Chimera, Boneback, Rammerhead, and the passive Elkanyne;
  - jungle: Bush Baby, Stinger;
  - swamp: Chomper, Skipper, Fishix, Swamp Charger, Hag;
  - warm conifer: Black Ursa, Hiding Fungi, Natura, Skolle, Urka;
  - mesa: Hill Charger, Muckopede;
  - ocean: Pincher;
  - beach: Sea Charger, Spinux, Sea Troll, Troll Trader;
  - plains, hills and forest: Cyclops, Charger, Sasquatch, Headless Hunter, the passive Glistening Pixon, Bone Creature, King Charger, Brute, Ghostine Ancient, Goblin, Nightfly;
  - plains extras: Horndron, Warclops, Ancient Golem;
  - forests: Dead Tree;
  - NPCs: Assassin, Naturalist, Realmshifter, Undead Herald, Lottoman.
  - Source: [ESR]
- **Events** (1/n chance per day or night): Big Day 1/50 (Wood, Stone, Ice, Sand and Leafy giants), Creep Day 1/45 (Creeper Host), Death Day 1/37 (Reaper Twins, Triclops, Headless Destroyer, Death Hunter), Soul Scurry 1/59 (ghostly re-spawns of 6 mobs), Blood Hunt 1/31 (Abyss mobs Bloodmist, Linger, Anemia), Lunar Invasion 1/26 (Roloscope, Vertebron, Walker, Terrestrial, Modulo), and Full Moon on every full moon (Irkling, Skellox, Scrubby, Night Watcher, Dark Beast) — [A12 ConfigurationUtil `SubCategoryOverworldEvents` lines 203–240; ESR event lists; A12 lang/guides/en_us/*.txt]
- **Structures:**
  - Amphibiyte Cove 1/20 in ocean biomes only (17×7×18, 4 Amphibiyte spawners);
  - **Ruined Teleporter Frame** 1/130, underground at y10–19 (11×9×15, Corrupted Traveller NPC);
  - Wind Rune Shrine 1/150, floating 30 blocks above the ground.
  - Sources: [A12 dimension/overworld/OverworldGen.java; SC `SubCategoryOverworld`; structure/overworld/*.java]
- **Onboarding chain:** find a ruined portal frame; get a Worn Book and Blank Realmstone from the Corrupted Traveller; collect Ancient Rock; find the four Carved Runic Blocks in "dungeons, caves, temples, and villages"; craft the Carved Rune of Power; build a 5×6 Ancient Rock frame; complete realmstone challenges — [A12 lang/guides/en_us/what_to_do_first.txt]
- **Ores:** Amethyst (y14–30), Rosite (y17–47), Limonite (y8–67), Runium (y5–132), Jade (y7–19), Sapphire (y4–11) — [A12 OverworldGen.doOreGen]
- **Bosses:** Smash (500 HP; Troll Idol, Overworld only). Corallus (5 Amphibiyte Lungs into water) — [A12 item/misc/summon/TrollIdol.java line 75; item/misc/AmphibiyteLung.java]
- **1.21:** Overworld and Nether spawns are reduced to biome modifiers: ancient golem, bomb carrier, bush baby, charger, chomper, cyclops, ghost, goblin, 5 giants, king charger, sasquatch, shiny squid, tree spirit, void walker, yeti, plus an End Void Walker. Ores are jade, limonite and runium (large and small). Ruined Teleporter Frames use random spread spacing 12, separation 8 — [A21 data/aoa3/neoforge/biome_modifier/; worldgen/structure_set/ruined_teleporter_frames.json]

### Inferences
- The Overworld feels alive in AoA because of event variety and biome families, not structures. The same tools (events, biome-specific families) were never applied inside the realms.

### Gaps
- Carved Runic Block loot rates in vanilla chests.

---

## 15. AoA content in the vanilla Nether (1.12.2)

### Takeaway
AoA adds a modest Nether layer: 9 fire-themed hostiles, 2 NPCs, Emberstone ore, a spawner pit, fire rune shrines and 2 summonable bosses. The Nether is also entered through AoA's own portal system.

### Cited Findings
- **Spawns:** Embrake 60, Fake Pigman 80, Flamewalker 60, Hellspot 60 (blindness), Infernal 10, Wither Wizard 55 (wither), Skeletal Cowman 45, Hellcat 50, Little Bam 35. Withering Lottoman and Undead Herald NPCs. Most of these mobs are fire-immune — [ESR `getNetherSpawns`; A12 entity/mobs/nether/*.java]
- **Structures:** Nethengeic Pit 1/200 (16×10×16, Nethengeic Beast spawner, y25–59). Fire Rune Shrine 1/100 (y10–59) — [A12 dimension/nether/NetherGen.java; SC `SubCategoryNether`; structure/nether/*.java]
- **Ore:** Emberstone, 6 veins per chunk, 5–8 ores, y5–124 in netherrack. Emberstone ingots make tools and a sword — [A12 NetherGen.doOreGen; OreConfig; en_us.lang]
- **Bosses:** Nethengeic Wither (Nethengeic Callstone, Nether only; immune to explosions, fire and magic). King BamBamBam (900 HP; Explosive Idol, Nether only) — [A12 item/misc/summon/NethengeicCallstone.java line 164; ExplosiveIdol.java line 44; entity/boss/*]
- **Access and role:** the Nether key is "Throw Out a Blank Realmstone While Submerged in Lava". The Abyss key is "Summon a Wither in The Nether" — [A12 en_us.lang]
- **1.21:** Nethengeic Pit uses spacing 30, separation 8. Nether spawns are embrake, flamewalker, infernal and little bam — [A21 structure_set/nethengeic_pits.json; biome_modifier/]

### Inferences
- AoA leaves the Nether's layout alone and only adds mobs. That fits the owner's view that the vanilla Nether is empty: AoA did not fix it either.

### Gaps
- None material for this scope.

---

## 16. Comparisons for Realmforge's candidate realms

### Takeaway
AoA already tried versions of every candidate realm: sky islands (Haven), a water realm (L'Borean), dinosaurs (Precasia), space (Lunalus), a room grid (Shyrelands), and a gauntlet and hub (Immortallis and Nowhere). In every case where AoA rebuilt a realm (Haven 3.5, L'Borean 3.5 and 1.21, Shyrelands 3.5, Lunalus 3.5, Precasia 1.21), it moved toward **bigger landmasses, more biomes and passive life, environmental mechanics with a counter-item, and fewer flat grid tricks**.

### Cited Findings
- **Sky islands ↔ Haven.**
  - 1.12.2: End-style noise islands, vanilla clouds at y128, green sky and permanent noon. Floating micro-dungeons sit below the islands, and a Guardian Tower (29×83×29, spawner floors, boss altar on top) stands on them. The void doubles as a key mechanic: jumping it with a Gravitator gives the Lunalus key, and a Floating Stone dropped into it returns as a Heavy Boulder.
  - 3.5: "massive", mountainous islands "fairly close together", mostly passive mobs.
  - Sources: [section 2; A12 ChunkGenHaven.java; Gravitator.java; FloatingStone.java; AoA wiki: The Haven](https://adventofascension.fandom.com/wiki/The_Haven)
  - Lelyetia adds a two-sided slab, inverted trees and dungeons hanging below, with gravity pushing you back up — [section 6; A12 LelyetiaEvents.java]
  - Iromine shows how to drop clouds: `getCloudHeight() = -1000f` — [A12 WorldProviderIromine.java]
- **Underwater ↔ L'Borean.**
  - 1.12.2: ponds on land.
  - 3.5: an ocean from y31 to a barrier ceiling at y127–128.
  - 1.21: sea level 384 and a portal air bubble.
  - Water as a boss resource: Hydrolisk heals 1 HP per tick in water; Dracyon floods the arena.
  - Fishing in the realm yields the boss key (10% per catch).
  - Aquatic Castle dungeon (5 chests, 4 spawners). Coral giants as "trees".
  - Passive fish arrived only in 3.5.
  - Sources: [section 5]
- **Dinosaurs ↔ Precasia.**
  - 1.12.2: a single dry biome with mostly hostile fauna, buried temple, dens, and the 38-wave Skeletal Army.
  - 1.21: 13 biomes, herds of 2–4, predators and fish, tar pits and volcanoes, and the "testing a reduction of mob spawns" changelog note.
  - Precasia also hosts other realms' key rituals.
  - Sources: [section 10]
- **Space ↔ Lunalus.**
  - 1.12.2: 11–15-block islands about 1 per chunk, no skylight, pink sky, a Zal village with 8 buildings at 1/1,500, a Lunar Maze, a prison and arenas. No low gravity.
  - Later: fall ×0.85 per tick, +0.5 jump, fall capped at 10, oxygen from airtight helmets, the void returns you from y350, asteroid features and ores.
  - Players complained about needing "many blocks or jump-boosting armor".
  - Sources: [section 7; A21 LunalusEvents.java; NamuWiki](https://en.namu.wiki/w/Nevermine)
- **Room-maze ↔ Shyrelands, plus the maze structures.**
  - The 1.12.2 Shyrelands grid is one 16×16 room per chunk with four archways, 1/25 feature rooms, dungeons under the floor at 1/250, and a boss tower spanning several cells.
  - Maze structures: IroMaze (40×26×47, boss altar at the end), Lunar Maze (18×15×18, crafting-table prize), and Immortallis (9 rooms in a ~255-block chain).
  - Nowhere's parkour courses (for example Ancient Hallways) are tiered.
  - Runandor is **not** a room realm.
  - Sources: [sections 3, 4, 7, 9, 11, 12]
- **Dungeon and hub ↔ Immortallis and Nowhere.**
  - Immortallis: a linear gauntlet alternating objective rooms (collect and deposit, kill 25) with four duels, gated by coins and Tribute, with no building and a token shop for unique weapons. Removed because the developer did not want several structure dimensions.
  - Nowhere: one non-natural hub with activity portals, stored inventory, checkpoints, tiered parkour, and a data-driven pool of boss arenas with queueing.
  - Sources: [sections 3, 9]

### Inferences
- Every rebuild pointed the same way the owner wants to go: large walkable landmasses, multiple biomes inside one realm style, passive and ambient life, and one environmental rule per realm with a counter-item.

### Gaps
- No primary player reviews compare these specific realms against each other.

---

## Relevance to Realmforge

### Adopt
1. **A safe arrival pocket for the underwater realm.** The shared portal places a sealed air bubble, as AoA 1.21 does for L'Borean (radius 3 of air inside a coral shell out to radius 4). Pair it with Realmforge's "void damage near the surface" instead of AoA's barrier ceiling at y127–128 — [A21 LboreanPortalBlock.java; AoA wiki: L'Borean](https://adventofascension.fandom.com/wiki/L%27Borean)
2. **The Skeletal Army pattern as Realmforge's wave-survival instance type.**
   - A start block; each wave has a leader whose death triggers the next.
   - Trash escalates through normal (waves 1–11) → "Strong" (12–19) → "Elite" (20–35); a final boss appears at wave N+1 (Skeletron at wave 39).
   - Abandoning is detected when the leader dies more than 50 blocks away.
   - Shorter than 38 waves for solo play.
   - Source: [A12 ArmyBlock.java; EntitySkeleElder.java]
3. **A data-driven boss arena registry** like Nowhere's (structure id, arena position, player spawn list, boss spawn list), with a "no free arena, please wait" fallback and a 5-second teleport warning. This feeds Realmforge's plot allocator for instanced dungeons and boss rooms — [A21 NowhereBossArena.java; A21 lang]
4. **Boss and dungeon keys earned through realm activities**, matching the owner's "crafted key item opens a temporary portal". Examples:
   - fish up a component (10% per catch in L'Borean);
   - a rare natural mob drops it (Voliant Heart);
   - a realm NPC sells it for realm materials (Shyre Archer: 2 Shyregem + 2 Shyrestone → Ancient Ring);
   - an environmental trick (a Floating Stone thrown into the void returns as a Heavy Boulder).
   - Sources: [sections 2, 4, 5, 12]
5. **One signature environmental rule per realm with a counter-item unlocked by progression.**
   - Lelyetia pushes you up under the slab (Distorting Artifact disables it).
   - Shyrelands wind gusts hit exposed high ground.
   - Vox Ponds atmosphere drains HP (Hazmat suit or Face Mask).
   - Lunalus has low gravity, oxygen and a void that returns you from the sky.
   - Every rebuilt AoA realm kept or added one. For Realmforge: underwater pressure/depth, space oxygen, sky-realm wind, a sandstorm event in the desert (owner's M4 desert; never temperature, a vision hard no).
   - Sources: [A12 LelyetiaEvents, ShyrelandsEvents, VoxPondsEvents; A21 LunalusEvents]
6. **Per-biome spawn tables with passive and ambient categories**, using A21 Precasia's layout as the template: herds of 2–4 grazers, predator packs of 2–3, river frogs and tadpoles, ocean fish and squid, cave-only species, all per biome. This directly answers "neutral mobs, animals… as full as a well-modded Overworld" — [A21 data/aoa3/worldgen/biome/precasian_*.json]
7. **Ambient "harvestable life" tied to a profession**, like pixons siphoned with a bowl for realm-specific infusion stones. This is a cheap way to make realms feel inhabited and give non-combat reasons to stay — [A12 EntityPixon.java; bestiary ambient_pixon.txt]
8. **A timed defend event as a no-puzzle open-world encounter.** Modelled on the Rune Templar: activate with a key, survive about 80 s while adds spawn (at most 4 nearby), get loot at the end. It fits open, non-instanced dungeons — [A12 EntityRuneTemplar.java]
9. **Per-realm music and ambience from day one.** AoA 1.12.2 shipped every realm silent (NULL_MUSIC) and later added biome music — [A12 Enums.java line 16; A21 biome JSONs]

### Adapt
1. **Sky-island realm**
   - Take Haven 3.5's "massive, mountainous, fairly close together" islands, not 1.12.2's End-style blobs.
   - Turn clouds off as the brief asks; Iromine's 1.12.2 provider does this with a cloud height of −1000.
   - Make one large island a vertical dungeon, combining the Guardian Tower (spawner floors, boss on top) with Lelyetia's hanging structures and push-back gravity, so the void is a hazard rather than instant death.
   - Use Angelica-style levitation snatchers only as rare elites.
   - Sources: [sections 2, 6]
2. **Space realm**
   - Keep AoA's later low-gravity numbers as a starting point (fall ×0.85 per tick, jump +0.5, fall capped at 10).
   - Give players large asteroids and planetoids to walk on, not 11–15-block rocks, and add a void return such as "fall back from y350".
   - Anchor the realm with a frequent settlement like the Zal village (8 buildings, 7 NPC roles, food, seeds, runes), placed far more often than 1/1,500 chunks.
   - Sources: [section 7]
3. **Dinosaur realm**
   - Use the 1.21 Precasia biome mosaic (tar pits, volcano, deadlands, river, ocean, jungle) and its herd life.
   - Keep 1.12.2's large set pieces as dungeons: the buried 44×36×50 temple, the 28×18×30 underground dens, and the wave arena.
   - Bone-fragment ores leading to bone gear make a good realm material loop.
   - Avoid Tortione-style mobs with triple immunity.
   - Sources: [section 10]
4. **Room-maze realm**
   - Use Shyrelands' "one room per cell" lattice as the macro-grid, but vary cell sizes (1×1 to 3×3 chunks), close some walls so real dead ends exist, and give each room type a purpose (ambush, NPC stand, shrine, loot, stairs to an under-layer).
   - Raise feature density well above AoA's 1 room in 25.
   - AoA abandoned this layout in 3.5, so prototype it first.
   - Sources: [section 12]
5. **Underwater realm content**
   - Aquatic Castle as a dungeon; coral giants as trees; bosses that use water (healing in water, flooding the arena); fishing-based keys.
   - Add the passive fish AoA only added later.
   - Sources: [section 5]
6. **Gauntlet instance**
   - Immortallis's chain (objective room → miniboss → objective room → miniboss → … → boss) maps to Realmforge's "miniboss → miniboss → boss".
   - Swap the fetch rooms ("collect gold, deposit") for combat rooms, given the owner's "almost no puzzles".
   - Run it per player in instanced plots, not one shared map at (0,0).
   - Sources: [section 3]
7. **Hub ideas from Nowhere:** no building, stored inventory for activities, checkpoints, and an activity-portal board. Realmforge's shared portal structure with a selector block can borrow the "activity portal" presentation. Parkour is optional — [section 9]
8. **Realm unlock tasks**
   - AoA's realmstone "challenges" make you use the previous realm's mechanics (melee-kill a Flye high in Lelyetia to unlock Haven; jump Haven's void with a Gravitator to unlock Lunalus).
   - Realmforge can reuse this as quest steps that attune its non-consumable realm items.
   - Prefer in-realm actions over AoA's cross-realm scavenger hunts (Shyrelands needs items from 4+ realms).
   - Sources: [A12 en_us.lang `gui.realmstoneMenu.hover.*`]
9. **Structure density targets**
   - AoA's signature structures are each 1 per ~300–1,700 chunks; with decorations, total attempts run from 1 per 5 to 1 per 121 chunks.
   - For "as full as a modded Overworld", set structure_set spacing so a player meets a point of interest every few chunks. A21 already uses spacing 12–14 (separation 6–8) for common small structures.
   - Add a GameTest that counts structures per N chunks, to catch bugs like AoA's.
   - Sources: [section 1; A21 structure_set JSONs]
10. **Respawn rule:** every 1.12.2 AoA realm forbids respawning there (`canRespawnHere = false`). Since Realmforge realms are persistent shared worlds, the owner should decide this explicitly, for example respawn at the realm's portal or a camp — [A12 WorldProvider*.java]

### Avoid
1. **Silent, single-biome, frozen-sky realms.** This is the root of AoA's emptiness (NULL_MUSIC, BiomeProviderSingle, fixed celestial angle in 10 of 11 H–Z realms) — [section 1]
2. **Terrain aligned to the 16-block chunk grid, or terrain that depends on generation order** (Mysterium's chessboard, Runandor's counter-driven plateaus, Shyrelands cells, Vox Ponds layers) — [sections 8, 11, 12, 13]
3. **Hostile-only spawn tables.** In 1.12.2 each H–Z realm had at most one passive species — [ESR]
4. **Mobs immune to whole damage categories without a tell** (Mechamaton and Enforcer melee+ranged, Paladin melee+ranged, Tortione gun+melee+ranged), **unkillable mobs** (Destructor 999 HP) and **item theft** (Quickpocket). A timed, readable form switch like Rockrider's (300-tick cycle) is the acceptable version — [sections 2, 4, 10, 11, 13]
5. **Constant damage you cannot avoid** until a specific suit (Vox Ponds), and **knockback flyers in void realms** (Flye "Goddamned Bats") without a fall safety net — [sections 6, 13]
6. **Realms that are a single fixed structure** (Immortallis). AoA's own developer removed them. Use instanced dungeons inside themed realms plus one hub instead — [AoA wiki: Immortallis](https://adventofascension.fandom.com/wiki/Immortallis)
7. **The "boss item → boss → shop → next realm" loop with nothing to come back for.** The community named it as the core emptiness problem — [GitHub #1895](https://github.com/Tslat/Advent-Of-Ascension/issues/1895)
8. **Unguarded world-gen chances.** AoA 3.3.6 has copy-paste bugs: the L'Borean Drowned Lotto Stand uses the castle chance, the Lunalus Observer's Eye uses the garden chance, and the Haven Guardian Tower check uses x instead of z. Two structures (Rockrider Boulder, Ancient Shrine) are registered but never generated. Realmforge's data-driven structure sets plus automated count tests should catch these — [A12 ChunkGenLBorean.java line 379; ChunkGenLunalus.java line 165; ChunkGenHaven.java line 278; StructuresHandler.java lines 122, 390]
