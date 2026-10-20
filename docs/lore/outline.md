# Lore outline

> The background story of Realmforge and its realms (RLM-43), written on 2026-10-13 from the
> [vision](../vision.md), the [realm design](../design/realms.md) (RLM-40), the
> [realm portal spec](../design/realm-portal.md) (RLM-41) with Matěj's portal decision of 2026-10-13 (RLM-128), and
> the reference-game research ([reference-games.md](../research/reference-games.md): WF-3, WF-7, WF-8, WF-9, RE-5,
> RE-18, RE-20). The story stays in the background: it gives quests, bosses and items a reason to exist. It explains
> the rules Matěj has decided and forces none he has not; where a line would, it waits under
> [What the lore leaves open](#what-the-lore-leaves-open). Matěj approved the outline, its names and the style guide
> on 2026-10-13 (RLM-139, [Decisions](#decisions)). How texts use it: the [style guide](style.md). The portal spec
> and the realm design record RLM-128's decision: the unlock comes from the realm's quest, portals link like the
> Nether's with scaled coordinates, and respawn is always in Overworld. Updated on 2026-10-16 with the v1 realm layout
> (RLM-188): the realm names Everwinter, Ashfire and Unmade Isles, the new realms Olympus, Prestoria and Quagmire, one
> chapter per realm (the twins share one), and realm names without an article.

## At a glance

- **Realmforge** is the world, and its name is its history: it was forged. Long ago its makers, the
  **Gatewrights**, split a world the **Void** was eating into separate **realms**, each forged around one element,
  and chained them in a line between Overworld and the Void.
- **The Chain of Seals** keeps that line: every realm beyond Overworld is sealed. The first **signet** is an old
  relic the Lodge has kept; every later main-path signet is forged from a shard held by the **guardian** of the realm
  before it, and a side realm's signet comes from wherever its design puts it. That is why the realms open one by
  one, in order, and why the Void realm is always the last.
- **The player** is a newcomer whom the **Wayfarers' Lodge**, a society of explorers and scholars, sends to walk
  the chain: earn each realm's passage, pass each guardian's trial, carry each shard onwards.
- **The finale** waits in the Void realm, where the last seal has to be forged anew, so the Void realm is on the
  main path (Matěj, RLM-139).
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
element (stone, sun, frost, fire and more), and every realm was set in a line between the heart of the world,
**Overworld**, and the Void. A realm is a wall in front of the next one: whatever comes out of the Void has to break
every realm in turn before it reaches home.

The Gatewrights called their work, and the world it made, **Realmforge**.

### The Chain of Seals

The Gatewrights sealed every realm but Overworld and made one gate design to open them all. The order of the
line is the **Chain of Seals**, and it is built into the seals themselves:

| Lore | What it is in the game |
| --- | --- |
| A realm's **seal** opens only to its **signet** | the realm portal opens onto a realm when its signet is used on the sealstone ([realm portal](../design/realm-portal.md)) |
| One **gate design** for every realm | one portal frame, with the sealstone as its keystone |
| Each sealed realm's **guardian** holds a **shard** for each seal it opens (two when twin realms follow) | the main-path boss drops, at 100%, one material per realm it opens, two for the twins of chapter 3 (RE-9, [realm portal](../design/realm-portal.md)) |
| A signet is forged from a realm's own ore and the guardian's shard | the next signet is crafted from the current realm's ore or gem and that drop |
| The seal knows a traveler by their deeds, not by the signet in their hand | per-player unlock through the realm's unlock quest; holding someone else's signet does not let a player through (RLM-128) |
| **Twin seals:** some realms were forged as a pair, and the next seal needs both shards | a two-realm chapter: the next main-path signet needs both realms' boss drops |
| **Branch seals:** small realms hang off the chain like charms | optional side realms, whose signet comes from a realm on the main path |
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
- **What they left:** the gate design, the signet craft, the guardians and their trials, the Hewn (stone servants),
  and their writings, which are the lore's main carrier (WF-7).

### Why now

The seals are thinning. Gatewright runes in Overworld's ruins have begun to glow, and the oldest stones of their gates
have grown warm to the touch. Maren Vell, the Lodge's archivist, has read the warning the First Wright carved into those
ruins: _when the gate stones grow warm, the chain is failing; someone must walk it and forge the last seal anew._ The
Lodge has kept an old stone signet for generations without knowing what it was. Now it knows, and it needs someone to
carry it. The gate plan also shows how to cut a copy from the relic, so a lost Signet of the Undervault can always be
made again (the repeatable quest of the [realm portal spec](../design/realm-portal.md)).

## The player

- **Role:** a newcomer to Overworld's wild lands, capable and curious, whom the Lodge calls a **Wayfarer**. Not
  a chosen one: the seals accept anyone who passes their trials, and that is the point of them. Texts never assume
  the player's race, class, gender or name ([style guide](style.md#the-player)).
- **The arc:**
  1. **Overworld (chapter 1, levels 1–10):** learn to fight and cast, build the first gate, earn passage into the
     first sealed realm.
  2. **Undervault (chapter 2, levels 6–18):** meet the Cairnfolk, open the Gatewrights' catacombs, pass the first
     guardian's trial and carry its shards home.
  3. **The chain (chapters 3 to 6):** each chapter opens the next realm, or the pair of twins (Sunwastes and
     Everwinter, chapter 3). Each realm shows the Void a little closer: its guardian is harder to reason with and its
     threat a little stranger. From level 30, Olympus hands out quests into the later realms.
  4. **The Void realm (chapter 7, the last of v1):** Unmade Isles, the realm nearest the Void, where the First Wright
     went.
- **The finale (Matěj, RLM-139):** in the Void Cathedral the player finds the First Wright, hollowed out by the
  Void after ages of holding the line. He no longer holds the last seal; he means to open it and forge the Void
  itself into a realm, which would unmake the rest. The player defeats him and forges the last seal anew from the
  signets of the main path (signets are never used up), as the [Void realm design](../design/realms.md#the-void-realm)
  proposes (Matěj decides it in RLM-164). The chain holds, the realms stay open to those who earned them, and the
  Lodge names the player the first Gatewright of a new age. Play goes on after it: the realms, their dungeons and
  later the contracts stay.

## The realms

The realms of the [realm design](../design/realms.md), in chain order. Realm names are the names players read,
approved by Matěj (RLM-139, RLM-188), and the design documents use them too. A realm name never takes an article
(Matěj, 2026-10-15). Where a player first meets one, it carries a plain subtitle with its element, such as
"Undervault — Stone Realm" ([style guide](style.md#text-types)). Colours are the portal colours of the
[realm portal spec](../design/realm-portal.md).

| Realm (design) | Name | Chapter (levels) | Element | Friendly people | Hostile people | Guardian |
| --- | --- | --- | --- | --- | --- | --- |
| Overworld | Overworld | 1 (1–10) | home | the Wayfarers' Lodge | the wild's monsters (vanilla) | none: no seal |
| Stone realm | Undervault | 2 (6–18) | earth and stone | the Cairnfolk | the Hewn | the Unbroken, a stone colossus |
| Desert realm | Sunwastes | 3 (16–24) | sun and sand | the Wellkeepers | the Gilded | a great sphinx (twin seal) |
| Tundra realm | Everwinter | 3 (20–30) | frost | the Kettlefolk | the Thawed | the Thawed's queen in the ice castle (twin seal) |
| Hub realm | Olympus | none (from 30) | the heavens | its citizens | none | none: a branch seal |
| Prehistoric realm | Prestoria | 4 (26–40) | the ancient wild | open | the ancient beasts | open |
| Swamp realm | Quagmire | 5 (36–44) | slime | open | the slimes | open |
| Fire realm | Ashfire | 6 (40–52) | fire | the Emberwrights | the forge-drakes | the eldest forge-drake |
| Void realm | Unmade Isles | 7 (48–60) | void | the Chainkeepers | the Unforged | the First Wright (finale) |

Each paragraph below gives the realm's history, its peoples and its threat; the dungeons, mobs and materials it
names are the realm design's.

### Overworld

The heart of the world and the start of the chain, the one place the Gatewrights never sealed. It has no seal and
no signet: every realm's way back leads here. The **Wayfarers' Lodge** has studied the Gatewright ruins here for
generations, and its archivist starts the player's journey. Overworld's danger is the ordinary wild (night
monsters, caves), which is where a Wayfarer learns to fight. Overworld gets dungeons, bosses and a final boss of its
own in a later milestone, and that boss then stands before the way into Undervault (Matěj, 2026-10-16); the
lore for it is written with them. In M1 the Lodge is a voice in the quest book; a Lodge
house in a village may come with the M4 village overhaul and offer more with each chapter (WF-8; Matěj, RLM-139).

### The stone realm: Undervault

The first realm the Gatewrights forged and the nearest to home: a vault of stone under the world, one great cave
where they kept their forge-stock and buried their dead. The ancient debris under its floor is that forge-stock,
sunk into the rock and fused there over an age. Its light comes from glowstone in the ceiling and crystal in the deep
grottos, the same crystal the Gatewrights used to steer light through their vaults (the light-beam puzzles).

- **The Cairnfolk** are a sturdy, cheerful mining people who have lived in Undervault since it was sealed, cut
  off from Overworld and in no hurry to leave it. They work the realm's mines, trade ore for anything new from
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

### The desert realm: Sunwastes

The sun realm, one of a pair. The Gatewrights forged it and Everwinter from one ingot and quenched them in two
ways: this half was left in the sun. Its people, the Sun Court, built pyramids and a great sphinx as vaults for
their treasures, and buried their own forges under the sand when the seal closed.

- **The Wellkeepers** live in the big oases and in the few towns not yet empty. They know the dunes, guard the
  water and trade with anyone who respects it.
- **The Gilded** are the Sun Court's servants, who covered themselves in gold to outlast the sealing. They did,
  after a fashion: sand-dry and gold-masked, they still guard the pyramids and raid the oases for what they think is
  theirs.
- **The guardian** is a great sphinx in its own dungeon, which holds one of the two shards the next seal needs.
  Its trial is the realm's riddles as much as its claws.

### The tundra realm: Everwinter

The frost realm, the other half of the pair: quenched in ice. The Gatewrights froze it to hold something still,
and it held: ships froze in the lakes mid-voyage and a sea-raiding people slept under the ice for an age.

- **The Kettlefolk** live in stilt villages around the hot springs, fishing through the ice. They are warm, nosy
  and generous, and they know which lakes hum at night.
- **The Thawed** are the raiders under the ice, waking as the seal thins. They are not dead, only very late, and
  they want their fortress, their ships and their sea back. Their nordic fortress and their frozen shipwrecks are
  the realm's dungeons.
- **The guardian** is the queen of the Thawed in the ice castle, who holds the other shard that seal needs. The
  two shards together forge the next signet.

### The hub realm: Olympus

A small realm the Gatewrights lifted into the heavens to keep it out of reach: a celestial city and palace above the
chain, a branch seal rather than a link, with no guardian and no trial. Its citizens are the only named people who
live in one place for good (Matěj: unique NPCs live only in Olympus); they know the later realms and send Wayfarers
into them from level 30, and they remember what the guide leaves out. Nothing there can be built or broken, and the
only ways out are the gate a traveler came through or a long fall. Its history, its people and how a Wayfarer first
reaches it are written with the realm ([realm design](../design/realms.md#the-hub-realm)).

### The prehistoric realm: Prestoria

A realm the Gatewrights sealed around the oldest wild they found, so its beasts would outlive the age that bred
them: dinosaurs. Its peoples and guardian are written with its chapter.

### The swamp realm: Quagmire

A sunken, steaming realm where the Gatewrights quenched their failed castings, which never cooled and never died:
slime of many kinds, in swamps and in banks of slime. Its peoples and guardian are written with its chapter.

### The fire realm: Ashfire

The realm of the Gatewrights' great forge, deliberately late in the chain: what burns here is too strong for a
newcomer. Its plains are black and red stone under burnt trees, with lava lakes, and volcanoes where the forge's
fires broke through. It is not the Nether, and nothing in it is (Matěj, 2026-10-16).

- **The Emberwrights** are smiths who still tend the forge's last fires, the nearest thing to Gatewrights left
  alive. They are proud, short-tempered and the best crafters in the chain.
- **The forge-drakes** are the Gatewrights' bellows-beasts gone wild: dragons born in the forge, with wings of
  iron too heavy to fly. That is why every dragon in Realmforge fights on the ground.
- **The guardian** is the eldest forge-drake, in an arena at the bottom of a volcano.

### The Void realm: Unmade Isles

The last realm the Gatewrights forged and the nearest to the Void, which broke it into islands. They bound the
islands together with great chains and bridges, raised dark stone gates and a cathedral on its farthest island,
and kept the last seal there. The Void here is not dark: it glows violet, under a pale moon, and the isles' purple
stone, dark wood and crystals catch that light (RE-18). Where the Void touches forged stone a green ore grows,
malachite, the one thing in the chain the Void seems unable to unmake.

- **The Chainkeepers** are the last order on the islands, each keeping one link of the great chains from a
  link-house where a chain is anchored. They are solemn, few and very glad to see anyone. They say the chains catch
  whoever falls, and the Void gives them back (the realm design's fall rule).
- **The Unforged** are things of the Void: half-made shapes that copy what they see of the realms and get it
  slightly wrong. Each copies a people of an earlier realm, a Hewn or a Gilded, with one detail out of place. Where
  they strike, the Void unmakes a little of what a Wayfarer wears, unless it is malachite. They are strange rather
  than gruesome.
- **The guardian** is the First Wright in the Void Cathedral (the finale above). The guard towers on the islands
  hold the Unforged that have climbed out of the Void, and at the broken gates they still pour through.
- The [realm design](../design/realms.md#the-void-realm) designs this realm (RLM-95), and this paragraph follows
  it; Matěj's approval of that design is RLM-164.

### Further realms

The chain can grow beyond v1 with the [candidate realms](../design/realms.md#candidate-realms), each a link nobody
had counted. The dense forest and the underwater realm are dropped (Matěj, RLM-188), and the sky islands' celestial
palace became Olympus.

A new realm fits the chain when it has: one element (RE-5) and a look of its own (RE-20); a friendly and a hostile
people; a guardian holding the shards of the seals after it; and a reason the Gatewrights forged it.

## Factions and recurring characters

### Factions

| Faction | Where | Stance | Gives quests about |
| --- | --- | --- | --- |
| The Wayfarers' Lodge | Overworld, and with the player along the chain | friendly | the main chain, Gatewright lore, exploration; later contracts and guild ranks (M7) |
| The Cairnfolk | Undervault's mines | friendly | ore, mining, the catacomb keys, repeatable trades |
| The Hewn | Undervault's catacombs and halls | hostile | - |
| The Wellkeepers | the oases and towns of Sunwastes | friendly | water, the dunes, the Gilded's raids |
| The Gilded | the pyramids of Sunwastes | hostile | - |
| The Kettlefolk | the hot-spring villages of Everwinter | friendly | fishing, the frozen lakes, the shipwrecks |
| The Thawed | the fortress and ships of Everwinter | hostile | - |
| The Emberwrights | the forges of Ashfire | friendly | crafting, the forge-drakes |
| The forge-drakes | the volcanoes of Ashfire | hostile | - |
| The Chainkeepers | the islands of Unmade Isles | friendly | the chains, the Cathedral, the last seal |
| The Unforged | Unmade Isles and the Void | hostile | - |
| The Sealbound | one in every sealed realm | friendly | the guardian's trial |

The friendly peoples are the realms' quest-givers, traders and, once villages are reworked, their villagers. A
faction that gives repeatable trades (the Cairnfolk taking ore) is also a natural home for the repeatable quests of
a chapter.

### Recurring characters

- **Maren Vell**, archivist of the Wayfarers' Lodge: the player's mentor and the voice of the quest book.
  Scholarly, kind, a little dry; she reads Gatewright script better than anyone alive and has never been through a
  gate herself, which she minds more than she says.
- **Torva Flintmark**, forewoman of the Cairnfolk mine nearest the arena: blunt, cheerful, keeps the catacomb keys
  and sells them again for repeat runs (LE-2). The Cairnfolk's voice in chapter 2. Unique NPCs live only in Olympus
  (Matěj, 2026-10-15), so in Undervault she speaks through the quest book; Claude's pick for Olympus's seller of
  late-game dungeon keys is Torva, who has kept keys all her life ([realm design](../design/realms.md#the-hub-realm)).
- **The Scribe in the Stone**, Undervault's Sealbound: a Gatewright set into the arena wall, slow to speak and
  formal. Every sealed realm has a Sealbound like him, so the player meets the same kind of figure before each
  guardian.
- **The First Wright**, founder of the Gatewrights: heard throughout the chain in the inscriptions he left, met only
  at the end.

## Chapters 1 and 2 for Q2

M1 ships chapters 1 and 2 (RLM-188), and Q2 writes both: about 25–30 quests, the 16 beats below as the two main
questlines plus side quests (roadmap: rewards from the gear set; each chapter's last quest grants its chapter
advancement). Beats 1–9 are chapter 1, in Overworld (levels 1–10); beats 10–16 are chapter 2, in Undervault (levels
6–18). These beats are the story they tell; each names what it teaches, so the quests can follow the order of the
systems M1 builds. The order, the counts and the rewards are Q2's to set, against the quest XP of the
[leveling model](../design/leveling.md#quests).

| # | Beat | Where | What the player does | Story |
| --- | --- | --- | --- | --- |
| 1 | A Letter From the Lodge | Overworld | opens the quest book; has chosen a class | Maren welcomes a new Wayfarer and explains the warm gate stones |
| 2 | The Wild Is Wide | Overworld | kills a few hostile mobs; gains levels | the Lodge wants to know the newcomer can handle themselves |
| 3 | A Spark in the Hand | Overworld | casts class spells | Maren on the old crafts of fighting and casting |
| 4 | What You Learn | Overworld | learns a talent | everyone in the Lodge has a specialty |
| 5 | Stone and Copper | Overworld | gathers stone bricks and copper | the Gatewrights built gates from plain stone; Maren has the plan |
| 6 | Realmforge Bricks | Overworld | crafts Realmforge Bricks | the plan's first page |
| 7 | The Keystone | Overworld | crafts the sealstone | the gate's heart: a stone that remembers where it leads |
| 8 | Raise the Gate | Overworld | builds the frame | the first gate raised in an age |
| 9 | The Signet of the Undervault | Overworld | finishes the unlock quest and receives the Signet of the Undervault; chapter 1's advancement raises the level cap | the Lodge's old relic turns out to be the first signet; Maren cuts a copy for the player from it (and will again if it is lost); the seal will know the player now |
| 10 | Into Undervault | Undervault | enters the realm (and finds a Cairnfolk mine once mines exist) | first arrival; the Cairnfolk are surprised to see anyone from above |
| 11 | Ore for the Cairnfolk | Undervault | mines the realm's ores | Torva trades ore for the catacomb key |
| 12 | The Catacombs | Undervault | clears the catacombs to their treasury | the Gatewrights' dead and their riddles; the arena key lies in the treasury |
| 13 | The Hewn Awake | Undervault | defeats the realm's statues | the stone servants wake as the seal thins |
| 14 | The Scribe in the Stone | Undervault | enters the arena, receives the summoning item | the Sealbound explains the trial |
| 15 | The Unbroken | Undervault | summons and defeats the boss | the guardian's trial; its two shards drop, one for each twin seal ahead |
| 16 | Home With the Shards | Overworld | brings the shards back | Maren reads the shards: two seals ahead, sun and frost. Chapter 2's advancement raises the level cap |

- **Givers:** the quest book speaks in Maren's voice (her notes and letters), and in Undervault in Torva's and
  the Scribe's. The beats need no NPC entity and no mine: the realm design leaves the NPC mines to later tasks, so
  where M1 has none, the same lines come from the quest book, beat 10 is the arrival itself, and beat 11 takes the
  realm's ores from anywhere.
- **The catacomb key** comes from Torva in the story. If M1 hands it out another way (a quest reward), beat 11
  becomes "Torva sends a key with the ore you brought" and stays true.
- **Before the way in:** RLM-224's optional "Prepare" quest (recommended gear and items) stands between beats 9 and
  10, until Overworld's final boss takes that place in a later milestone (Matěj, 2026-10-16).
- **Lost pages:** Gatewright inscriptions found along the way can be click-to-read quests that teach one system
  each (PR-2, PR-11), in the First Wright's voice.

## How the lore explains decided rules

Decided rules that a line of lore makes feel natural. Texts may use these lines; none of them changes a rule.

| Rule (source) | Lore |
| --- | --- |
| Respawn always at the bed in Overworld or at world spawn, never in a realm (RLM-128) | Realmforge's oldest magic is the pull of home: the Gatewrights made sure nobody who falls beyond a seal is lost there |
| Keep inventory on death (vision) | the pull of home brings back what the traveler carried, too |
| "Return to last death" scroll (RLM-128) | a scroll that follows the thread the traveler's spirit left on the way home |
| Level cap per chapter, its last level, raised when the chapter is done (D2, RLM-188) | the seals bind strength as well as realms, so nobody could grow strong enough to break one by force; the bond loosens once every trial of a chapter is passed (both guardians of twin realms) |
| Signets are never used up; one signet opens any number of portals (RLM-128) | a seal asks for its signet every time and never takes it: a signet is shown, not paid |
| Unlocks are per player: without their own unlock, a player can neither open nor switch a portal to that realm with its signet, nor pass through an open one (RLM-128) | the seal knows a traveler by their deeds |
| Portals link like the Nether's, with scaled coordinates, so arrivals stay near a realm's origin (RLM-128) | none needed: texts never explain where a portal leads inside a realm |
| Only players travel through realm portals (RLM-128, "for now") | the gates pass a living traveler and what they carry, nothing that walks or drifts on its own |
| The next signet is crafted from the current realm (RE-9) | the guardian holds the shards of the seals after it |
| No flying bosses (vision) | the forge-drakes' iron wings are too heavy to fly |
| Every realm has its own light (RE-18) | the Gatewrights forged light into every realm, the violet glow of Unmade Isles included |

## What the lore leaves open

The outline forces none of these; each is decided elsewhere, and the lore follows whatever is decided:

- **The Lodge house.** Portals can stand anywhere in Overworld (RLM-128), so in M1 the Lodge is a voice in the
  quest book and a society, not a building. A Lodge house in a village may come with the M4 village overhaul
  (RLM-139), and the lore follows whatever that task builds.
- **Friendly NPCs in M1.** There are none (Matěj, 2026-10-15), and the beats of chapters 1 and 2 work without them.
- **Olympus, Prestoria and Quagmire.** Their lines above give each a reason to exist; their peoples, guardians and
  history are written with their chapters, and Olympus's way in with its design.
- **Boss mechanics, mob and item names** (W3, W5, A1). The outline gives the bosses and peoples an identity; the
  tasks that build them name and shape them, within the [style guide](style.md#names).
- **The Void realm's gameplay** ([realm design](../design/realms.md#the-void-realm), RLM-95, waiting for Matěj in
  RLM-164): the last seal forged from the main-path signets, malachite gear (the lore says the Void cannot unmake
  malachite, which the proposed set bonus follows) and the fall rule. If he changes one, the lines that tell it
  change with it.

## Decisions

Matěj's answers of 2026-10-13 and 2026-10-14 (RLM-139), which this version records:

1. **The outline is approved:** the Gatewrights split the world into realms chained against the Void; signets open
   seals; each guardian is a trial that holds the shards of the seals after it; the Wayfarers' Lodge sends the
   player along the chain. The [style guide](style.md) is approved too.
2. **The names are approved as proposed:** Realmforge, the Gatewrights, the Chain of Seals, the Wayfarers' Lodge,
   Maren Vell, Torva Flintmark, the Cairnfolk, the Hewn, the Unbroken, the Scribe in the Stone and the other
   peoples in the tables.
   - **Realm names:** Undervault, Sunwastes, Rimefields, Forgefire and Brink; Overworld stays Overworld (three were
     renamed on 2026-10-15, item 7). Where a player first meets a realm's name (the guide, the signet tooltip, the
     arrival title), it carries a plain subtitle with its element, such as "Undervault — Stone Realm". The
     [realm design](../design/realms.md) uses these names.
   - **"Maren Vell" stays** (2026-10-14). Said aloud it is "Ma-ren Vell", not Marvel's "Mar-Vell", and "Vell"
     echoes vellum, which suits an archivist.
3. **The finale as proposed:** the First Wright, hollowed out by the Void, is the final antagonist in the Void
   Cathedral, so the Void realm is on the main path. Forging the last seal from every main-path signet is a
   gameplay idea for the Void realm design: [RLM-95 proposes it](../design/realms.md#the-void-realm), and Matěj
   decides it in RLM-164. Side realms stay optional, so their signets are never part of it.
4. **The level cap lore is used:** texts may say that the seals bind strength and that the bond loosens once every
   trial of a chapter is passed. The rule itself is unchanged.
5. **The Lodge is a voice in the quest book for M1;** a Lodge house in a village may come with the M4 village
   overhaul.
6. **No Czech texts, ever:** the game is English only ([vision](../vision.md)), so the style guide has no rules for
   other languages.
7. **The v1 realm layout** (Matěj, 2026-10-14 to 2026-10-16, RLM-188):
   - **renames:** the Rimefields became **Everwinter**, the Forgefire **Ashfire**, and the Brink **Unmade Isles**;
   - **no article:** a realm name never takes "the": Overworld, Undervault, Sunwastes, Everwinter, Olympus,
     Prestoria, Quagmire, Ashfire, Unmade Isles;
   - **new realms:** Olympus (the hub, from level 30), Prestoria (prehistoric) and Quagmire (swamp); the forest and
     underwater realms are dropped;
   - **chapters:** a realm is a chapter and the twins share one, so chapter 1 is Overworld (levels 1–10), chapter 2
     Undervault (6–18), and so on to chapter 7, Unmade Isles (48–60). M1 ships chapters 1 and 2;
   - **Ashfire is not the Nether** and is not inspired by it (2026-10-16).

## Open questions

None: Matěj answered every question of the first version (RLM-139). The Void realm's own questions are the
[realm design's](../design/realms.md#open-questions).
