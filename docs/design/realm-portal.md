# Realm portal

> The realm portal spec (RLM-41): the one portal that leads to every realm, how a player opens it, unlocks a realm,
> travels there and back, and what happens at the edges. Written on 2026-10-12 from Matěj's requirements in RLM-41
> and the [realm design](realms.md) (RLM-40), with the reference-game research
> ([reference-games.md](../research/reference-games.md), cited by item ID such as RE-1; AoA's portal in detail:
> [aoa-systems-progression.md](../research/notes/aoa-systems-progression.md#1-portals-realmstones-and-the-unlock-graph-hub-returning)).
> Approved by Matěj on 2026-10-13 with changes (RLM-128, [Decisions](#decisions)):
>
> - portals link like the Nether's, with scaled coordinates, so there is no landing and no home point;
> - a realm's unlock comes from its quest, not from holding its sigil;
> - a player always respawns in the Overworld.
>
> RLM-133 closed three gaps that the review of the first version found. Roadmap tasks W2a (the realm, RLM-32) and
> W2b (the portal, RLM-154) build it ([What M1 takes from this](#what-m1-takes-from-this)). Block, item and realm
> names are working names. APIs named here were checked against Minecraft 26.1.2 and NeoForge 26.1.2.116 on
> 2026-10-13, except where marked *verify*.

## At a glance

- **One frame for every realm** (RE-1): 5 blocks wide and 6 tall around a 3×4 opening, built by the player from cheap
  Overworld materials. The bottom middle block is the **realm selector**.
- **One sigil per realm**, never consumed and stacking to 1; one sigil opens any number of portals. Right-clicking
  the selector with a sigil opens the portal onto that realm, or switches an open portal straight to it, with no
  clearing step (RE-1).
- **The unlock is the player's own:** a realm's unlock quest grants it, for that player, for good. Every copy of a
  sigil is the same item and anyone may hold one, but without the unlock a player can neither open a portal to that
  realm with it nor pass through an open one.
- **Portals link like the Nether's, in both directions** (Matěj, RLM-128). An Overworld portal leads to the realm
  portal near its scaled position in the realm, or builds one there, and the way back works the same in reverse.
  Any number of portals can stand anywhere in the Overworld.
- **Scaled coordinates keep arrivals near a realm's origin,** where its lowest levels are: the stone realm links at
  1:16, so every Overworld portal within 10,000 blocks of the origin arrives in its first level band
  ([The coordinate scale](#the-coordinate-scale)).
- **A player always respawns in the Overworld,** at their bed or at the world spawn, never in a realm. A
  "Return to last death" scroll (RLM-155) takes them back to where they died.
- **Each realm has its own colour**, particles and sound, and a flavour line (RE-1).
- **Only players travel** (Matěj: "for now"). Items, mobs and vehicles never pass a realm portal, and no other
  entity is moved into or out of a realm, so there is no chunk-loading trick and no realm mob in the Overworld
  ([Multiplayer](#multiplayer) names the one path the rule cannot see).

| Realm | Sigil (working name) | How a player gets the sigil | What unlocks the realm | Portal colour |
| --- | --- | --- | --- | --- |
| The Overworld | none | - | always open | green `#5DBB63` |
| Stone realm | Stone Sigil | reward of the early chapter 1 unlock quest; a repeatable quest replaces a lost one | that quest, which a level-1 player can finish | slate blue `#7A8FB0` |
| Desert realm | Desert Sigil | crafted from a stone realm ore plus the stone realm boss's guaranteed drop (RE-9) | its main-chain quest after the stone realm boss | gold `#E8B84A` |
| Tundra realm | Tundra Sigil | the same, with a different stone realm ore | its own quest after the same boss | ice white `#BFF4FF` |
| Fire realm | Fire Sigil | from the realm before it, by the same rule | the quest after that realm's boss | red `#E0502C` |
| Void realm | Void Sigil | from the realm before it, by the same rule (RLM-95) | the quest after that realm's boss | deep purple `#4B1F6F` |

Colours are proposals and live in data ([Data](#data-contentrealms)). Purple is kept for the Void realm, which comes
after M4 removes the nether portals ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)), so no realm portal
looks like a nether portal.

## The frame

- **Two block types** (Matěj, RLM-128, as in Advent of Ascension): **Portal Stone** for the frame and the **realm
  selector** for its bottom middle. Both are crafted from Overworld materials.
- **Shape:** a vertical rectangle, 5 wide and 6 tall, around a 3×4 opening, along either horizontal axis (like a
  nether portal). The four corners are optional decoration, as on a nether portal: the frame is the 14 blocks that
  touch the opening, 13 Portal Stone and one realm selector in the middle of the bottom row. So every required block
  touches a portal block, and breaking any of them reaches the portal through a neighbour update
  ([Edge cases](#edge-cases)). One fixed size keeps the check simple and every portal recognisable; AoA used the
  same outline (RE-1).
- **Materials, cheap and from the Overworld,** because the first frame is built before any realm is open, and AoA's
  players stalled on exactly this step (they ended up buying its runes from an NPC):
  - **Portal Stone:** 4 stone bricks around a copper ingot make 4. A tag of stone bricks
    (`#realmforge:portal_stone_bricks`) also takes the realms' own bricks, so a frame can be built inside a realm
    that has no vanilla stone;
  - **realm selector:** 4 Portal Stone, 4 gold ingots and a compass;
  - a whole frame takes 17 Portal Stone (13 in the frame, 4 in the selector), so 5 crafts: 20 stone bricks and 5
    copper ingots, plus 4 gold ingots and a compass. That is a short mining trip for a level 1–5 player, and chapter
    1's first quests walk through it. The numbers are proposals for Matěj's playtest.
- **Lighting:** right-click the selector with a sigil ([The realm selector](#the-realm-selector)). The selector
  checks the player's unlock first, then the frame:
  - **complete,** with the opening empty (air, or blocks such as grass that are replaced when something is placed
    there) or already filled with this frame's own portal blocks (a switch): the opening fills with portal blocks of
    that realm, and the action bar says "The portal opens onto the Stone Realm";
  - **incomplete:** nothing lights, the action bar says "The frame is incomplete", and particles mark every missing
    or wrong block for a few seconds, so the player sees what is left (the visible-progress rule of the
    [realm design](realms.md#puzzles-and-traps)).
- **No other igniter.** Fire and flint and steel do nothing; a nether portal never lights inside the frame (it needs
  obsidian).
- **Frame blocks cannot be pushed** by pistons (`BlockBehaviour.Properties#pushReaction(PushReaction.BLOCK)`), so no
  contraption moves a lit frame apart.

## The realm selector

The bottom middle block of the frame (Matěj's requirement). Its block entity holds one setting, which always matches
the opening:

- **none** while the opening is dark;
- **a realm** while the portal is open onto it;
- **return** while it is open onto the Overworld, the way back from a realm ([Travel](#travel)).

The setting becomes none whenever the opening goes dark: the sneak gesture closes it, or a broken frame block takes
the portal blocks with it (they check their frame on neighbour updates) and resets a selector that still stands. So a
closed or rebuilt frame lights again with the sigil it showed before (RLM-133). The selector stores no item, so
hoppers have nothing to take.

| Action on the selector | Result |
| --- | --- |
| right-click with a sigil of a realm the player has unlocked | opens the portal onto the sigil's realm, or switches it there at once; the sigil stays in the hand |
| right-click with the sigil of the realm a lit portal already shows | nothing changes; "The portal already opens onto the Stone Realm" |
| right-click with a sigil of a realm the player has not unlocked | refused, with the realm's hint: "You are not eligible to open a portal to the Stone Realm" |
| right-click with the sigil of the realm the frame stands in | refused: "You are already in the Stone Realm" |
| right-click with an empty hand | the action bar shows where the portal opens onto, or "The portal is closed" for a dark one |
| sneak and right-click with an empty main hand, whatever the off hand holds | in the Overworld, closes the portal (setting none); inside a realm, opens it onto return, or switches it back to return |
| break the selector | the portal closes, and the selector drops as an item without its setting |

- **Who may switch a portal:** anyone holding a sigil they are eligible to use (Matěj, RLM-128). Unlocks are per
  player ([Multiplayer](#multiplayer)), so switching changes only where the portal leads, never who may enter.
- **The sneak gesture with something in the off hand** (RLM-133). Vanilla skips a block's use while the player
  sneaks with anything in either hand that does not bypass the sneak (`ServerPlayerGameMode#useItemOn` on the server,
  `MultiPlayerGameMode#performUseItemOn` on the client; `IItemStackExtension#doesSneakBypassUse` is true only for an
  empty stack by default). A shield, torch or food in the off hand would then block the gesture, and a torch or a
  block there would be placed against the selector instead. Both methods post `PlayerInteractEvent.RightClickBlock`
  before that check. So a common listener in `world`, which runs on both sides, sets `useBlock` to `TRUE` when the
  player sneaks, the main hand is empty and the target is a realm selector. The selector's `useWithoutItem` then
  handles the gesture on both sides. On the client the main hand's use succeeds, so the client never goes on to the
  off hand.

## Realm sigils

- **One per realm,** stacking to 1, never consumed (RE-1), fire resistant (`Item.Properties#fireResistant`), so lava
  does not take it. One sigil opens any number of portals (Matěj, RLM-128). Its tooltip names the realm and says
  "Never used up". It shows no levels: they live in the server-only `realmforge:dimension_rules`, and the arrival
  title, which the server sends, shows them.
- **A sigil is a key, not the unlock.** Every copy of a realm's sigil is the same item, whoever obtained it, and
  anyone may hold one (Matěj, RLM-128). A player needs a sigil to open or switch a portal, and their own unlock
  ([Access and unlocks](#access-and-unlocks)) to use it.
- **Where sigils come from** (RE-9, LE-1):
  - **the Stone Sigil** is a reward of the stone realm's unlock quest, an early chapter 1 quest that a level-1 player
    can finish (the roadmap's "locked until a chapter 1 quest unlocks it"; the stone realm is open from level 1 and
    recommended from 5, [realm design](realms.md#chapters-and-levels)). The same quest grants the unlock;
  - **a lost Stone Sigil** comes back through a repeatable quest that opens once that first quest is complete and
    gives a new sigil for a few copper ingots (FTB Quests' repeatable quests; Q1 checks the field). A player who lost
    the sigil before or after opening a portal is never stuck;
  - **every later main-path sigil is crafted** from an ore or gem of the realm the player is leaving plus the
    guaranteed drop of that realm's main-path boss. The recipe is shown in the quest log, and crafting the sigil is
    a step of the chapter's main chain. Beating that boss is what "completing" a realm means; the realm itself has
    no completion flag ([realm design](realms.md#opening-the-next-realm));
  - **when a chapter opens two realms** (chapter 2: the desert and the tundra), the boss drops one material per
    realm it opens, two for chapter 2, every kill;
  - **when two realms share a chapter, the next main-path sigil needs both:** its recipe takes the guaranteed drop
    of each realm's main-path boss (for the realm after chapter 2: the desert's and the tundra's), so finishing one
    branch never opens the next realm. Both realms are needed for further progress
    ([realm design](realms.md#the-desert-realm));
  - **an optional side realm's sigil** comes from wherever its design puts it: a chest at 100% in an instanced
    dungeon, which is rebuilt for every run, or a trade that restocks; never a random drop, and never a chest found
    only once (PR-15).
- **Nothing depends on luck, and every sigil can be replaced:** each source is a quest reward, a guaranteed drop or
  a recipe. The Stone Sigil has its repeatable quest; every later sigil is crafted again from a boss the player can
  always replay, since an NPC sells the keys of dungeons already done (LE-2). A lost sigil never locks the realm
  again, because the unlock belongs to the player.

## Access and unlocks

- **The unlock is an advancement per realm,** `realmforge:realm/<realm>`, generated from the realm's data. Its only
  criterion is `minecraft:impossible`, like a chapter's advancement
  ([architecture 16](../architecture.md#16-progression-and-quests)), so nothing in play grants it but the realm's
  unlock quest. Holding, crafting, picking up or trading a sigil unlocks nothing. The advancement is hidden until
  earned and shows a toast, "Realm unlocked: the Stone Realm"; it adds no chat line (`hidden`, `show_toast`,
  `announce_to_chat` in vanilla's advancement display).
- **The unlock quest** grants it through an `AdvancementReward` that is per player and claims itself
  (`team_reward: false`, `auto: "enabled"`), the rule every chapter reward follows
  ([architecture 16](../architecture.md#16-progression-and-quests)). Its tasks are the player's own progress, never
  "have the sigil", which a trade could complete:
  - **the stone realm's:** the early chapter 1 quest; it also rewards the Stone Sigil. Q2 writes it;
  - **a later main-path realm's:** the main-chain step after the boss or bosses whose drops its sigil takes, with a
    task such as defeating that boss (`realmforge:boss_defeated`). The chapter that adds the realm writes it.
- **The access requirement:** the realm dimension's entry in `realmforge:dimension_rules` holds it
  ([architecture 15.1](../architecture.md#151-dimensions)). For a realm it is
  `{type: realmforge:has_advancement, advancement: realmforge:realm/<realm>}`, from the first set of
  `realmforge:requirement_type` ([architecture 5.3](../architecture.md#53-type-registries)). A realm may add more
  requirements, such as a level; validation fails a realm whose rules leave out its unlock advancement.
- **The selector checks it** before it opens or switches a portal to a realm
  ([The realm selector](#the-realm-selector)).
- **The portal checks it when a player steps into a portal block,** so the portal never starts its countdown for a
  player without access. That player stays where they are, and a centre-screen message says "You are not eligible
  to pass through the portal." (Matěj's wording, RLM-128), with the realm's hint below it ("Unlocked by a chapter 1
  quest"). The message comes at most once every 3 seconds, with a short refusal sound; vanilla's title and
  subtitle packets carry it.
- **Every other way in is checked too,** so the advancement seals the realm whatever the path. In 26.1.2 every move
  into another level goes through `Entity#teleport(TeleportTransition)` (`ServerPlayer` overrides it), which posts
  NeoForge's cancellable `EntityTravelToDimensionEvent` before it moves anything. That covers:
  - realm and vanilla portals;
  - `/tp` and `/execute in … run tp` (`TeleportCommand` calls `Entity#teleportTo(ServerLevel, …)`, which calls
    `teleport`);
  - ender pearls;
  - other mods' teleports that use either method, waystone warps among them (*verify* the pack's Waystones
    version).

  `world` cancels the event for a player whose target is a realm they have not unlocked, with the same message. A
  warp to a waystone in a realm the player has not unlocked therefore never works (Matěj, RLM-128). A respawn does
  not pass through the event (`PlayerList#respawn` creates the player in the target level directly), and a respawn
  never puts a player in a realm ([Respawn](#respawn)).
- **What a locked realm shows elsewhere:** a portal someone else opened onto it looks the same as for anyone (its
  colour, particles and sound), so a player sees where a friend went. The sigil's tooltip and the realm's hint say
  how to unlock it. A realm map with locked realms and their hints (RE-7) is a later view, in the guide (RLM-102,
  approval in RLM-116) or a screen of its own; M1 needs none.
- **No operator bypass:** a creative player needs the unlock like anyone else, so tests and playtests see the real
  rule. `/advancement grant @s only realmforge:realm/stone`, or a QA command, unlocks a realm for testing.

## Travel

Portals link like the Nether's, in both directions (Matěj, RLM-128). There is no landing and no home point: a
player arrives wherever the linked portal stands.

### Linking

- **Into a realm:** the trip multiplies the portal's x and z by the realm's scale
  ([The coordinate scale](#the-coordinate-scale)) and clamps them into the realm's world border, as
  `NetherPortalBlock#getPortalDestination` does with `DimensionType.getTeleportationScale` and
  `WorldBorder#clampToBounds`. It then looks for a lit realm portal that leads back to the Overworld (setting return)
  within 16 blocks of that position. When it finds one, the player arrives there; otherwise a portal is built there
  ([Building a portal](#building-a-portal)).
- **Back to the Overworld:** the same in reverse, within 128 blocks of the scaled-back position, looking for a lit
  portal open onto the realm the player comes from.
- **The same radii as vanilla's `PortalForcer#findClosestPortalPosition`** (16 blocks in the Nether, 128 in the
  Overworld). So Overworld portals up to about 256 blocks apart (16 blocks × 16) lead to one realm portal, as
  Overworld portals up to 128 blocks apart share one nether portal. Matěj asked for exactly that: Overworld portals
  close to each other target the same realm portal.
- **The search reads point-of-interest data, as vanilla's does:** the realm selector is a point of interest of our
  own (`Registries.POINT_OF_INTEREST_TYPE`, `PoiManager#getInSquare`). The search then keeps only selectors whose
  portal is lit and leads the right way. Portals are never linked by a saved pair, so breaking or switching a portal
  needs no bookkeeping.
- **Between two realms:** a frame in realm A opened onto realm B with B's sigil links at the ratio of the two
  scales (1:1 when they are equal) to a return portal in B, or builds one. The way back from B leads to the
  Overworld, never to A, so chained trips cannot loop between realms (the "portal loops" AoA's changelogs fixed).
- **Linking can miss, like the Nether's.** A portal built up to 16 blocks from the scaled position maps back up to
  256 blocks from the Overworld portal it came from, beyond the 128-block search, and the way back then builds a
  new Overworld portal. A player fixes such a pair as with nether portals: build the realm-side portal nearer the
  scaled position, or the Overworld one nearer the scaled-back one. Waystones are the easy way around it.

### The coordinate scale

A realm's dimension type sets vanilla's `coordinate_scale`; the Nether's is 8 and the Overworld's 1.
`DimensionType.getTeleportationScale(from, to)` divides the source's scale by the target's. A trip from the
Overworld into a realm of scale 16 therefore multiplies x and z by 1/16, the way back by 16, and a trip between two
realms of the same scale keeps them. In 26.1.2 nothing else reads `coordinate_scale`, so it changes no other rule.
The scale is data, so a playtest can change it without code.

**The stone realm uses 1:16, twice the Nether's ratio** (Matěj: "like the Nether's 1:8, or stronger"). The ratio
follows from the realm's level bands:

- **Bands:** the [realm design](realms.md#the-stone-realm) puts the stone realm's sub-regions at about levels 5–7,
  7–9 and 9–10, rising from the realm's origin outwards.
- **The level rule W2a writes** (a proposal, by distance only; the depth part and the jitter of
  [architecture 14.1](../architecture.md#141-mob-levels) aside): level 5 at the origin, one level more every 320
  blocks, capped at 10. The bands are then 5–7 within 640 blocks of the origin, 7–9 up to 1,280, and 9–10 beyond.
- **The scale:** Overworld bases mostly stand within 10,000 blocks of the origin (the realm design's travel rule
  leaves trips of 1,000–10,000 blocks to waystones). 10,000 ÷ 640 ≈ 16, so every Overworld portal within 10,000
  blocks arrives in the first band.

| Overworld portal, blocks from 0, 0 | Arrival in the stone realm, blocks from 0, 0 | Level there, about |
| --- | --- | --- |
| 1,000 | 63 | 5 |
| 5,000 | 313 | 6 |
| 10,000 | 625 | 7 |
| 20,000 | 1,250 | 9 |
| 30,000 | 1,875 | 10 (the cap) |

- **At the Nether's 1:8,** a portal at 5,000 would arrive at 625 (level 7, the edge of the first band) and one at
  10,000 at 1,250 (level 9, deep in the second).
- **The way back multiplies by 16:** a realm portal 1,000 blocks from the realm's origin leads to the Overworld
  16,000 blocks out. That is how nether travel works too.
- **A later realm** sets its own scale from its own bands, by the same arithmetic.

### Building a portal

When no portal leads the right way within the search radius, the trip builds one, as vanilla's
`PortalForcer#createPortal` does for nether portals:

- **The spot:** the one nearest the scaled position, searched in a spiral within 16 blocks horizontally, where the
  frame stands on solid floor with air in its opening and on one side in front of it. It must be inside the world
  border and within the `y` range of the dimension's realm entry (`arrival.y`, [Data](#data-contentrealms)), which
  keeps arrivals in the depth band of the realm's lowest levels. The source portal's height, clamped into that
  range, decides between spots at the same distance.
- **Where nothing fits** (the stone realm has a bedrock ceiling; a realm can have a void below or water), the frame is
  carved out at the scaled position, clamped into the `y` range: a floor of the entry's arrival block (`arrival.floor`)
  under and in front of it, and air around it. A safe pocket of `arrival.pocket_radius` blocks keeps out fluids and
  falling blocks (RE-6: 3 by default, larger for a realm that needs it, such as an underwater one).
- **The new portal is lit:** in a realm its selector shows return, in the Overworld the realm the player came from.
- **It is an ordinary frame:** anyone may break, switch or close it, and its blocks drop like built ones, as a
  built nether portal's obsidian does. Its materials are cheap, so a built portal gives nothing worth farming.
- **The Overworld is no exception** (Matěj: the way back works "exactly like Nether → Overworld"): the way back
  builds a frame there when none is near. Its floor is the Overworld entry's arrival block.

### Arriving

- **The player arrives in front of the exit portal, facing away from it,** when one side of the opening has floor and
  two blocks of air. Otherwise they arrive in the opening, as a nether portal places them, and the portal cooldown
  vanilla sets after every trip (`Entity#setPortalCooldown`) keeps them from bouncing straight back.
- **A title** shows the realm's name and levels for 2 seconds on every arrival in a realm; the player's first
  arrival also shows its flavour line.
- **Chunk loading:** a trip loads only the chunks around the exit portal, for a short time, through vanilla's portal
  ticket (`TeleportTransition.PLACE_PORTAL_TICKET`, as for nether portals). Portals load nothing permanently, and
  since items and mobs never travel, no portal works as a chunk loader. Building a portal generates its chunks on the
  main thread, as vanilla does for a nether portal; W2b measures that hitch.

### Recall and waystones

- **Recall items** (Matěj, RLM-128): the Waystones mod's Warp Scroll and Return Scroll (TR-2), plus our own
  **"Return to last death" scroll** (RLM-155), which takes the player to where they last died (vanilla keeps that
  place for the recovery compass, `Player#getLastDeathLocation`). Its numbers (uses, recipe, cast time) are its
  ticket's. It moves the player with `Entity#teleport`, so the access rule applies, and since unlocks are permanent, a
  player who died in a realm can always go back. No Hearthstone of our own.
- **Waystones are crafted normally.** A player carries one into a realm, places it, and can warp to it later
  (TR-1, TR-7). Nothing places a waystone for them, and no spot is reserved for one.
- **Global or private** is each player's own choice, through the Waystones mod's own setting; the pack lets every
  player make their waystones global (RLM-132, [pack README](../../pack/README.md#configuration)).
- **A warp into a realm the player has not unlocked never works** ([Access and unlocks](#access-and-unlocks)).

## Respawn

Matěj's rule (RLM-128): **a player always respawns in the Overworld,** at their bed there, or at the world spawn
without one; never in a realm. This answers the realm design's
[open question 1](realms.md#open-questions).

- **Beds in a realm do nothing,** and they do not explode either, so a bed is no trap there. The realm's dimension
  type sets vanilla's `minecraft:gameplay/bed_rule` to `can_sleep: never`, `can_set_spawn: never`,
  `explodes: false` and an `error_message` such as "Beds only work in the Overworld".
  `ServerPlayer#startSleepInBed` then refuses with that message and sets no spawn point. The Nether's rule explodes
  instead.
- **Respawn anchors do not work there** (`minecraft:gameplay/respawn_anchor_works: false`): as in the Overworld, a
  charged anchor explodes when used.
- **A spawn point in a realm set some other way** (`/spawnpoint`, another mod) is overridden. NeoForge's
  `PlayerRespawnPositionEvent`, which `PlayerList#respawn` posts, moves a respawn whose level is a realm to the
  Overworld's world spawn.
- **A death costs the way back, nothing more:** `keep_inventory` and `immediate_respawn` are on
  ([architecture 19.1](../architecture.md#191-world-defaults)). The ways back are the player's own Overworld portal,
  a waystone they placed in the realm, and the "Return to last death" scroll.

## Look and sound

- **One portal block** for every realm, tinted with the colour of the realm its selector shows (return portals use
  the Overworld's green). Its texture is one greyscale swirl, tinted on the client by a block tint source
  (`RegisterColorHandlersEvent.BlockTintSources`, a `BlockTintSource` whose `colorInWorld` reads the frame's
  selector). The selector's block entity sends its setting to clients, and the colours come from the synced realm
  registry ([Data](#data-contentrealms)); *verify* that the region the chunk mesher reads holds the selector's block
  entity. Block colours are baked into the chunk mesh, so when the setting arrives on the client, the selector marks
  the opening for re-rendering (`Level#sendBlockUpdated` on the client level, which reaches
  `LevelRenderer#blockChanged`); otherwise a switched portal would keep the old realm's colour.
- **Particles:** a swirl of vanilla dust in the realm's colour (`DustParticleOptions`), plus one vanilla particle
  that suits the realm: ash for the stone realm (`minecraft:ash`), pale ash like blown sand for the desert
  (`minecraft:white_ash`), snowflakes for the tundra (`minecraft:snowflake`), small flames for the fire realm
  (`minecraft:small_flame`). The Void realm's green malachite sparks (RLM-95) may need a particle of our own.
- **Sound:** an ambient hum and a travel sound per realm. Until the sound task (A3, RLM-49) brings our own, the
  vanilla portal sounds play at a different pitch per realm.
- **No vanilla portal overlay:** `Portal.Transition.CONFUSION` makes the client draw the nether portal's purple
  texture over the screen (`LocalPlayer#handlePortalTransitionEffect` and `Gui`), so the realm portal uses
  `Portal.Transition.NONE`. A tinted overlay of our own in `client/` is polish for later.
- **Travel takes 2 seconds** standing in the portal (the nether portal takes 4), and one tick in creative
  (`Portal#getPortalTransitionTime`).

## Multiplayer

- **Unlocks are per player and permanent** (Matěj, RLM-128: "for now"). Each player completes the realm's unlock
  quest, so a server where one player is in chapter 2 does not open chapter 2's realms to a newcomer. Shared quests
  in a party come later, with low priority (RLM-156, on top of RLM-68's parties): when one member completes a quest,
  the members who can complete it get it too, the unlock included.
- **Sigils change hands freely,** but a friend's sigil opens nothing for a player without their own unlock.
- **A player without access** in a portal someone else opened is refused as in
  [Access and unlocks](#access-and-unlocks), while their friends pass through.
- **Everyone who comes through one portal arrives at the same linked portal,** and the way back links by position,
  whoever built the portals.
- **Only players travel.** Vanilla already keeps a passenger out of portals (`Entity#canUsePortal` is false for a
  passenger), and the realm portal refuses every entity that is not a player, so a player on a horse must dismount.
  WoW-style summoned mounts (M6) are dismissed and summoned again on the other side, and pets (later) follow their
  owner through their own system, which adds its own exception.
- **Other paths carry no mobs either** (RLM-133, rechecked for 26.1.2). Every move into another level goes through
  `Entity#teleport(TeleportTransition)`, which posts `EntityTravelToDimensionEvent` for every entity
  ([Access and unlocks](#access-and-unlocks) lists the paths). `world` cancels it for every entity that is not a
  player when a realm is its source or its target. The Waystones mod takes leashed mobs along on a warp
  (`transportLeashed = "ENABLED"` in the pack); *verify* that its version moves them with `Entity#teleport` or
  `Entity#teleportTo`, so the event stops them. If it does not, the pack sets `SAME_DIMENSION`, which leaves them
  behind on every warp to another dimension.
- **The one path the rule cannot see:** an entity created anew in another level (`EntityType#create` plus
  `ServerLevel#addFreshEntity`), as another mod's mob-catching item would do. That is a spawn, not a trip, and no
  event says where the mob came from. If the pack ever gains such a mod, its own config is the place to forbid
  realms; a mixin is the last resort ([architecture 19.3](../architecture.md#193-mixins-and-access-transformers)).

## Edge cases

- **A frame inside a realm:** allowed. The sneak gesture opens it onto return (the Overworld, at the scaled-back
  position); a sigil opens it onto another realm ([Linking](#linking)). It cannot open onto the realm it stands in.
- **Instance dimensions and other dimensions:** a frame lights only in dimensions that have a realm entry: the
  Overworld and the realms. In `realmforge:instances`, in the Nether and End until M4 removes them, and in
  dimensions of other mods the selector refuses: "The portal cannot open here". Inside instances players are in
  Adventure mode anyway and cannot build one ([architecture 15.5](../architecture.md#155-instances)). Dungeon
  entrances are a different thing: a temporary portal opened with a dungeon key that is used up (D8, W4), and
  leaving an instance returns the player to its entrance. Matěj sees a fixed entry and exit point as the instances'
  way, not the realms' (RLM-128).
- **Broken frames:** breaking any frame block or the selector closes the portal at once (the portal blocks check
  their frame on neighbour updates, as nether portal blocks do; every required frame block touches the opening, and
  the corners are optional, so every break reaches a portal block). A player standing in it stays where they are. The
  selector's setting becomes none, so the rebuilt frame opens again with the sigil
  ([The realm selector](#the-realm-selector)).
- **A missing destination:** a selector set to a realm that no longer exists (content removed, data pack off) shows
  "unknown realm" and refuses. A player who logs in inside a dimension that no longer exists is moved by vanilla.
- **Fluids:** the portal block refuses fluids on both of vanilla's paths:
  - **flowing fluids:** it is a `LiquidBlockContainer` whose `canPlaceLiquid` answers false. Vanilla's
    `FlowingFluid#canHoldAnyFluid` spares only its own portal blocks by name (`Blocks.NETHER_PORTAL`, `END_PORTAL`,
    `END_GATEWAY`), and `canHoldSpecificFluid` asks a `LiquidBlockContainer`. Without the refusal, flowing water or
    lava would wash away a realm portal's blocks, which have no collision;
  - **buckets and dispensers:** it overrides `canBeReplaced(BlockState, Fluid)` to answer false.
    `BucketItem#emptyContents`, which dispensers call too, asks `canBeReplaced` first. The default answers true for
    any block that does not block motion, and a block without collision does not, so the bucket would destroy the
    portal block and never ask `canPlaceLiquid`. With the override, the bucket leaves the portal alone: depending
    on the face it aims at, it empties in front of the portal or not at all.

  Every portal keeps its opening.
- **World border:** every arrival is clamped inside the target dimension's border (each dimension has its own in
  26.1), and a portal is never built outside it.
- **Other travel:** a cross-dimension warp by waystone, an instance exit or a command needs no portal and links
  nothing. Entering a realm that way still needs access ([Access and unlocks](#access-and-unlocks)).

## Data: `content/realms/`

```yaml
# content/realms/stone.yaml (sketch; names are examples, the pydantic model is the authority once written)
id: stone
dimension: realmforge:stone          # the realm's dimension (content/worldgen/dimension/stone.json)
name: {en_us: The Stone Realm}
locked_hint: {en_us: "Unlocked by a chapter 1 quest."}
flavour: {en_us: "Cold air rises from below, smelling of dust and old stone."}
# W2b adds the portal's fields:
sigil: realmforge:stone_sigil        # an item from content/items/, stack size 1
colour: "#7A8FB0"
particle: minecraft:ash              # the extra particle next to the tinted dust
sound_pitch: 0.8                     # until A3 brings our own sounds
arrival: {y: [20, 90], floor: realmforge:slate_bricks, pocket_radius: 3}   # where a built portal may stand
```

- **The coordinate scale is not here:** it is the `coordinate_scale` of the realm's dimension type
  (`content/worldgen/dimension_type/stone.json`, `16.0`), next to its `bed_rule` and `respawn_anchor_works`
  attributes, because vanilla reads it from there.
- **`realmforge:realm`, a new datapack registry** owned by `world`
  ([architecture 5.2](../architecture.md#52-our-datapack-registries)), synced to clients, because they need the
  colour and the name. The Overworld is an entry too (`overworld`: no sigil, an arrival floor such as
  `minecraft:stone_bricks` for portals built on the way back), which gives return portals their colour and gives a
  later realm map its root. The architecture lists only what is built: the task that builds the registry adds it
  to [architecture 5.2](../architecture.md#52-our-datapack-registries) and the content type to
  [6.2](../architecture.md#62-what-each-content-type-becomes) in its own pull request (CLAUDE.md rule 11).
- **Package direction** ([architecture 4.4](../architecture.md#44-dependencies-between-features)): the realm entry
  names its sigil by id in vanilla's static item registry, resolved by vanilla's item codec, the way a worldgen file
  names its blocks. `world` uses no code and no registry of the `item` feature for it: a sigil is a plain generated
  item without behaviour of its own, and the selector compares the held stack with the realm's item. So `world`
  keeps its row in the dependency table.
- **`rf gen` writes** the registry entry, the unlock advancement `realmforge:realm/<id>` (criterion
  `minecraft:impossible`), the lang keys (`realm.realmforge.<id>.name`, ...) and GameTest expectations.
- **Validation:**
  - every id the entry names (the dimension, the sigil, the particle, the arrival floor block) exists in the ID
    universe, which holds `minecraft:particle_type` and `minecraft:block` (CLAUDE.md rule 3);
  - the particle is a type without options (a `SimpleParticleType` such as `minecraft:ash`, from a list the
    validator keeps), because types such as `minecraft:dust` or `minecraft:block` need option data the entry does
    not carry;
  - one realm per dimension, and one realm per sigil: two realms on one sigil would leave the selector no single
    destination;
  - every realm but the Overworld names a sigil, since a realm without one has no way onto a selector, and the
    sigil stacks to 1;
  - the realm dimension's `dimension_rules` access requirement includes the unlock advancement;
  - the realm's dimension type sets a `coordinate_scale` above 1 and a `bed_rule` that sets no spawn.
- **The sigil source check comes later.** Every sigil needs a renewable source (a recipe, a repeatable quest, or the
  guaranteed drop of a boss that can be replayed), never only a one-time reward, as the "every item obtainable" rule
  asks ([ADR 0005](../decisions/0005-content-pipeline-yaml-single-source-of-truth.md)). That rule is not in the
  pipeline yet: it needs recipes, loot and quests as data, and quests arrive with Q1. The sigil check joins it then,
  together with a check that every realm has an unlock quest. Until Q2 adds the chapter 1 quests, the Stone Sigil and
  the unlock have no source in play: `/give`, `/advancement grant` and a QA command hand them out for tests and
  playtests, so W2a and W2b do not wait for Q2.
- **Not in the data yet:** a realm map's positions and parents (RE-7) and a level band of its own (the levels come
  from `dimension_rules`, which stays on the server). They join when a client view needs them, synced with the realm
  entry.

## Code shape

For the tasks that build it ([architecture 4](../architecture.md#4-code-layout)), in the `world` feature, which owns
dimension rules and access:

- `world/realm/`:
  - `Realm` (record with `CODEC`) and its registry key;
  - `RealmAccess`, which asks `core`'s requirement API at the selector, at the portal and in its
    `EntityTravelToDimensionEvent` listener, which also stops every entity that is not a player from entering or
    leaving a realm;
  - the respawn listener (`PlayerRespawnPositionEvent`);
  - `PortalFrame`, the frame check as plain Java over a block lookup: both axes, the missing positions;
  - `RealmPortalBlock`, implementing vanilla's `Portal` (`getPortalDestination`, `getPortalTransitionTime`,
    `getLocalTransition`) and `LiquidBlockContainer`, and overriding `canBeReplaced(BlockState, Fluid)` (it refuses
    every fluid, flowing or from a bucket);
  - `RealmSelectorBlock` with its block entity, and its point-of-interest type;
  - `RealmPortalLinker`: the scaled position, the search for a linked portal and the building of a new one, with the
    spot search as plain Java over a column of blocks;
  - the `PlayerInteractEvent.RightClickBlock` listener for the sneak gesture (common code: it runs on both sides);
- `client/`: the portal block's tint source;
- Portal Stone, the selector and the sigils as YAML content: blocks once the block content type exists, sigils as
  items.

## Tests

- **JUnit:**
  - the frame check (both axes; corners present or missing, which both light; a missing side block; a block in the
    opening; the selector off-centre; a frame too big or too small);
  - the scaled position, both ways and between two realms, with the world border clamp;
  - the spot search for a built portal (a ceiling, a void, water, a full column, the `y` range).
- **pytest:** the schema, the references, the generated advancement (criterion `minecraft:impossible`), the access
  requirement check, the dimension type checks (scale, bed rule).
- **GameTests** (W2a's and W2b's "Done when"):
  - access is refused before the unlock advancement and allowed after it: through a teleport into the realm (as a
    waystone makes one), and through the portal, where the player stays put and gets the centre-screen message;
  - a player holding a sigil without its unlock cannot open or switch a portal with it, and can after the unlock;
  - a sigil lights a complete frame, a second sigil switches it, and an incomplete frame stays dark;
  - breaking a frame block closes the portal; closed by the sneak gesture, or broken and rebuilt, the frame relights
    with the sigil it showed before (RLM-133);
  - the sneak gesture with a shield in the off hand closes an Overworld portal, and opens a frame in a realm onto
    return (RLM-133);
  - a trip from the Overworld builds a return portal at the scaled position in the realm; a second trip from a
    portal within 256 blocks of the first arrives at that same realm portal; the way back arrives at the Overworld
    portal near the scaled-back position, or builds one;
  - a built portal never stands outside the world border or outside the realm's `y` range;
  - a water bucket emptied into a lit portal's opening, by a player and by a dispenser, and water flowing into it,
    leave its portal blocks in place;
  - the selector refuses in the instance dimension;
  - an item, a mob and a mounted player do not travel through a portal; a mob moved into or out of a realm with
    `Entity#teleport(TeleportTransition)`, and one moved with `/tp` (`Entity#teleportTo`), stays where it is
    (RLM-133);
  - a death in a realm respawns at the player's Overworld bed, or at the world spawn without one; a bed in a realm
    sets no spawn point and does not explode; a spawn point set in a realm by command still respawns the player in
    the Overworld.

## What M1 takes from this

W2 is split in two (Matěj, RLM-128; the roadmap holds the plan):

- **W2a, the stone realm** (RLM-32): the dimension from the [realm design](realms.md#the-stone-realm), its
  dimension type (the coordinate scale, beds and anchors) and its `dimension_rules` entry (the level rule, the
  access requirement), the `realmforge:requirement_type` registry with its first types, `content/realms/` with the
  realm's name, hint and unlock advancement, the access check on every way in with the non-player rule, and the
  respawn rule.
- **W2b, the realm portal** (RLM-154, after W2a): the frame, the selector, the portal block, the Stone Sigil, the
  portal's fields in `content/realms/` and the synced registry, the linking and building, the selector's gestures,
  the look and the sounds. It uses vanilla particles and sounds; textures are placeholders until the art pipeline
  makes them.
- **Q2** (chapter 1) adds the stone realm's unlock quest, which rewards the unlock and the Stone Sigil, the quests
  that walk through the frame, and the repeatable quest that replaces a lost sigil. Until then `/give`,
  `/advancement grant` and the QA mod stand in.
- **Later:** the "Return to last death" scroll (RLM-155); the desert and tundra sigils with M4's realms; a tinted
  overlay; our own sounds (A3); a realm map; shared quests in a party (RLM-156).

## Decisions

Matěj's answers of 2026-10-13 (RLM-128), which this version records:

1. **The spec is approved** with the changes below.
2. **Sigils:** every sigil of a realm is the same item, and anyone may hold one. What counts is the player's own
   progress: without the realm's unlock quest a player can neither open a portal to that realm with the sigil nor
   pass through an open one ("You are not eligible to pass through the portal."). One sigil opens any number of
   portals.
3. **First portal:** answered by the Nether-like travel. Any number of portals can lead to one realm, anywhere in the
   Overworld, so no generated shrine is needed.
4. **Who may switch a portal:** anyone holding a sigil they are eligible to use.
5. **Recall:** the Waystones mod's Warp Scroll and Return Scroll, plus our "Return to last death" scroll.
6. **W2's size:** split into W2a (the stone realm's dimension, its rules and the requirement types) and W2b (the
   realm portal), each M.
7. **Travel works like the Nether, in both directions,** with scaled coordinates so arrivals stay near a realm's
   origin. The spec picks the ratio from the level bands (1:16 for the stone realm). There is no landing: the
   landing and the home point fit instanced dungeons, not realms.
8. **Respawn:** always at the player's bed in the Overworld, or at the world spawn; never in a realm.
9. **Only players travel,** and unlocks are per player and permanent, for now; shared party quests come later with
   low priority.
10. **Waystones:** crafted and placed by players, global or private by each player's choice; no waystone is placed
    at an arrival point, and a warp into a realm the player has not unlocked never works.

## Open questions

None: Matěj answered every question of the first version (RLM-128). The numbers marked as proposals (the
materials, the stone realm's level rule and its 1:16 scale) are checked in his playtests of W2a and W2b.
