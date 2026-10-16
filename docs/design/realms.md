# Realms

> The realm progression structure (RLM-40): which realms Realmforge has, how a player moves through them, and what
> fills them. Written on 2026-10-12 from Matěj's decisions of 2026-10-11 and 2026-10-12 (Jira RLM-40 and RLM-47) and
> the reference-game research ([reference-games.md](../research/reference-games.md), cited by item ID such as
> RE-9). It is a first version: Matěj expects to change it after playing, so it sets rules and direction, not every
> detail. Realm names are the names players read, approved by Matěj with the [lore outline](../lore/outline.md)
> (RLM-139); mob and place names are working names. The [Void realm](#the-void-realm) section is RLM-95's design,
> which waits for his approval (RLM-164). Plan: [roadmap](../roadmap.md); the Nether and End:
> [ADR 0025](../decisions/0025-remove-the-nether-and-end.md).

## At a glance

| Realm | Name | Chapter (levels) | Milestone | Identity | Signature content |
| --- | --- | --- | --- | --- | --- |
| [The Overworld](#the-overworld) | the Overworld | 1 (1–5) | M1 | home | the starting area; realm portals built anywhere |
| [Stone realm](#the-stone-realm) | the Undervault | 1 (5–10) | M1 | earth and stone | an enclosed cave world, mines with friendly NPCs, catacombs, a boss arena |
| [Desert realm](#the-desert-realm) | the Sunwastes | 2 (10–20) | M4 | sun and sand | dunes and big oases, pyramid and sphinx dungeons, ancient debris |
| [Tundra realm](#the-tundra-realm) | the Rimefields | 2 (10–20) | M4 | frost | frozen lakes with something beneath, a nordic fortress, an ice castle |
| [Fire realm](#the-fire-realm) | the Forgefire | late (open) | later | fire | lava rivers, volcanoes, grounded dragons |
| [Void realm](#the-void-realm) | the Brink | always the last | later | void | islands joined by bridges and chains, guard towers, the Void Cathedral and the finale, malachite gear |

**Names** (Matěj, RLM-139): the names players read, from the [lore outline](../lore/outline.md#the-realms). Where a
player first meets one (the guide, the sigil tooltip, the arrival title), it carries a plain subtitle with its
element, such as "The Undervault — Stone Realm" ([style guide](../lore/style.md#text-types)). This document keeps
"stone realm", "desert realm" and so on as plain descriptions.

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
- **The Void realm is always the last realm on the main path** (Matěj, RLM-95). A new main-path realm joins the
  chain before it, never after it, so its chapter is always the last one and its level band the game's highest.
  Its place is data, not code: adding a realm before it moves its level band (its `dimension_rules`) and its sigil's
  recipe down the chain ([The Void realm](#the-void-realm)).
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
  show the danger (W1), so the realm needs no hard level lock. It still opens through an early chapter 1 quest
  that a level-1 player can complete: the realm's unlock quest, which also gives the Stone Sigil
  ([realm portal spec](realm-portal.md#access-and-unlocks)). "Open from level 1" means no level gate, not no gate. The item rule of
  [Opening the next realm](#opening-the-next-realm) (RE-9) starts with the second realm, because the Overworld has
  neither a realm ore nor a boss drop to craft the item from.
- 1 stat point (Matěj, 2026-10-15, RLM-196) and about 1 skill point per level; spell limits per chapter as in D4
  (RLM-54).
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
  consumed. It opens the portal; the realm itself unlocks for each player through the realm's unlock quest (Matěj,
  RLM-128). The portal itself, the item per realm and the access rules are the
  [realm portal spec](realm-portal.md)'s job (RLM-41).

### Travel

The layout of every realm assumes cheap travel; nobody walks 1,000–10,000 blocks to get somewhere.

- Waystones are easy to get, sit at the hub and in settlements (TR-1, TR-7), and generate near main sites.
- Early flight: a flying mount from chapter 2 (our own mounts, tiers 1 to 3 in M4, RLM-115), or a long-range
  teleport spell. Elytra come from chapter 3's final boss, the step into chapter 4 (RLM-138,
  [ADR 0025](../decisions/0025-remove-the-nether-and-end.md)): faster in the air than any mount, while a mount wins
  on overall speed, utility and combat (RLM-115).
- Dungeon portals and hubs are marked on the map once found (TR-11), and every quest target has a locator or a
  vendor fallback (PR-17).

## The realms

### The Overworld

- **The starting area for levels 1–5**, kept close to vanilla plus the pack's world mods. The Nether and End are
  removed ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).
- **The hub.** Every realm's way back leads to the Overworld. [Realm portals](realm-portal.md) link like the
  Nether's (Matěj, RLM-128), so players build as many as they like, anywhere, and waystones they place make the
  Overworld the node between realms (TR-7). With cheap waystones the hub costs no walking. As a place, the hub
  waits (Matěj, RLM-139): in M1 the Wayfarers' Lodge is a voice in the quest book, and a Lodge house in a village,
  with NPCs and services that grow each chapter (WF-8), may come with the M4 village overhaul
  ([lore outline](../lore/outline.md#decisions)).

### The stone realm

Name: **the Undervault**. Chapter 1 (open from level 1, recommended from 5), the realm of M1. Identity: earth and
stone. Mining is its main profession. As the first realm it offers most of what the Nether offered
([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).

- **Shape:** one huge enclosed cave world with a bedrock floor and ceiling and big walkable spaces, Stoneblock-like
  but never cramped. Several floor heights, ledges, chasms and multi-level halls instead of one flat walking layer
  (RE-10). Sub-regions of rising level from the realm's origin outwards (about 5–7, 7–9 and 9–10), with a crystal
  grotto as one sub-zone (RE-11). Portals arrive near the origin: they link at 1:16, so an Overworld portal within
  10,000 blocks of the Overworld's origin arrives in the first band
  ([realm portal spec](realm-portal.md#the-coordinate-scale)).
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
  | Stone arena with an altar, a procedural run whose end room is the altar arena | instanced, procedural | the boss, summoned at the altar | 1 |
  | Dwarf-like fortress in the rock | open-world structure | none (no completion) | 0–2 minibosses |

- **The chain:** a mine NPC trades realm ores for the catacombs key; the catacombs' treasury holds the arena key at
  100%; inside the arena instance an NPC gives the item that summons the boss at the altar; the boss drops, at 100%,
  the material for the next realm's item (RE-9) and finishes the chapter's main chain. The mine NPC sells the keys
  again for repeat runs (LE-2).
- **What M1 takes from this** (a proposal for the M1 tasks, not a plan change): W2a's dimension is this realm's
  worldgen (the stone types, floor and ceiling, the sub-regions); W3's 1–2 custom mobs (D11) are two animated statues
  (a melee and a caster archetype); W6's type A (ends in the boss's lair) is the stone arena and type B (ends in a
  treasury) the catacombs; W5's boss fights in the arena. The fortress waits for M4's world-structure dungeons, and
  the NPC mines and the full material set grow as later tasks need them.

### The desert realm

Name: **the Sunwastes**. Chapter 2 (levels 10–20), M4, together with the tundra; both are needed for further progress.
Identity: sun and sand.

- **Landscape:** dunes, sand mountains, big oases (sunken basins with water and fauna found nowhere else, RE-12),
  abandoned towns that are empty or hold friendly NPCs.
- **Dungeons:** a pyramid, a sphinx, an abandoned desert town or village.
- **Materials:** ancient debris as a rare, artefact-like ore, about as rare as in the Nether; structures full of
  suspicious sand and red sand whose reworked brushing loot holds a little trash, desert-themed gems for refining,
  enchanted books and netherite scrap ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).

### The tundra realm

Name: **the Rimefields**. Chapter 2 (levels 10–20), M4, together with the desert. Identity: frost (no temperature
mechanic: the vision rules it out).

- **Landscape:** big frozen lakes with something beneath them (under-ice content), ice spikes, frozen shipwrecks;
  Hytale's frozen zone adds glacier lakes, hot-spring plains, stilt villages and coastal ice camps (RE-13).
- **Dungeons:** a nordic fortress, a big igloo or a group of igloos, an ice castle.
- **Visual hints:** ice spikes and a campfire mark a cave worth entering (WF-5).

### The fire realm

Name: **the Forgefire**. Replaces the Nether ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)) and comes
deliberately late: it has dragons, and weak dragons make no sense.

- **Landscape:** like an Overworld, but with lava rivers instead of water, volcanoes and dragons; passable, not as
  blocked as the Nether. A volcano the player climbs down into, with a dragon arena at the bottom (RE-14).
- **Dragons fight on the ground:** the vision allows no flying bosses.
- **Materials:** quartz; blazes drop blaze rods.

### The Void realm

Name: **the Brink**, shown as "The Brink — Void Realm" where a player first meets it. The last chapter, on the main
path: the story's finale is here (Matěj, RLM-139). Identity: the Void. This section is RLM-95's design from Matěj's
points of 2026-10-12, and its gameplay proposals wait for his approval in RLM-164
([open questions 9–12](#open-questions)). The mood images he shared (a purple void world behind a portal arch, and
two images of a void zone from WoW) are inspiration only: no names, art or assets of other games. "Void Realm" is a
plain compound of two English words, which the [style guide](../lore/style.md#name-rules) allows, although a Hytale
mod has a dimension of that name.

- **Place in the chain: always last** ([Realms](#realms)). Its sigil is crafted like every later main-path sigil
  (RE-9), from the chapter before it: an ore of that chapter's realm and its guardian's guaranteed drop (the
  Forgefire's, once it exists). When that chapter has two realms, the recipe takes both guardians' drops, as the
  [realm portal spec](realm-portal.md#realm-sigils) requires after every shared chapter, so finishing one branch
  never opens the Void realm.
  - **Built early:** Matěj may have it built before other realms. Then it follows the last chapter that exists at
    the time (for example the desert and the tundra after M4, with both drops), and each realm added before it
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
    (the [lore](../lore/outline.md#the-void-realm-the-brink)): on island rims and undersides, and in the malachite
    scars. Mining it means working along the edges, which the fall rule (below) keeps safe;
  - the realm's purple crystal, for building and for the Cathedral's light-beam puzzle;
  - dark wood from the violet groves;
  - **voidglass** (working name), the guard tower captain's guaranteed drop, which malachite gear needs.
- **Mobs:**
  - **the Unforged** (hostile) copy the hostile peoples of earlier realms, such as a Hewn or a Gilded, with a violet
    sheen and one detail out of place that is their tell: a mirrored swing, a blow that lands a beat late. Each
    variant has one clear role (BM-11), and earlier realms' models with new textures keep them cheap (BM-15).
    Their hits carry **unmaking**, a short, stacking loss of armour;
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
  - A fall costs a few seconds, never the run. A player always respawns in the Overworld (RLM-128), so a death here
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
    is built: a sword, a bow once a ranged class exists, and a caster's weapon if casters use one by then. No
    malachite tools: netherite stays the best tool tier.
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
- **Later:** the fire realm, further realms from the candidates, and the Void realm as the last one. It may be
  built earlier than other realms, and still stays last on the main path ([Realms](#realms)).

## Open questions

For Matěj; each has a proposal so the design can go on.

1. **Respawn in a realm** (research question 1, RE-16). **Decided 2026-10-13 (Matěj, RLM-128): never in a
   realm.** A player always respawns in the Overworld, at their bed or at the world spawn; a "Return to last death"
   scroll (RLM-155) takes them back ([realm portal spec](realm-portal.md#respawn)).
2. **Finished realms** (research question 2, RE-4): a harder "enraged" band once later chapters raise the cap, or
   their own levels forever? Proposal: their own levels until a later milestone shows players want to return.
3. **One environmental rule per realm** (research question 15, RE-6), such as wind gusts on high ground or a
   sandstorm in the desert, each with a cheap counter item that arrives with the realm. Proposal: yes, from M4.
4. **Completing an instance without a boss.** The rule says an instance completes when its final boss dies, but the
   catacombs (W6's type B) end in a treasury without one. Proposal: a bossless instance completes when its treasury
   opens (DU-6).
5. **The realm for chapter 3** (levels 20–30). The M4 outline brings chapters 2 and 3, while the desert and tundra
   cover chapter 2. Proposal: the forest for chapter 3, with underwater in chapter 4.
6. **The fire realm's chapter.** Proposal: chapter 5 or later, after the elytra (from chapter 3's final boss), so
   its dragons can be strong.
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
11. **Malachite gear.** Proposal: four armour pieces and one weapon per play style, no tools; single pieces as
    strong as the previous chapter's best boss drops and below the First Wright's; with 2 pieces immunity to
    unmaking, with 4 a permanent damage reduction against every source. Alternative: every malachite piece better
    than every boss drop.
12. **The fall rule.** Proposal: the Void pulls a falling player back to their last solid ground at no cost.
    Alternatives: the same with a cost in health that never kills, or vanilla's void damage (death and a respawn in
    the Overworld).
