# 0036. Room lights and decorations form patterns

Date: 2026-10-17

Status: Accepted

## Context

The room generator (A4d, RLM-121) first lit its rooms greedily: a wall light wherever the most dark cells were,
three blocks from the next. Every room was lit, but in Matěj's gallery review (2026-10-17) the lights looked random:
a single torch at the end of a wall, one torch behind a pillar, a corridor with one torch on one wall. In his words,
the human brain likes patterns and dislikes randomness, so a light out of a pattern feels odd even when nobody can
say why. He extended it to decorations: variety from barrels, torches, cobwebs and vines is good, but it must not
feel out of place; cobwebs almost never do, vines rarely, barrels and torches do.

## Decision

1. **Lights and decorations that a player reads as placed form a pattern.** Randomness may pick which pattern, and
   where cobwebs and vines grow, but never scatters a torch or a barrel on its own.
2. **Wall lights in generated rooms** (`tools/realmforge_tools/rooms/lights.py`):
   - all at one height, one block above the floor players walk on, and never above a marker;
   - along a wall at least two free blocks from the wall's end (a corner, a doorway, the room's entry edge) and at
     least three free blocks between two lights on one wall, spread evenly;
   - opposite walls carry N and N, or N and N + 1 lights (at least one each where both can carry one); a single
     light only in a dead end, or as the last resort where nothing else lights the room;
   - on a wall with alcoves only on the pilasters, facing the room;
   - in a room with pillars only on the pillars: their faces toward the middle; with four pillars in the four
     quarters also one per pillar, each facing another world side, or the inward faces of two opposite pillars;
   - a light on each side of a doorway where no pillar stands within two blocks: all doorways or none (with four,
     also an opposite pair);
   - lanterns from the ceiling in big rooms: none, one in the middle or a pair;
   - among the combinations that light the room, those the room's own symmetry (a mirror or turn that maps its
     doorways onto themselves) maps onto themselves come first, then the fewer lights; the seed picks one.
3. **Markers that stand for things on the ground stand on the ground:** `mob` and `chest` markers stand on full
   blocks of the floor, never on a step, a slab or a balcony. A treasure room has exactly five chest spots, so every
   treasure room gives the same; a chest faces the room's middle (W6 places it).

## Consequences

- A room whose rubble or features leave no pattern that lights it is refused by the generator; its seed never
  becomes a candidate.
- The import dressing (`rooms/dress.py`) places barrels by rule in the imported rooms; they are checked against
  this decision in a follow-up, RLM-262.
- New placed decorations (W6's chests, banners, later props) follow point 1 from the start.
