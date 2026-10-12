# Realms

> The realm progression structure (RLM-40): which realms Realmforge has, how a player moves through them, and what
> fills them. Written on 2026-10-12 from Matěj's decisions of 2026-10-11 and 2026-10-12 (Jira RLM-40 and RLM-47) and
> the reference-game research ([reference-games.md](../research/reference-games.md), cited by item ID such as
> RE-9). It is a first version: Matěj expects to change it after playing, so it sets rules and direction, not every
> detail. All realm, mob and place names here are working names. Plan: [roadmap](../roadmap.md); the Nether and
> End: [ADR 0025](../decisions/0025-remove-the-nether-and-end.md).

## At a glance

| Realm | Working name | Chapter (levels) | Milestone | Identity | Signature content |
| --- | --- | --- | --- | --- | --- |
| [The Overworld](#the-overworld) | - | 1 (1–5) | M1 | home | the starting area and the hub with the realm portal |
| [Stone realm](#the-stone-realm) | "Stoneblock" | 1 (5–10) | M1 | earth and stone | an enclosed cave world, mines with friendly NPCs, catacombs, a boss arena |
| [Desert realm](#the-desert-realm) | "Sahara" | 2 (10–20) | M4 | sun and sand | dunes and big oases, pyramid and sphinx dungeons, ancient debris |
| [Tundra realm](#the-tundra-realm) | "Tundra" | 2 (10–20) | M4 | frost | frozen lakes with something beneath, a nordic fortress, an ice castle |
| [Fire realm](#the-fire-realm) | - | late (open) | later | fire | lava rivers, volcanoes, grounded dragons |
| [Void realm](#the-void-realm) | - | always the last | later | void | islands and bridges, the best craftable gear (RLM-95) |

Chapter 3 (levels 20–30) has no realm yet ([open question 5](#open-questions)); [candidates](#candidate-realms)
for it and later chapters are listed below.

## Progression rules

### Realms

- **A realm is a custom dimension built around one style of biome**, not one biome: a desert with big oases, a
  tundra with frozen lakes. Each realm splits into sub-regions of rising level, like a Hytale zone with its three
  regions (RE-3).
- **No fixed number of realms.** Start with 3–5. Not every realm has to be on the main path: some exist only for
  gear or a specific item, and progress may run through two realms at once (FTB Architect's Exodus is the reference).
  An optional side realm takes its level from the realms a player must visit to reach it (PR-15).
- **Every realm feels full**, like a well-modded Overworld (Regions Unexplored, Better Villages, random structures
  and dungeons), never empty like the vanilla End: neutral mobs, animals, small structures, vegetation and landmarks.
  That is measured, not guessed (WF-1, WF-2):
  - small sites (camps, ruins, mine entrances) every 12–16 chunks, medium sites every 18–42 chunks, landmarks every
    40–80 chunks, checked by a GameTest that counts structures per area;
  - spawn tables per biome with passive, ambient and hostile mobs, so no realm has only hostile life (WF-14);
  - a friendly or neutral faction next to a hostile one, each with its own settlements (WF-3);
  - one identity element visible on the realm's mobs and drops (RE-5), and music and ambience from the start (RE-8).
- **No horror realm with limited visibility** (RE-18). Every realm has its own light (glowstone, crystals, a sky),
  so Dark Vision is a comfort, not a requirement.

### Chapters and levels

- **10 levels per chapter**, linear. Matěj expects to change the numbers at least once after playing.
- **A chapter is a level band, not a realm.** Two realms can share one band: for chapter 2 (levels 10–20) the desert
  and the tundra each have dungeons for about 10–13, 13–17 and 17–20, and the player alternates between them
  (waystones make that easy).
- **Levels 1–5 are played in the Overworld.** The stone realm is open from level 1 but strongly recommended from
  level 5; gathering what a player needs to survive there naturally brings them to about level 5. Mob levels by area
  show the danger (W1), so the realm needs no hard level lock.
- About 2 stat points and 1 skill point per level; spell limits per chapter as in D4 (RLM-54).
- Each chapter ends in a short main quest chain whose last step opens the next band (PR-1); everything else is
  optional.

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
  consumed. The portal itself, the item per realm and the access rules are the portal spec's job (RLM-41).

### Travel

The layout of every realm assumes cheap travel; nobody walks 1,000–10,000 blocks to get somewhere.

- Waystones are easy to get, sit at the hub and in settlements (TR-1, TR-7), and generate near main sites.
- Early flight: wings or a flying mount early (Matěj's mount tiers in RLM-101 fly from chapter 2), or a long-range
  teleport spell. Elytra come in chapter 4 ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).
- Dungeon portals and hubs are marked on the map once found (TR-11), and every quest target has a locator or a
  vendor fallback (PR-17).

## The realms

### The Overworld

- **The starting area for levels 1–5**, kept close to vanilla plus the pack's world mods. The Nether and End are
  removed ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).
- **The hub.** The realm portal (RLM-41) stands in the Overworld, and every realm's way back leads there. A waystone
  at the portal makes the hub the node between realms (TR-7), and the area around it gains NPCs and services with
  each chapter (WF-8). With cheap waystones the hub costs no walking.

### The stone realm

Working name "Stoneblock". Chapter 1 (open from level 1, recommended from 5), the realm of M1. Identity: earth and
stone. Mining is its main profession. As the first realm it offers most of what the Nether offered
([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).

- **Shape:** one huge enclosed cave world with a bedrock floor and ceiling and big walkable spaces, Stoneblock-like
  but never cramped. Several floor heights, ledges, chasms and multi-level halls instead of one flat walking layer
  (RE-10). Sub-regions of rising level from the arrival point outwards (about 5–7, 7–9 and 9–10), with a crystal
  grotto as one sub-zone (RE-11).
- **Stone:** many custom stone types (no vanilla stone; variants in the spirit of diorite, granite and andesite)
  mixed on floor, walls and ceiling, with Y-range rules (this ore or gem only at Y 10–30, that stone only near the
  ceiling).
- **Light and life:** glowstone near the ceiling, as in the Nether; grassy pockets where things grow; special oases
  with water.
- **Materials:**
  - at least 3 new ores or gems for gear, weapons, curios and refining, one of them the crystal the light-beam
    puzzles use;
  - quartz set in stone (quartz early, because other mods expect it);
  - glowstone near the ceiling;
  - patches of soul sand with nether wart, or a mob that drops nether wart;
  - ore bands in the walls and pillars of walkable layers, and a treasure ore with its own loot table (PF-5).
- **Mobs:** stone-looking mobs and animated statues, each variant with one clear role (BM-11); a rock mob that climbs
  and a timid critter that flees into the rock; animals too, such as a "Stoneshroom" stone cow; shulkers with grey as
  their default colour (the source of shulker shells).
- **Natural structures:** 2–3 kinds of mines, some with friendly NPCs who take the realm's ores and gems and trade
  back the item that unlocks a dungeon; Stonehenge-like rings and big boulders as landmarks. The NPC mine near the
  boss site is the realm's friendly settlement next to danger (WF-9).
- **Dungeons:**

  | Dungeon | Kind | Ending | Bosses |
  | --- | --- | --- | --- |
  | Catacombs, an open catacomb like the interior part of Mine Cells | instanced, procedural | a treasury | none, or minibosses |
  | Stone arena with an altar | instanced | the boss, summoned at the altar | 1 |
  | Dwarf-like fortress in the rock | open-world structure | none (no completion) | 0–2 minibosses |

- **The chain:** a mine NPC trades realm ores for the catacombs key; the catacombs' treasury holds the arena key at
  100%; inside the arena instance an NPC gives the item that summons the boss at the altar; the boss drops, at 100%,
  the material for the next realm's item (RE-9) and finishes the chapter's main chain. The mine NPC sells the keys
  again for repeat runs (LE-2).
- **What M1 takes from this** (a proposal for the M1 tasks, not a plan change): W2's dimension is this realm's
  worldgen (the stone types, floor and ceiling, the sub-regions); W3's 1–2 custom mobs (D11) are two animated statues
  (a melee and a caster archetype); W6's type A (ends in the boss's lair) is the stone arena and type B (ends in a
  treasury) the catacombs; W5's boss fights in the arena. The fortress waits for M4's world-structure dungeons, and
  the NPC mines and the full material set grow as later tasks need them.

### The desert realm

Working name "Sahara". Chapter 2 (levels 10–20), M4, together with the tundra; both are needed for further progress.
Identity: sun and sand.

- **Landscape:** dunes, sand mountains, big oases (sunken basins with water and fauna found nowhere else, RE-12),
  abandoned towns that are empty or hold friendly NPCs.
- **Dungeons:** a pyramid, a sphinx, an abandoned desert town or village.
- **Materials:** ancient debris as a rare, artefact-like ore, about as rare as in the Nether; structures full of
  suspicious sand and red sand whose reworked brushing loot holds a little trash, desert-themed gems for refining,
  enchanted books and netherite scrap ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).

### The tundra realm

Working name "Tundra". Chapter 2 (levels 10–20), M4, together with the desert. Identity: frost (no temperature
mechanic: the vision rules it out).

- **Landscape:** big frozen lakes with something beneath them (under-ice content), ice spikes, frozen shipwrecks;
  Hytale's frozen zone adds glacier lakes, hot-spring plains, stilt villages and coastal ice camps (RE-13).
- **Dungeons:** a nordic fortress, a big igloo or a group of igloos, an ice castle.
- **Visual hints:** ice spikes and a campfire mark a cave worth entering (WF-5).

### The fire realm

Replaces the Nether ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)) and comes deliberately late: it has
dragons, and weak dragons make no sense.

- **Landscape:** like an Overworld, but with lava rivers instead of water, volcanoes and dragons; passable, not as
  blocked as the Nether. A volcano the player climbs down into, with a dragon arena at the bottom (RE-14).
- **Dragons fight on the ground:** the vision allows no flying bosses.
- **Materials:** quartz; blazes drop blaze rods.

### The Void realm

Always the last realm: whenever a new realm is added, the Void realm moves further down so that it stays the final
one. Its theme, dungeons and gear (malachite, the best craftable gear) are designed in RLM-95, which adds its
section here.

### Candidate realms

Themes Matěj described for later chapters, to pick from as the progression grows:

- **Sky islands:** large, well-connected islands (no mini islands, no clouds); a whole island as one dungeon with
  paths woven vertically and horizontally; a celestial palace or an Olympus-like theme of gods. A void rescue keeps a
  fall from being a lost run (RE-15).
- **Dense forest:** a giant tree (or a group of trees) as a dungeon, a small castle or fortress, maybe a volcano;
  varied tree biomes, flowers, rivers, lots of vegetation.
- **Underwater**, rather in chapter 3 or 4: content on or just above the sea floor, void damage when swimming too
  high instead of a bedrock ceiling; a coral reef, sunken ships (as dungeons and as decoration with a chest), an
  Atlantis-like city, giant corals, shells and skeletons of huge fish; a safe air pocket at the arrival point (RE-6).
- **From Advent of Ascension:** flower, clown, dinosaur and space realms, and a realm made of rooms (it feels like one
  giant dungeon but is a biome). Only the themes transfer, never AoA's names or assets.

## Milestones

- **M1:** the Overworld (levels 1–5) and the stone realm (chapter 1).
- **M4:** the desert and the tundra (dimensions 2 and 3, chapter 2), and the removal of the Nether and End.
- **Later:** the fire realm, further realms from the candidates, and the Void realm as the last one.

## Open questions

For Matěj; each has a proposal so the design can go on.

1. **Respawn in a realm** (research question 1, RE-16). Proposal: at the portal the player arrived through, so a
   death costs no long way back.
2. **Finished realms** (research question 2, RE-4): a harder "enraged" band once later chapters raise the cap, or
   their own levels forever? Proposal: their own levels until a later milestone shows players want to return.
3. **One environmental rule per realm** (research question 15, RE-6), such as wind gusts on high ground or a
   sandstorm in the desert, each with a cheap counter item that arrives with the realm. Proposal: yes, from M4.
4. **Completing an instance without a boss.** The rule says an instance completes when its final boss dies, but the
   catacombs (W6's type B) end in a treasury without one. Proposal: a bossless instance completes when its treasury
   opens (DU-6).
5. **The realm for chapter 3** (levels 20–30). The M4 outline brings chapters 2 and 3, while the desert and tundra
   cover chapter 2. Proposal: the forest for chapter 3, with underwater in chapter 4.
6. **The fire realm's chapter.** Proposal: chapter 5 or later, after the elytra in chapter 4, so its dragons can be
   strong.
7. **Elytra or flying mounts first?** Your reason for elytra in chapter 4 ("a little better than a mount") differs
   from the vision and the M6 outline, which want flying mounts better than elytra
   ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)). Proposal: your newer rule wins (elytra a little
   better than the fastest mount tier of RLM-101, so they come with or after it), and the vision's line and the M6
   outline change to say so once you confirm.
