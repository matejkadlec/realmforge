# 0049. Player hits bypass the hurt cooldown in our damage pipeline, not through Iron's RPG Tweaks

Date: 2026-10-23

Status: Accepted (amends [0044](0044-third-party-mods-by-milestone.md))

## Context

[0044](0044-third-party-mods-by-milestone.md) decided that a player's damage never loses anything to vanilla's 10-tick
hurt cooldown, melee included, because attack speed 3 and more will be reachable (Matěj, 2026-10-19). Iron's RPG
Tweaks with only its combat module was to carry it; "if it misbehaves, the fallback is our data pack putting player
attacks, arrows and our spell damage types into `#minecraft:bypasses_cooldown`". PK4 (RLM-311) checked both against
the pack's builds on 2026-10-23.

**Iron's RPG Tweaks 26.1.2-2.3.0** (decompiled, `damage_module/DamageServerEvents`):

- It sets every entity's invulnerable time to its own `invulnerabilityTickCount` (0) after each hit, players
  included, unless `playerDamageMode` is `NONE`.
- To keep lava and fire from hurting every tick, it cancels, at the lowest priority, any hit of a damage type that
  hurt the same entity in the tick before. That drops real player damage: a second player's melee hit one tick after
  the first player's (both `minecraft:player`), and our spell hits, whose damage type is their school's, when two hits
  of one school land on consecutive ticks (two players' fire spells, a bolt and its own burning tick). Our pipeline
  already makes school hits always land (architecture 9.1); the mod would undo that. The config cannot exempt a damage
  type: its `damageTypeSameTickWhitelist` is never read.
- By default it also cancels every player attack below 75 % attack strength (`minimumAttackStrength`).

So the mod misbehaves in the sense 0044 left open: it trades one lost hit for another.

**The tag fallback** has its own hole: arrows have one damage type, `minecraft:arrow`, whoever shoots them, so putting
it into `#minecraft:bypasses_cooldown` would let every skeleton's arrow through a player's window too, which 0044's
"crowds of mobs must not hit every tick" forbids.

**Better Combat** already lets its own swings through: with `allow_fast_attacks` (on by default) it sets the target's
invulnerable time to 0 before each attack. Arrows, tridents and attacks with an item that has no Better Combat
attributes still meet the window.

## Decision

- **Iron's RPG Tweaks stays out of the pack.**
- **Our damage pipeline lets a player's melee hit or projectile through an open hurt cooldown window**
  (`combat/DamagePipeline`, architecture 9.1): when the defender's invulnerable time is above 10, the hit lowers it to
  10, so vanilla deals the hit in full and opens a new window, exactly as for a damage type in
  `#minecraft:bypasses_cooldown`. A hit counts as the player's when its causing entity is a player and it is a melee
  hit or a projectile; school hits keep their own rule (they land and leave the window as it was).
- **Mobs' and the world's hits keep vanilla's window**, on players and on mobs: lava, fire, cactus, a crowd of mobs
  and skeletons' arrows hurt a player no more often than in vanilla.

## Consequences

- Two melee hits of a player 6 ticks apart both land in full, from Better Combat's swings and from any other attack;
  so do a player's arrows and tridents, and two players hitting one mob on consecutive ticks. `HurtCooldownGameTests`
  covers each, and checks that lava, fire, cactus, five zombies and a skeleton's arrows still meet the window.
- No third-party mod changes damage timing; a later pack mod that touches invulnerability ticks needs the same check.
- The data-pack fallback of 0044 is not used: no vanilla damage type changes its tags.
- 0044 keeps its accepted text; its hit invulnerability bullet is read with this ADR.
