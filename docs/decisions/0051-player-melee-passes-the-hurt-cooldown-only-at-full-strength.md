# 0051. A player's melee hit passes the hurt cooldown only at full attack strength

Date: 2026-10-23

Status: Accepted (amends [0049](0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md))

## Context

[0049](0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md) let every melee hit and projectile of a player
through an open hurt cooldown window, with no condition on the attack, so that a player's damage never loses anything
to the window (Matěj, 2026-10-19: attack speed 3 and more will be reachable). Iron's RPG Tweaks, which 0049 kept out
of the pack, guarded the same idea with a minimum attack strength (75 % by default).

The independent review of PR #105 (RLM-386) found what the missing guard allows. Better Combat zeroes the window for
its own swings, so the gap is melee with an item that has no Better Combat attributes: spam-clicking it at about 10
clicks a second lands each weak hit in full through the window, each about a fifth of a full hit and with knockback,
about 2.1 times the damage per second of timed full-strength hits. Before 0049 vanilla dropped those hits (no bigger
than the window's `lastHurt`). The empty hand (attack speed 4) gains nothing, so the exposure is narrow but real.

The same review asked about Multishot: a crossbow's three arrows all land in full at one close target, about three
times the damage, where the window used to let one through. Matěj decided on 2026-10-23 to keep that, in line with
"player damage never loses anything to the hurt cooldown".

## Decision

- **A player's melee hit lands through an open window only at full attack strength:** above 0.9, the strength a crit
  needs (`DamagePipeline#landsThroughCooldown`). NeoForge resets the attack strength only at the end of
  `Player#attack`, so the pipeline still reads the swing's own strength.
- **A weaker melee hit meets the window as in vanilla:** no bigger than the window's `lastHurt`, it is dropped; a
  bigger one deals the difference.
- **Projectiles are unchanged:** a player's arrows, bolts and tridents land through the window in full, all three
  arrows of a Multishot volley included (Matěj, 2026-10-23).
- Everything else in 0049 stands: school hits keep their own rule, and mobs' and the world's hits keep vanilla's
  window.

## Consequences

- Timed melee hits still never lose anything to the window, at any attack speed, and spam clicks deal no more than
  vanilla allows. `HurtCooldownGameTests` checks both: a weak hit right after a full one deals nothing (from a
  stronger weapon, only its difference), and a full-strength one in the same window lands in full.
- `DamagePipeline#droppedByCooldown` names the hits the window drops anyway, after this rule, so other rules can leave
  them alone: a raised two-handed weapon no longer spends its block on them (RLM-387, architecture 9.3).
- 0049 keeps its accepted text; its decision on a player's melee hits is read with this ADR.
