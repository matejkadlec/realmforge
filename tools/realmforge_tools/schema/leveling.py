"""Character XP and levels (architecture 10.2): the rules ``character/XpTable`` and ``character/KillXp`` follow in the
game, computed from the curves in ``content/curves/``, so the pipeline can write expectations a GameTest compares
with the game's numbers.

- ``xp_to_next_level`` at level L is the XP that takes a player from L to L + 1 (at least 1).
- A player's level is the highest level whose XP the player has, at most the level cap.
- XP stops at the cap (Matěj, 2026-10-15): a player holds at most the XP of the cap's level, so a gain past it is lost
  and raising the cap starts the player at 0 XP toward the next level.
- ``stat_points_per_level`` at level L is the primary stat points a player gets on reaching L, rounded half up and at
  least 0 (RLM-196). A player of level L has earned the points of every level from 2 to L; level 1 gives none.
- A kill gives ``kill_xp`` at the victim's level, times the victim's kill XP multiplier (its rank,
  ``content/kill_xp_multipliers/``, 1 by default), times ``kill_xp_below_player`` at the levels the victim is below
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
STAT_POINTS_PER_LEVEL = "stat_points_per_level"

# The curves leveling reads, and the smallest value each may hold: a level always costs XP, and neither a kill nor a
# level takes anything away.
LEVELING_CURVES: dict[str, float] = {
    XP_TO_NEXT_LEVEL: 1,
    KILL_XP: 0,
    KILL_XP_BELOW_PLAYER: 0,
    KILL_XP_ABOVE_PLAYER: 0,
    STAT_POINTS_PER_LEVEL: 0,
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


def capped_xp(curve: CurveDef, total_xp: int, cap: int) -> int:
    """The XP a player under ``cap`` holds of ``total_xp``: all of it, at most the XP of the cap's level."""
    return min(total_xp, xp_to_reach(curve, cap))


def stat_points_at(curve: CurveDef, level: int) -> int:
    """The primary stat points reaching ``level`` gives, at least 0; level 1 gives none."""
    return max(0, java_round(curve.value_at(level))) if level > 1 else 0


def stat_points_to(curve: CurveDef, level: int) -> int:
    """The primary stat points a player of ``level`` has earned: those of every level from 2 to ``level``."""
    return sum(stat_points_at(curve, reached) for reached in range(2, level + 1))


def kill_xp(
    base: CurveDef,
    below: CurveDef,
    above: CurveDef,
    victim_level: int,
    player_level: int,
    rank: float,
    multiplier: float,
) -> int:
    """The XP a player of ``player_level`` with XP ``multiplier`` gets for killing a mob of ``victim_level`` whose
    kill XP multiplier is ``rank``, never negative. The factors multiply in this order, as in the game."""
    difference = victim_level - player_level
    factor = below.value_at(-difference) if difference < 0 else above.value_at(difference)
    return max(0, java_round(base.value_at(victim_level) * rank * factor * multiplier))
