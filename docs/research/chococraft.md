# Chococraft and mount tiers

> Research of 2026-10-12 (RLM-101): can the Chococraft mod carry Matěj's mount tiers (better tiers locked by
> chapter, flight from chapter 2, quest rewards instead of breeding), or is our own mount system simpler? Facts come
> from the NeoForge 1.21.1 jar (Chococraft 0.11.12, read with `javap` in a scratch directory; the jar is not in the
> repository), its `neoforge.mods.toml`, its data files, the licence files of its source repository and Modrinth.
> Mechanics only: no code, asset or name of Chococraft goes into Realmforge without its licence allowing it.
> **Decided 2026-10-12 and 2026-10-13 (RLM-115): option B, our own mounts**, with the tier table as proposed, the
> mount system and tiers 1 to 3 in M4, and elytra faster in the air while mounts win on everything else
> ([Decision](#decision-rlm-115)).

**In short.** Chococraft 0.11.12 runs on NeoForge 1.21.1 and needs only Architectury API, which the pack already
has. Its code is MIT. Its chocobos, models, animations, flight, saddle bags and taming all exist, so a first
version of mounts in the pack would be cheap. But its tiers are ten **hard-coded colours**: speed, health,
flight and the rider effects are fixed per colour in Java, and the config only switches flight on or off for every
chocobo. Matěj's table (a slow flier in chapter 2, a faster one in chapter 3, the fastest in chapter 4, then the
same speed plus a resistance) needs code that reaches into Chococraft's internals, which the architecture forbids
(principle 8). A chocobo also lives in the world, can die and must be tamed, while the vision and the M6 outline
want mounts that are summoned when used. And Chococraft's tiers would make "chocobo" and "gysahl", names from
Final Fantasy, part of Realmforge's progression and quest texts. The recommendation is our own mount system, built
with Chococraft's MIT code as a reference for riding and flight ([Proposal](#proposal-for-matěj)).

## Facts

| What | Value |
| --- | --- |
| Project | Chococraft 4 by Mrbysco (with Xalcon, Slayer5934, balika011), Modrinth `chococraft`, CurseForge project 225280, source [Mrbysco/ChocoCraft4](https://github.com/Mrbysco/ChocoCraft4) (branch `arch/1.21`) |
| NeoForge 1.21.1 build | 0.11.12, release of 2026-03-01; file `chococraft-1.21.1-neoforge-0.11.12.jar`, 669 KB, SHA-1 `b9ef3d215d08348b2b29cf58b9683a9a9c81ac05` (matches Modrinth) |
| Requires | Minecraft `[1.21,1.22)`, NeoForge `[21.0.75-beta,)`, Architectury API `[13.0.4,)` (the pack has 13.0.11). Patchouli is optional (only for the Chocopedia book item) |
| Sides | both (Modrinth: client and server required) |
| Licence | code **MIT** (`LICENSE-code.md`, also `license="MIT"` in the jar), assets **CC BY-SA 4.0** (`LICENSE-art.md`), except the chocobo "kweh" sound, which the licence file names as taken from Final Fantasy 7 and owned by Square Enix. Modrinth lists the project as All Rights Reserved; the repository's licence files say otherwise |
| Names | chocobo, gysahl and Chocopedia come from Final Fantasy (Square Enix) |
| Newer lines | 1.21.4 to 1.21.11 and 26.1.2 on other branches; the 1.21.1 line still gets fixes (0.11.12 corrected a config translation) |

## How Chococraft works

### One entity, ten colours

There is one entity type, `chococraft:chocobo` (a `TamableAnimal`). Its colour, saved as `Color` and synced to
clients, decides everything else through `ChocoboColor`, an enum whose abilities are set in Java:

| Colour | Movement speed | Flight (airborne speed) | Max health | Abilities | Rider gets |
| --- | --- | --- | --- | --- | --- |
| Yellow | 0.20 | no | 30 | | |
| Green | 0.27 | no | 30 | step 2 | |
| Blue | 0.27 | no | 30 | walks on water | Water Breathing |
| White | 0.35 | no | 40 | climbs, walks on water, step 2 | |
| Black | 0.40 | no | 40 | climbs, walks on water, step 2 | Night Vision |
| Gold | 0.50 | 0.25 | 50 | climbs, walks on water, fire-immune, step 2 | |
| Pink | 0.50 | 0.30 | 50 | climbs, walks on water, step 2 | |
| Red | 0.50 | 0.35 | 50 | climbs, walks on water, step 2 | |
| Purple | 0.40 | 0.25 | 50 | climbs, fire-immune | Fire Resistance |
| Flame | 0.40 | 0.25 | 50 | climbs, fire-immune | Fire Resistance |

- **Step 2:** steps up two blocks when ridden; the other colours step up one.
- **Movement speed** is the vanilla `minecraft:generic.movement_speed` base value, set when the chocobo spawns
  (the enum value divided by 100). A ridden chocobo moves like a ridden horse (`travel` sets its speed from the
  attribute), so the numbers compare directly: a vanilla horse rolls 0.1125–0.3375
  (`AbstractHorse#generateSpeed`, 0.225 on average). Yellow is a slightly slow horse; Gold, Pink and Red are
  about 1.5 times the best possible horse.
- **Flight** is not an attribute at runtime: `travel` reads the colour's airborne speed directly, so flight speed
  cannot be changed with attribute modifiers. Red flies 40% faster than Gold, which flies like Purple and Flame.
  The golden chocobo is the all-rounder (fire-immune, climbs, walks on water), not the fastest flier.
- **Rider effects** are reapplied every tick, to the rider and to the chocobo.

### Flight

- A colour that can fly flies while ridden, when its saved `AllowedFlight` flag is set and the rider holds jump:
  every tick it jumps again and accelerates forward by the airborne speed / 100. There is no stamina, no ceiling
  and no landing rule; releasing jump lets it fall.
- `AllowedFlight` is written at spawn from the one flight setting in the config (`canChocobosFly`, default true),
  so the config turns flight on or off for every chocobo spawned after the change. It is saved per entity, so a
  command (`/data`) can change one chocobo.
- The rider cannot dismount while the chocobo is in the air (`RidingEventHandler` cancels the dismount unless it
  is on the ground or in water), which prevents falls.
- How fast this is in blocks per second was not measured; any adaptation measures it in play first.

### Getting one

- **Wild chocobos** spawn through biome modifiers in `#c:is_plains`, `#c:is_mountain` and `#minecraft:is_nether`
  (weight 10, packs of 1–3, both in the config). Every wild one is yellow, except in Nether biomes, where it is
  Flame. With the Nether removed (RLM-47), Flame never spawns. Our realm biomes get chocobos only if they carry one
  of those tags.
- **Taming:** feed gysahl greens; each green tames with a chance of 15% (`tameChance`). Gysahl greens generate in
  every `#minecraft:is_overworld` biome and grow as a crop. A saddle (crafted) makes it rideable; saddle bags and
  the saddle pack add an inventory; the whistle, used on a tamed chocobo, switches it between follow, stay and
  wander.
- **Spawn eggs:** one per colour (`chococraft:<colour>_chocobo_spawn_egg`). It is Chococraft's own item, not a
  vanilla spawn egg: it spawns an untamed adult (a baby when the player sneaks) and ignores any entity data on the
  stack. A spawn egg as a quest reward therefore still needs taming.
- **Breeding:** two chocobos fed a loverly or a golden gysahl breed; the child's colour comes from a rules file per
  pair of parent colours, with conditions ("both parents fed a golden gysahl") and random thresholds. The golden
  gysahl drops with 5% from a mature gysahl crop, the loverly one with 15%. Two purple parents drop a purple spawn
  egg instead of a child. Matěj's "golden chocobo by breeding on day 3" is this chain.

### What can be changed without code

| Lever | Where | Changes |
| --- | --- | --- |
| Common config | `config/chococraft-common.toml` (NeoForge config) | flight on or off for all, tame chance, spawn weight and pack size, auto-naming and the name lists, ambient sound interval |
| Breeding rules | `config/chococraft-4-breeding.json`, written from the jar's default on first start and reloaded on `/reload` | which colour two parents produce, under which condition and with which chance |
| Data pack | recipes, loot tables, biome modifiers (`data/chococraft/...`) | how saddles, whistles and gysahl foods are crafted, where chocobos and gysahl greens spawn, what a dead chocobo drops |

Hard-coded, so only code can change it: the colours, their speeds, health, flight and abilities, the rider
effects, the flight controls, taming by chance, and that a chocobo is a persistent world entity. `ChocoboColor`
exposes its `ChocoboAbilityInfo` with public setters, so a compatibility layer could rewrite the numbers at
startup, but that is another mod's internals, not an API.

## Matěj's tier table against Chococraft

His proposal (RLM-101): tiers unlock by chapter or quest; breeding never yields a tier above the player's highest
unlocked one; a quest reward gives the spawn egg of each new tier.

| Tier | Proposal | Closest colour | What it would take |
| --- | --- | --- | --- |
| 1 (chapter 1) | does not fly | Yellow (wild, tameable from day one) | nothing |
| 2 (chapter 2) | flies slowly, not super slowly | Gold (0.25) | nothing, but Gold is also fire-immune, which tier 5 is meant to bring |
| 3 (chapter 3) | flies faster | Pink (0.30) | nothing |
| 4 | the fastest | Red (0.35) | only 40% above tier 2; a real spread needs new numbers (internals) |
| 5 | as fast as tier 4, plus fire resistance | Purple or Flame (rider gets Fire Resistance) | they fly like tier 2 and walk slower than tier 4 (0.40); matching tier 4 needs new numbers (internals) |
| 6 | as fast as tier 4, plus a later chapter's debuff resistance | none | a new rider effect for one colour (internals) |

The rest of the proposal:

- **Breeding never upgrades a tier:** possible with the breeding file alone, by making every child take a
  parent's colour. Breeding then only makes more of a tier the player already has.
- **Locked per player:** in multiplayer, a player could ride a tier given by a teammate. A small listener in our
  mod can refuse the mount (`EntityMountEvent` is cancellable) when the rider's chapter is below the chocobo's
  tier, reading the colour from the entity's saved data.
- **The quest reward:** an FTB Quests item reward of the tier's spawn egg works without code, but the player must
  then tame the chocobo (15% per green). A tamed reward needs our own item that spawns it through Chococraft's
  classes (internals again).
- **Summoned when used, hidden otherwise** ([vision](../vision.md),
  [M6 outline](../roadmap.md#m6-companions-and-mounts)): Chococraft has no such mode. Chocobos stay where they were
  left, can be killed and are then gone; the whistle is used on the chocobo itself, so it cannot call one from afar.

### Against our own rules

- **[ADR 0004](../decisions/0004-build-rpg-systems-in-house.md):** RPG systems are built in-house; third-party mods
  are QoL, UI, performance, libraries and four big mods taken unchanged, and nothing is forked. Mount tiers that
  gate chapters are progression, so taking Chococraft for them needs a new ADR that adds it as a fifth mod taken as
  it is.
- **[Architecture principle 8](../architecture.md#2-principles):** integrate through advancements, statistics, data
  files and public APIs, never another mod's internals. Config, data and standard events reach tiers 1 to 3 as
  above; tiers 4 to 6 as proposed do not.
- **Names:** quest texts and rewards would name chocobos and gysahl greens, so Final Fantasy names would become
  part of Realmforge's story. Our own rules keep other games' names out of Realmforge content (the reference games'
  names in [reference-games.md](reference-games.md#reference-games-at-a-glance), and "never use names from other
  games" in the lore task RLM-43). A third-party mod's own item names are not covered by either; the decision
  settles whether quests may use them.
- **Replacing it later** (Chococraft now, our mounts in M6) would strand every player's chocobos and their tiers.

### What our own mounts would reuse

Chococraft's code is MIT, which allows copying and adapting it into our MPL-2.0 code as long as its copyright and
licence notice stay with the copied parts ([ADR 0009](../decisions/0009-licensing.md)). Its riding and flight logic
(`AbstractChocobo#travel`, the dismount guard) is a tested reference for ours. Its assets are CC BY-SA 4.0, whose
share-alike clause conflicts with our CC BY 4.0 assets, and its sound belongs to Square Enix: neither is reused.

## Proposal for Matěj

### Options

| | A: Chococraft in the pack | B: our own mounts |
| --- | --- | --- |
| What ships | Chococraft unchanged; a breeding file where children keep a parent's colour; a mount lock per chapter in `compat/chococraft/`; spawn eggs as quest rewards | a `realmforge:mount` registry from `content/mounts/`: tier, unlock requirement, ground and flight speed, abilities as our status effects, model; a mount summoned by a key or spell and dismissed on dismount |
| Tiers | 1 to 3 as proposed; 4 only 40% faster than 2; 5 slower than 4; no tier 6 | all six as proposed, numbers in data and balanced with the rest |
| Model and animations | done (chocobo, CC BY-SA, Final Fantasy names) | new GeckoLib models per mount family, reviewed by Matěj (like A2) |
| Behaviour | world entity: tame, saddle, feed, can die, left where it was dismounted | summoned when used, cannot be lost, faster than Speed II on the ground, flying tiers measured against elytra |
| Rules | new ADR beside ADR 0004; principle 8 kept only for tiers 1 to 3 | fits ADR 0004 and the architecture as they stand |
| Size | S to M: the pack entry, the breeding file, the mount lock, rewards, a pack-boot check, a playtest | L, split: the mount registry and summoning with one ground tier; flight; the remaining tiers with their models |

**Recommendation: B.** A would be quicker to play with, but it delivers half of the tier table, contradicts the
summoned-mount model the vision asks for, needs an exception to ADR 0004, and makes a later move to our own mounts
costly for every player. B reaches every point of the proposal, keeps progression in our data and can borrow
Chococraft's MIT riding and flight code.

### Tier table (for B; numbers are starting points for balance)

Ground speed uses the vanilla movement-speed scale of ridden animals (a horse rolls 0.1125–0.3375). The vision
asks ground mounts to beat a Speed II potion and flying mounts to beat elytra; the flying numbers are set once the
first flier can be measured in play.

| Tier | Unlock | Ground speed | Flight | Extra |
| --- | --- | --- | --- | --- |
| 1 | chapter 1 quest | 0.25 (a good horse) | none | |
| 2 | chapter 2 quest | 0.30 | slow: clearly faster than walking, slower than elytra | |
| 3 | chapter 3 quest | 0.35 | faster | |
| 4 | chapter 4 quest | 0.40 | the fastest | |
| 5 | the fire realm's chapter | as tier 4 | as tier 4 | the rider ignores fire and lava damage while mounted |
| 6 | a later chapter | as tier 4 | as tier 4 | the rider resists that chapter's debuff |

- **Unlock:** each tier is a `chapter_completed` requirement ([architecture 5.3](../architecture.md#53-type-registries))
  checked when the player summons; a quest reward grants the mount itself (an entry in the player's mount
  collection), so nothing can be lost or traded past the player's chapter.
- **No breeding:** tiers come only from progression, which removes the "breeding never yields a higher tier" rule
  instead of enforcing it.

### Questions the decision answers

1. **A or B** (or B with Chococraft's chocobo model as inspiration only).
2. **The tier table** above: tiers, unlocks, the extras of tiers 5 and 6.
3. **Milestone:** mounts are in M6 today. A flying tier in chapter 2 means mounts arrive with chapters 2 and 3, in
   M4. Options: move the first part (registry, summoning, tiers 1 and 2) into M4 and keep tiers 3 to 6 with M6; or
   keep all mounts in M6 and give chapter 2 another way to fly (wings or a teleport spell, TR-8 in
   [reference games](reference-games.md#10-travel-and-qol-tr-cte2s-cheap-travel-hytales-gadgets-without-the-gates)).
4. **Mounts against elytra:** the vision says flying mounts beat elytra; the decision on RLM-47 says elytra (own
   slot, chapter 4–5) are "a little better than a mount". Which one holds for tier 4?

## Decision (RLM-115)

Matěj decided on 2026-10-12 and 2026-10-13; the ticket's comments hold his words.

1. **B, our own mounts.** A `realmforge:mount` registry from `content/mounts/`; a mount is summoned when used and
   cannot be lost. Chococraft does not go into the pack.
2. **The tier table** above is approved as proposed. Its numbers are starting points for balance.
3. **Each tier ships with the chapter that unlocks it** (delegated to Claude by Matěj, 2026-10-13):
   - **M4**, which brings chapters 2 and 3: the registry and content type, the player's mount collection, summon and
     dismiss, ground riding with attacks from the mount, flight, and tiers 1 to 3. Tier 1 becomes the reward of a
     chapter 1 quest at the same time.
   - **M6:** tiers 4 to 6 with their extras (tier 6 needs the debuff and tenacity system) and the optional mount
     journal. If chapter 4 is planned earlier than M6, tier 4 moves with it.
   - Not M2: it brings no chapter, so its only part could be ground riding with tier 1, which helps little in M1's
     cave realm. Flight, the riskiest part, is needed from chapter 2, so registry, riding and flight are built and
     tested together once.
4. **Mounts against elytra:**
   - **Pure travel speed in the air:** elytra are faster.
   - **Overall speed, utility and combat:** the mount wins clearly. Elytra use up rockets, and a player gliding on
     them can hardly fight; a mount costs a summon but uses nothing, gives buffs, and its rider can fight.
   - **New requirement:** the rider can attack from the mount, as from a vanilla horse.

   This settles the same question in [ADR 0025](../decisions/0025-remove-the-nether-and-end.md) ("Elytra and flying
   mounts"), the [realm design](../design/realms.md#open-questions) (open question 7) and the
   [vision](../vision.md) (flying mounts against elytra). Elytra come from chapter 3's final boss (RLM-138).

### Notes for the mount tasks

From the Chococraft 0.11.12 source (branch `arch/1.21` of Mrbysco/ChocoCraft4), recorded with the decision on
RLM-115; the vanilla facts are checked against the 26.1.2 sources.

- **Size** (roadmap sizes): the registry and content type S; the mount collection, granting a mount from a quest
  and the requirement check on summon S; the mount entity (no AI, never saved to the world) and ground riding M;
  summon and dismiss (a spell with a cast time; the mount is gone on dismount, logout, teleport and death, and
  refused where `dimension_rules` say so) S–M; flight M, the riskiest part, whose feel needs a playtest; the
  extras of tiers 5 and 6 S each; art M–L, mostly Matěj's review time (one model per mount family with a texture
  per tier is the cheap way). Roughly five to eight agent sessions of code, art on top.
- **Reference code:** Chococraft's whole riding and flight logic is about 60 lines (`AbstractChocobo#travel`), plus
  a dismount guard of about 15 (`RidingEventHandler`) and the rider-effect tick. It is MIT, so adapted parts keep
  its copyright and licence notice; its assets, its sound and its Final Fantasy names stay out.
- **Flight:** a dedicated server without `allow-flight` kicks a player who floats a ridden vehicle for about 80
  ticks, unless the vehicle is a flying vehicle (`Entity#isFlyingVehicle()`) or has no gravity
  (`ServerGamePacketListenerImpl#getMaximumFlyingTicks` and `clientVehicleIsFloating`); singleplayer allows flight
  (`MinecraftServer#allowFlight`). Our flying tiers override `isFlyingVehicle()` or keep their gravity at zero
  while they fly (`setNoGravity`). Chococraft does neither. 26.1.2's Happy Ghast (`HappyGhast`) is a vanilla
  reference for a ridden flier: it overrides `isFlyingVehicle()` and steers by its rider's input.
- **Jump input:** the mount reads its rider's jump through the public `LivingEntity#isJumping()` in
  `getRiddenInput`, as the Happy Ghast does, so no access transformer is needed (Chococraft widens the protected
  `jumping` field instead).
- **Vanilla riding API** (26.1.2): `getControllingPassenger`, `travel`, `tickRidden`, `getRiddenInput`,
  `getRiddenSpeed`, `positionRider`, `getPassengerAttachmentPoint`.
