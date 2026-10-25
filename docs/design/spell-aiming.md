# Spell aiming

> How a player aims spells: bolts that fly where the crosshair points (RLM-344), ground spells that land at the
> crosshair with clamp cast (RLM-346), and smart auto-aim for the spells that keep a target (RLM-345). Written on
> 2026-10-25 from Matěj's playtest of C3b (RLM-25, `qa2` of 2026-10-21, point 7). The first two are decided and built
> ([ADR 0052](../decisions/0052-spells-aim-with-the-crosshair.md), [architecture
> 11.2](../architecture.md#112-casting-server-authoritative) and [11.3](../architecture.md#113-spell-entities)). The
> auto-aim is a **proposal for Matěj's review**: its rules are his draft with its holes settled, and no code is
> written before he approves this document.

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

## Proposal: auto-aim for targeted spells (RLM-345)

### Which spells stay targeted

A spell stays targeted when its effect lands on one creature without flying there, or when it must reach one:

| Kind | Aims | First spells (C4, architecture 11.7) |
| --- | --- | --- |
| Bolts and missiles that fly | crosshair (RLM-344) | Fireball, Frostbolt |
| Areas on the ground | crosshair, clamp cast (RLM-346) | none of the first spells |
| Melee abilities that hit an area in front | the caster's facing (RLM-399) | Slam, Reckless Strike, Shield Bash |
| Spells on one enemy: hits, debuffs, homing missiles, a charge | **auto-aim** | Arcane Missiles (Q5) |
| Heals and buffs on one ally | **auto-aim**, else the caster | none of the first spells |

### Candidates

1. **Enemies:** living creatures that are not the caster's allies and can be damaged, within the spell's range (16
   blocks for most) and in its line of sight, on the screen.
2. **Hostile before passive:** auto-aim picks only hostile mobs and mobs angry at the player (a wolf or an iron golem
   that attacks). A passive or calm neutral mob (a cow, a villager, a calm enderman) is a target only when the
   crosshair is right on it, so an auto-aimed spell never jumps to a cow beside the fight. (Q6)
3. **Not picked:** invisible mobs, and mobs the player cannot see on the screen (behind them, off screen).

### The cast zone and direct aim

1. **Direct aim first:** a mob right under the crosshair, today's picking, is the target, whatever the rules below
   say. It is the one way to choose among several mobs in the cast zone. (Q1)
2. **The cast zone:** the screen in three vertical strips of the same width, the full height of the screen. While a
   hostile candidate is in the middle strip, the target comes from the middle strip only.
3. **Otherwise** the target comes from every candidate on the screen, the side strips included.

### Choosing among candidates

The draft says both "in order" and "they add up", which disagree (Q2). Proposed: the rules go **in order**, each
deciding only between candidates the rules above it tie, which keeps the choice predictable. The draft's example (a
boss's add close to death beats the boss) holds because the boss's adds rank above the boss while they are the point
of the fight:

1. adds the boss fight wants killed first: while the boss is invulnerable, or while its phase asks the player to
   clear them (the boss framework, W5, says which);
2. a boss;
3. close to death: below 25 % of its maximum health;
4. the current target: the mob the player's last targeted spell aimed at, or that the player last hit, within the
   last 10 s (the in-combat time, RLM-348);
5. a higher rank: a higher kill XP multiplier (the XP model's rank, architecture 10.2);
6. a higher level;
7. **added:** nearest to the crosshair (the smallest angle from the look), so a choice is always made.

An invulnerable boss is no candidate at all (it cannot be damaged), so rule 1's first case also follows from the
candidate rules.

### Friendly spells

1. **Direct aim first:** an ally right under the crosshair.
2. **Heals:** otherwise the injured ally in the cast zone with the lowest share of their health; an ally at full
   health is skipped, so a heal is never wasted while the caster bleeds. Then the caster.
3. **Buffs and cleanses:** otherwise the ally in the cast zone nearest to the crosshair, then the caster.

### Sticky target

1. A cast-time or channelled spell keeps the target it picked until the cast ends, even when the target leaves the
   zone, the screen or the spell's range.
2. The cast is interrupted when the target gets more than twice the spell's range away (32 blocks for a 16-block
   spell, the draft's number), checked every tick as the Hewn Chanter's kept range is.
3. Line of sight is still checked when the effects land: a target behind a wall at the end fails the cast ("No line
   of sight"), unless the spell locks on (homing missiles do).
4. **A target that dies during the cast:** the spell picks a new target with the same rules when its effects land,
   instead of failing, so a friend's kill never wastes the Fireball you were about to finish. With none, the cast
   fails as "Target lost". (Q3)

### The highlight

1. **During a cast:** a circle on the ground under the sticky target, in the spell school's colour, until the cast
   ends.
2. **Before a cast:** a fainter circle under the mob an enemy spell would pick now, at the usual 16 blocks, so the
   player sees the target before pressing a key. (Q4)
3. The circle is drawn by the client and is purely visual; its art comes with the spell visuals (C6).

### Built so

- The client picks the target, since only it knows the screen (field of view, aspect ratio). It sends the entity in
  `CastSpellPayload`, as today.
- The server checks what it can: the range, line of sight, an enemy that can be damaged (or an ally), and that the
  target lies within the widest view a client can have, so a modified client cannot target behind the player.
- The ranking is a pure function over what the client knows of each candidate (health share, boss and add flags, the
  current target, rank, level, angle), with JUnit for the ranking, the cast zone and the sticky target. A GameTest
  covers the 32-block interrupt.
- Mob health is synced already, and so are mob levels. The boss and add flags come with W5's boss framework.

## Open questions for Matěj

1. **Direct aim:** should a mob right under the crosshair always win, as proposed? Without it, the player cannot
   choose between two mobs in the middle strip.
2. **In order or added up?** Proposed: in order, each rule deciding only ties of the rules above, so the player can
   tell why a mob was picked. Added up as points (say 6 for a boss down to 1 for a higher level) would let a current
   target of a higher rank beat a mob close to death.
3. **A target that dies mid-cast:** pick a new one (proposed), or fail the cast as today?
4. **The highlight before a cast:** show the faint circle all the time (proposed), only while a spell key is held, or
   only during a cast?
5. **The split:** are Fireball and Frostbolt crosshair bolts, and Arcane Missiles an auto-aimed channel of homing
   missiles, as proposed? Or are Arcane Missiles crosshair missiles too?
6. **Passive mobs:** auto-aim skips them unless the crosshair is right on them (proposed). Should a cow under the
   crosshair be hit at all, or never by a targeted spell?
