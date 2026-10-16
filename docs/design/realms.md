# Realms

> The realm progression structure (RLM-40): which realms Realmforge has, how a player moves through them, and what
> fills them. Written on 2026-10-12 from Matěj's decisions of 2026-10-11 and 2026-10-12 (Jira RLM-40 and RLM-47) and
> the reference-game research ([reference-games.md](../research/reference-games.md), cited by item ID such as
> RE-9). Updated on 2026-10-16 with the v1 realm layout (RLM-188): Matěj's decisions of 2026-10-14 to 2026-10-16 on
> level bands, chapters and caps, realm names, new and dropped realms and realm tool tiers. It is a first version:
> Matěj expects to change it after playing, so it sets rules and direction, not every detail. Realm names are the
> names players read, approved by Matěj with the [lore outline](../lore/outline.md) (RLM-139, RLM-188); mob and
> place names are working names. The [Void realm](#the-void-realm) section is RLM-95's design, which waits for his
> approval (RLM-164). Plan: [roadmap](../roadmap.md); the XP model that fills the bands:
> [leveling.md](leveling.md); the Nether and End: [ADR 0025](../decisions/0025-remove-the-nether-and-end.md), amended
> by [ADR 0032](../decisions/0032-v1-realm-layout-amends-the-nether-and-end-removal.md).

## At a glance

v1 has eight realms on the main path and one hub (Matěj, 2026-10-14 and 2026-10-15, RLM-188). Bands overlap, as in
WoW Classic: each realm starts 2–6 levels before the previous one ends, and bands are 8–14 levels wide, so some
realms are deeper than others and a player can come back for a harder optional dungeon.

| Realm | Name | Levels | Chapter | Milestone | Identity | Signature content |
| --- | --- | --- | --- | --- | --- | --- |
| [Overworld](#the-overworld) | Overworld | 1–10 | 1 | M1 | home | the first story realm; realm portals built anywhere |
| [Stone realm](#the-stone-realm) | Undervault | 6–18 | 2 | M1 | earth and stone | one giant cave, the mine of the whole game, mines with friendly NPCs, catacombs, a boss arena, ancient debris |
| [Desert realm](#the-desert-realm) | Sunwastes | 16–24 | 3 (twin) | M4 | sun and sand | dunes and big oases, pyramid and sphinx dungeons |
| [Tundra realm](#the-tundra-realm) | Everwinter | 20–30 | 3 (twin) | M4 | frost | frozen lakes with something beneath, a nordic fortress, an ice castle |
| [Hub realm](#the-hub-realm) | Olympus | from 30 | none | later | the heavens | a celestial city and palace full of NPCs, quests into realms 4–7, trading |
| [Prehistoric realm](#the-prehistoric-realm) | Prestoria | 26–40 | 4 | later | the ancient wild | dinosaurs |
| [Swamp realm](#the-swamp-realm) | Quagmire | 36–44 | 5 | later | slime | slime-block biomes, several kinds of slime, a slime dungeon |
| [Fire realm](#the-fire-realm) | Ashfire | 40–52 | 6 | later | fire | black and red plains, burnt trees, lava lakes, volcanoes, grounded dragons |
| [Void realm](#the-void-realm) | Unmade Isles | 48–60 | 7, the last of v1 | later | void | islands joined by bridges and chains, guard towers, the Void Cathedral and the finale, malachite gear |

**Names** (Matěj, RLM-139 and RLM-188): the names players read, from the [lore outline](../lore/outline.md#the-realms).
**A realm name never takes an article** (Matěj, 2026-10-15 and 2026-10-16): Overworld, Undervault, Sunwastes,
Everwinter, Olympus, Prestoria, Quagmire, Ashfire, Unmade Isles, in every text and every document. Where a player
first meets one (the guide, the sigil tooltip, the arrival title), it carries a plain subtitle with its element, such
as "Undervault — Stone Realm" ([style guide](../lore/style.md#text-types)). This document keeps "stone realm",
"desert realm" and so on as plain descriptions. Renamed on 2026-10-15: the Rimefields became Everwinter, the
Forgefire Ashfire, and the Brink Unmade Isles (RLM-164).

## Progression rules

### Realms

- **A realm is a custom dimension built around one style of biome**, not one biome: a desert with big oases, a
  tundra with frozen lakes. Each realm splits into sub-regions of rising level, like a Hytale zone with its three
  regions (RE-3).
- **v1 has the realms of [At a glance](#at-a-glance); more may follow.** Not every realm has to be on the main
  path: some exist only for gear or a specific item, and progress may run through two realms at once (FTB
  Architect's Exodus is the reference; the twins of chapter 3 do). An optional side realm takes its level from the
  realms a player must visit to reach it (PR-15).
- **The Void realm is always the last realm on the main path** (Matěj, RLM-95). A new main-path realm joins the
  chain before it, never after it, so its chapter is always the last one and its level band the game's highest.
  Its place is data, not code: adding a realm before it moves its level band (its `dimension_rules`) and its sigil's
  recipe down the chain ([The Void realm](#the-void-realm)).
- **Portals start in Overworld** (Matěj, RLM-128 and RLM-188): a portal in Overworld opens onto any realm the player
  has unlocked, and a portal built inside a realm opens only back to Overworld
  ([realm portal spec](realm-portal.md#the-realm-selector)).
- **Every realm feels full**, like a well-modded Overworld (Regions Unexplored, Better Villages, random structures
  and dungeons), never empty like the vanilla End: neutral mobs, animals, small structures, vegetation and landmarks.
  That is measured, not guessed (WF-1, WF-2):
  - small sites (camps, ruins, mine entrances) every 12–16 chunks, medium sites every 18–42 chunks, landmarks every
    40–80 chunks, checked by a GameTest that counts structures per area;
  - spawn tables per biome with passive, ambient and hostile mobs, so no realm has only hostile life (WF-14);
  - a friendly or neutral faction next to a hostile one, each with its own settlements (WF-3);
  - one identity element visible on the realm's mobs and drops (RE-5), and music and ambience from the start (RE-8);
  - Olympus is the exception: a small, passive realm of NPCs ([The hub realm](#the-hub-realm)).
- **No horror realm with limited visibility** (RE-18). Every realm has its own light (glowstone, crystals, a sky),
  so Dark Vision is a comfort, not a requirement.

### Chapters and levels

- **A realm is a chapter, and the twins share one** (Matěj, RLM-188): chapter 1 is Overworld, chapter 2
  Undervault, chapter 3 Sunwastes and Everwinter together, then Prestoria, Quagmire, Ashfire and Unmade Isles as
  chapters 4 to 7. Olympus is no chapter. The twins each have dungeons across their own band, and the player
  alternates between them (waystones make that easy). The old rule "10 levels per chapter" is gone; Matěj expects to
  change the numbers at least once after playing.
- **A chapter's level cap is its last level:** 10, 18, 30, 40, 44, 52 and 60, raised by the chapter's main
  questline (Matěj, 2026-10-15; C2 implements it, [architecture 10.2](../architecture.md#102-leveling)). XP stops at
  the cap, so a finished chapter brings no level by itself ([leveling.md](leveling.md#chapters-and-caps)).
- **What is mandatory:** every realm's main questline and its sigil, for both twins too (the twin seal needs both
  shards). Not every dungeon or boss is. Levels can also come from repeatable instanced dungeons.
- **A main questline ends at about two thirds of its band,** so the next realm opens at the start of its own band,
  and the top of a band holds harder optional content a player comes back for.
- **Levels 1–10 are Overworld's, and Undervault opens at about level 6.** The old "levels 1–5 in Overworld" is gone.
  Mob levels by area show the danger (W1), so the realm needs no hard level lock. It opens through chapter 1's main
  questline, whose last step is the realm's unlock quest and also gives the Stone Sigil
  ([realm portal spec](realm-portal.md#access-and-unlocks)). Overworld's own final boss gates Undervault once it
  exists (Matěj, 2026-10-16; [Overworld](#the-overworld)). The item rule of
  [Opening the next realm](#opening-the-next-realm) (RE-9) starts with the second realm, because Overworld has
  neither a realm ore nor a boss drop to craft the item from.
- 1 stat point (Matěj, 2026-10-15, RLM-196) and about 1 skill point per level; spell limits per chapter as in D4
  (RLM-54).
- Each chapter ends in a short main quest chain whose last step opens the next band (PR-1); everything else is
  optional.

### Realm tool tiers

Matěj's rule (2026-10-15, RLM-188; RLM-194 implements it, in M4):

- **A realm's own blocks** (stone, wood, ores) are mined at full efficiency only by tools made from that realm's
  materials or a later realm's. A pickaxe from realm N+1 is fully effective in N+1, N, N−1 and every realm below.
- **A tool from an earlier realm** still works, at a reduced efficiency (first guess 25 %, to tune), whatever its
  enchantments or mining-speed gems, because it is not made of that realm's materials. Target feel: mining a new
  realm with the previous realm's pickaxe is about as fast as mining vanilla stone with an iron pickaxe. Slow, not
  painful.
- **So an effective tool for a realm** needs that realm's materials, mined there first with the old tool, plus other
  items. The recipes are open ([open question 13](#open-questions)).
- **The tiers** (Matěj, 2026-10-15): vanilla tools from wood to diamond are Overworld's tier; netherite is
  Undervault's, so no tier sits between diamond and netherite; the twins get two tiers in band order, Sunwastes then
  Everwinter; Unmade Isles' tools are the top tier (this replaces the Void section's earlier "no malachite tools:
  netherite stays the best tool tier").
- **Undervault is the exception:** every pickaxe is 100 % effective there. Its common stone is slow instead and
  never breaks instantly ([The stone realm](#the-stone-realm)). Its high-tier ores drop nothing for a pickaxe below
  their tier, like obsidian mined with an iron pickaxe ([open question 14](#open-questions)).
- **In M1** the reduced efficiency never applies, since no realm after Undervault exists yet; M1 keeps Undervault's
  stone hardness and its ancient debris (W2a) and the netherite templates in its dungeon loot (W6).

### Gear archetypes

Matěj's direction for armour (2026-10-15), designed step by step as sets, weapons and curios are needed:

- **Melee:** vanilla armour (armour and armour toughness).
- **Caster:** robes in leather, iron, diamond and netherite tiers, made mainly from a magic cloth, with magic shield,
  spell power and mana. A full diamond-tier caster set costs about as much effort as a full diamond set (24
  diamonds); whether the tier's base material is part of the recipe is open.
- **Ranged and rogue:** less armour than melee, dodge chance and movement speed; partly base materials plus an
  advanced leather from animals that are hard to kill and spawn only in some biomes (that leather plus diamonds
  makes the diamond-tier set).
- Claude's first proposal for Overworld and Undervault:
  [Realmforge: gear v1 – Overworld a Undervault (návrh)](https://claude.ai/code/artifact/bbef7300-4ccc-4901-8cac-4e18005d2f22)
  (Czech). Implementation tickets follow once Matěj has tuned it.

### Dungeons

- **2–4 dungeons per realm**, some procedural, some instanced. An instance portal can sit in a structure that spawns
  in the realm.
- **0–3 bosses per dungeon:** trash and loot only; one boss; or miniboss → miniboss → boss.
- **Completion rule: only instanced dungeons have one, killing the final boss.** A wave-survival instance (waves in
  an instance, like Hytale's wave fights) completes when its last wave is cleared. Open-world dungeons (like a Nether
  fortress) have no defined completion.
- **Endings vary** by dungeon type ([ADR 0012](../decisions/0012-procedural-dungeons-and-dimensions.md)): a boss
  lair, a treasury, waves, an escape, a mounted run, ruins to explore.
- **Every progression item has a guaranteed source:** keys and chain items drop at 100% (LE-1), and an NPC sells the
  keys of dungeons already done (LE-2), so a failed or repeated run never needs luck.
- **Before every realm's final boss** stands an optional "Prepare" quest with recommended gear and items (Matěj,
  2026-10-16, RLM-224).

### Puzzles and traps

Puzzles are welcome under these rules (Matěj, 2026-10-12, replacing the earlier "almost no puzzles"):

- **Visible progress is the dealbreaker.** A puzzle shows progress while the player works on it. Pressing random
  buttons and levers without knowing whether anything happened is the anti-pattern (the Ancient Remnant dungeon in
  L_Ender's Cataclysm). Plain levers and pressure plates are not interesting.
- **Liked:**
  - **light beams and crystals** (the favourite): rotate crystal blocks, made from the stone realm's crystal, that
    emit beams until they form a given shape or hit a given block;
  - **spells as keys:** Slam breaks a cracked wall, Frostbolt freezes water into a bridge, fire lights braziers.
    Every class always has a way through, never one spell school only;
  - **lore riddles:** inscriptions hint which of several sarcophagi to open; a wrong choice spawns mobs;
  - **a golem at the gate** that lets the player through when given the ore its glyph shows (ties into mining);
  - **sokoban-lite:** push 2–3 statues onto marks in a few moves, with a reset button.
- **Matěj's own ideas** (between puzzle and trap): fake rooms that look normal but hold nothing; a chest that is a
  mimic (it wakes on right-click); disappearing blocks; portals inside a dungeon that move the player ahead or back
  (at most about 3 rooms); the key to a door hidden in a chest, a vase or another container.
- **Also wanted, not a puzzle:** an NPC inside the dungeon asks for an item and gives a progress item or a hint in
  return.
- **Rejected:** a miniboss shield that only the environment breaks; statues that move only while nobody looks.
- **Rules:**
  - a pure puzzle takes about 5–10 minutes, never 30; mixed with combat or NPC interaction, a section may take up to
    about 30 minutes;
  - failure means a fight where possible, otherwise a small setback (a teleport a few rooms back);
  - clear visual hints where they are needed (the in-dungeon portals need none, though a hint for which is which is
    fine);
  - on the main path to the boss: 1 bigger puzzle, 2–3 smaller ones, or 1 bigger and 1 smaller; anything more is
    optional, with a bonus chest;
  - no parkour.

### Opening the next realm

- **A realm has no completion flag of its own.** The main path runs through its instanced dungeons and the chapter's
  quest chain.
- **The next realm's item comes from the realm the player leaves:** crafted from the current realm's ore or gem plus
  the guaranteed drop of its main-path boss, with the recipe shown in the quest log (RE-9). The item is never
  consumed. It opens the portal; the realm itself unlocks for each player through the realm's unlock quest (Matěj,
  RLM-128). The portal itself, the item per realm and the access rules are the
  [realm portal spec](realm-portal.md)'s job (RLM-41).

### Travel

The layout of every realm assumes cheap travel; nobody walks 1,000–10,000 blocks to get somewhere.

- Waystones are easy to get, sit at the hub and in settlements (TR-1, TR-7), and generate near main sites.
- Early flight: a flying mount from chapter 2 (our own mounts, tiers 1 to 3 in M4, RLM-115), or a long-range
  teleport spell. Elytra come from chapter 3's final boss, the step into chapter 4 (RLM-138,
  [ADR 0025](../decisions/0025-remove-the-nether-and-end.md)): faster in the air than any mount, while a mount wins
  on overall speed, utility and combat (RLM-115). The v1 layout moved both chapters under these rules
  ([open question 15](#open-questions)).
- Dungeon portals and hubs are marked on the map once found (TR-11), and every quest target has a locator or a
  vendor fallback (PR-17).

## The realms

<a id="the-overworld"></a>

### Overworld

Chapter 1 (levels 1–10), the realm of M1. Identity: home.

- **The first story realm** (Matěj, 2026-10-16), kept close to vanilla plus the pack's world mods. The Nether and End
  are removed ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).
- **Its own dungeons, bosses and loot** come with a later milestone, and its final boss is what a player must kill to
  enter Undervault from then on (Matěj, 2026-10-16). Until then chapter 1's main questline, ending with the Stone
  Sigil, is the gate, and RLM-224's "Prepare" quest stands before the quest that enters Undervault. M1 has no Overworld
  dungeon or boss (Matěj chose option b on RLM-188, 2026-10-16).
- **The hub.** Every realm's way back leads to Overworld. [Realm portals](realm-portal.md) link like the
  Nether's (Matěj, RLM-128), so players build as many as they like, anywhere, and waystones they place make
  Overworld the node between realms (TR-7). With cheap waystones the hub costs no walking. As a place, the hub
  waits (Matěj, RLM-139): in M1 the Wayfarers' Lodge is a voice in the quest book, and a Lodge house in a village,
  with NPCs and services that grow each chapter (WF-8), may come with the M4 village overhaul
  ([lore outline](../lore/outline.md#decisions)). Olympus, from level 30, is the second hub: the place of quests,
  not of portals ([The hub realm](#the-hub-realm)).

### The stone realm

Name: **Undervault**. Chapter 2 (levels 6–18), the realm of M1. Identity: earth and stone. Mining is its main
profession: it is the mine of the whole game, in the spirit of Stoneblock (Matěj, RLM-188). As the first sealed realm
it offers most of what the Nether offered ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).

- **Shape: one giant cave** (Matěj, 2026-10-15): a bedrock floor and ceiling around a single walkable floor level,
  give or take a few blocks, with narrower caves in the walls. No further floor layers, no chasms, no floating
  islands: open space that is easy to cross, unlike the Nether. Sub-regions of rising level from the realm's origin
  outwards (about 6–10, 10–14 and 14–18), with a crystal grotto as one sub-zone (RE-11). Portals arrive near the
  origin: they link at 1:16, so every Overworld portal within 20,000 blocks of Overworld's origin arrives in the
  first band ([realm portal spec](realm-portal.md#the-coordinate-scale)).
- **No weather and no environmental rule** (wind, sandstorm): it is an enclosed cave (Matěj, 2026-10-15).
- **Stone:** many custom stone types (no vanilla stone; variants in the spirit of diorite, granite and andesite)
  mixed on floor, walls and ceiling, with Y-range rules (this ore or gem only at Y 10–30, that stone only near the
  ceiling). **The common stone is slow** (Matěj, 2026-10-15): it never breaks instantly, and with a diamond pickaxe
  (Efficiency IV and some mining-speed bonuses) it takes about half as long as obsidian, a hardness of about 25
  (obsidian has 50).
- **Light and life:** glowstone near the ceiling, as in the Nether; grassy pockets where things grow; special oases
  with water.
- **Materials:**
  - at least 3 new ores or gems for gear, weapons, curios and refining, one of them the crystal the light-beam
    puzzles use;
  - **gem ores** (Matěj, 2026-10-15): Ruby and Sapphire, in veins of at most 3. Eight gems around one base item
    (3×3) craft an upgrade for netherite armour: defense, offense or utility, at most one per piece and no set
    bonuses, so players combine them freely. **Topaz** is for curios. All three also go into other recipes of
    Undervault's tier or higher, so the armour upgrade is never a gem's only use;
  - **ancient debris** (Matěj, 2026-10-15): netherite is Undervault's tier, so the debris generates here, only below
    the walkable floor's Y, never in walls or ceilings;
  - **high-tier ores** that drop only for a pickaxe from a higher realm ([Realm tool tiers](#realm-tool-tiers)), so
    players come back to mine; probably materials for curios, reforging and the like, not gear;
  - quartz set in stone (quartz early, because other mods expect it);
  - glowstone near the ceiling;
  - patches of soul sand with nether wart, or a mob that drops nether wart;
  - ore bands in the walls and pillars of the walkable floor, and a treasure ore with its own loot table (PF-5).
- **Mobs:** stone-looking mobs and animated statues, each variant with one clear role (BM-11); a rock mob that climbs
  and a timid critter that flees into the rock; animals too, such as a "Stoneshroom" stone cow; shulkers with grey as
  their default colour (the source of shulker shells).
- **Natural structures:** 2–3 kinds of mines, some with friendly NPCs who take the realm's ores and gems and trade
  back the item that unlocks a dungeon; mineshafts like vanilla's but larger and more structured (Matěj, RLM-188);
  bone structures like the Nether's fossils, which suit a giant cave; Stonehenge-like rings and big boulders as
  landmarks. The NPC mine near the boss site is the realm's friendly settlement next to danger (WF-9).
- **Dungeons:**

  | Dungeon | Kind | Ending | Bosses |
  | --- | --- | --- | --- |
  | Catacombs, an open catacomb like the interior part of Mine Cells | instanced, procedural | a treasury | none, or minibosses |
  | Stone arena with an altar, a procedural run whose end room is the altar arena | instanced, procedural | the boss, summoned at the altar | 1 |
  | Dwarf-like fortress in the rock | open-world structure | none (no completion) | 0–2 minibosses |

  The netherite upgrade smithing template is a fairly common item in every Undervault dungeon's loot (Matěj,
  2026-10-15).
- **The chain:** a mine NPC trades realm ores for the catacombs key; the catacombs' treasury holds the arena key at
  100%; inside the arena instance an NPC gives the item that summons the boss at the altar; the boss drops, at 100%,
  the material for the next realm's item (RE-9) and finishes the chapter's main chain. The mine NPC sells the keys
  again for repeat runs (LE-2).
- **What M1 takes from this** (a proposal for the M1 tasks, not a plan change): W2a's dimension is this realm's
  worldgen (the one-floor cave, the stone types and their hardness, the ancient debris, the sub-regions); W3's 1–2
  custom mobs (D11) are two animated statues (a melee and a caster archetype); W6's type A (ends in the boss's lair)
  is the stone arena and type B (ends in a treasury) the catacombs, both with netherite templates in their loot;
  W5's boss fights in the arena. The fortress waits for M4's world-structure dungeons, and the NPC mines, the gems
  and the full material set grow as later tasks need them.

### The desert realm

Name: **Sunwastes**. Chapter 3 (levels 16–24), M4, the twin of Everwinter; both are needed for further progress.
Identity: sun and sand.

- **Landscape:** dunes, sand mountains, big oases (sunken basins with water and fauna found nowhere else, RE-12),
  abandoned towns that are empty or hold friendly NPCs.
- **Dungeons:** a pyramid, a sphinx, an abandoned desert town or village.
- **Materials:** structures full of suspicious sand and red sand whose reworked brushing loot holds a little trash,
  desert-themed gems for refining and enchanted books. Ancient debris and netherite scrap moved to Undervault (Matěj,
  2026-10-15, [ADR 0032](../decisions/0032-v1-realm-layout-amends-the-nether-and-end-removal.md)); what Sunwastes
  gets instead is open ([open question 16](#open-questions)). Its tools are the first of the twins' two tiers.

### The tundra realm

Name: **Everwinter**. Chapter 3 (levels 20–30), M4, the twin of Sunwastes. Identity: frost (no temperature
mechanic: the vision rules it out).

- **Landscape:** big frozen lakes with something beneath them (under-ice content), ice spikes, frozen shipwrecks;
  Hytale's frozen zone adds glacier lakes, hot-spring plains, stilt villages and coastal ice camps (RE-13).
- **Dungeons:** a nordic fortress, a big igloo or a group of igloos, an ice castle.
- **Visual hints:** ice spikes and a campfire mark a cave worth entering (WF-5).
- **Tools:** the second of the twins' two tiers.

### The hub realm

Name: **Olympus** (the name may still change, Matěj). Open from level 30, no chapter. Identity: the heavens. Matěj's
rules (2026-10-15, RLM-188):

- **A small, finite, passive realm in the heavens,** built like one giant dungeon: a celestial city and palace full
  of NPCs, inspired by Hytale's Forgotten Temple and WoW's Dalaran (also a flying city). Technically a dimension, but
  more like a dungeon.
- **Nothing can be broken or built there,** it is bounded, and there is no flight. The only ways out are the portal
  the player came through, or a jump into the void (death).
- **What it offers:** quests into Prestoria, Quagmire, Ashfire and Unmade Isles (sometimes into lower realms or
  dungeons), about a fifth of those realms' quests ([leveling.md](leveling.md#content-budget-per-realm)); trading,
  profession levelling, dialogues, and campaign hints the guide leaves out (the guide only says something like
  "… Olympus citizens could know more about this prehistoric realm."). Players keep coming back until level 60.
- **Unique NPCs live only in Olympus.** They matter to the story and give unique quests. One of them sells keys to
  late-game dungeons (early dungeon keys stay craftable, RLM-181): Matěj left the name open ("it can be Torva"), and
  Claude's pick is **Torva Flintmark**, the Cairnfolk forewoman of chapter 2, who already keeps the catacomb keys in
  the lore outline.
- **Everywhere else** (RLM-147): NPCs generated in structures, with random names and simple quests ("do a task, get
  a reward"). A few important campaign NPCs keep one title-like name in every structure, as bosses do (for example
  "The Wanderer"), like Twilight Forest's Questing Ram, who sets a task the campaign needs.
- **No custom NPCs in M1.**
- How a player first reaches it is open ([open question 17](#open-questions)).

### The prehistoric realm

Name: **Prestoria**. Chapter 4 (levels 26–40), later. Identity: the ancient wild: dinosaurs (Matěj, RLM-188). The
rest is designed with its chapter.

### The swamp realm

Name: **Quagmire**. Chapter 5 (levels 36–44), later. Identity: slime (Matěj, RLM-188).

- **Landscape:** swamp, with biomes of slime blocks.
- **Mobs:** several kinds of slime.
- **Dungeons:** a slime dungeon among them.

### The fire realm

Name: **Ashfire**. Chapter 6 (levels 40–52), later. It comes deliberately late: it has dragons, and weak dragons make
no sense. **It does not replace the Nether and is not inspired by it** (Matěj, 2026-10-16): a fire realm much further
into the game than the vanilla Nether, with no Nether structures and no Nether look; it may share some of its mobs
([ADR 0032](../decisions/0032-v1-realm-layout-amends-the-nether-and-end-removal.md)).

- **Landscape:** plains-like terrain of dark black and red blocks, burnt trees, lava lakes and volcanoes (Matěj,
  RLM-188), with dragons; passable, not blocked. A volcano the player climbs down into, with a dragon arena at the
  bottom (RE-14).
- **Dragons fight on the ground:** the vision allows no flying bosses.
- **Materials:** quartz; blazes drop blaze rods.

### The Void realm

Name: **Unmade Isles** (renamed from the Brink on 2026-10-15, RLM-188), shown as "Unmade Isles — Void Realm" where a
player first meets it. Chapter 7 (levels 48–60), the last chapter of v1, on the main path: the story's finale is here
(Matěj, RLM-139). Identity: the Void. This section is RLM-95's design from Matěj's points of 2026-10-12, and its
gameplay proposals wait for his approval in RLM-164 ([open questions 9–12](#open-questions)). The mood images he
shared (a purple void world behind a portal arch, and two images of a void zone from WoW) are inspiration only: no
art or assets of other games.

- **Place in the chain: always last** ([Realms](#realms)). Its sigil is crafted like every later main-path sigil
  (RE-9), from the chapter before it: an ore of that chapter's realm and its guardian's guaranteed drop (Ashfire's).
  When that chapter has two realms, the recipe takes both guardians' drops, as the
  [realm portal spec](realm-portal.md#realm-sigils) requires after every shared chapter, so finishing one branch
  never opens the Void realm.
  - **Built early:** Matěj may have it built before other realms. Then it follows the last chapter that exists at
    the time (for example Sunwastes and Everwinter after M4, with both drops), and each realm added before it
    moves its sigil recipe and its level band down the chain.
  - **Worlds that already reached it:** the unlock is permanent (RLM-128), so a player who has it keeps it. A realm
    added before it opens through its own quest, and the player can go back for it.
- **Shape:** large floating islands over the Void, at different heights, joined by bridges of dark stone with chain
  railings and held by great chains that hang down into the Void. Big islands close together (RE-15): about 100–300
  blocks across, with gaps of about 10–30 blocks, and no mini islands or clouds.
  - **Walkable:** every island is reachable on foot from the one a player arrives on, because bridges join each
    island to at least one neighbour nearer the realm's origin. Ramps and stairs lead between heights; there is no
    parkour.
  - **How it could generate** (for the task that builds it, which checks the APIs in the 26.1.2 sources first): one
    island per cell of a jittered grid. An island's centre and size then follow from the seed and its cell alone,
    and either chunk can place the bridge between two neighbouring cells in any generation order (RE-22).
    Probably a density function of our own for the islands, plus a structure for the bridges and chains.
- **Sub-regions** of rising level outwards from the origin, as in the stone realm (RE-3), named by their distance
  from it. Portals arrive in the first:
  1. **the near islands:** violet groves (dark wood with purple and dark-purple leaves, purple grass), dusk meadows
     with herds, and the Chainkeepers' link-houses;
  2. **the middle islands:** crystal shelves of purple and indigo spires along the island rims, thickets of twisted
     dark-blue growths with glowing orbs, and fields of floating rune obelisks;
  3. **the far islands:** dead woods on grey-violet stone, malachite scars, and the island of the Void Cathedral,
     farthest from where portals arrive.
- **Light, sky and sound:**
  - the Void below glows violet and lights the islands from beneath, and crystals and the growths' orbs glow;
  - the sky is magenta and violet, with a pale moon; a day and night cycle keeps it from freezing (RE-17);
  - it is never dark and never horror (RE-18), so Dark Vision stays a comfort;
  - music and ambience come with the realm (RE-8): low choirs, the creak of the chains.
- **Landmarks** (WF-1, WF-5): dark Roman-style Gatewright gates (arches) where bridges meet the islands; the great
  chains, anchored on the island rims; rune obelisks floating above the guard towers, the link-houses and the
  Cathedral, so a player sees from far away where something is.
- **Materials:**
  - **malachite,** green bands in dark-purple stone: the realm's ore. It grows where the Void touches forged stone
    (the [lore](../lore/outline.md#the-void-realm-unmade-isles)): on island rims and undersides, and in the
    malachite scars. Mining it means working along the edges, which the fall rule (below) keeps safe;
  - the realm's purple crystal, for building and for the Cathedral's light-beam puzzle;
  - dark wood from the violet groves;
  - **voidglass** (working name), the guard tower captain's guaranteed drop, which malachite gear needs.
- **Mobs:**
  - **the Unforged** (hostile) copy the hostile peoples of earlier realms, such as a Hewn or a Gilded, with a violet
    sheen and one detail out of place that is their tell: a mirrored swing, a blow that lands a beat late. Each
    variant has one clear role (BM-11), and earlier realms' models with new textures keep them cheap (BM-15).
    Their hits carry **unmaking**, a short, stacking loss of armour. Their kill XP rank is "tough normal", 1.25
    ([leveling.md](leveling.md#ranks));
  - **no flying hostile mob,** and none whose main attack is knockback: on islands, knockback is the worst attack
    there is, as AoA's void realms show
    ([research notes](../research/notes/aoa-dimensions-h-to-z.md));
  - **passive and ambient life** (WF-2, WF-14): grazing herds on the dusk meadows (food and leather), glowing
    drifters that float between the islands and light the gaps, and a timid, crystal-shelled critter that hides in
    the crystal shelves;
  - **the Chainkeepers** (friendly, WF-3): their link-houses stand where a great chain is anchored (WF-9), with
    traders and quest-givers.
- **The fall rule: the Void pulls you back.** A player who falls below the islands returns to the last solid ground
  they stood on, with no damage, nothing lost and no fall damage on landing.
  - A fall costs a few seconds, never the run. A player always respawns in Overworld (RLM-128), so a death here
    would cost the whole way back.
  - It is the rescue DU-4 recommends for dungeon instances, so one rule can serve both.
  - Mobs and items that fall are gone, as in vanilla.
  - It is the realm's environmental rule ([open question 3](#open-questions)), and it needs no counter item because
    it costs nothing (RE-19).
- **Flight** works here as anywhere (flying mounts from chapter 2, elytra from the end of chapter 3). The bridges
  keep the realm walkable for a player on foot, and the fall rule covers missteps and knockback.
- **Dungeons:**

  | Dungeon | Kind | Ending | Bosses |
  | --- | --- | --- | --- |
  | Guard tower: the Unforged that climbed out of the Void hold it; small groups on its floors, stairs up, no parkour | open-world structure, a medium site | the captain at the top (no completion) | 1 miniboss |
  | Broken gate: a ruined Gatewright gate the Unforged still pour through; optional | instanced, waves (DU-8) | the last wave; pays by the last wave reached (DU-9) | a captain in the last wave |
  | The Void Cathedral, the finale | instanced; its entrance is the door of the Cathedral, a landmark on the far islands | the First Wright's lair | miniboss → miniboss → the First Wright |

  Towers refill by the open-world respawn rule
  ([research open question 6](../research/reference-games.md#open-questions-for-matěj)), so voidglass never runs
  out. The broken gates give players who prefer fights to mining another source of malachite and voidglass.
- **The chain** (the realm's main path):
  1. the Chainkeepers trade supplies for malachite and point the way;
  2. each guard tower's captain drops voidglass at 100%;
  3. the Chainkeepers trade voidglass for the Cathedral's key, and sell the key again for repeat runs (LE-2);
  4. in the Cathedral, two minibosses drop the keys to the nave (DU-7). One bigger light-beam puzzle with the
     realm's crystal lies on the way ([Puzzles and traps](#puzzles-and-traps)), and a Sealbound Gatewright opens
     the nave, as the Scribe in the Stone does in the stone realm;
  5. the First Wright fights on the ground, in phases, with no flying phase (vision);
  6. **the last seal** (proposed, [open question 10](#open-questions)): at the Cathedral's heart the player shows
     every main-path sigil, which is never consumed ("a sigil is shown, not paid"), and forges the last seal. That
     step finishes the main story; the realms, their dungeons and later the contracts stay.
- **Malachite gear** (proposed, [open question 11](#open-questions)): the best craftable gear.
  - **The set:** helmet, chestplate, leggings and boots, plus one weapon per play style that exists when the realm
    is built: a sword, a bow once a ranged class exists, and a caster's weapon if casters use one by then. Malachite
    tools are the top tool tier ([Realm tool tiers](#realm-tool-tiers); Matěj, 2026-10-15, superseding "no
    malachite tools: netherite stays the best tool tier").
  - **Single pieces** are about as strong as the best boss drops of the chapter before, and the First Wright's drops
    are better. A player who swaps in a boss piece gives up the full set's bonus, so a full set is the strongest
    choice for most players and strongly endgame (Matěj).
  - **Set bonus:** with 2 pieces, the wearer is immune to unmaking (the Void cannot unmake malachite); with 4, a
    permanent damage reduction against every source. Numbers live in data and are tuned in playtests; set bonuses
    use the talent effect types ([architecture 5.2](../architecture.md#52-our-datapack-registries)).
  - **Cost:** malachite (mining), voidglass (fighting) and the Chainkeepers' forge (trading), so a full set takes
    a few sessions of varied play, never one long grind (vision). How crafted gear rolls (fixed stats or affixes)
    is M2's to decide, and malachite follows it.
- **Portals built here:** the realm entry's `arrival.y` covers the islands' heights, so a portal built on the way
  in stands on an island where one is near. Where none is, its frame stands on a small platform of the arrival
  floor ([realm portal spec](realm-portal.md#building-a-portal)), and the player bridges across or goes back.

### Candidate realms

Themes Matěj described for later realms, to pick from if v1 grows. What the v1 layout took or dropped (Matěj,
RLM-188):

- **Taken:** the sky islands' celestial palace and Olympus-like theme of gods became [Olympus](#the-hub-realm);
  Advent of Ascension's dinosaur theme became [Prestoria](#the-prehistoric-realm).
- **Dropped:**
  - **the dense forest** (a giant tree as a dungeon, varied tree biomes): too close to Overworld;
  - **a labyrinth realm** (AoA's realm made of rooms): perhaps a dungeon instead;
  - **underwater** (a coral reef, sunken ships, an Atlantis-like city): an underwater instanced dungeon instead.
- **Still open:**
  - **sky islands** as a realm of their own: large, well-connected islands (no mini islands, no clouds), a whole
    island as one dungeon with paths woven vertically and horizontally; a void rescue keeps a fall from being a lost
    run (RE-15);
  - **from Advent of Ascension:** flower, clown and space realms. Only the themes transfer, never AoA's assets.

## Milestones

- **M1:** Overworld (chapter 1, levels 1–10) and Undervault (chapter 2, levels 6–18), with no Overworld dungeon or
  boss.
- **M4:** Sunwastes and Everwinter (dimensions 2 and 3, chapter 3), realm tool tiers (RLM-194), and the removal of
  the Nether and End.
- **Later:** Overworld's dungeons and final boss, Olympus, Prestoria, Quagmire, Ashfire, and the Void realm as the
  last one. The Void realm may be built earlier than other realms, and still stays last on the main path
  ([Realms](#realms)).

## Open questions

For Matěj; each has a proposal so the design can go on.

1. **Respawn in a realm** (research question 1, RE-16). **Decided 2026-10-13 (Matěj, RLM-128): never in a
   realm.** A player always respawns in Overworld, at their bed or at the world spawn; a "Return to last death"
   scroll (RLM-155) takes them back ([realm portal spec](realm-portal.md#respawn)).
2. **Finished realms** (research question 2, RE-4): a harder "enraged" band once later chapters raise the cap, or
   their own levels forever? Proposal: their own levels until a later milestone shows players want to return. The
   v1 layout already keeps harder optional content at the top of each band (RLM-188).
3. **One environmental rule per realm** (research question 15, RE-6), such as wind gusts on high ground or a
   sandstorm in the desert, each with a cheap counter item that arrives with the realm. Proposal: yes, from M4.
   Undervault has none (Matěj, 2026-10-15).
4. **Completing an instance without a boss.** The rule says an instance completes when its final boss dies, but the
   catacombs (W6's type B) end in a treasury without one. Proposal: a bossless instance completes when its treasury
   opens (DU-6).
5. **The realm for chapter 3.** **Decided 2026-10-15 (Matěj, RLM-188):** chapter 3 is the twin chapter, Sunwastes
   and Everwinter (levels 16–30); the forest and underwater realms are dropped.
6. **The fire realm's chapter.** **Decided 2026-10-15 (Matěj, RLM-188):** chapter 6 (levels 40–52), after the
   elytra, so its dragons can be strong.
7. **Elytra or flying mounts first?** Asked once, in the mount decision RLM-115, question 4
   ([Chococraft research](../research/chococraft.md#questions-the-decision-answers)), which ADR 0025's consequence
   "Elytra and flying mounts" also waited for. **Decided 2026-10-13 (Matěj, RLM-115): elytra are faster in the air,
   mounts win on everything else.** Elytra use up rockets and leave their wearer unable to fight; a mount uses
   nothing, gives buffs and lets its rider fight from it, which is a new requirement for the mounts. The vision,
   ADR 0025 and the M6 outline say the same
   ([Chococraft research, decision](../research/chococraft.md#decision-rlm-115)).
8. **Elytra between the M4 removal and chapter 4.** ADR 0025 ships the removal of the End and the new sources
   together in M4, but the elytra's source was chapter 4, which no milestone names yet. **Decided 2026-10-13 (Matěj,
   RLM-138): elytra stay unobtainable until the end of chapter 3, and chapter 3's final boss gives them**, as its
   drop or in an item frame in its room (like the End ship). Chapter 3 arrives in M4, so the source ships with the
   removal (the M4 outline in the [roadmap](../roadmap.md#m4-the-world-grows)), and the "every item is obtainable"
   check lists elytra as a known exception only until that boss exists.
9. **The Void realm** (RLM-95). Approve [its section](#the-void-realm): islands on a grid joined by bridges and
   chains, three sub-regions, the Chainkeepers and the Unforged, the guard towers, the broken gates and the Void
   Cathedral, and how your "always last" rule works when a realm is added before it. Proposal: as written.
10. **The last seal from the main-path sigils.** The finale's last step asks the player to show every main-path
    sigil, which is never consumed. Proposal: yes. It ties the chain together and costs nothing, since a player keeps
    every sigil and can replace a lost one. Alternative: defeating the First Wright alone ends the story.
11. **Malachite gear.** Proposal: four armour pieces and one weapon per play style, plus the top tier of tools
    (Matěj, 2026-10-15); single pieces as strong as the previous chapter's best boss drops and below the First
    Wright's; with 2 pieces immunity to unmaking, with 4 a permanent damage reduction against every source.
    Alternative: every malachite piece better than every boss drop.
12. **The fall rule.** Proposal: the Void pulls a falling player back to their last solid ground at no cost.
    Alternatives: the same with a cost in health that never kills, or vanilla's void damage (death and a respawn in
    Overworld).
13. **Realm tool recipes** ([Realm tool tiers](#realm-tool-tiers)): some like vanilla's diamond tools, some unique,
    some at the smithing table, to discuss with Matěj (RLM-188). Proposal: a realm's tools take its main ore or gem
    in the vanilla tool shapes, and a smithing-table upgrade from the previous tier for the pickaxe, so the first
    effective pickaxe needs the old one. The tiers themselves are decided (Matěj, 2026-10-15): vanilla tools from
    wood to diamond are Overworld's tier, netherite Undervault's, and the twins get two tiers in band order.
14. **The reduced efficiency** of an earlier realm's tool: 25 % is a first guess. Proposal: keep it until RLM-194's
    playtest measures the target feel (the previous realm's pickaxe about as fast as an iron pickaxe on vanilla
    stone).
15. **Knock-on of the v1 layout** (to check with Matěj, not changed here): mount tiers unlock by chapter quests
    (RLM-115), so tier 2's flight now comes from Undervault's chapter while mounts are planned for M4; elytra come
    from chapter 3's final boss (RLM-138), and chapter 3 is now the twin chapter with two guardians; spell and talent
    unlocks per chapter (research PR-8) follow the new chapters. Proposal: elytra from the second of the twins'
    guardians a player beats, so either order works.
16. **Sunwastes' material** now that ancient debris and netherite scrap moved to Undervault (Matěj,
    2026-10-15). Proposal: the twins' first tool tier comes from a desert gem or metal of its own, and the brushing
    loot keeps its gems and books.
17. **How a player reaches Olympus.** Proposal: a sigil like every other realm's, from the quest that ends the twin
    chapter, so it opens at level 30 as the layout says; its portal leads only back to Overworld, like every realm's.
