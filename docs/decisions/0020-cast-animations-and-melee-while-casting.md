# 0020. Cast animations through playerAnimator, no melee while casting

Date: 2026-10-11

Status: Accepted (settles spike 4, input for decision D4; keeps the Better Combat integration of
[0014](0014-core-mod-architecture.md) free of compile-time dependencies)

## Context

Better Combat, taken unchanged ([0004](0004-build-rpg-systems-in-house.md)), animates melee swings through
playerAnimator, a client library that layers keyframe animations on the player model. M1's casters need cast
animations (task C6) and casting controls (C3b), so spike 4 asked whether our cast animations can play on the
player while Better Combat is installed, and whether the inputs coexist
([roadmap, phase 0](../roadmap.md#phase-0-spikes)). The versions are the pack's: Better Combat 2.4.0,
playerAnimator 2.0.4 (MIT; no longer developed for 1.21.1, and its successor Player Animation Library serves Better
Combat only from 1.21.11) and Cloth Config 15.0.140.

The spike read both jars and ran a prototype in the QA mod (`/qa cast <ticks>`) in a dev client under Xvfb, with and
without Better Combat: first and third person, dual wielding, and attacks before, during and after a cast. Findings
of 2026-10-11:

- **playerAnimator's API** (checked against the 2.0.4 jar): every client player has an `AnimationStack` of layers
  with priorities. `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(id, priority, factory)` gives
  every player its own layer (a `ModifierLayer`) from client setup on. `PlayerAnimationRegistry` loads Emotecraft
  JSON animations from `assets/<namespace>/player_animations/` and names each `<namespace>:<its name field>`.
  `replaceAnimationWithFade` fades from one animation to the next, and `FirstPersonMode.THIRD_PERSON_MODEL` draws
  the animated arms of the third-person model in first person. Layers apply from low to high priority, and an
  animation overrides only the body parts and axes it animates.
- **Better Combat's layers:** it adds four idle-pose layers (priorities 1 to 4) and its attack layer (2000) to
  every client player. A cast layer at 3000 won in every case tried: over the one-handed idle pose, while dual
  wielding (both swords rise), in first person, and in third person from behind and in front. Held items follow
  the animated hands; legs and body keep their usual motion. Without Better Combat, playerAnimator alone plays the
  same animation the same way.
- **Better Combat's disable switch is data:** the entity tag `bettercombat_disabled` (`CombatFlags.DISABLED_TAG`,
  a constant in its API package). Its server copies the tag into a synced player flag every tick. While the flag is
  set, its client starts no swing and cancels the one in progress, its server rejects attack requests, and the
  attack key falls through to vanilla.
- **Every melee hit ends in `Player#attack`**, Better Combat's too (its server calls `ServerPlayer#attack`), which
  posts NeoForge's cancellable `AttackEntityEvent`. On the client, vanilla's attack and block breaking post the
  cancellable `InputEvent.InteractionKeyMappingTriggered`.
- **Measured** on a pig with 1000 health, an iron sword and the attack key held down throughout, Better Combat
  installed: 1000 → 970 before the cast, 970 and 970 during it, 892 after it. Swings stop when a cast starts, the
  one in progress included, and resume on their own afterwards. Without Better Combat: one click before the cast
  hit, four clicks during it did nothing, three clicks after it hit.
- **Inputs:** Better Combat uses only the vanilla attack key (click, and hold to attack) and blocks the use key
  during its own swings. Its two key mappings (Feint, Toggle mine with weapons) are unbound by default.

## Decision

1. **Cast animations use playerAnimator**, a required dependency of the core mod on the client only
   (`side = "CLIENT"` in `neoforge.mods.toml`, added by C6 with the first real animation). Better Combat already
   requires it, so the pack ships it either way, and a dedicated server needs none of it. Every playerAnimator call
   sits in one client package, `client/animation/`, so a later move to Player Animation Library (with a Minecraft
   upgrade) touches one place. Rejected: an optional dependency (casts without animations and two code paths, for a
   library the pack always has); our own model hooks (they would fight playerAnimator's mixins over the same model
   parts whenever Better Combat is installed); GeckoLib (it animates its own entities, not the vanilla player).
2. **One cast layer per player at priority 3000**, above Better Combat's attack layer, registered at client setup.
   Spell animations are Emotecraft JSON files in `assets/realmforge/player_animations/` (the format of Better
   Combat's own 31 animations), named by the spell's animation key. They animate only what the cast needs, usually
   the arms, so walking and Better Combat's poses show through, and they use `THIRD_PERSON_MODEL` in first person.
   They start on `CastStartedPayload` and fade out when the cast ends, fails or is interrupted, for the caster and
   every player tracking them (architecture 11.2).
3. **No melee while casting.** From the start of a cast-time or channelled spell until it ends, fails or is
   interrupted, no melee swing starts and no melee hit lands; instant spells have no such window. Starting a cast
   cancels a swing in progress, and an attack does not interrupt a cast (moving and enemy interrupts do). WotLK
   pauses melee during a cast too, two animations would otherwise fight over the arms, and a caster would get a
   free hit in. Three parts, none with a compile-time dependency on Better Combat:
   - the server cancels `AttackEntityEvent` for a casting player: authoritative, for vanilla and Better Combat
     alike;
   - the server sets `bettercombat_disabled` at cast start and removes it at the end. Entity tags are saved with
     the player, so the player's `combat` attachment records that a cast set the tag: an operator's own tag
     survives a cast, and a login removes a tag that a crash left behind;
   - the client cancels the attack key while the local player casts, so neither a vanilla swing nor block breaking
     shows.
4. **The prototype stays in the QA mod** as a preview tool until C3b and C6 replace it with real casts:
   `/qa cast <ticks>` and `/qa cast stop` (`qa/castanim/`, `qa/client/castanim/`), the test animation
   `realmforge_qa:cast_test`, and `QaCastGameTests` for the server rules. playerAnimator is a QA mod dependency;
   `-PwithBetterCombat` adds Better Combat and Cloth Config to the dev runs, while the default runs and CI stay
   without them.

## Consequences

- C3a and C3b implement point 3 in the core mod, with the "tag set by a cast" flag in the `combat` attachment (the
  prototype uses a marker tag instead). C6 adds playerAnimator to the core mod, `client/animation/` and the real
  animations. The client import check already bans `dev.kosmx.playerAnim` outside `client/` (architecture 18).
- First person needs its own framing: the test pose (arms raised forward) fills most of the screen and brings the
  held sword close to the camera. C6 tunes first-person poses (lower arms, or one hand) and, like Better Combat,
  falls back to `FirstPersonMode.NONE` when a camera mod such as First-person Model or Real Camera is installed;
  the pack has none.
- D4 can keep its proposal of a spell bar on its own keys: it collides neither with Better Combat (attack key only)
  nor with its unbound key mappings. What the use key does during a cast (eating, potions, a shield) is decided
  with C3b.
- The disable tag is a constant in Better Combat's API, not a documented feature: a Better Combat update could
  drop it, and swings would start during casts again (the server would still block their hits). After every Better
  Combat update, `/qa cast` with `-PwithBetterCombat` rechecks it. The fallback is its API call
  `CombatFlags.setAttacksDisabled`, at the cost of a compile-time dependency in `compat/bettercombat/`.
- playerAnimator gets no more fixes on 1.21.1. Its MIT license allows patching or vendoring it if a bug needs it.
- Not covered by the spike: a second client watching the caster (the prototype sends the payload to tracking
  players and to players who start tracking mid-cast, as `CastStartedPayload` will), and shaders (Iris).
