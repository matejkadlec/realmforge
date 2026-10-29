# Realm portal

> The realm portal spec (RLM-41): the one portal that leads to every realm, how a player opens it, unlocks a realm,
> travels there and back, and what happens at the edges. Written on 2026-10-12 from Matěj's requirements in RLM-41
> and the [realm design](realms.md) (RLM-40), with the reference-game research
> ([reference-games.md](../research/reference-games.md), cited by item ID such as RE-1; AoA's portal in detail:
> [aoa-systems-progression.md](../research/notes/aoa-systems-progression.md#1-portals-realmstones-and-the-unlock-graph-hub-returning)).
> Approved by Matěj on 2026-10-13 with changes (RLM-128, [Decisions](#decisions)):
>
> - portals link like the Nether's, with scaled coordinates, so there is no landing and no home point;
> - a realm's unlock comes from its quest, not from holding its signet;
> - a player always respawns in Overworld.
>
> RLM-133 closed three gaps that the review of the first version found, and RLM-157 the ones the review of the
> second found: the realm-side build radius allows for rounding, frames a trip builds drop nothing, Overworld
> has an arrival height, and shoulder parrots stay out of realms. Roadmap tasks W2a (the realm, RLM-32) and
> W2b (the portal, RLM-154) build it ([What M1 takes from this](#what-m1-takes-from-this)). Matěj's playtest of
> W2b (2026-10-20) named the frame's blocks Realmforge Bricks and the Realmforge Sealstone and the realms' keys
> signets (the first version's Portal Stone, realm selector and sigils), changed their recipes, let every entity
> travel and set trip-built frames into the floor, with Realmforge Bricks in their corners ([Decisions](#decisions),
> 12 to 14). RLM-395 (2026-10-23 and 2026-10-24) made every signet a recipe around a shard that its realm's final
> boss drops, and let a quest shop sell a lost signet ([Decisions](#decisions), 15 to 17). RLM-335 and RLM-406
> (2026-10-27) let players travel at once and made the sealstone glow near a signet ([Decisions](#decisions), 18 and
> 19), and RLM-341 let Overworld frames stand on ground at y 63. RLM-458 (2026-10-28) let a realm's own signet open a
> frame inside the realm back onto Overworld, with the glow, and set the stone realm's scale to the Nether's 1:8
> ([Decisions](#decisions), 22 to 24). Other block and item names
> are working names; realm names are the approved ones of the [lore outline](../lore/outline.md#the-realms)
> (RLM-139, RLM-188), without an article. The v1 realm layout (RLM-188, 2026-10-16) moved the stone realm to
> chapter 2 (levels 6–18) and its unlock to the end of chapter 1's main questline. APIs named here were checked
> against Minecraft 26.1.2 and NeoForge 26.1.2.116 on 2026-10-13 and 2026-10-14, except where marked *verify*.

## At a glance

- **One frame for every realm** (RE-1): 5 blocks wide and 6 tall around a 3×4 opening, built by the player from cheap
  Overworld materials. The bottom middle block is the **sealstone**.
- **One signet per realm**, never consumed and stacking to 1; one signet opens any number of portals. Right-clicking
  the sealstone with a signet opens the portal onto that realm, or switches an open portal straight to it, with no
  clearing step (RE-1).
- **The unlock is the player's own:** a realm's unlock quest grants it, for that player, for good. Every copy of a
  signet is the same item and anyone may hold one, but without the unlock a player can neither open a portal to that
  realm with it nor pass through an open one.
- **Portals link like the Nether's, in both directions** (Matěj, RLM-128). An Overworld portal leads to the realm
  portal near its scaled position in the realm, or builds one there, and the way back works the same in reverse.
  Any number of portals can stand anywhere in Overworld.
- **Scaled coordinates keep arrivals near a realm's origin:** the stone realm links at 1:8, as the Nether does (Matěj,
  2026-10-28), so every Overworld portal within 10,000 blocks of the origin arrives within 1,250 blocks of the realm's
  origin ([The coordinate scale](#the-coordinate-scale)).
- **A frame inside a realm opens back onto Overworld** with the realm's own signet or the sneak gesture, so a player
  whose portal broke is never stuck there (Matěj, 2026-10-28).
- **A player always respawns in Overworld,** at their bed or at the world spawn, never in a realm. A
  "Return to last death" scroll (RLM-155) takes them back to where they died.
- **Each realm has its own colour**, particles and sound, and a flavour line (RE-1).
- **Every entity travels through a portal** (Matěj, 2026-10-20, replacing the first version's "only players
  travel"): items, mobs and vehicles pass as through a nether portal, while a player still needs the realm's unlock.
  Every other way into or out of a realm (`/tp`, a warp) still carries only players ([Multiplayer](#multiplayer)).

| Realm | Signet (working name) | How a player gets the signet | What unlocks the realm | Portal colour |
| --- | --- | --- | --- | --- |
| Overworld | none | - | always open | green `#5DBB63` |
| Stone realm (Undervault) | Signet of the Undervault | in M1 the reward of chapter 1's last main quest, the realm's unlock quest; from M2 crafted from the Illusioner's Stone Signet Shard, 4 diamonds and 4 stone (RLM-395); the quest shop sells a lost one, up to 3 times | that quest, at about level 6 | slate blue `#7A8FB0` |
| Desert realm (Sunwastes) | Signet of the Sunwastes | crafted around the Sun Signet Shard, which the stone realm's boss drops at 100%, with 2 Rubies, 2 Netherite Scraps, 2 Sand and 2 Red Sand (RE-9, RLM-395; the twins' recipe, Matěj, 2026-10-26, RLM-432) | its main-chain quest after the stone realm boss | gold `#E8B84A` |
| Tundra realm (Everwinter) | Signet of the Everwinter | the same, around the Ice Signet Shard, with a different stone realm gem: 2 Sapphires, 2 Netherite Scraps, 2 Snow Blocks and 2 Ice | its own quest after the same boss | ice white `#BFF4FF` |
| Fire realm (Ashfire) | Fire Signet | from the realm before it, by the same rule | the quest after that realm's boss | red `#E0502C` |
| Void realm (Unmade Isles) | Void Signet | from the realm before it, by the same rule (RLM-95) | the quest after that realm's boss | deep purple `#4B1F6F` |

Skyhearth, Deepholm and Quagmire ([realm design](realms.md#at-a-glance)) get their rows when they are designed.
Skyhearth opens with the Skyhearth Medallion, crafted from the Stone Heart of Undervault's final boss (RLM-245), and
gets its own portal of Holy Stone bricks (RLM-302). Colours are proposals and live in data
([Data](#data-contentrealms)). Purple is kept for the Void realm, which comes after M4 removes the nether portals
([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)), so no realm portal looks like a nether portal.

## The frame

- **Two block types** (Matěj, RLM-128, as in Advent of Ascension; named by him on 2026-10-20): **Realmforge Bricks**
  for the frame and the **Realmforge Sealstone** (the sealstone) for its bottom middle. Both are crafted from
  Overworld materials.
- **Shape:** a vertical rectangle, 5 wide and 6 tall, around a 3×4 opening, along either horizontal axis (like a
  nether portal). The four corners are optional decoration, as on a nether portal: the frame is the 14 blocks that
  touch the opening, 13 Realmforge Bricks and one sealstone in the middle of the bottom row. So every required block
  touches a portal block, and breaking any of them reaches the portal through a neighbour update
  ([Edge cases](#edge-cases)). One fixed size keeps the check simple and every portal recognisable; AoA used the
  same outline (RE-1).
- **Materials from Overworld,** because the first frame is built before any realm is open (Matěj, 2026-10-20):
  - **Realmforge Stone:** the bricks of eight Overworld stones (stone, deepslate, quartz, mud, tuff, diorite, granite
    and andesite bricks) around an ender pearl make 8. Vanilla has no diorite, granite or andesite bricks, so they
    are blocks of ours: 4 of the polished stone make 4, as vanilla makes tuff bricks;
  - **Realmforge Bricks:** 4 Realmforge Stone make 4, as stone makes stone bricks;
  - **Realmforge Sealstone:** 4 Realmforge Bricks, 4 gold ingots and a compass;
  - a whole frame takes 17 Realmforge Bricks (13 in the frame, 4 in the sealstone): 5 crafts of bricks from 20
    Realmforge Stone, so 3 crafts of the stone (3 ender pearls and 3 of each of the eight bricks), plus 4 gold
    ingots and a compass. The ender pearl makes the first frame a goal of Overworld's early levels; chapter 1's
    quests walk through it. The numbers are proposals for Matěj's playtest.
- **Lighting:** right-click the sealstone with a signet ([The sealstone](#the-sealstone)). The sealstone
  checks the player's unlock first, then the frame:
  - **complete,** with the opening empty (air, or blocks such as grass that are replaced when something is placed
    there) or already filled with this frame's own portal blocks (a switch): the opening fills with portal blocks of
    that realm, and the action bar says "The portal opens onto Undervault";
  - **incomplete:** nothing lights, the action bar says "The frame is incomplete", and particles mark every missing
    or wrong block for a few seconds, so the player sees what is left (the visible-progress rule of the
    [realm design](realms.md#puzzles-and-traps)).
- **No other igniter.** Fire and flint and steel do nothing; a nether portal never lights inside the frame (it needs
  obsidian).
- **Frame blocks cannot be pushed** by pistons (`BlockBehaviour.Properties#pushReaction(PushReaction.BLOCK)`), so no
  contraption moves a lit frame apart.

## The sealstone

The bottom middle block of the frame (Matěj's requirement). Its block entity holds one setting, which always matches
the opening:

- **none** while the opening is dark;
- **a realm** while the portal is open onto it;
- **return** while it is open onto Overworld, the way back from a realm ([Travel](#travel)).

The setting becomes none whenever the opening goes dark: the sneak gesture closes it, or a broken frame block takes
the portal blocks with it (they check their frame on neighbour updates) and resets a sealstone that still stands. So a
closed or rebuilt frame lights again with the signet it showed before (RLM-133). The sealstone stores no item, so
hoppers have nothing to take.

| Action on the sealstone | Result |
| --- | --- |
| right-click with a signet of a realm the player has unlocked | opens the portal onto the signet's realm, or switches it there at once; the signet stays in the hand |
| right-click with the signet of the realm a lit portal already shows | nothing changes; "The portal already opens onto Undervault" |
| right-click with a signet of a realm the player has not unlocked | refused, with the realm's hint: "You are not eligible to open a portal to the stone realm. Unlocked by completing The Ominous Chief quest." (Matěj, 2026-10-20) |
| right-click with the realm's own signet on a frame inside that realm | opens it onto return, or keeps it there, as the sneak gesture does (Matěj, 2026-10-28: "the portal in the Undervault would get activated by the same signet") |
| right-click with another realm's signet on a frame inside a realm | refused: "This portal leads only back to Overworld", because no portal links two realms (Matěj, RLM-188) |
| right-click with an empty hand | the action bar shows where the portal opens onto, or "The portal is closed" for a dark one |
| sneak and right-click with an empty main hand, whatever the off hand holds | in Overworld, closes the portal (setting none); inside a realm, opens it onto return, or switches it back to return |
| break the sealstone | the portal closes, and the sealstone drops as an item without its setting; one a trip built drops nothing ([Building a portal](#building-a-portal)) |

- **Who may switch a portal:** anyone holding a signet they are eligible to use (Matěj, RLM-128). Unlocks are per
  player ([Multiplayer](#multiplayer)), so switching changes only where the portal leads, never who may enter.
- **It glows near a signet** (Matěj, 2026-10-24, RLM-406: "the Sealstone block would smoothly light up if player with
  Signet item in hand"; chapter 1's "the gate lights"). While a player holding a signet that works on it in either
  hand stands within 5 blocks of its centre, the sealstone lights up, one light level a tick to light level 15, so it
  rises over 15 ticks, about a second, and fades the same way once they walk away or put the signet away. In
  Overworld, a portal open onto a realm glows for that realm's signet, and a dark one, as a frame that was never
  opened is, for any signet. Inside a realm, the sealstone glows for that realm's own signet, which opens it back onto
  Overworld, whether it is dark or open onto return (Matěj, 2026-10-28: "I'd add the glowing effect as well"). In a
  dimension without an entry (the instances, the Nether, other mods'), where no signet opens a portal, it stays dark
  (RLM-458). The glow is the block's own light (a block state, `glow`), so every player sees it, also on a dedicated
  server; spectators light nothing. The radius, the signet rule and the full light level are proposals for Matěj's
  playtest. Its look, an emissive texture, comes with the frame's retexture (RLM-301).
- **The sneak gesture with something in the off hand** (RLM-133). Vanilla skips a block's use while the player
  sneaks with anything in either hand that does not bypass the sneak (`ServerPlayerGameMode#useItemOn` on the server,
  `MultiPlayerGameMode#performUseItemOn` on the client; `IItemStackExtension#doesSneakBypassUse` is true only for an
  empty stack by default). A shield, torch or food in the off hand would then block the gesture, and a torch or a
  block there would be placed against the sealstone instead. Both methods post `PlayerInteractEvent.RightClickBlock`
  before that check. So a common listener in `world`, which runs on both sides, sets `useBlock` to `TRUE` when the
  player sneaks, the main hand is empty and the target is a sealstone. The sealstone's `useWithoutItem` then
  handles the gesture on both sides. On the client the main hand's use succeeds, so the client never goes on to the
  off hand.

## Realm signets

- **One per realm,** stacking to 1, never consumed (RE-1), fire resistant (`Item.Properties#fireResistant`), so lava
  does not take it. One signet opens any number of portals (Matěj, RLM-128). Its tooltip is one line, "Does not
  consume on use" (Matěj, 2026-10-20, replacing the first version's "Undervault — Stone Realm" and "Never used up").
  It shows no levels: the arrival title shows the realm entry's level band.
- **A signet is a key, not the unlock.** Every copy of a realm's signet is the same item, whoever obtained it, and
  anyone may hold one (Matěj, RLM-128). A player needs a signet to open or switch a portal, and their own unlock
  ([Access and unlocks](#access-and-unlocks)) to use it.
- **A signet opens its own realm only** (Matěj, 2026-10-23, RLM-395): the Signet of the Undervault opens Undervault,
  in the game and in the lore, and there is no master signet that opens several.
- **Players keep their signets** (Matěj, 2026-10-23, RLM-395): no quest takes, cuts or sends one. A quest that needs
  a signet only confirms that the player holds the right one, and the player keeps it. The same decision's second
  half is a **story rule**: items do not travel between Skyhearth and the Overworld, and Skyhearth's Mirror is the
  exception (RLM-395; the story texts carry it, [lore outline](../lore/outline.md)). It says why nothing is sent up or
  down in the story; it is no travel rule, and in the game every entity travels through a realm portal
  ([Multiplayer](#multiplayer)).
- **Where signets come from** (RE-9, LE-1; Matěj, 2026-10-23 and 2026-10-24, RLM-395):
  - **final bosses never drop signets.** Each realm's final boss drops, at 100%, a unique crafting material that the
    next signet's recipe needs, its **shard**, and every kill gives another, so the boss is always a source;
  - **every signet's recipe follows one rule:** its realm's shard in the middle, 4 of the previous realm's ore or gem
    on the sides and 4 of the new realm's theme block in the corners, with no ring step. The Signet of the Undervault
    takes the Stone Signet Shard, 4 diamonds and 4 stone ("combine the shard with the most precious of the
    Overworld's gems and some stone", as the lore puts it). The twins' signets keep the shard in the middle and take
    two of each ingredient instead (Matěj, 2026-10-26, RLM-432): the Signet of the Sunwastes 2 Rubies at the top and
    bottom, 2 Netherite Scraps left and right, 2 Sand in the top left and bottom right corners and 2 Red Sand in the
    other two; the Signet of the Everwinter 2 Sapphires, 2 Netherite Scraps, 2 Snow Blocks and 2 Ice in the same
    places. The recipe is shown in the quest log, and crafting the signet is a step of the chapter's main
    chain. Beating the boss is what "completing" a realm means; the realm itself has no completion flag
    ([realm design](realms.md#opening-the-next-realm));
  - **the Signet of the Undervault** is crafted around the **Stone Signet Shard**, the Illusioner's piece of the old
    signet: Overworld's final boss (M2, RLM-321) drops it. In M1 there is no Illusioner, so the signet stays a reward
    of the stone realm's unlock quest, the last quest of chapter 1's main questline, The Ominous Chief (the roadmap's
    "locked until chapter 1's main questline unlocks it"; Undervault opens at about level 6, with no hard level lock,
    [realm design](realms.md#chapters-and-levels)). The same quest grants the unlock in both milestones;
  - **a lost signet comes back from the quest shop** or from its boss. After a realm's final boss quest, a shop in the
    quest journal (the Lodge Merchant tab, RLM-407, M2) sells the signet of the realm that boss unlocks, as a whole
    item, not its shard: repeatable up to 3 times, for a price relative to the signet's level that does not rise
    with purchases. The Signet of the Undervault costs 50 copper coins, the lowest of RLM-284's coins (Matěj,
    2026-10-24), and its quest stays locked until the Illusioner quest is done. After 3 purchases only the boss is
    left; with keep inventory, losing a signet 4 times should be rare. This replaces the first version's repeatable
    quest that gave a new Signet of the Undervault for a few copper ingots. M1's case is
    [open question 4](#open-questions);
  - **when the next chapter has two realms** (chapter 3: Sunwastes and Everwinter), the boss before them drops one
    shard per realm it opens: Undervault's final boss, Astone, the Unbroken, drops the Sun Signet Shard and the Ice
    Signet Shard, with the Stone Heart for the Skyhearth Medallion, all three at 100% on every kill (Matěj, 2026-10-25
    and 2026-10-27, RLM-432; [open question 2](#open-questions));
  - **when two realms share a chapter, the next main-path signet needs both:** its recipe takes the guaranteed drop
    of each realm's main-path boss (for the realm after chapter 3: Sunwastes' and Everwinter's), so finishing one
    branch never opens the next realm. Both realms are needed for further progress
    ([realm design](realms.md#the-desert-realm)); how that fits a recipe with one shard is
    [open question 3](#open-questions);
  - **an optional side realm's signet** comes from wherever its design puts it: a chest at 100% in an instanced
    dungeon, which is rebuilt for every run, or a trade that restocks; never a random drop, and never a chest found
    only once (PR-15).
- **Nothing depends on luck, and from M2 every signet can be replaced** (M1's lost Signet of the Undervault is
  [open question 4](#open-questions)): each source is a quest reward, a guaranteed drop, a recipe or the quest shop. A
  lost signet is bought back, up to 3 times, or crafted again from a boss the player can always replay: another
  Illusioner kill gives another Stone Signet Shard, and every later boss waits in its dungeon, since an NPC sells the
  keys of dungeons already done (LE-2). A lost signet never locks the realm again, because the unlock belongs to the
  player.

## Access and unlocks

- **The unlock is an advancement per realm,** `realmforge:realm/<realm>`, generated from the realm's data. Its only
  criterion is `minecraft:impossible`, like a chapter's advancement
  ([architecture 16](../architecture.md#16-progression-and-quests)), so nothing in play grants it but the realm's
  unlock quest. Holding, crafting, picking up or trading a signet unlocks nothing. The advancement is hidden until
  earned and shows a toast, "Realm unlocked: Undervault"; it adds no chat line (`hidden`, `show_toast`,
  `announce_to_chat` in vanilla's advancement display).
- **The unlock quest** grants it through an `AdvancementReward` that is per player and claims itself
  (`team_reward: false`, `auto: "enabled"`), the rule every chapter reward follows
  ([architecture 16](../architecture.md#16-progression-and-quests)). FTB Quests keeps quest progress per FTB Teams
  team, and every player stays a team of their own: FTB Teams parties are off in the pack (RLM-206,
  [ADR 0053](../decisions/0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md)), as the
  core mod sets FTB Teams' `setPartyCreationFromAPIOnly(true)`, so nobody joins a party and gets its realm unlocks and
  chapter advancements. The unlock is per player. Its tasks are the player's own progress, never "have the signet",
  which a trade could complete:
  - **the stone realm's:** the last quest of chapter 1's main questline; in M1 it also rewards the Signet of the
    Undervault, which the player crafts from M2 on ([Realm signets](#realm-signets)). Q2 writes it;
  - **a later main-path realm's:** the main-chain step after the boss or bosses whose drops its signet takes, with a
    task such as defeating that boss (`realmforge:boss_defeated`). The chapter that adds the realm writes it.
- **The access requirement:** the realm dimension's entry in `realmforge:dimension_rules` holds it
  ([architecture 15.1](../architecture.md#151-dimensions)). For a realm it is
  `{type: realmforge:has_advancement, advancement: realmforge:realm/<realm>}`, from the first set of
  `realmforge:requirement_type` ([architecture 5.3](../architecture.md#53-type-registries)). A realm may add more
  requirements, such as a level; validation fails a realm whose rules leave out its unlock advancement.
- **The sealstone checks it** before it opens or switches a portal to a realm
  ([The sealstone](#the-sealstone)).
- **The portal checks it when a player steps into a portal block,** so the portal never starts its countdown for a
  player without access. That player stays where they are, and a centre-screen message says "You are not eligible
  to pass through the portal." (Matěj's wording, RLM-128), with the realm's hint below it ("Unlocked by a chapter 1
  quest"). The message comes at most once every 3 seconds, with a short refusal sound. The server sends it as a
  payload of our own (`RealmRefusedPayload`, the realm's hint), and the client draws it like a title and subtitle at
  2x and 1x, half of vanilla's 4x and 2x, which run the sentence into the screen's edges (Matěj's playtests of W2a,
  2026-10-19, and W2b, 2026-10-20: "make both fonts 1 scale lower"), so the portal, `/tp` and a warp show the same
  refusal.
- **Every other way in is checked too,** so the advancement seals the realm whatever the path. In 26.1.2 every move
  into another level goes through `Entity#teleport(TeleportTransition)` (`ServerPlayer` overrides it), which posts
  NeoForge's cancellable `EntityTravelToDimensionEvent` before it moves anything. The event also fires for a move
  within one level (its target may be the entity's current dimension), so the realm rules act only when the target
  differs from the dimension the entity is in. The event covers:
  - realm and vanilla portals;
  - `/tp` and `/execute in … run tp` (`TeleportCommand` calls `Entity#teleportTo(ServerLevel, …)`, which calls
    `teleport`);
  - ender pearls;
  - other mods' teleports that use either method, waystone warps among them (*verify* the pack's Waystones
    version).

  `world` cancels the event for a player who is about to enter a realm they have not unlocked from another level,
  with the same message. A warp to a waystone in a realm the player has not unlocked therefore never works (Matěj,
  RLM-128). A respawn does not pass through the event (`PlayerList#respawn` creates the player in the target level
  directly), and a respawn never puts a player in a realm ([Respawn](#respawn)).
- **What a locked realm shows elsewhere:** a portal someone else opened onto it looks the same as for anyone (its
  colour, particles and sound), so a player sees where a friend went. The realm's hint says how to unlock it. A
  realm map with locked realms and their hints (RE-7) is a later view, in the guide (RLM-102, approved for M4 in
  RLM-116) or a screen of its own; M1 needs none.
- **No operator bypass:** a creative player needs the unlock like anyone else, so tests and playtests see the real
  rule. `/advancement grant @s only realmforge:realm/stone`, or a QA command, unlocks a realm for testing.

## Travel

Portals link like the Nether's, in both directions (Matěj, RLM-128). There is no landing and no home point: a
player arrives wherever the linked portal stands.

### Linking

- **Into a realm:** the trip divides the x and z of the source portal's centre by the realm's scale
  ([The coordinate scale](#the-coordinate-scale)) and clamps them into the realm's world border, as
  `NetherPortalBlock#getPortalDestination` does with `DimensionType.getTeleportationScale` and
  `WorldBorder#clampToBounds`. It starts from the centre of the opening, not from the player, whose place in a
  3-wide opening would shift the target by up to 16 blocks on the way back. It then looks for a lit realm portal
  that leads back to Overworld (setting return) within ⌊128 ÷ scale⌋ blocks of that position: 16 at 1:8, as vanilla
  searches the Nether. When it finds one, the player arrives there; otherwise a portal is built within one block less,
  ⌊128 ÷ scale⌋ − 1: 15 at 1:8 ([Building a portal](#building-a-portal); the rounding below needs that block).
- **Back to Overworld:** the same in reverse. The trip multiplies by the scale and looks within 128 blocks of
  that position for a lit portal open onto the realm the player comes from, or builds one within 16 blocks.
- **The radii follow vanilla's arithmetic.** Vanilla's `PortalForcer#findClosestPortalPosition` searches 16 blocks
  in the Nether and 128 in Overworld, and 16 × 8 = 128 is what makes a nether portal pair link back. A realm
  side radius derived from 128 ÷ scale keeps that true at any scale, once it allows for the rounding:
  `WorldBorder#clampToBounds` returns `BlockPos.containing`, which rounds the scaled position down. An Overworld
  portal centred at x 7 aims at realm x 0 (7 ÷ 8, rounded down); a realm portal built 16 blocks away, at x −16,
  would aim back at −128, and the search there (−256 to 0) misses x 7.
  - a portal a trip builds in a realm stands within 15 blocks of the rounded position, which lies less than one
    realm block below the exact one (at most 7 Overworld blocks), so the way back aims within 15 × 8 + 7 = 127
    blocks of the Overworld portal it came from, inside the Overworld search;
  - one a trip builds in Overworld stands within 16 blocks of the scaled-back position, so the way back aims
    within 2 blocks of the realm portal it came from, inside the realm search of 16: a realm portal at x R scales
    back to 8R + 4, a frame built within 16 blocks of that has its centre between 8R − 11.5 and 8R + 20.5, and
    dividing by 8 and rounding down gives R − 2 to R + 2.

  So a pair a trip built always links back, both ways. Overworld portals up to about 128 blocks apart (16 × 8) lead
  to one realm portal, as Overworld portals up to 128 blocks apart share one nether portal. Matěj asked for exactly
  that: Overworld portals close to each other target the same realm portal. At 1:8 the search radii are vanilla's own
  (128 in Overworld, 16 in the realm), so realm portals pair as nether portals do (Matěj, 2026-10-28: "make it exactly
  the same behavior as per nether"); only the realm's build radius is one block less, for the rounding.
- **The search reads point-of-interest data, as vanilla's does:** the sealstone is a point of interest of our
  own (`Registries.POINT_OF_INTEREST_TYPE`, `PoiManager#getInSquare`). The search then keeps only sealstones whose
  portal is lit and leads the right way. Portals are never linked by a saved pair, so breaking or switching a portal
  needs no bookkeeping.
- **No portal links two realms** (Matěj, RLM-188): a frame inside a realm opens only onto return
  ([Edge cases](#edge-cases)), so every trip between realms goes through Overworld and chained trips cannot loop
  between realms (the "portal loops" AoA's changelogs fixed).
- **Portals players place themselves can still pair unevenly,** as nether portals do. Two Overworld portals within
  about 128 blocks of each other lead to one realm portal, and it leads back to the one nearer its scaled-back
  position. Waystones are the easy way around it.

### The coordinate scale

A realm's dimension type sets vanilla's `coordinate_scale`; the Nether's is 8 and Overworld's 1.
`DimensionType.getTeleportationScale(from, to)` divides the source's scale by the target's. A trip from
Overworld into a realm of scale 8 therefore multiplies x and z by 1/8, and the way back by 8. Realm portals never
link two realms (Matěj, RLM-188). The scale is data, so a playtest can change it without code.

**Besides portals, only `/execute in` reads it** in 26.1.2. The command's `in` redirect (`ExecuteCommand`) calls
`CommandSourceStack#withLevel`, which multiplies the source's x and z by `DimensionType.getTeleportationScale`, as
it does for the Nether, so relative positions after it are scaled. From Overworld x 1,600,
`/execute in realmforge:stone run tp @s ~ ~ ~` lands at realm x 200, not 1,600. Test and playtest commands
therefore give absolute coordinates in a realm. `/execute at <entity>` is not affected: it sets the entity's own
position after the level.

**The stone realm uses 1:8, the Nether's ratio** (Matěj, 2026-10-28: "note that the coordinates are 1:8, undervault
is basically nether replacement"). It replaces W2b's 1:16 (his earlier "like the Nether's 1:8, or stronger"), which
came from mob levels that rose with the distance from the realm's origin. Since RLM-303 they follow the nearest
player instead, floored at the realm's entry level ([architecture 14.1](../architecture.md#141-mob-levels)), so where
a portal arrives no longer decides the levels met there:

- **Arrivals:** Overworld bases mostly stand within 10,000 blocks of the origin (the realm design's travel rule leaves
  trips of 1,000–10,000 blocks to waystones). At 1:8 every Overworld portal within 10,000 blocks arrives within
  1,250 blocks of the realm's origin, where the [realm design](realms.md#the-stone-realm)'s sub-regions start.
- **Where 1:16 came from** (W2b, replaced on 2026-10-28): the first level rule (W2a, by distance) gave level 6 at the
  origin and one level more every 320 blocks, so the realm's first band, levels 6–10, lay within 1,280 blocks of the
  origin. 1:16 was picked for the first version's narrower bands (10,000 ÷ 640 ≈ 16) and kept arrivals at the realm's
  lowest levels. The bands are the old area model now, as the realm design says.

| Overworld portal, blocks from 0, 0 | Arrival in the stone realm, blocks from 0, 0 |
| --- | --- |
| 1,000 | 125 |
| 5,000 | 625 |
| 10,000 | 1,250 |
| 20,000 | 2,500 |
| 30,000 | 3,750 |

- **The way back multiplies by 8:** a realm portal 1,000 blocks from the realm's origin leads to Overworld
  8,000 blocks out. That is how nether travel works too.
- **A later realm** sets its own scale, by the same arithmetic of where its portals arrive.

### Building a portal

When no portal leads the right way within the search radius, the trip builds one, as vanilla's
`PortalForcer#createPortal` does for nether portals:

- **The spot:** the one nearest the scaled position, searched in a spiral within the build radius of that side (⌊128 ÷
  scale⌋ − 1 blocks in a realm, 15 at 1:8, one less than the search for the rounding in [Linking](#linking); 16 blocks
  in Overworld, as vanilla's spiral), where the frame is set into solid floor (Matěj, 2026-10-20: "the bottom side in
  the floor … so player doesn't have to jump in order to get in, but can just walk": its bottom row replaces the top of
  the floor, so the opening is at floor level), the rest of the frame's space is free, and one side has floor and two
  free blocks in front of the opening, at its height, to arrive on. It must be inside the world border and within the
  `y` range of the dimension's realm entry (`arrival.y`, [Data](#data-contentrealms)), which keeps arrivals in the depth
  band of the realm's lowest levels. The source portal's height, clamped into that range, decides between spots at the
  same distance.
- **Overworld's `y` range** is 63 to 310 (a proposal): the frame's bottom row, set into the ground, lies at
  least at sea level (63), the first block above the sea's top water block (62). Water is no floor, so a way back over
  deep ocean, from a realm portal at y 30 say, finds no floor in the spiral and builds on a platform at the surface
  instead of carving a frame under the seabed. Vanilla's own fallback in `PortalForcer#createPortal` keeps y ≥ 70; 63
  also lets a frame be set into beaches, river banks and plains, which lie between 63 and 70 (RLM-341: the range
  started at 64 while frames stood on top of the floor, and ground at 63 then took no frame set into it). The top,
  310, is that fallback's ceiling (the highest buildable y less 9), so a mountain's surface is a spot too, where a
  lower ceiling would carve the frame into the rock.
- **Where nothing fits** (the stone realm has a bedrock ceiling; a realm can have a void below or water), the frame is
  carved out at the scaled position, clamped into the `y` range: set into a floor of the entry's arrival block
  (`arrival.floor`) that reaches in front of it, and air around it. A safe pocket of `arrival.pocket_radius` blocks
  keeps out fluids and falling blocks (RE-6: 3 by default, larger for a realm that needs it, such as an underwater one).
- **Its corners are Realmforge Bricks too** (Matěj, 2026-10-20: "without it it looks bad"). A frame needs no corners
  to light, and a player's own frame may leave them out; every frame a trip builds has them.
- **The new portal is lit:** in a realm its sealstone shows return, in Overworld the realm the player came from.
- **It works like a frame a player built:** anyone may switch or close it, and breaking any of its blocks closes it
  ([Edge cases](#edge-cases)).
- **Its blocks drop nothing.** Every frame a trip builds holds 17 Realmforge Bricks (its corners included) and a
  sealstone, which cost ender
  pearls, eight kinds of bricks, 4 gold ingots and a compass to craft, and waystone warps are free in the pack. Entering
  a portal, breaking the return frame the trip built, warping back to the first portal and entering it again would yield
  a frame each time: a farm. So Realmforge Bricks and the sealstone carry a block state flag, `generated` (a working
  name), which a trip sets on the blocks it places and a player's placement never does, and their loot tables drop the
  block only without it (vanilla's `minecraft:block_state_property` condition). The floor blocks a carved spot gets
  (`arrival.floor`) drop as usual: they are plain building blocks the realm or Overworld has in plenty.
- **Overworld is no exception** (Matěj: the way back works "exactly like Nether → Overworld"): the way back
  builds a frame there when none is near. Its floor is the Overworld entry's arrival block, and its height comes
  from the Overworld entry's `y` range (above).

### Arriving

- **The player arrives in front of the exit portal, facing away from it,** when one side of the opening has floor and
  two blocks of air at the opening's height: on the floor the frame is set into, so they walk straight back in, or,
  for a frame a player stood on top of the floor, on that floor one step below the opening (RLM-341). Otherwise they
  arrive in the opening, as a nether portal places them, and the portal cooldown vanilla sets after every trip
  (`Entity#setPortalCooldown`, half a second for a player, counted down only once their client has loaded the new
  dimension) keeps them from bouncing straight back: it starts again on every tick spent in a portal, so a player who
  arrives in the opening, or steps straight back into it, travels only once they stepped out for half a second and in
  again (RLM-335).
- **A title** shows the realm's name with its element subtitle, and its level band (the realm entry's `levels`,
  "Stone Realm · Levels 6–18"), for 2 seconds on every arrival in a realm (RLM-139); the player's first arrival also
  shows its flavour line.
- **Chunk loading:** a trip loads only the chunks around the exit portal, for a short time, through vanilla's portal
  ticket (`TeleportTransition.PLACE_PORTAL_TICKET`, as for nether portals). Portals load nothing permanently; since
  every entity travels, an item thrown through keeps the exit's chunks loaded for a short time on each trip, the
  chunk-loading trick a nether portal allows too. Building a portal generates its chunks on the
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

Matěj's rule (RLM-128): **a player always respawns in Overworld,** at their bed there, or at the world spawn
without one; never in a realm. This answers the realm design's
[open question 1](realms.md#open-questions).

- **Beds in a realm do nothing,** and they do not explode either, so a bed is no trap there. The realm's dimension
  type sets vanilla's `minecraft:gameplay/bed_rule` to `can_sleep: never`, `can_set_spawn: never`,
  `explodes: false` and an `error_message` such as "Beds only work in Overworld".
  `ServerPlayer#startSleepInBed` then refuses with that message and sets no spawn point. The Nether's rule explodes
  instead.
- **Respawn anchors do not work there** (`minecraft:gameplay/respawn_anchor_works: false`): as in Overworld, a
  charged anchor explodes when used.
- **A spawn point in a realm set some other way** (`/spawnpoint`, another mod) is overridden. NeoForge's
  `PlayerRespawnPositionEvent`, which `PlayerList#respawn` posts, moves a respawn whose level is a realm to the
  Overworld's world spawn.
- **A death costs the way back, nothing more:** `keep_inventory` and `immediate_respawn` are on
  ([architecture 19.1](../architecture.md#191-world-defaults)). The ways back are the player's own Overworld portal,
  a waystone they placed in the realm, and the "Return to last death" scroll.

## Look and sound

- **One portal block** for every realm, tinted with the colour of the realm its sealstone shows (return portals use
  Overworld's green). Its texture is one greyscale swirl, tinted on the client by a block tint source
  (`RegisterColorHandlersEvent.BlockTintSources`, a `BlockTintSource` whose `colorInWorld` reads the frame's
  sealstone). The sealstone's block entity sends its setting to clients, and the colours come from the synced realm
  registry ([Data](#data-contentrealms)); *verify* that the region the chunk mesher reads holds the sealstone's block
  entity. Block colours are baked into the chunk mesh, so when the setting arrives on the client, the sealstone marks
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
- **Travel starts at once** for every entity, a player in survival too (Matěj's playtest of W2b, 2026-10-20, RLM-335:
  "entities and items are teleported instantly, but I still have to wait in the portal"; the nether portal takes 4
  seconds): `Portal#getPortalTransitionTime` answers 0, as vanilla's nether portal does for a creative player
  (`players_nether_portal_creative_delay`).

## Multiplayer

- **Unlocks are per player and permanent** (Matěj, RLM-128: "for now"). Each player completes the realm's unlock
  quest, so a server where one player is in chapter 2 does not open chapter 2's realms to a newcomer. FTB Teams, the
  team system FTB Quests runs on, keeps progress per team, so its parties are off in the pack (RLM-206): creating one
  is refused for every player, and nobody can invite or join ([architecture
  19.4](../architecture.md#194-optional-integrations)). Shared quests in our own parties come later, with low
  priority (RLM-156, on top of RLM-68's parties), by Matěj's rule of 2026-10-15: when one member completes a quest
  that unlocks a realm or a chapter, another member gets it only if they have that quest unlocked themselves and both
  are in the party at that moment; a player who joins later finishes it themselves.
- **Signets change hands freely,** but a friend's signet opens nothing for a player without their own unlock.
- **A player without access** in a portal someone else opened is refused as in
  [Access and unlocks](#access-and-unlocks), while their friends pass through.
- **Everyone who comes through one portal arrives at the same linked portal,** and the way back links by position,
  whoever built the portals.
- **Every entity travels through a portal** (Matěj, 2026-10-20: "when any entity goes in, it teleports it into the
  dimension"). A mob, an item, a projectile or a vehicle with its passengers that steps in travels as through a
  nether portal (vanilla keeps a passenger itself out of portals, `Entity#canUsePortal`, so it travels with its
  vehicle) and arrives at the linked portal, which a trip builds when none is near. A player still needs access: a
  vehicle that carries a player without it does not step in. A creature keeps the level it has, so an Overworld mob
  in a realm is weaker than the realm's, and a realm's creature in Overworld stronger than Overworld's. This replaces
  the first version's "only players travel" (decision 9).
- **Other paths carry no mobs** (RLM-133, rechecked for 26.1.2). Every move into another level goes through
  `Entity#teleport(TeleportTransition)`, which posts `EntityTravelToDimensionEvent` for every entity
  ([Access and unlocks](#access-and-unlocks) lists the paths). `world` cancels it for every entity that is not a
  player when the target is another level and a realm is the source or the target, unless the move is a trip
  through a realm portal (the portal lets the entity and its passengers through on the tick of the trip). A move
  within one realm passes: `/tp` inside the realm, a leashed mob on a warp between two waystones in the same realm,
  a teleport spell. The Waystones mod takes leashed mobs along on a warp
  (`transportLeashed = "ENABLED"` in the pack); *verify* that its version moves them with `Entity#teleport` or
  `Entity#teleportTo`, so the event stops them. If it does not, the pack sets `SAME_DIMENSION`, which leaves them
  behind on every warp to another dimension.
- **Shoulder entities stay behind.** A parrot on a player's shoulder is no entity while it sits there: it travels
  inside the player's data, no `EntityTravelToDimensionEvent` fires for it, and vanilla recreates it in whatever
  level the player is in when it gets off (`ServerPlayer#removeEntitiesOnShoulder`, which calls
  `respawnEntityOnShoulder`). So `world` puts a player's shoulder entities down in the level they leave:
  - **the travel listener,** before it lets a player into or out of a realm (a portal, a teleport, a warp);
  - **the respawn listener,** for a player who died in a realm. Vanilla puts them down at death (`ServerPlayer#die`),
    but `removeEntitiesOnShoulder` does nothing within 20 ticks of a landing, and `ServerPlayer#restoreFrom` copies
    what is left onto the respawned player in Overworld.

  Neither uses `removeEntitiesOnShoulder`, because of that 20-tick wait. A helper recreates each shoulder entity at
  the player's position, as `respawnEntityOnShoulder` does (`EntityType#create` from the saved tag, the owner set,
  `ServerLevel#addWithUUID`), and clears it (`ServerPlayer#setShoulderEntityLeft` and `Right` are protected: an
  access transformer, [architecture 19.3](../architecture.md#193-mixins-and-access-transformers)).
- **The one path the rule cannot see:** an entity created anew in another level (`EntityType#create` plus
  `ServerLevel#addFreshEntity`), as another mod's mob-catching item would do. That is a spawn, not a trip, and no
  event says where the mob came from. If the pack ever gains such a mod, its own config is the place to forbid
  realms; a mixin is the last resort ([architecture 19.3](../architecture.md#193-mixins-and-access-transformers)).

## Edge cases

- **A frame inside a realm:** allowed, and it opens only back to Overworld (Matěj, RLM-188): the realm's own signet
  and the sneak gesture open it onto return (Overworld, at the scaled-back position, [Linking](#linking)), so a
  player whose portal broke builds a new one and leaves (Matěj, 2026-10-28). Another realm's signet is refused there
  ([The sealstone](#the-sealstone)). A trip to another realm goes through Overworld.
- **Instance dimensions and other dimensions:** a frame lights only in dimensions that have a realm entry: the
  Overworld and the realms. In `realmforge:instances`, in the Nether and End until M4 removes them, and in
  dimensions of other mods the sealstone refuses: "The portal cannot open here". Inside instances players are in
  Adventure mode anyway and cannot build one ([architecture 15.5](../architecture.md#155-instances)). Dungeon
  entrances are a different thing: a temporary portal opened with a dungeon key that is used up (D8, W4), and
  leaving an instance returns the player to its entrance. Matěj sees a fixed entry and exit point as the instances'
  way, not the realms' (RLM-128).
- **Broken frames:** breaking any frame block or the sealstone closes the portal at once (the portal blocks check
  their frame on neighbour updates, as nether portal blocks do; every required frame block touches the opening, and
  the corners are optional, so every break reaches a portal block). A player standing in it stays where they are. The
  sealstone's setting becomes none, so the rebuilt frame opens again with the signet
  ([The sealstone](#the-sealstone)).
- **A missing destination:** a sealstone set to a realm that no longer exists (content removed, data pack off) shows
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
name: {en_us: Undervault}            # the approved name (RLM-139), without an article (RLM-188)
subtitle: {en_us: Stone Realm}       # shown with the name in the guide and the arrival title
description: {en_us: stone realm}    # in running text: "a portal to the stone realm" (Matěj, 2026-10-20)
locked_hint: {en_us: "Unlocked by completing The Ominous Chief quest."}   # the quest's name is a placeholder
flavour: {en_us: "Cold air rises from below, smelling of dust and old stone."}
# W2b adds the portal's fields:
signet: realmforge:undervault_signet        # an item from content/items/, stack size 1
colour: "#7A8FB0"
particle: minecraft:ash              # the extra particle next to the tinted dust
sound_pitch: 0.8                     # until A3 brings our own sounds
arrival: {y: [20, 90], floor: realmforge:slate_bricks, pocket_radius: 3}   # where a built portal may stand
```

```yaml
# content/realms/overworld.yaml (sketch): always open, so no signet and no hint
id: overworld
dimension: minecraft:overworld
name: {en_us: Overworld}
# W2b adds the portal's fields:
colour: "#5DBB63"                    # return portals
arrival: {y: [63, 310], floor: minecraft:stone_bricks, pocket_radius: 3}   # portals built on the way back
```

- **The name in messages:** a realm's name takes no article (Matěj, RLM-188), so it reads the same alone (the
  arrival title) and mid-sentence ("The portal opens onto Undervault"). The pydantic model carries one form and each
  message a template; no message hard-codes a realm's name. The refusal at the sealstone names the realm by its
  plain `description` instead: "You are not eligible to open a portal to the stone realm." (Matěj, 2026-10-20).
- **The coordinate scale is not here:** it is the `coordinate_scale` of the realm's dimension type
  (`content/worldgen/dimension_type/stone.json`, `8.0`), next to its `bed_rule` and `respawn_anchor_works`
  attributes, because vanilla reads it from there.
- **`realmforge:realm`, a new datapack registry** owned by `world`
  ([architecture 5.2](../architecture.md#52-our-datapack-registries)), synced to clients, because they need the
  colour and the name. Overworld is an entry too (`overworld`: no signet, an arrival `y` range and floor for
  portals built on the way back, [Building a portal](#building-a-portal)), which gives return portals their colour
  and gives a later realm map its root. The architecture lists only what is built: the task that builds the
  registry adds it to [architecture 5.2](../architecture.md#52-our-datapack-registries) and the content type to
  [6.2](../architecture.md#62-what-each-content-type-becomes) in its own pull request (CLAUDE.md rule 11).
- **Package direction** ([architecture 4.4](../architecture.md#44-dependencies-between-features)): the realm entry
  names its signet by id in vanilla's static item registry, resolved by vanilla's item codec, the way a worldgen file
  names its blocks. `world` uses no code and no registry of the `item` feature for it: a signet is a plain generated
  item without behaviour of its own, and the sealstone compares the held stack with the realm's item. So `world`
  keeps its row in the dependency table.
- **`rf gen` writes** the registry entry, the unlock advancement `realmforge:realm/<id>` (criterion
  `minecraft:impossible`), the lang keys (`realm.realmforge.<id>.name`, ...) and GameTest expectations.
- **Validation:**
  - every id the entry names (the dimension, the signet, the particle, the arrival floor block) exists in the ID
    universe, which holds `minecraft:particle_type` and `minecraft:block` (CLAUDE.md rule 3);
  - the particle is a type without options (a `SimpleParticleType` such as `minecraft:ash`, from a list the
    validator keeps), because types such as `minecraft:dust` or `minecraft:block` need option data the entry does
    not carry;
  - one realm per dimension, and one realm per signet: two realms on one signet would leave the sealstone no single
    destination;
  - every realm but Overworld names a signet, since a realm without one has no way onto a sealstone, and the
    signet stacks to 1;
  - the realm dimension's `dimension_rules` access requirement includes the unlock advancement;
  - the realm's dimension type sets a `coordinate_scale` above 1 and a `bed_rule` that sets no spawn.
- **The signet source check comes later.** Every signet needs a renewable source (a recipe, a repeatable quest, or the
  guaranteed drop of a boss that can be replayed), never only a one-time reward, as the "every item obtainable" rule
  asks ([ADR 0005](../decisions/0005-content-pipeline-yaml-single-source-of-truth.md)). That rule is not in the pipeline
  yet: it needs recipes, loot and quests as data, and quests arrive with Q1. The signet check joins it then, together
  with a check that every realm has an unlock quest. Until Q2 adds the chapter 1 quests, the Signet of the Undervault
  and the unlock have no source in play: `/give`, `/advancement grant` and a QA command hand them out for tests and
  playtests, so W2a and W2b do not wait for Q2.
- **Not in the data yet:** a realm map's positions and parents (RE-7). They join when a client view needs them,
  synced with the realm entry. The realm's level band is in it (`levels`, for the arrival title): mob levels follow
  the nearest player up to the dimension rules' `max_level` (RLM-303), so the band no longer comes from them;
  validation keeps its lowest at their `entry_level`.

## Code shape

For the tasks that build it ([architecture 4](../architecture.md#4-code-layout)), in the `world` feature, which owns
dimension rules and access:

- `world/realm/`:
  - `Realm` (record with `CODEC`) and its registry key;
  - `RealmAccess`, which asks `core`'s requirement API at the sealstone, at the portal and in its
    `EntityTravelToDimensionEvent` listener, which also stops every entity that is not a player from entering or
    leaving a realm other than through a realm portal, puts a travelling player's shoulder entities down, and
    ignores a move within one level;
  - the respawn listener (`PlayerRespawnPositionEvent`), which also puts down the shoulder entities of a player who
    died in a realm;
  - `PortalFrame`, the frame check as plain Java over a block lookup: both axes, the missing positions;
  - `RealmPortalBlock`, implementing vanilla's `Portal` (`getPortalDestination`, `getPortalTransitionTime`,
    `getLocalTransition`) and `LiquidBlockContainer`, and overriding `canBeReplaced(BlockState, Fluid)` (it refuses
    every fluid, flowing or from a bucket);
  - `SealstoneBlock` with its block entity, and its point-of-interest type;
  - `RealmPortalLinker`: the scaled position, the search for a linked portal and the building of a new one, with the
    spot search as plain Java over a column of blocks;
  - the `PlayerInteractEvent.RightClickBlock` listener for the sneak gesture (common code: it runs on both sides);
- `client/`: the portal block's tint source;
- Realmforge Bricks, the sealstone and the signets as YAML content: blocks once the block content type exists, signets
  as items. The two blocks' `generated` flag and the loot tables that check it come from that content type ([Building a
  portal](#building-a-portal)).

## Tests

- **JUnit:**
  - the frame check (both axes; corners present or missing, which both light; a missing side block; a block in the
    opening; the sealstone off-centre; a frame too big or too small);
  - the scaled position, both ways, with the world border clamp and its rounding down;
  - the realm's own signet opens a frame inside the realm onto return, and so does the sneak gesture (2026-10-28);
  - the build radius of each side, ⌊128 ÷ scale⌋ − 1 in a realm: from every Overworld x in one 16-block cell,
    a realm portal at either end of the radius aims back within 128 blocks;
  - the spot search for a built portal (a ceiling, a void, water, a full column, the `y` range), including a way
    back from a realm portal at y 30 to Overworld columns of deep ocean, which builds on a platform at y 63, not
    under the seabed, and Overworld ground at y 63, which takes a frame set into it (RLM-341);
  - the arrival in front of a frame a player stood on top of the floor, on that floor one step below the opening
    (RLM-341).
- **pytest:** the schema, the references, the generated advancement (criterion `minecraft:impossible`), the access
  requirement check, the dimension type checks (scale, bed rule).
- **GameTests** (W2a's and W2b's "Done when"):
  - access is refused before the unlock advancement and allowed after it: through a teleport into the realm (as a
    waystone makes one), and through the portal, where the player stays put and gets the centre-screen message;
  - a player holding a signet without its unlock cannot open or switch a portal with it, and can after the unlock;
  - a signet lights a complete frame, a second signet switches it, and an incomplete frame stays dark;
  - breaking a frame block closes the portal; closed by the sneak gesture, or broken and rebuilt, the frame relights
    with the signet it showed before (RLM-133);
  - the sneak gesture with a shield in the off hand closes an Overworld portal, and opens a frame in a realm onto
    return (RLM-133);
  - a trip from Overworld builds a return portal within the realm's build radius (15 blocks at 1:8) of the scaled
    position in the realm, and the first trip back through it arrives at the Overworld portal it came from, also
    from a portal the scale rounds down the most (centred at x 7) when the only free spot in the realm lies 15
    blocks towards negative x; a second trip from a portal within 128 blocks of the first arrives at that same realm
    portal;
  - a trip from a realm portal with no Overworld portal near its scaled-back position builds one there, and the trip
    back arrives at the realm portal it came from;
  - a built portal never stands outside the world border or outside its dimension's `y` range;
  - breaking the blocks of a frame a trip built closes the portal and drops nothing, while a frame a player built
    drops its Realmforge Bricks and its sealstone;
  - a water bucket emptied into a lit portal's opening, by a player and by a dispenser, and water flowing into it,
    leave its portal blocks in place;
  - the sealstone refuses in the instance dimension;
  - an item and a mob step into a portal and travel, and a pig carrying a player without the unlock does not;
  - a player in survival travels on the first tick in the portal, and standing in the exit portal right after does
    not take them back, until they stepped out for half a second and in again (RLM-335);
  - the sealstone glows while a player holding a signet stands within 5 blocks, rising over 15 ticks, fades once
    they step away, stays dark with empty hands, and a return portal stays dark for a realm's signet (RLM-406);
    inside the stone realm, the realm's own signet lights a dark sealstone and one open onto return, a shield
    lights nothing, and in the instance dimension no signet lights one; this asks the rule itself, with no player
    kept in the realm for the glow's ticks, whose background chunk loading held up the tests beside it (RLM-458);
    a mob moved into or out of a realm with
    `Entity#teleport(TeleportTransition)`, and one moved with `/tp` (`Entity#teleportTo`), stays where it is
    (RLM-133), while a mob teleported within the realm moves;
  - a parrot on a player's shoulder stays in the level the player leaves, through a portal into a realm, a
    teleport out of one, and a death in one, also when it landed less than a second before;
  - a death in a realm respawns at the player's Overworld bed, or at the world spawn without one; a bed in a realm
    sets no spawn point and does not explode; a spawn point set in a realm by command still respawns the player in
    Overworld.

## What M1 takes from this

W2 is split in two (Matěj, RLM-128; the roadmap holds the plan):

- **W2a, the stone realm** (RLM-32): the dimension from the [realm design](realms.md#the-stone-realm), its
  dimension type (the coordinate scale, beds and anchors) and its `dimension_rules` entry (the level rule, the
  access requirement), the `realmforge:requirement_type` registry with its first types, `content/realms/` with the
  realm's name, hint and unlock advancement, the access check on every way in with the non-player and shoulder
  entity rules, and the respawn rule.
- **W2b, the realm portal** (RLM-154, after W2a): the frame, the sealstone, the portal block, the Signet of the
  Undervault, the portal's fields in `content/realms/` (the Overworld entry's included) and the synced registry, the
  linking and building (trip-built frames drop nothing), the sealstone's gestures, the look and the sounds. It uses
  vanilla particles and sounds; textures are placeholders until the art pipeline makes them.
- **Q2** (chapters 1 and 2) adds the stone realm's unlock quest, which rewards the unlock and the Signet of the
  Undervault, and the quests that walk through the frame. Until then `/give`, `/advancement grant` and the QA mod
  stand in.
- **M2** brings the Illusioner and his Stone Signet Shard (RLM-321), the signet's recipe, and the quest shop that
  sells a lost signet (the Lodge Merchant tab, RLM-407), whose buy quests the quest pipeline generates (Q1, RLM-37).
- **Later:** the "Return to last death" scroll (RLM-155); the desert and tundra signets with M4's realms; a tinted
  overlay; our own sounds (A3); a realm map; shared quests in a party (RLM-156).

## Decisions

Matěj's answers of 2026-10-13 (RLM-128), which this version records:

1. **The spec is approved** with the changes below.
2. **Signets:** every signet of a realm is the same item, and anyone may hold one. What counts is the player's own
   progress: without the realm's unlock quest a player can neither open a portal to that realm with the signet nor
   pass through an open one ("You are not eligible to pass through the portal."). One signet opens any number of
   portals.
3. **First portal:** answered by the Nether-like travel. Any number of portals can lead to one realm, anywhere in the
   Overworld, so no generated shrine is needed.
4. **Who may switch a portal:** anyone holding a signet they are eligible to use.
5. **Recall:** the Waystones mod's Warp Scroll and Return Scroll, plus our "Return to last death" scroll.
6. **W2's size:** split into W2a (the stone realm's dimension, its rules and the requirement types) and W2b (the
   realm portal), each M.
7. **Travel works like the Nether, in both directions,** with scaled coordinates so arrivals stay near a realm's
   origin. The spec picks the ratio from the level bands (1:16 for the stone realm; 1:8 since decision 24). There is
   no landing: the landing and the home point fit instanced dungeons, not realms.
8. **Respawn:** always at the player's bed in Overworld, or at the world spawn; never in a realm.
9. **Only players travel,** and unlocks are per player and permanent, for now; shared party quests come later with
   low priority. FTB Teams parties are off in the pack until then (RLM-206, from his sharing rule of 2026-10-15,
   RLM-156), which answers [open question 1](#open-questions).
10. **Waystones:** crafted and placed by players, global or private by each player's choice; no waystone is placed
    at an arrival point, and a warp into a realm the player has not unlocked never works.
11. **Portals inside a realm lead only back to Overworld** (Matěj, 2026-10-15, RLM-188 item 8): a portal opens onto
    a realm only from Overworld, and a frame built inside a realm opens only onto return. This replaces the first
    version's signet that switched a frame inside a realm to another realm. Decision 22 lets the realm's own signet
    open it onto return.

His playtest of W2b (2026-10-20, RLM-154) added:

12. **Names:** the frame's blocks are **Realmforge Bricks** and the **Realmforge Sealstone** (the first version's
    Portal Stone and realm selector), and a realm's key is its **signet** ("Signet of the Undervault"; the first
    version's sigil). Realmforge Bricks are made of Realmforge Stone, which is made of eight Overworld bricks around
    an ender pearl ([The frame](#the-frame)).
13. **Every entity travels through a portal** ([Multiplayer](#multiplayer)), replacing 9's "only players travel"
    for portals; every other way into or out of a realm still carries only players.
14. **A frame a trip builds is set into the floor, with Realmforge Bricks in its corners**
    ([Building a portal](#building-a-portal)): its bottom row replaces the top of the floor, so a player walks in
    without jumping (Matěj's reconfirmation of 2026-10-20; the first revision of that day stood it on top of the
    floor). The corners stay optional for a player's own frame.

His decisions of 2026-10-23 and 2026-10-24 on signets (RLM-395, from RLM-351's story beats) added:

15. **One signet, one realm, and the player keeps it:** the Signet of the Undervault opens Undervault only, with no
    master signet, and no quest takes, cuts or sends a signet ([Realm signets](#realm-signets)).
16. **Final bosses drop shards, never signets:** at 100%, every kill, the shard the next signet's recipe takes in its
    middle, with 4 of the previous realm's ore or gem and 4 of the new realm's theme block. The Illusioner's is the
    Stone Signet Shard (M2); in M1 the last quest of chapter 1 still gives the Signet of the Undervault.
17. **A quest shop sells a lost signet,** up to 3 times, after the boss quest: the Signet of the Undervault for 50
    copper coins, locked until the Illusioner quest is done. It replaces the repeatable quest of the first version.

His later requests added:

18. **Players travel at once** (his playtest of W2b, 2026-10-20, RLM-335), as every other entity does, instead of the
    first version's 2 seconds ([Look and sound](#look-and-sound)).
19. **The sealstone glows near a signet** (2026-10-24, RLM-406, on chapter 1's text): it lights up smoothly while a
    player holding a signet is near ([The sealstone](#the-sealstone)). Its radius (5 blocks), which signets light it
    and its light level (15) are proposals for his playtest.

His answers on chapter 2 (2026-10-25 to 2026-10-27, RLM-351, recorded by RLM-432) added:

20. **Undervault's final boss, Astone, the Unbroken, drops three items** at 100% on every kill: the Stone Heart for
    the Skyhearth Medallion, and the Sun Signet Shard and the Ice Signet Shard for the twins' signets (2026-10-25,
    confirmed 2026-10-27). This answers [open question 2](#open-questions).
21. **The twins' signets take two of each ingredient** around their shard (2026-10-26): the Signet of the Sunwastes 2
    Rubies, 2 Netherite Scraps, 2 Sand and 2 Red Sand; the Signet of the Everwinter 2 Sapphires, 2 Netherite Scraps, 2
    Snow Blocks and 2 Ice ([Realm signets](#realm-signets)). This replaces 16's rule for the twins; the Signet of the
    Undervault keeps it.

His answer to RLM-458's playtest handoff (2026-10-28) added:

22. **A realm's own signet opens a frame inside the realm back onto Overworld** ("the portal in the Undervault would
    get activated by the same signet"; "if a player broke the frame by accident, they would get lock in the
    dimension"), as the sneak gesture, which stays, does. The spec keeps another realm's signet refused there, so no
    portal links two realms (11) ([The sealstone](#the-sealstone)).
23. **The sealstone glows inside a realm too** ("I'd add the glowing effect as well"), for the realm's own signet.
24. **Realms link at 1:8 and pair like nether portals** ("make it exactly the same behavior as per nether, and note
    that the coordinates are 1:8, undervault is basically nether replacement"). This replaces 7's 1:16
    ([The coordinate scale](#the-coordinate-scale)).

## Open questions

Matěj answered every question of the first version (RLM-128). The numbers marked as proposals (the materials, the
stone realm's level rule and its scale, Overworld's arrival `y` range) are checked in his playtests of W2a
and W2b. Two questions are open (3 and 4); questions 1 and 2 are decided:

1. **FTB Teams parties share realm unlocks.** FTB Quests keeps progress per FTB Teams team, so a player who joins
   someone's party (`/ftbteams party`) would get that team's realm unlocks and chapter advancements at their next
   login, an exception to "unlocks are per player, for now". **Decided 2026-10-15 (Matěj's sharing rule, RLM-156;
   built by RLM-206): FTB Teams parties are off in the pack** until our own parties (RLM-68) share quests by that rule
   ([Access and unlocks](#access-and-unlocks), [Multiplayer](#multiplayer)).
2. **What Undervault's final boss drops** (RLM-395, raised on 2026-10-25). Matěj's rule of 2026-10-23 has it drop
   the Stone Heart for the twins' signets. That clashes with his signet rule of 2026-10-24, which puts the realm's
   own shard in the middle of every signet, and with the [realm design](realms.md#the-hub-realm), where the Stone
   Heart goes into the Skyhearth Medallion (RLM-245). **Decided 2026-10-25 (Matěj, RLM-351 chapter 2 question 1,
   RLM-432): the boss, Astone, the Unbroken, drops all three at 100%, every kill:** the Stone Heart for the medallion,
   and two shards, the Sun Signet Shard and the Ice Signet Shard, one for each twin signet (decision 20).
3. **The signet after the twins** needs the guaranteed drop of both twin bosses
   ([Realm signets](#realm-signets)), but the recipe rule has one shard in the middle. Proposal: each twin boss drops
   one half of the next realm's shard, and the two halves craft into the shard. It matters with M4's twin realms.
4. **A lost Signet of the Undervault in M1.** M1 has neither the Illusioner (M2, RLM-321) nor the Lodge Merchant
   (M2, in RLM-407's own description), so its signet has only the one quest reward, and the signet source check
   ([Data](#data-contentrealms)) asks for a renewable source. Proposal: M1 already gets the shop's one signet quest
   (50 copper, up to 3 times, unlocked by The Ominous Chief), since coins (RLM-284, labelled `m1`) and the quest
   pipeline (Q1) are M1 work; the rest of the tab stays M2.
