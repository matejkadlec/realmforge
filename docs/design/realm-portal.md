# Realm portal

> The realm portal spec (RLM-41): the one portal that leads to every realm, how a player opens it, unlocks a realm,
> travels there and back, and what happens at the edges. Written on 2026-10-12 from Matěj's requirements in RLM-41
> and the [realm design](realms.md) (RLM-40), with the reference-game research
> ([reference-games.md](../research/reference-games.md), cited by item ID such as RE-1; AoA's portal in detail:
> [aoa-systems-progression.md](../research/notes/aoa-systems-progression.md#1-portals-realmstones-and-the-unlock-graph-hub-returning)).
> It defines how roadmap task W2 builds "access by portal or item, locked by a chapter 1 step". Approval and the W2
> wording: Matěj's decision (RLM-128). Block, item and realm names are working names. APIs named here were checked
> against Minecraft 1.21.1 and NeoForge 21.1.259 on 2026-10-12; the 26.1.2 port (RLM-123) may rename some, so W2
> checks each one in the sources it builds on.

## At a glance

- **One frame for every realm** (RE-1): 5 blocks wide and 6 tall around a 3×4 opening, built by the player from cheap
  Overworld materials. The bottom middle block is the **realm selector**.
- **One sigil per realm**, never consumed and stacking to 1. Right-clicking the selector with a sigil opens the
  portal onto that realm, or switches an open portal straight to it, with no clearing step (RE-1).
- **Holding a sigil unlocks its realm for that player, for good.** The unlock is a per-player advancement, and it is
  the realm's access requirement in `realmforge:dimension_rules`. Losing the sigil later locks nothing.
- **Arrival is always at the realm's landing**, one shared portal near the realm's origin, where its lowest-level
  area starts. **The way back always leads home**: to the Overworld portal the player last left from (RE-2).
- **Each realm has its own colour**, particles and sound, and a flavour line (RE-1).
- **Only players travel.** Items, mobs and vehicles never pass, so there is no chunk-loading trick and no realm mob
  in the Overworld.

| Realm | Sigil (working name) | How a player gets it | Portal colour |
| --- | --- | --- | --- |
| The Overworld | none | the way home from any realm ([Return travel](#return-travel)) | green `#5DBB63` |
| Stone realm | Stone Sigil | reward of an early chapter 1 quest that a level-1 player can finish; a repeatable quest replaces a lost one | slate blue `#7A8FB0` |
| Desert realm | Desert Sigil | crafted from a stone realm ore plus the stone realm boss's guaranteed drop (RE-9) | gold `#E8B84A` |
| Tundra realm | Tundra Sigil | the same, with a different stone realm ore | ice white `#BFF4FF` |
| Fire realm | Fire Sigil | from the realm before it, by the same rule | red `#E0502C` |
| Void realm | Void Sigil | from the realm before it, by the same rule (RLM-95) | deep purple `#4B1F6F` |

Colours are proposals and live in data ([Data](#data-contentrealms)). Purple is kept for the Void realm, which comes
after M4 removes the nether portals ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)), so no realm portal
looks like a nether portal.

## The frame

- **Shape:** a vertical rectangle, 5 wide and 6 tall, around a 3×4 opening, along either horizontal axis (like a
  nether portal). The four corners are optional decoration, as on a nether portal: the frame is the 14 blocks that
  touch the opening, 13 **Portal Stone** and one **realm selector** in the middle of the bottom row. So every
  required block touches a portal block, and breaking any of them reaches the portal through a neighbour update
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
- **Lighting:** right-click the selector with a sigil. The selector checks the frame:
  - **complete,** with the opening empty (air, or blocks such as grass that are replaced when something is placed
    there): the opening fills with portal blocks of that
    realm, and the action bar says "The portal opens onto the Stone Realm";
  - **incomplete:** nothing lights, the action bar says "The frame is incomplete", and particles mark every missing
    or wrong block for a few seconds, so the player sees what is left (the visible-progress rule of the
    [realm design](realms.md#puzzles-and-traps)).
- **No other igniter.** Fire and flint and steel do nothing; a nether portal never lights inside the frame (it needs
  obsidian).
- **Frame blocks cannot be pushed** by pistons (`PushReaction.BLOCK`), so no contraption moves a lit frame apart.

## The realm selector

The bottom middle block of the frame (Matěj's requirement). It holds one setting in a block entity: a realm, or
**return** ([Return travel](#return-travel)). It stores no item, so hoppers have nothing to take.

| Action on the selector | Result |
| --- | --- |
| right-click with a sigil | opens the portal onto the sigil's realm, or switches it there at once; the sigil stays in the hand |
| right-click with the sigil of the realm it already shows | nothing changes; "The portal already opens onto the Stone Realm" |
| right-click with the sigil of the realm the frame stands in | refused: "You are already in the Stone Realm" |
| right-click with an empty hand | shows where the portal opens onto, on the action bar |
| sneak and right-click with an empty hand | in the Overworld, closes the portal; inside a realm, opens it onto return (or sets it back to return) |
| break the selector | the portal closes, the selector drops as an item and forgets its setting |

Anyone holding a sigil may switch any selector. Unlocks are per player ([Multiplayer](#multiplayer)), so switching
changes only where the portal leads, never who may enter ([open question 3](#open-questions)). The one exception is
a realm's landing ([below](#into-a-realm-the-landing)): its selector always stays on return.

## Realm sigils

- **One per realm,** stacking to 1, never consumed (RE-1), fire resistant (`Item.Properties#fireResistant`), so lava
  does not take it. Its tooltip names the realm and says "Never used up". It shows no levels: they live in the
  server-only `realmforge:dimension_rules`, and the arrival title, which the server sends, shows them.
- **Where sigils come from** (RE-9, LE-1):
  - **the stone realm's sigil** is the reward of an early chapter 1 quest, for example "build the realm portal".
    That quest step is W2's "locked by a chapter 1 step", and a level-1 player can finish it (the stone realm is
    open from level 1 and recommended from 5, [realm design](realms.md#chapters-and-levels));
  - **a lost Stone Sigil** comes back through a repeatable quest that opens once that first quest is complete and
    gives a new sigil for a few copper ingots (FTB Quests' repeatable quests; Q1 checks the field). The quest gate
    holds, and a player who lost the sigil before or after opening a portal is never stuck;
  - **every later main-path sigil is crafted** from an ore or gem of the realm the player is leaving plus the
    guaranteed drop of that realm's main-path boss. The recipe is shown in the quest log, and crafting the sigil is
    a step of the chapter's main chain. Beating that boss is what "completing" a realm means; the realm itself has
    no completion flag ([realm design](realms.md#opening-the-next-realm));
  - **when a chapter opens two realms** (chapter 2: the desert and the tundra), the boss drops one material per
    realm it opens, two for chapter 2, every kill;
  - **an optional side realm's sigil** comes from wherever its design puts it: a dungeon chest at 100% or a trade,
    never a random drop (PR-15).
- **Nothing depends on luck, and every sigil can be replaced:** each source is a quest reward, a guaranteed drop or
  a recipe. The Stone Sigil has its repeatable quest; every later sigil is crafted again from a boss the player can
  always replay, since an NPC sells the keys of dungeons already done (LE-2). A lost sigil never locks the realm
  again, because the unlock belongs to the player; the player needs a sigil only to open or switch a portal.

## Access and unlocks

- **The unlock is an advancement per realm,** `realmforge:realm/<realm>`, generated from the realm's data: its only
  criterion is vanilla's `minecraft:inventory_changed` for the realm's sigil. Whatever puts the sigil into a
  player's inventory unlocks the realm for that player: the quest reward, crafting, picking it up, a trade. The
  advancement is hidden until earned and shows a toast, "Realm unlocked: the Stone Realm"; it adds no chat line
  (`hidden`, `show_toast`, `announce_to_chat` in vanilla's advancement display).
- **The access requirement:** the realm dimension's entry in `realmforge:dimension_rules` holds it
  ([architecture 15.1](../architecture.md#151-dimensions)). For a realm it is
  `{type: realmforge:has_advancement, advancement: realmforge:realm/<realm>}`, from the first set of
  `realmforge:requirement_type` ([architecture 5.3](../architecture.md#53-type-registries)). A realm may add more
  requirements, such as a level; validation fails a realm whose rules leave out its unlock advancement.
- **The server checks access when a player steps into a portal block,** so the portal never starts its countdown for
  a player without access. That player stays where they are; the action bar says "The Stone Realm is sealed to you"
  plus the realm's hint ("Its sigil is a chapter 1 quest reward"), at most once every 3 seconds, with a short
  refusal sound.
- **Every other way in is checked too,** so the advancement seals the realm whatever the path: NeoForge's
  cancellable `EntityTravelToDimensionEvent` is cancelled for a player whose target is a realm they have not
  unlocked, with the same message. `ServerPlayer#changeDimension` posts that event, and `ServerPlayer#teleportTo`
  into another level goes through it, so it covers waystones (a global waystone at a landing included), other
  mods' teleports and commands. A respawn does not pass through it (`PlayerList#respawn` creates the player in the
  target level directly), and a player respawns in a realm only at its landing or at their own bed there, both
  places they first reached with access.
- **What a locked realm shows elsewhere:** a portal someone else opened onto it looks the same as for anyone (its
  colour, particles and sound), so a player sees where a friend went. The sigil's tooltip and the realm's hint say
  how to get it. A realm map with locked realms and their hints (RE-7) is a later view, in the guide (RLM-102,
  approval in RLM-116) or a screen of its own; M1 needs none.
- **No operator bypass:** a creative player needs the unlock like anyone else, so tests and playtests see the real
  rule. `/advancement grant @s only realmforge:realm/stone`, or a QA command, unlocks a realm for testing.

## Travel

### Into a realm: the landing

- **Every trip into a realm arrives at its landing**, near the origin of the realm's dimension. Mob levels rise with
  the distance from the origin ([architecture 14.1](../architecture.md#141-mob-levels)), and the stone realm's
  sub-regions rise from its arrival point outwards ([realm design](realms.md#the-stone-realm)). With AoA's 1:1
  coordinates, a player whose Overworld portal stands at x 5,000 would arrive in the level-10 area of the stone realm.
  One landing per realm also means one place to find, mark on the map (TR-11) and grow into a camp with NPCs later
  (WF-8).
- **The first arrival builds the landing:** a floor of the realm's own stone (named in its data), 7 wide and 5 deep
  under and in front of a frame, with the frame's selector set to return, air 4 blocks high above the floor, light,
  and a safe pocket around it (RE-6: radius 3 by default, larger for a realm that needs it, such as an underwater
  one). The spot is the nearest one to the realm's arrival point (its `x`, `z` in data) where the landing fits
  between the floor and the ceiling, searched within the realm's `y` range; when nothing fits, the landing is carved
  out at the range's middle. That covers the cases AoA kept fixing (RE-2): a ceiling (the stone realm has a bedrock
  one), a void below, water.
- **The landing's position is saved** for the realm (a `SavedData` in the realm's level), so later arrivals look at
  one known place instead of searching a radius. Vanilla's nether portal search reads the point-of-interest data of
  every chunk within 128 blocks (`PortalForcer#findClosestPortalPosition` with `PoiManager#ensureLoadedAndValid`);
  the landing needs none of that. A saved landing that the world border has left outside (an operator moved or shrank
  it) counts as gone: the next arrival builds a new landing at the spot nearest the arrival point inside the border
  and saves that one, so the player never arrives away from the portal.
- **The landing cannot be broken, built over, flooded or switched.** Its protected zone is the landing's box (the
  floor and the air above it) widened by 4 blocks on each side. In the zone, players outside creative:
  - cannot break or place blocks (`BlockEvent.BreakEvent` and `BlockEvent.EntityPlaceEvent` are cancelled for
    them), except that they may place the blocks in the tag `#realmforge:landing_placeable`, which holds the
    Waystones mod's waystone (*verify* its id), for the waystone fallback below;
  - cannot use buckets. `BucketItem#use` places and picks up fluids with `Level#setBlock` and posts no block event
    ([architecture 15.5](../architecture.md#155-instances)), so, as in instances,
    `PlayerInteractEvent.RightClickItem` is cancelled for a `BucketItem` when the player stands in the zone or aims
    at a block in it (`Entity#pick`).

  Its frame and selector resist explosions like bedrock, and the selector refuses sigils ("The landing always leads
  home"). Fluids that flow in from outside the zone cannot remove the portal ([Edge cases](#edge-cases)), and the
  arrival check below handles a flooded spot. A player in a realm can then always get home. If the frame is gone
  anyway (an operator, another mod), the next arrival rebuilds it in place.
- **Arrival puts the player on the floor in front of the frame, facing away from it,** not inside the portal, so
  nobody bounces straight back. Every arrival checks that spot first (solid floor and 2 blocks of air); when
  something still blocks it (an operator, a fluid, another mod), the player lands on the nearest safe spot next to
  the landing instead, with the same search as for the home point. A title shows the realm's name and levels for 2
  seconds on every arrival; the first arrival also shows its flavour line.
- **A waystone at the landing** makes it the node between realms (TR-7), if the Waystones mod's block can be placed
  from code and then works like a placed one (*verify* its API); otherwise a landing quest step asks the player to
  place one on the landing's floor, which the zone allows for that block.

### Return travel

- **A return portal leads home:** to the Overworld portal the player last left from (RE-2; Hytale's hub also "will
  send the player back to the same portal they came from"). Every player has one **home point** (the dimension, the
  position in front of that portal and the facing), written each time they leave the Overworld through a realm
  portal. It lives in a player attachment `realmforge:realm_travel` (owned by `world`, copied on death, not synced:
  only the server reads it).
- **Return always goes to the Overworld,** even after a trip from one realm to another
  ([Edge cases](#edge-cases)). AoA kept one return point per dimension, and chained trips could then send a player
  back and forth between two realms (the "portal loops" its changelogs fixed). One home point in the Overworld
  cannot loop, and it matches the realm design: the Overworld is the hub, and every realm's way back leads there.
- **If home has moved:** the portal at the home point may be broken or built over. The player then still arrives
  there, at the nearest spot with solid floor and 2 blocks of air within 8 blocks, or on top of the column if none.
  No frame is built in the Overworld, where it could land inside someone's base. Without any home point (a player
  who came by waystone or command), the return leads to the player's respawn point when it is in the Overworld, else
  the world spawn.
- **No recall item of our own in M1.** The landing portal is always there and cannot be broken, and the pack's
  Waystones mod has its own warp items (*verify* which ones the pack enables) for an emergency return (TR-2). A
  Hearthstone (the vision's "hearthstone?") is [open question 4](#open-questions).

### Respawn in a realm

The realm design's [open question 1](realms.md#open-questions) proposes respawning at the portal the player arrived
through, so a death costs no long way back. With the landing, that becomes:

- a player who dies in a realm respawns at its landing, unless their spawn point (a bed) is in that realm;
- beds work in realms (`bed_works: true` in the realm's dimension type, so they set a spawn point and do not
  explode); respawn anchors do not (`respawn_anchor_works: false`);
- implemented with NeoForge's `PlayerRespawnPositionEvent`, which may change where a player respawns, and with
  `keepInventory` and `doImmediateRespawn` on ([architecture 19.1](../architecture.md#191-world-defaults)) a death
  is cheap.

If Matěj decides otherwise in the realm design, this section follows his answer.

## Look and sound

- **One portal block** for every realm, tinted with the colour of the realm its selector shows (return portals use
  the Overworld's green). Its texture is one greyscale swirl, tinted on the client by a block colour handler
  (`RegisterColorHandlersEvent.Block`) that reads the frame's selector; the selector's block entity sends its
  setting to clients, and the colours come from the synced realm registry ([Data](#data-contentrealms)). Block
  colours are baked into the chunk mesh, so when the setting arrives on the client, the selector marks the opening
  for re-rendering (`Level#sendBlockUpdated` on the client level, which calls `LevelRenderer#blockChanged`);
  otherwise a switched portal would keep the old realm's colour.
- **Particles:** a swirl of vanilla dust in the realm's colour (`DustParticleOptions`), plus one vanilla particle
  that suits the realm: ash for the stone realm (`minecraft:ash`), pale ash like blown sand for the desert
  (`minecraft:white_ash`), snowflakes for the tundra (`minecraft:snowflake`), small flames for the fire realm
  (`minecraft:small_flame`). The Void realm's green malachite sparks (RLM-95) may need a particle of our own.
- **Sound:** an ambient hum and a travel sound per realm. Until the sound task (A3, RLM-49) brings our own, the
  vanilla portal sounds play at a different pitch per realm.
- **No vanilla portal overlay:** vanilla's `Portal.Transition.CONFUSION` draws the nether portal's purple texture
  over the screen (`Gui#renderPortalOverlay`), so the realm portal uses `Portal.Transition.NONE`. A tinted overlay of
  our own in `client/` is polish for later.
- **Travel takes 2 seconds** standing in the portal (the nether portal takes 4), and one tick in creative
  (`Portal#getPortalTransitionTime`).

## Multiplayer

- **Unlocks are per player, not per world.** Each player earns the advancement, so a server where one player is in
  chapter 2 does not open chapter 2's realms to a newcomer. Team-wide unlocks could come later with our own party
  system (RLM-68).
- **Sigils can be traded.** Whoever holds one unlocks its realm, so a friend who is ahead can craft one for a
  newcomer ([open question 1](#open-questions)).
- **A player without access** in a portal someone else opened is refused as in
  [Access and unlocks](#access-and-unlocks), while their friends pass through.
- **Everyone arrives at the same landing** and each player returns to their own home point.
- **Only players travel.** Vanilla already keeps a passenger out of portals (`Entity#canUsePortal` is false for a
  passenger), and the realm portal refuses every entity that is not a player, so a player on a horse must dismount.
  WoW-style summoned mounts (M6) are dismissed and summoned again on the other side, and pets (later) follow their
  owner through their own system.

## Edge cases

- **A frame inside a realm:** allowed. It opens onto another realm, which the player enters at that realm's landing,
  or stays on return. It cannot open onto the realm it stands in. The home point does not change, so the way back
  still leads to the Overworld.
- **Instance dimensions and other dimensions:** a frame lights only in dimensions that have a realm entry: the
  Overworld and the realms. In `realmforge:instances`, in the Nether and End until M4 removes them, and
  in dimensions of other mods the selector refuses: "The portal cannot open here". Inside instances players are in
  Adventure mode anyway and cannot build one ([architecture 15.5](../architecture.md#155-instances)). Dungeon
  entrances are a different thing: a temporary portal opened with a dungeon key that is used up (D8, W4); leaving an
  instance returns the player to its entrance and never touches the home point.
- **Broken frames:** breaking any frame block or the selector closes the portal at once (the portal blocks check
  their frame on neighbour updates, as nether portal blocks do; every required frame block touches the opening, and
  the corners are optional, so every break reaches a portal block). A player standing in it stays where they are. The
  frame can be rebuilt and opened again with the sigil. The landing is the exception: it cannot be broken.
- **Chunk loading:** a trip loads only the chunks around the arrival point, for a short time, through vanilla's
  portal ticket (`DimensionTransition.PLACE_PORTAL_TICKET`, as for nether portals). Portals load nothing
  permanently, and since items and mobs never travel, no portal works as a chunk loader. The first arrival in a realm
  generates the landing's chunks on the main thread once, as vanilla does when it builds a nether portal; W2
  measures that hitch.
- **A missing destination:** a selector set to a realm that no longer exists (content removed, data pack off) shows
  "unknown realm" and refuses; a home point in a missing dimension falls back to the respawn point, then the world
  spawn. A player who logs in inside a dimension that no longer exists is moved by vanilla.
- **Fluids:** the portal block refuses fluids: it is a `LiquidBlockContainer` whose `canPlaceLiquid` answers false.
  Vanilla's `FlowingFluid#canHoldFluid` spares only its own portal blocks by name (`Blocks.NETHER_PORTAL`,
  `END_PORTAL`, `END_GATEWAY`), so without that, flowing water or lava would wash away a realm portal's blocks,
  which have no collision, and a bucket could empty into them. A portal anywhere, the landing included, keeps its
  opening.
- **World border:** every arrival point is clamped inside the border, and a landing the border has left outside is
  rebuilt inside it ([the landing](#into-a-realm-the-landing)).
- **Other travel:** a cross-dimension warp by waystone, an instance exit or a command leaves the home point alone,
  so the return portal still leads to the last Overworld portal the player used. Entering a realm that way still
  needs access ([Access and unlocks](#access-and-unlocks)).

## Data: `content/realms/`

```yaml
# content/realms/stone.yaml (sketch; names are examples, the pydantic model is the authority once written)
id: stone
dimension: realmforge:stone          # the realm's dimension (content/worldgen/dimension/stone.json)
name: {en_us: The Stone Realm}
sigil: realmforge:stone_sigil        # an item from content/items/, stack size 1
colour: "#7A8FB0"
particle: minecraft:ash              # the extra particle next to the tinted dust
sound_pitch: 0.8                     # until A3 brings our own sounds
flavour: {en_us: "Cold air rises from below, smelling of dust and old stone."}
locked_hint: {en_us: "Its sigil is a chapter 1 quest reward."}
arrival: {x: 0, z: 0, y: [20, 90], pocket_radius: 3, floor: realmforge:slate_bricks}
```

- **`realmforge:realm`, a new datapack registry** owned by `world`
  ([architecture 5.2](../architecture.md#52-our-datapack-registries)), synced to clients, because they need the
  colour and the name. The Overworld is an entry too (`overworld`, no
  sigil), which gives return portals their colour and gives a later realm map its root.
- **Package direction** ([architecture 4.4](../architecture.md#44-dependencies-between-features)): the realm entry
  names its sigil by id in vanilla's static item registry, resolved by vanilla's item codec, the way a worldgen file
  names its blocks. `world` uses no code and no registry of the `item` feature for it: a sigil is a plain generated
  item without behaviour of its own, and the selector compares the held stack with the realm's item. So `world`
  keeps its row in the dependency table.
- **`rf gen` writes** the registry entry, the unlock advancement `realmforge:realm/<id>`, the lang keys
  (`realm.realmforge.<id>.name`, ...) and GameTest expectations.
- **Validation:** the dimension and the sigil exist; one realm per dimension; the sigil stacks to 1; the realm
  dimension's `dimension_rules` access requirement includes the unlock advancement; every sigil has a renewable
  source (a recipe, a repeatable quest, or the guaranteed drop of a boss that can be replayed), never only a one-time
  reward, as the "every item obtainable" rule asks
  ([ADR 0005](../decisions/0005-content-pipeline-yaml-single-source-of-truth.md)).
- **Not in the data yet:** a realm map's positions and parents (RE-7) and a level band of its own (the levels come
  from `dimension_rules`, which stays on the server). They join when a client view needs them, synced with the realm
  entry.

## Code shape

For the task that builds it ([architecture 4](../architecture.md#4-code-layout)), in the `world` feature, which owns
dimension rules and access:

- `world/realm/`:
  - `Realm` (record with `CODEC`) and its registry key;
  - `PortalFrame`, the frame check as plain Java over a block lookup: both axes, the missing positions;
  - `RealmPortalBlock`, implementing vanilla's `Portal` (`getPortalDestination`, `getPortalTransitionTime`,
    `getLocalTransition`) and `LiquidBlockContainer` (it refuses every fluid);
  - `RealmSelectorBlock` with its block entity;
  - `RealmTravel`: the landing, the home point and the fallbacks, with the safe-spot search as plain Java over a
    column of blocks;
  - `RealmLandings`, the saved landing per realm;
  - `RealmAccess`, which asks `core`'s requirement API, at the portal and in its `EntityTravelToDimensionEvent`
    listener;
  - the `realmforge:realm_travel` attachment and the respawn listener;
- `client/`: the portal block's colour handler;
- Portal Stone, the selector and the sigils as YAML content: blocks once the block content type exists, sigils as
  items.

Tests:

- **JUnit:** the frame check (both axes; corners present or missing, which both light; a missing side block; a block
  in the opening; the selector off-centre; a frame too big or too small); the home point and its fallbacks; the
  safe-spot search (a ceiling, a void, water, a full column).
- **pytest:** the schema, the references, the generated advancement, the access requirement check.
- **GameTests** (W2's "Done when"):
  - access is refused before the unlock advancement and allowed after it, through the portal and through a teleport
    into the realm (as a waystone makes one);
  - a sigil lights a complete frame and a second sigil switches it; the landing's selector refuses a sigil;
  - an incomplete frame stays dark;
  - breaking a frame block closes the portal;
  - a trip there builds the landing, and the way back arrives at the home point;
  - a removed landing frame is rebuilt, and so is a landing outside a shrunken world border;
  - a block placed into the landing's arrival space is refused, and a blocked arrival spot moves the player to a safe
    one;
  - a bucket used in the landing's zone is refused, and water flowing into a portal's opening leaves its portal
    blocks in place;
  - the selector refuses in the instance dimension;
  - an item, a mob and a mounted player do not travel;
  - a death in the realm respawns at the landing.

## What M1 takes from this

A proposal for the M1 tasks, not a plan change:

- **W2** builds the whole portal for one realm: the frame, the selector, the portal block, the Stone Sigil, the
  `realmforge:realm` entries for the Overworld and the stone realm, the unlock advancement and the access check,
  the landing, the return, and the respawn rule. It uses vanilla particles and sounds; textures are placeholders
  until the art pipeline makes them.
- **Q2** (chapter 1) adds the quests that walk through the frame and reward the Stone Sigil, and the repeatable
  quest that replaces a lost one.
- **Later:** the desert and tundra sigils with M4's realms; a tinted overlay; our own sounds (A3); a realm map;
  team unlocks with parties; a portal shrine near spawn ([open question 2](#open-questions)).
- **Size:** W2 is M in the roadmap. Worldgen for the stone realm, its dimension rules, the requirement types and
  this portal together are closer to L ([open question 5](#open-questions)).

## Open questions

For Matěj; each has a proposal, so W2 can go on.

1. **Tradeable sigils.** Whoever holds a sigil unlocks its realm, so a friend can craft one for you. Or should a
   sigil unlock only the player who crafted or earned it (bound items)? Proposal: tradeable.
2. **Where the first portal stands.** The player builds it anywhere in the Overworld (M1), or a portal shrine with a
   ready frame and a waystone generates near the world spawn and grows with each chapter (WF-8)? Proposal: built by
   the player in M1, a shrine near spawn later, when the hub gains NPCs.
3. **Who may switch a portal.** Anyone with a sigil (proposal: unlocks are per player, so switching only changes the
   destination), or only the player who built the frame?
4. **A recall item.** A Hearthstone of our own (bound to a waystone, with a cooldown), or the Waystones mod's warp
   items? Proposal: Waystones' items for now, a Hearthstone if they feel wrong in play.
5. **W2's size.** Split W2 into W2a (the stone realm's dimension, its rules and the requirement types) and W2b (the
   realm portal), each M? Proposal: split; it is a plan change, so it waits for your answer.
6. **Respawn in a realm** stays the realm design's open question 1; this spec assumes its proposal (the landing).
