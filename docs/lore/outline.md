# Lore outline

> The background story of Realmforge and its realms (RLM-43), written on 2026-10-13 from the
> [vision](../vision.md), the [realm design](../design/realms.md) (RLM-40), the
> [realm portal spec](../design/realm-portal.md) (RLM-41) with Matěj's portal decision of 2026-10-13 (RLM-128), and
> the reference-game research ([reference-games.md](../research/reference-games.md): WF-3, WF-7, WF-8, WF-9, RE-5,
> RE-18, RE-20). The story stays in the background: it gives quests, bosses and items a reason to exist. It explains
> the rules Matěj has decided and forces none he has not; where a line would, it waits under
> [Open questions](#open-questions). Every name here is a proposal until Matěj approves the outline
> (RLM-139). How texts use it: the [style guide](style.md). Where RLM-128 changed the portal spec (the unlock
> comes from the realm's quest, travel links portals like the Nether's instead of arriving at a landing,
> respawn is always in the Overworld), the spec and the realm design still describe the earlier proposal; they
> record the decision when RLM-128 closes, which W2 (RLM-32) waits for.

## At a glance

- **Realmforge** is the world, and its name is its history: it was forged. Long ago its makers, the
  **Gatewrights**, split a world the **Void** was eating into separate **realms**, each forged around one element,
  and chained them in a line between the Overworld and the Void.
- **The Chain of Seals** keeps that line: every realm is sealed, and the shard that forges each next realm's
  **sigil** is held by the **guardian** of the realm before it. That is why the realms open one by one, in order,
  and why the Void realm is always the last.
- **The player** is a newcomer whom the **Wayfarers' Lodge**, a society of explorers and scholars, sends to walk
  the chain: earn each realm's passage, pass each guardian's trial, carry each shard onwards.
- **The finale (proposed, [open question 3](#open-questions))** waits in the Void realm, where the last seal has
  to be forged anew. If RLM-95 makes the Void realm a post-story realm, it moves to the realm before it.
- **Every realm has two peoples:** a friendly one to trade and quest with, and a hostile one to fight (WF-3), plus one
  element that shows on its mobs and drops (RE-5).
- **The tone is adventure, not dread:** an old, slightly mysterious world, told in ruins, inscriptions and short
  quest texts (WF-7). No horror, no realm without light (RE-18).

## The world

### Realmforge

Once there was one world. At its edges lay the Void: not darkness, but nothing at all, the raw stuff of the world
before anything was made from it. The Void unmakes what it touches, and in the age before this one it began to eat
the world from the edges inwards.

The greatest smiths and mages of that age called themselves the **Gatewrights**. They could not push the Void back,
so they did something bolder: they hammered the world apart. Each piece became a **realm**, forged around one
element (stone, sun, frost, fire and more), and every realm was set in a line between the heart of the world, the
**Overworld**, and the Void. A realm is a wall in front of the next one: whatever comes out of the Void has to break
every realm in turn before it reaches home.

The Gatewrights called their work, and the world it made, **Realmforge**.

### The Chain of Seals

The Gatewrights sealed every realm and made one gate design to open them all. The order of the line is the
**Chain of Seals**, and it is built into the seals themselves:

| Lore | What it is in the game |
| --- | --- |
| A realm's **seal** opens only to its **sigil** | the realm portal opens onto a realm when its sigil is used on the realm selector ([realm portal](../design/realm-portal.md)) |
| One **gate design** for every realm | one portal frame, with the realm selector as its keystone |
| Each realm's **guardian** holds a **shard** for each seal it opens (two when twin realms follow) | the main-path boss drops, at 100%, one material per realm it opens, two for chapter 2 (RE-9, [realm portal](../design/realm-portal.md)) |
| A sigil is forged from a realm's own ore and the guardian's shard | the next sigil is crafted from the current realm's ore or gem and that drop |
| The seal knows a traveller by their deeds, not by the sigil in their hand | per-player unlock through the realm's unlock quest; holding someone else's sigil does not let a player through (RLM-128) |
| **Twin seals:** some realms were forged as a pair, and the next seal needs both shards | a two-realm chapter: the next main-path sigil needs both realms' boss drops |
| **Branch seals:** small realms hang off the chain like charms | optional side realms, whose sigil comes from a realm on the main path |
| The Void lies past the end of the chain, however long it grows | the Void realm is always the last realm; a new realm joins the chain before it |

The chain has no known length. The Gatewrights forged more realms than anyone remembers, so a realm found later
is a link nobody had counted, and the Void stays where it always was: past the last one.

### The Gatewrights

- **Who they were:** a guild of smiths, mages and builders, the people of the age before this one. Their works are
  everywhere: ruins, gate frames, vaults, statues, inscriptions in a blocky script that the Lodge has slowly learned
  to read.
- **Where they went:** nobody knows for sure, and the story keeps it that way until the finale. Some stayed in the
  realms as the **Sealbound**, bound into stone, ice or iron beside their realm's guardian, to watch the seal. Their
  founder, the **First Wright**, went to the last realm to hold the line against the Void.
- **What they left:** the gate design, the sigil craft, the guardians and their trials, the Hewn (stone servants),
  and their writings, which are the lore's main carrier (WF-7).

### Why now

The seals are thinning. Gatewright runes in the Overworld ruins have begun to glow, and the oldest stones of their
gates have grown warm to the touch. Maren Vell, the Lodge's archivist, has read the warning the First Wright carved
into those ruins: _when the gate stones grow warm, the chain is failing; someone must walk it and forge the last
seal anew._ The Lodge has kept an old stone sigil for generations without knowing what it was. Now it knows, and it
needs someone to carry it.

## The player

- **Role:** a newcomer to the Overworld's wild lands, capable and curious, whom the Lodge calls a **Wayfarer**. Not
  a chosen one: the seals accept anyone who passes their trials, and that is the point of them. Texts never assume
  the player's race, class, gender or name ([style guide](style.md#the-player)).
- **The arc:**
  1. **Overworld (chapter 1, levels 1–5):** learn to fight and cast, build the first gate, earn passage into the
     first realm.
  2. **The stone realm (chapter 1, levels 5–10):** meet the Cairnfolk, open the Gatewrights' catacombs, pass the
     first guardian's trial and carry its shards home.
  3. **The chain (chapters 2 and later):** each chapter opens the next realm or pair of realms. Each realm shows the
     Void a little closer: its guardian is harder to reason with and its threat a little stranger.
  4. **The Void realm (the last chapter):** the Brink, the realm nearest the Void, where the First Wright went.
- **The finale (proposal, [open question 3](#open-questions)):** in the Void Cathedral the player finds the First
  Wright, hollowed out by the Void after ages of holding the line. He no longer holds the last seal; he means to
  open it and forge the Void itself into a realm, which would unmake the rest. The player defeats him and, with the
  shards carried along the whole chain, forges the last seal anew. The chain holds, the realms stay open to those
  who earned them, and the Lodge names the player the first Gatewright of a new age. Play goes on after it: the
  realms, their dungeons and later the contracts stay.

## The realms

The realms of the [realm design](../design/realms.md), in chain order. Realm names are proposals for the names
players read ([open question 2](#open-questions)); the design keeps its working names until they are approved.
Colours are the portal colours of the [realm portal spec](../design/realm-portal.md).

| Realm (design) | Proposed name | Chapter | Element | Friendly people | Hostile people | Guardian |
| --- | --- | --- | --- | --- | --- | --- |
| The Overworld | the Overworld | 1 (1–5) | home | the Wayfarers' Lodge | the wild's monsters (vanilla) | none: no seal |
| Stone realm | the Undervault | 1 (5–10) | earth and stone | the Cairnfolk | the Hewn | the Unbroken, a stone colossus |
| Desert realm | the Sunwastes | 2 (10–20) | sun and sand | the Wellkeepers | the Gilded | a great sphinx (twin seal) |
| Tundra realm | the Rimefields | 2 (10–20) | frost | the Kettlefolk | the Thawed | the Thawed's queen in the ice castle (twin seal) |
| Fire realm | the Forgefire | late | fire | the Emberwrights | the forge-drakes | the eldest forge-drake |
| Void realm | the Brink | always the last | void | the Chainkeepers | the Unforged | the First Wright (finale) |

Each paragraph below gives the realm's history, its peoples and its threat; the dungeons, mobs and materials it
names are the realm design's.

### The Overworld

The heart of the world and the start of the chain, the one place the Gatewrights never sealed. It has no guardian
and no sigil: every realm's way back leads here. The **Wayfarers' Lodge** has studied the Gatewright ruins here for
generations, and its archivist starts the player's journey. The Overworld's danger is the ordinary wild (night
monsters, caves), which is where a Wayfarer learns to fight. With each chapter the Lodge has more to offer as its
members follow the player along the chain (WF-8), wherever the story shows it ([open question 5](#open-questions)).

### The stone realm: the Undervault

The first realm the Gatewrights forged and the nearest to home: a vault of stone under the world, where they kept
their forge-stock and buried their dead. Its light comes from glowstone in the ceiling and crystal in the deep
grottos, the same crystal the Gatewrights used to steer light through their vaults (the light-beam puzzles).

- **The Cairnfolk** are a sturdy, cheerful mining people who have lived in the Undervault since it was sealed, cut
  off from the Overworld and in no hurry to leave it. They work the realm's mines, trade ore for anything new from
  above, and keep the keys to the Gatewright catacombs, which they would rather someone else opened.
- **The Hewn** are the Gatewrights' stone servants: statues carved to guard the catacombs and the forge-halls. They
  still obey orders nobody is left to give, and as the seal thins they wake more often, and less kindly. One kind
  fights with its fists and hammers, another chants the old stone-songs (W3's melee and caster archetypes).
- **The guardian, the Unbroken**, is a colossus of living stone that waits at an altar at the end of the arena
  halls. It is a trial, not a villain: it fights anyone who would carry its shards further, so that nobody weak ever
  carries them towards the Void. It holds two, one for each of the twin realms ahead. The **Scribe in the Stone**,
  a Sealbound Gatewright set into the arena wall, gives a Wayfarer the means to wake it (the item that summons the
  boss at the altar).
- **Lore carriers:** inscriptions on catacomb sarcophagi (the lore riddles of the realm design), the Scribe's
  words, and the Cairnfolk's tall tales.

### The desert realm: the Sunwastes

The sun realm, one of a pair. The Gatewrights forged it and the Rimefields from one ingot and quenched them in two
ways: this half was left in the sun. Its people, the Sun Court, built pyramids and a great sphinx as vaults for
their treasures, and buried their own forges under the sand when the seal closed; the ancient debris found here is
the slag of those forges.

- **The Wellkeepers** live in the big oases and in the few towns not yet empty. They know the dunes, guard the
  water and trade with anyone who respects it.
- **The Gilded** are the Sun Court's servants, who covered themselves in gold to outlast the sealing. They did,
  after a fashion: sand-dry and gold-masked, they still guard the pyramids and raid the oases for what they think is
  theirs.
- **The guardian** is a great sphinx in its own dungeon, which holds one of the two shards the next seal needs.
  Its trial is the realm's riddles as much as its claws.

### The tundra realm: the Rimefields

The frost realm, the other half of the pair: quenched in ice. The Gatewrights froze it to hold something still,
and it held: ships froze in the lakes mid-voyage and a sea-raiding people slept under the ice for an age.

- **The Kettlefolk** live in stilt villages around the hot springs, fishing through the ice. They are warm, nosy
  and generous, and they know which lakes hum at night.
- **The Thawed** are the raiders under the ice, waking as the seal thins. They are not dead, only very late, and
  they want their fortress, their ships and their sea back. Their nordic fortress and their frozen shipwrecks are
  the realm's dungeons.
- **The guardian** is the queen of the Thawed in the ice castle, who holds the other shard that seal needs. The
  two shards together forge the next sigil.

### The fire realm: the Forgefire

The realm of the Gatewrights' great forge, deliberately late in the chain: what burns here is too strong for a
newcomer. It looks like an Overworld with lava rivers instead of water, and volcanoes where the forge's fires broke
through.

- **The Emberwrights** are smiths who still tend the forge's last fires, the nearest thing to Gatewrights left
  alive. They are proud, short-tempered and the best crafters in the chain.
- **The forge-drakes** are the Gatewrights' bellows-beasts gone wild: dragons born in the forge, with wings of
  iron too heavy to fly. That is why every dragon in Realmforge fights on the ground.
- **The guardian** is the eldest forge-drake, in an arena at the bottom of a volcano.

### The Void realm: the Brink

The last realm the Gatewrights forged and the nearest to the Void, which broke it into islands. They bound the
islands together with great chains and bridges, raised dark stone gates and a cathedral at its heart, and kept the
last seal there. The Void here is not dark: it glows violet, under a pale moon, and the Brink's purple stone, dark
wood and crystals catch that light (RE-18). Where the Void touches forged stone a green ore grows, malachite, the
one thing in the chain the Void seems unable to unmake.

- **The Chainkeepers** are the last order on the islands, each keeping one link of the great chains. They are
  solemn, few and very glad to see anyone.
- **The Unforged** are things of the Void: half-made shapes that copy what they see of the realms and get it
  slightly wrong. They are strange rather than gruesome.
- **The guardian** is the First Wright in the Void Cathedral (the finale above). The guard towers on the islands
  hold the Unforged that have climbed out of the Void.
- RLM-95 designs this realm; this paragraph follows Matěj's points there and changes with that design.

### Further realms

The chain grows with the [candidate realms](../design/realms.md#candidate-realms), each a link nobody had counted.
One line each, for when they are designed:

- **Sky islands:** a realm the Gatewrights lifted into the sky to keep it out of reach; a celestial palace at its top.
- **Dense forest:** a realm grown rather than forged, around a giant tree the Gatewrights planted.
- **Underwater:** a realm sunk on purpose, its city kept dry under a bubble that still holds.

A new realm fits the chain when it has: one element (RE-5) and a look of its own (RE-20); a friendly and a hostile
people; a guardian holding the shards of the seals after it; and a reason the Gatewrights forged it.

## Factions and recurring characters

### Factions

| Faction | Where | Stance | Gives quests about |
| --- | --- | --- | --- |
| The Wayfarers' Lodge | the Overworld, and with the player along the chain | friendly | the main chain, Gatewright lore, exploration; later contracts and guild ranks (M7) |
| The Cairnfolk | the Undervault's mines | friendly | ore, mining, the catacomb keys, repeatable trades |
| The Hewn | the Undervault's catacombs and halls | hostile | - |
| The Wellkeepers | the Sunwastes' oases and towns | friendly | water, the dunes, the Gilded's raids |
| The Gilded | the Sunwastes' pyramids | hostile | - |
| The Kettlefolk | the Rimefields' hot-spring villages | friendly | fishing, the frozen lakes, the shipwrecks |
| The Thawed | the Rimefields' fortress and ships | hostile | - |
| The Emberwrights | the Forgefire's forges | friendly | crafting, the forge-drakes |
| The forge-drakes | the Forgefire's volcanoes | hostile | - |
| The Chainkeepers | the Brink's islands | friendly | the chains, the Cathedral, the last seal |
| The Unforged | the Brink and the Void | hostile | - |
| The Sealbound | one in every sealed realm | friendly | the guardian's trial |

The friendly peoples are the realms' quest-givers, traders and, once villages are reworked, their villagers. A
faction that gives repeatable trades (the Cairnfolk taking ore) is also a natural home for the repeatable quests of
a chapter.

### Recurring characters

- **Maren Vell**, archivist of the Wayfarers' Lodge: the player's mentor and the voice of chapter 1's quest book.
  Scholarly, kind, a little dry; she reads Gatewright script better than anyone alive and has never been through a
  gate herself, which she minds more than she says.
- **Torva Flintmark**, forewoman of the Cairnfolk mine nearest the arena: blunt, cheerful, keeps the catacomb keys
  and sells them again for repeat runs (LE-2). The Cairnfolk's voice in chapter 1.
- **The Scribe in the Stone**, the Undervault's Sealbound: a Gatewright set into the arena wall, slow to speak and
  formal. Every sealed realm has a Sealbound like him, so the player meets the same kind of figure before each
  guardian.
- **The First Wright**, founder of the Gatewrights: heard throughout the chain in the inscriptions he left, met only
  at the end.

## Chapter 1 for Q2

Q2 writes about 15 quests for chapter 1 (roadmap: rewards from the gear set, the last quest grants the chapter
advancement). These beats are the story they tell; each names what it teaches, so the quests can follow the order
of the systems M1 builds. The order, the counts and the rewards are Q2's to set.

| # | Beat | Where | What the player does | Story |
| --- | --- | --- | --- | --- |
| 1 | A Letter from the Lodge | Overworld | opens the quest book; has chosen a class | Maren welcomes a new Wayfarer and explains the warm gate stones |
| 2 | The Wild Is Wide | Overworld | kills a few hostile mobs; gains levels | the Lodge wants to know the newcomer can handle themselves |
| 3 | A Spark in the Hand | Overworld | casts class spells | Maren on the old crafts of fighting and casting |
| 4 | What You Learn | Overworld | learns a talent | everyone in the Lodge has a speciality |
| 5 | Stone and Copper | Overworld | gathers stone bricks and copper | the Gatewrights built gates from plain stone; Maren has the plan |
| 6 | Portal Stone | Overworld | crafts Portal Stone | the plan's first page |
| 7 | The Keystone | Overworld | crafts the realm selector | the gate's heart: a stone that remembers where it leads |
| 8 | Raise the Gate | Overworld | builds the frame | the first gate raised in an age |
| 9 | The Stone Sigil | Overworld | finishes the unlock quest and receives the Stone Sigil | the Lodge's old relic turns out to be the first sigil; the seal will know the player now |
| 10 | Below the World | the Undervault | enters the realm, finds a Cairnfolk mine | first arrival; the Cairnfolk are surprised to see anyone from above |
| 11 | Ore for the Cairnfolk | the Undervault | mines the realm's ores | Torva trades ore for the catacomb key |
| 12 | The Catacombs | the Undervault | clears the catacombs to their treasury | the Gatewrights' dead and their riddles; the arena key lies in the treasury |
| 13 | The Hewn Awake | the Undervault | defeats the realm's statues | the stone servants wake as the seal thins |
| 14 | The Scribe in the Stone | the Undervault | enters the arena, receives the summoning item | the Sealbound explains the trial |
| 15 | The Unbroken | the Undervault | summons and defeats the boss | the guardian's trial; its two shards drop, one for each twin seal ahead |
| 16 | Home with the Shards | Overworld | brings the shards back | Maren reads the shards: two seals ahead, sun and frost. The chapter advancement raises the level cap |

- **Givers:** the quest book speaks in Maren's voice (her notes and letters), and in the Undervault in Torva's and
  the Scribe's. The beats need no NPC entity: where M1 has none, the same lines come from the quest book.
- **The catacomb key** comes from Torva in the story. If M1 hands it out another way (a quest reward), beat 11
  becomes "Torva sends a key with the ore you brought" and stays true.
- **Lost pages:** Gatewright inscriptions found along the way can be click-to-read quests that teach one system
  each (PR-2, PR-11), in the First Wright's voice.

## How the lore explains decided rules

Decided rules that a line of lore makes feel natural. Texts may use these lines; none of them changes a rule.

| Rule (source) | Lore |
| --- | --- |
| Respawn always at the bed in the Overworld or at world spawn, never in a realm (RLM-128) | Realmforge's oldest magic is the pull of home: the Gatewrights made sure nobody who falls beyond a seal is lost there |
| Keep inventory on death (vision) | the pull of home brings back what the traveller carried, too |
| "Return to last death" scroll (RLM-128) | a scroll that follows the thread the traveller's spirit left on the way home |
| Level cap per chapter, raised when the chapter is done (D2) | the seals bind strength as well as realms, so nobody could grow strong enough to break one by force; the bond loosens once every trial of a chapter is passed (both guardians of twin realms) |
| Sigils are never used up; one sigil opens any number of portals (RLM-128) | a sigil is a key, not a toll |
| Unlocks are per player; a sigil without the player's own unlock does nothing (RLM-128) | the seal knows a traveller by their deeds |
| Only players travel through realm portals (RLM-128, "for now") | the gates pass a living traveller and what they carry, nothing that walks or drifts on its own |
| The next sigil is crafted from the current realm (RE-9) | the guardian holds the shards of the seals after it |
| No flying bosses (vision) | the forge-drakes' iron wings are too heavy to fly |
| Every realm has its own light (RE-18) | the Gatewrights forged light into every realm, the Brink's violet glow included |

## What the lore leaves open

The outline forces none of these; each is decided elsewhere, and the lore follows whatever is decided:

- **Arrival in a realm** (RLM-128's open question: scaled coordinates, levels by sub-region, or a clamped arrival).
  The lore says nothing about where a portal leads inside a realm or why some areas are more dangerous.
- **The Void realm on the main path or after the story** (RLM-95). The finale assumes the main path; see
  [open question 3](#open-questions).
- **The hub as a place.** Portals can stand anywhere in the Overworld (RLM-128), so the Lodge is a voice and a
  society, not a building, until something decides otherwise ([open question 5](#open-questions)).
- **Friendly NPCs in M1.** The chapter 1 beats work with or without them.
- **Boss mechanics, mob and item names** (W3, W5, A1). The outline gives the bosses and peoples an identity; the
  tasks that build them name and shape them, within the [style guide](style.md#names).
- **Malachite's effects** (RLM-95). The lore says the Void cannot unmake it; if RLM-95 gives malachite gear other
  effects than void resistance, that line changes.

## Open questions

For Matěj; each has a proposal so writing can go on.

1. **Approve the outline:** the Gatewrights split the world into realms chained against the Void; sigils open
   seals; each guardian is a trial that holds the shards of the seals after it; the Wayfarers' Lodge sends the
   player along the chain.
2. **Names:** Realmforge, the Gatewrights, the Chain of Seals, the Wayfarers' Lodge, Maren Vell, Torva Flintmark,
   the Cairnfolk, the Hewn, the Unbroken, the Scribe in the Stone and the other peoples in the tables. Realm names:
   proposed the Undervault, the Sunwastes, the Rimefields, the Forgefire and the Brink for players, with the
   Overworld kept as the Overworld. Alternative: plain descriptive names ("the Stone Realm"), as the portal spec's
   data sketch has.
3. **The finale:** the First Wright, hollowed out by the Void, as the final antagonist in the Void Cathedral, and
   the last seal forged from the shards of the whole chain (proposed). This puts the Void realm on the main path,
   which is RLM-95's first question; if it becomes a post-story realm, the finale moves to the realm before it.
   Forging the last seal from every shard is a gameplay idea, not a rule: RLM-95 decides it.
4. **Lore for the level cap** ("the seals bind strength"; the bond loosens when a chapter's trials are all
   passed): use it in texts (proposed), or leave the cap a plain game rule.
5. **The Lodge as a place:** a voice in the quest book for M1 (proposed), and a Lodge house in a village once the
   village overhaul arrives (M4), growing each chapter (WF-8). Alternative: no building ever.
6. **Czech texts:** address the player with "ty" (proposed) or "vy"; translate descriptive names (the Undervault)
   or keep them in English ([style guide](style.md#czech-cs_cz)).
