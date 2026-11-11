# 0077. An instant spell's animation is a player's cast, never an aura's toggle, and it has one length

Date: 2026-11-11

Status: Accepted (amends [0073](0073-spell-visuals-are-reusable-parts-in-data.md): decision 8, which instant spells
play the animation and block melee)

## Context

[0073](0073-spell-visuals-are-reusable-parts-in-data.md) decision 8 says cast animations are "started by
`CastStartedPayload`, which every instant spell now sends too, marked as an instant: a 0.5 s push of both hands, or a
weapon move, with no cast bar, and it ends after the animation's length", and that no melee is possible during it,
from Matěj's answers of 2026-10-29 (RLM-463): "instant spells get a quick animation too, about 0.5 s (the global
cooldown): both hands push forward and back. There is no cast, it cannot be interrupted, and the spell fires at once"
and "Melee during an instant spell's animation: blocked".

Building it (RLM-641, the last part of RLM-439) met four cases that "every instant spell" does not settle:

- **Auras** (RLM-595, merged after the design was approved): Battle Stance names no cast in its data, so its cast
  type is the default, `instant`, but `SpellCaster` toggles it instead of casting it: free, off the global cooldown,
  and "beside any cast in progress".
- **Mobs and procs:** a mob's instant spell and a proc's spell (`SpellCaster#castProc`) also run "at once".
- **The length:** the server has to block melee for "the animation's length", but an animation is a client asset the
  server cannot read.
- **The release** (the short push as a formed projectile leaves) and a spell's own animation, which the design names
  but 0073 does not decide.

The playtest of this part is deferred (Matěj, 2026-11-10, the `flow1 local` marathon), so these choices are the
agent's, and RLM-641's deferred playtest ticket lists them as his open questions.

## Decision

1. **The animation is a player's own cast.** A player's instant spell that is cast through `SpellCaster#cast` plays
   it and blocks their melee. Not:
   - **an aura's toggle.** It is toggled, not cast, and is off the global cooldown whose length the push has
     (Matěj's "about 0.5 s (the global cooldown)"), so a Warrior changing stance in a fight would lose half a second
     of melee for every change; and it may be toggled during a cast, where a push would take the arms out of the
     cast's pose. This is the one point that narrows 0073's "every instant spell";
   - **a proc's spell,** which nobody's hands cast;
   - **a mob's instant spell:** a mob has no arm layer for it, and the melee block is about `Player#attack`;
   - **a swing** (RLM-399), which stays a cast of its own with its weapon move, as 0073 says ("or a weapon move").
2. **One length for every instant spell: 10 ticks** (`SpellCast#PUSH_TICKS`), half a second, the global cooldown's
   default length. The server blocks melee for it and sends it in the payload; the default animation
   (`realmforge:instant`) lasts exactly that, which a test keeps true, and validation refuses an instant spell's own
   animation that loops or lasts longer. Rejected: a length per animation in the spell's data, checked against the
   file, as swings have. That is needed where a hit moment depends on it; here every instant spell would carry a
   number that is always 10.
3. **It is no cast in progress,** on either side: the `combat` attachment keeps only the window's end
   (`meleeBlockedUntil`, transient, synced to the owner), not a current cast, and the client plays the animation
   without storing a cast. So it shows no cast bar, a second spell is never refused for it, and no end is sent: the
   client lowers the arms when the length has passed. A player who starts tracking the caster during those 10 ticks
   is not told of it.
4. **The release is the client's alone.** When a cast time that formed a projectile completes, the client plays
   `realmforge:release` for 5 ticks before it lowers the arms. The server sends nothing for it and blocks no melee:
   the cast is over, and its own no-melee window ended with it.
5. **A cast may name its own animation,** `cast.player_animation`, an animation of the core mod's
   `player_animations/` by Player Animation Library's id. It replaces the cast type's own (`cast`, `channel`,
   `instant`); validation refuses one that is missing or does not fit the cast (a cast time's or a channel's must
   loop). A swing names none until RLM-444 gives swings their weapon moves.

## Consequences

- The Warrior's instant abilities that are no swings (Execute, Overpower, Rend, Victory Rush, Charge, Battle Shout)
  now cost their caster half a second of auto-attacks each, as Matěj decided for every instant spell, and play the
  two-handed push with a weapon in hand until RLM-444 and RLM-592 give the Warrior's spells their weapon moves. The
  Mage's (Ice Lance, Frost Nova, Cone of Cold, Ice Barrier) and the Human's Will To Survive play it too.
- A held attack key neither swings nor breaks a block for those 10 ticks, as during a cast.
- Battle Stance and later auras change at once, with no animation at all. If Matěj wants one, it needs a way to play
  beside a cast in progress, which the single cast layer does not have.
- A new instant spell needs nothing for its animation; a spell that wants another names it in its data.
- 0073's decision 8 otherwise stands: the cast layer, the payload marked as an instant, the three parts of the melee
  block and the window in the `combat` attachment.
