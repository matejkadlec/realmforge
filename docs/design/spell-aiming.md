# Spell aiming

> How a player aims spells: bolts that fly where the crosshair points (RLM-344), ground spells that land at the
> crosshair with clamp cast (RLM-346), and aim assistance for every spell aimed at an enemy (RLM-345). Written on
> 2026-10-25 from Matěj's playtest of C3b (RLM-25, `qa2` of 2026-10-21, point 7); the aim assistance from his answers
> of 2026-10-27 to this document's proposal (Q1 to Q6) and four follow-ups. All three are decided and built
> ([ADR 0052](../decisions/0052-spells-aim-with-the-crosshair.md),
> [ADR 0055](../decisions/0055-aim-assistance-for-a-players-enemy-spells.md), [architecture
> 11.2](../architecture.md#112-casting-server-authoritative) and [11.3](../architecture.md#113-spell-entities)); the
> Highlight Spell Target key and the "Target lost" text came with RLM-468.

## Goals

Matěj, 2026-10-21:

- "Cast anytime, aim with the cursor (= the middle of the screen)", like Iron's Spells 'n Spellbooks, not WoW's
  targeting: "casting anytime and aiming feels way more natural and smooth" in Minecraft.
- Some spells keep a target even in Iron's Spells. Those are auto-aimed, "similar to how Hades II does it", with the
  target highlighted by a circle under the mob. His rules are "just a draft and probably have some weaknesses or holes
  … but it has the kind of direction I'd like".
- Ground spells such as Flamestrike or Blizzard land at the crosshair within 16 blocks and clamp beyond it, as League
  of Legends does.

## Decided: bolts at the crosshair (RLM-344)

- A direction spell (a bolt, a missile) casts at any time and flies where the crosshair points. A cast time follows
  the crosshair until the cast completes, and a channel shoots each tick where it points then.
- A bolt flies straight, without gravity, and as far as the player can see: it has no lifetime unless its spell sets
  one.
- It hits the first mob in its way that can be damaged, hostile or passive alike. It passes through the caster's
  allies (other players, the caster's pets). A block stops it, and so does a mob that cannot be damaged, untouched.
- Nothing flies forever: a bolt is removed above the build height, beyond where the world moves (the simulation
  distance, the render distance by default) and in the void.

## Decided: ground spells and clamp cast (RLM-346)

- A ground spell needs no target and is never refused for range. It lands on the block the crosshair hits within its
  range, 16 blocks for the first spells.
- Aimed farther, or at the sky, it lands at the farthest point in range along the crosshair's line.
- Either way it lands on the ground below that point, since it comes down from above: aimed at a wall, at the wall's
  foot; at a ceiling, on the floor beneath it; at the air over a drop, on the ground under it. It needs no line of
  sight for that.
- A mob under the crosshair puts it at the mob's feet. The spot is chosen when the cast begins and stays there, as in
  WoW and League of Legends, so a cast-time area is something other players can see coming.

## Decided: aim assistance (RLM-345)

Matěj's answers (2026-10-27) turned the proposal's screen-wide auto-aim into aim assistance, a narrow cone around the
crosshair, for every spell aimed at an enemy, the bolts included (Q5): "A cast aimed slightly beside a mob targets it
anyway, purely for QoL (far shots would be hard without it)". Aimed too far beside it, the spell misses.

### The cone

1. **Direct aim first:** the first mob under the crosshair, in front of every block, is the target, whatever the rules
   below say. It is the one way to choose among several mobs, and it reaches passive mobs too (Q1).
2. **The cone:** otherwise a hostile mob in sight whose hitbox the crosshair misses by at most its distance / 8 is the
   target. That is about 7 degrees around the crosshair, one number that tunes it:

   | Distance | Allowed miss |
   | --- | --- |
   | right next to the player | none: the crosshair must be on the mob |
   | 4 blocks | 0.5 block |
   | 8 blocks | 1 block |
   | 16 blocks | 2 blocks |
   | 32 blocks | 4 blocks |

   Matěj made up these numbers and does not know yet whether they are too strict or too generous: the playtest
   decides.
3. **Reach:** 32 blocks (follow-up 3: "16 seems too restrictive"). Farther away a bolt still flies, unassisted; a
   targeted spell's reach is its range or 32 blocks, whichever is shorter.
4. **Passive and calm neutral mobs** get no assistance at any distance; the player must aim right at them (Q6). A
   neutral mob (a wolf, an iron golem, an enderman) counts as hostile only while it is angry at the player or targets
   them.
5. **No screen-wide cast zone:** the proposal's three vertical strips are dropped (follow-up 2).

### Choosing among mobs in the cone

The rules go **in order**, each breaking only the ties left by those above (Q2), so the player can tell why a mob was
picked:

1. adds the boss fight wants killed first: while the boss is invulnerable, or while its phase asks the player to clear
   them (the boss framework, W5, says which);
2. a boss;
3. close to death: below 25 % of its maximum health;
4. the current target: the mob the player's last targeted spell aimed at, or that the player last hit, within the
   last 10 s (the in-combat time, RLM-348);
5. a higher rank: a higher kill XP multiplier (the XP model's rank, architecture 10.2);
6. a higher level;
7. nearest to the crosshair: the smallest miss for its distance.

### Bolts

- **An assisted bolt curves gently toward its target** (follow-up 1), so it hits a mob on the move: it leaves toward
  the mob and turns toward it by at most 6 degrees a tick. A block still stops it, and it flies on straight once the
  mob is dead, gone or behind it.
- It is assisted as it leaves the caster's hands, from where the player looks then: an instant bolt at the cast, a
  cast time's at its end, a channel's every missile (Arcane Missiles) at its tick.
- **No mob in the cone:** a bolt flies where the crosshair points (RLM-344).

### Targeted spells

- **Enemy spells** take the mob under the crosshair, else the one the cone picks; with none, they are refused ("You
  have no target").
- **Ally spells** (heals, buffs) take the ally under the crosshair, with the same assistance, else the caster
  (follow-up 2).

### Sticky target

For a cast-time or channelled spell that lands on its target:

1. it keeps the target until the cast ends, even when the target leaves the cone or the spell's range;
2. it is interrupted at once when the target gets more than twice the spell's range away (32 blocks for a 16-block
   spell);
3. line of sight is still checked when the effects land; a spell that locks on (a homing missile) keeps its target
   wherever it goes in the caster's dimension, as its data says;
4. a target that dies during the cast fails it: no new target is picked (Q3). The cast bar says "Target lost"
   (RLM-468).

### The target circle

1. **Only during a cast-time or channelled spell** (Q4, follow-up 4), never all the time: with one targeted spell on
   the bar, a circle under mobs all the time would be annoying. It lies under the mob the spell will hit: the cast's
   target, or the mob a bolt would be assisted to if it left now, so it follows the crosshair through Fireball's cast.
2. **The Highlight Spell Target key** (RLM-468; Left Alt by default, rebindable; named so in Matěj's playtest of
   2026-10-29): while it is on, the circle shows under the mob an enemy spell would pick now, without a cast and without
   a targeted spell on the bar. A press switches it on until the next press, or, with the option Highlight Spell
   Target: Hold, it is on while the key is held. In Matěj's words, it "can also help player to understand how the
   targeting works, or to be sure that their targeted spell will land at the target they want, and not e.g. a mob close
   next to it".
3. The circle is purely visual (Matěj's playtest of 2026-10-29): a thin, smooth, very light yellow circle lying flat
   under the mob, as wide as its hitbox and more, with no particles and no animation, the same for every spell, as easy
   to see in the dark as in daylight but lighting nothing around it
   ([ADR 0061](../decisions/0061-the-target-circle-is-a-smooth-light-ring-and-the-highlight-toggles.md)).

### Built so

- **The server picks**, not the client as the proposal had it: the cone needs only the player's look, which the server
  has, and a cast-time or channelled bolt leaves at a moment only the server sees. The client still names the entity
  under its crosshair for a targeted spell (direct aim), and the server picks in the cone when it names none.
- The server checks what a client names: the range, line of sight, an enemy that can be damaged (or an ally), and that
  the target lies in front of the player, the widest view a client can have, so a modified client cannot target behind
  the player.
- The ranking is a pure function over what the server knows of each candidate (the add and boss flags, the health
  share, the current target, rank, level, the miss for its distance), with JUnit for the cone, the ranking and the
  passive-mob rule; GameTests cover assisted bolts beside the crosshair, beyond the cone and after a moving mob, and
  the sticky target.
- The server names the circle's mob to the caster's client during the cast, so the circle shows exactly where the
  spell will go.
