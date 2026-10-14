"""Character XP and levels (architecture 10.2): the rules ``character/XpTable`` and ``character/KillXp`` follow in the
game, computed from the curves in ``content/curves/``, so the pipeline can write expectations a GameTest compares
with the game's numbers.

- ``xp_to_next_level`` at level L is the XP that takes a player from L to L + 1 (at least 1).
- A player's level is the highest level whose XP the player has, at most the level cap; XP beyond the cap keeps
  accumulating, so raising the cap levels the player up at once.
- A kill gives ``kill_xp`` at the victim's level, times ``kill_xp_below_player`` at the levels the victim is below
  the player (or ``kill_xp_above_player`` at the levels it is above), times the player's XP multiplier.

Rounding follows Java's ``Math.round``: half up.
"""

from __future__ import annotations

import math

from realmforge_tools.schema.curve import CurveDef

XP_TO_NEXT_LEVEL = "xp_to_next_level"
KILL_XP = "kill_xp"
KILL_XP_BELOW_PLAYER = "kill_xp_below_player"
KILL_XP_ABOVE_PLAYER = "kill_xp_above_player"

# The curves leveling reads, and the smallest value each may hold: a level always costs XP, and no kill takes XP away.
LEVELING_CURVES: dict[str, float] = {
    XP_TO_NEXT_LEVEL: 1,
    KILL_XP: 0,
    KILL_XP_BELOW_PLAYER: 0,
    KILL_XP_ABOVE_PLAYER: 0,
}


def java_round(value: float) -> int:
    """Java's ``Math.round``: the closest integer, halves rounded up."""
    return math.floor(value + 0.5)


def xp_to_next(curve: CurveDef, level: int) -> int:
    """The XP from ``level`` to the next one, at least 1."""
    return max(1, java_round(curve.value_at(level)))


def xp_to_reach(curve: CurveDef, level: int) -> int:
    """The total XP a player needs for ``level``: 0 for level 1."""
    return sum(xp_to_next(curve, below) for below in range(1, level))


def level_for(curve: CurveDef, total_xp: int, cap: int) -> int:
    """The level of a player with ``total_xp``, at most ``cap``."""
    level = 1
    needed = 0
    while level < cap:
        needed += xp_to_next(curve, level)
        if total_xp < needed:
            break
        level += 1
    return level


def kill_xp(
    base: CurveDef, below: CurveDef, above: CurveDef, victim_level: int, player_level: int, multiplier: float
) -> int:
    """The XP a player of ``player_level`` gets for killing a mob of ``victim_level``, never negative."""
    difference = victim_level - player_level
    factor = below.value_at(-difference) if difference < 0 else above.value_at(difference)
    return max(0, java_round(base.value_at(victim_level) * factor * multiplier))
