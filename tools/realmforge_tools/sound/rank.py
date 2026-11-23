"""A candidate's score from what can be measured without listening (Matěj, 2026-11-23): an agent that cannot hear
picks by objective checks, and Matěj swaps the picks after listening in the gallery.

The checks: no clipping (in any channel), the length fits the moment (at least 60 % of the length asked for, measured
without the silence around it, and not cut off at the end), little silence before the sound starts, a level well
above the noise, and for a loop a seam without a jump. A
candidate starts at 100 and loses points for each; the reasons are shown next to the score.
"""

from __future__ import annotations

from dataclasses import dataclass

from realmforge_tools.sound.store import Candidate

QUIET_LUFS = -40.0
LATE_START = 0.15
# A sound shorter than this share of the length asked for misses its moment; a shorter one that decays early does not.
SHORT = 0.6
# A sound whose last 50 ms are this loud against its peak was cut off by its length.
CUT_OFF_DB = -30.0


@dataclass(frozen=True)
class Score:
    value: float
    reasons: tuple[str, ...]


def score(candidate: Candidate) -> Score:
    m = candidate.measured
    if m is None:
        return Score(0.0, ("not measured",))
    value = 100.0
    reasons: list[str] = []
    if m.sound_seconds < 0.1:
        value -= 60
        reasons.append("almost no sound")
    if m.clipped_runs:
        value -= min(40.0, 10.0 + 5.0 * m.clipped_runs)
        reasons.append(f"clips ({m.clipped_runs} runs)")
    if m.loudness_lufs < QUIET_LUFS:
        value -= 15
        reasons.append(f"quiet ({m.loudness_lufs:.0f} LUFS)")
    target = candidate.duration_seconds
    if target and not candidate.loop and candidate.kind == "sfx" and m.sound_seconds < SHORT * target:
        value -= min(30.0, 40.0 * (1 - m.sound_seconds / target))
        reasons.append(f"{m.sound_seconds:.2f} s of sound for {target:g} s")
    if not candidate.loop and candidate.kind == "sfx" and m.end_db > CUT_OFF_DB:
        value -= 15
        reasons.append(f"cut off at the end ({m.end_db:.0f} dB)")
    if m.lead_seconds > LATE_START and not candidate.loop:
        value -= 5
        reasons.append(f"starts after {m.lead_seconds:.2f} s")
    if candidate.loop and m.loop_seam > 0.05:
        value -= min(20.0, 100.0 * m.loop_seam)
        reasons.append(f"seam jump {m.loop_seam:.2f}")
    if candidate.review == "rejected":
        value -= 100
        reasons.append("rejected")
    return Score(round(max(value, 0.0), 1), tuple(reasons))


def ranked(found: list[Candidate]) -> list[tuple[Candidate, Score]]:
    """Best first; a tie goes to the lower id (the earlier candidate)."""
    scored = [(c, score(c)) for c in found]
    return sorted(scored, key=lambda pair: (-pair[1].value, pair[0].id))
