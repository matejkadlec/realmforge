"""PCM WAV files and what can be measured on them without listening (RLM-126, ADR 0079).

A candidate is judged by numbers before anyone hears it: its length once the silence is trimmed, its sample peak and
whether it clips, its silence before and after, and its loudness (ITU-R BS.1770-4, the LUFS that EBU R128 and every
loudness meter use, mono). Everything here is plain Python on 16-bit PCM, so it runs wherever the tools run and
gives the same numbers on every run.
"""

from __future__ import annotations

import io
import math
import sys
import wave
from array import array
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

FULL_SCALE = 32767
# A sample at or above this share of full scale counts as clipped when it is part of a run (CLIP_RUN samples in a
# row): one sample at full scale can be a clean peak, three in a row are a flattened wave.
CLIP_LEVEL = 0.999
CLIP_RUN = 3
# The quietest level any measurement reports (silence has no finite level).
FLOOR_DB = -120.0
# BS.1770 gating: 400 ms blocks every 100 ms, the absolute gate at -70 LUFS, the relative gate 10 LU below.
BLOCK_SECONDS = 0.4
STEP_SECONDS = 0.1
ABSOLUTE_GATE = -70.0
RELATIVE_GATE = -10.0
# Silence is what lies this far below the sound's own peak (at least SILENCE_FLOOR dBFS): the noise floor of a
# generated sound and the last of its reverb tail.
SILENCE_BELOW_PEAK = 50.0
SILENCE_FLOOR = -70.0
# How much of a sound's end ``end_level`` measures.
END_SECONDS = 0.05


class WavError(ValueError):
    """A file that is not a 16-bit PCM WAV."""


@dataclass(frozen=True)
class Pcm:
    """Mono samples in [-1, 1] and their rate. ``channels`` is what the file had before the downmix, and
    ``clipped`` the clipped runs counted in each of its channels (a downmix can hide one channel's)."""

    rate: int
    samples: tuple[float, ...]
    channels: int = 1
    clipped: int = 0

    @property
    def seconds(self) -> float:
        return len(self.samples) / self.rate if self.rate else 0.0


def read_wav(data: bytes) -> Pcm:
    """A 16-bit PCM WAV, mono or stereo, as mono samples: the channels' mean."""
    try:
        with wave.open(io.BytesIO(data), "rb") as wav:
            channels, width, rate, frames = (
                wav.getnchannels(),
                wav.getsampwidth(),
                wav.getframerate(),
                wav.getnframes(),
            )
            raw = wav.readframes(frames)
    except (wave.Error, EOFError) as e:
        raise WavError(f"not a PCM WAV: {e}") from e
    if width != 2:
        raise WavError(f"{8 * width}-bit samples: only 16-bit PCM is read")
    if channels not in (1, 2):
        raise WavError(f"{channels} channels: only mono and stereo are read")
    values = array("h")
    values.frombytes(raw[: len(raw) - len(raw) % 2])
    if sys.byteorder == "big":
        values.byteswap()
    if channels == 2:
        left = [v / FULL_SCALE for v in values[0::2]]
        right = [v / FULL_SCALE for v in values[1::2]]
        samples = tuple((a + b) / 2 for a, b in zip(left, right, strict=False))
        clipped = clipped_runs(left) + clipped_runs(right)
    else:
        samples = tuple(v / FULL_SCALE for v in values)
        clipped = clipped_runs(samples)
    return Pcm(rate, samples, channels, clipped)


def read_wav_file(path: Path) -> Pcm:
    return read_wav(path.read_bytes())


def wav_bytes(pcm: Pcm) -> bytes:
    """``pcm`` as a 16-bit mono WAV, each sample rounded to the nearest step and clamped to full scale."""
    values = array("h", (max(-FULL_SCALE, min(FULL_SCALE, round(s * FULL_SCALE))) for s in pcm.samples))
    if sys.byteorder == "big":
        values.byteswap()
    out = io.BytesIO()
    with wave.open(out, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(pcm.rate)
        wav.writeframes(values.tobytes())
    return out.getvalue()


def db(level: float) -> float:
    """A level as dBFS (full scale is 0), at least ``FLOOR_DB``."""
    return max(FLOOR_DB, 20 * math.log10(level)) if level > 0 else FLOOR_DB


def peak(samples: Sequence[float]) -> float:
    return max((abs(s) for s in samples), default=0.0)


def clipped_runs(samples: Sequence[float]) -> int:
    """How many runs of at least ``CLIP_RUN`` samples in a row sit at full scale: a clipped, flattened wave."""
    runs = length = 0
    for s in samples:
        if abs(s) >= CLIP_LEVEL:
            length += 1
            if length == CLIP_RUN:
                runs += 1
        else:
            length = 0
    return runs


def silence_threshold(samples: Sequence[float]) -> float:
    """The level below which a sample is silence: ``SILENCE_BELOW_PEAK`` dB under the peak, at least
    ``SILENCE_FLOOR`` dBFS, as a linear level."""
    return 10 ** (max(db(peak(samples)) - SILENCE_BELOW_PEAK, SILENCE_FLOOR) / 20)


def sound_bounds(samples: Sequence[float]) -> tuple[int, int]:
    """The first sample above silence and the one after the last: ``(0, 0)`` for a silent sound."""
    threshold = silence_threshold(samples)
    loud = [i for i, s in enumerate(samples) if abs(s) > threshold]
    return (loud[0], loud[-1] + 1) if loud else (0, 0)


def _biquad(samples: Sequence[float], b: tuple[float, float, float], a: tuple[float, float]) -> list[float]:
    """A second-order filter, direct form I: ``y[n] = b0 x[n] + b1 x[n-1] + b2 x[n-2] - a1 y[n-1] - a2 y[n-2]``."""
    b0, b1, b2 = b
    a1, a2 = a
    x1 = x2 = y1 = y2 = 0.0
    out: list[float] = []
    append = out.append
    for x in samples:
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x, y1, y
        append(y)
    return out


def k_weighted(samples: Sequence[float], rate: int) -> list[float]:
    """BS.1770's K-weighting at any sample rate: the head's high shelf, then the RLB high-pass (the coefficients that
    libebur128 derives for rates other than 48 kHz)."""
    k = math.tan(math.pi * 1681.974450955533 / rate)
    q = 0.7071752369554196
    vh = 10 ** (3.999843853973347 / 20)
    vb = vh**0.4996667741545416
    a0 = 1 + k / q + k * k
    shelf = _biquad(
        samples,
        ((vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0),
        (2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0),
    )
    k = math.tan(math.pi * 38.13547087602444 / rate)
    q = 0.5003270373238773
    a0 = 1 + k / q + k * k
    return _biquad(shelf, (1.0, -2.0, 1.0), (2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0))


def _lufs(mean_square: float) -> float:
    return -0.691 + 10 * math.log10(mean_square) if mean_square > 0 else FLOOR_DB


def loudness(samples: Sequence[float], rate: int) -> float:
    """Integrated loudness in LUFS (BS.1770-4, gated), mono. A sound shorter than one 400 ms block is measured as
    one block, so a short hit still has a loudness."""
    weighted = k_weighted(samples, rate)
    if not weighted:
        return FLOOR_DB
    block = max(1, round(BLOCK_SECONDS * rate))
    step = max(1, round(STEP_SECONDS * rate))
    squares = [s * s for s in weighted]
    if len(squares) <= block:
        return _lufs(sum(squares) / len(squares))
    running = [0.0]
    for s in squares:
        running.append(running[-1] + s)
    blocks = [
        (running[start + block] - running[start]) / block
        for start in range(0, len(squares) - block + 1, step)
    ]
    gated = [m for m in blocks if _lufs(m) > ABSOLUTE_GATE]
    if not gated:
        return FLOOR_DB
    relative = _lufs(sum(gated) / len(gated)) + RELATIVE_GATE
    kept = [m for m in gated if _lufs(m) > relative]
    return _lufs(sum(kept) / len(kept))


def end_level(samples: Sequence[float], rate: int) -> float:
    """The level of the last ``END_SECONDS`` against the sound's peak, in dB: a sound still loud there was cut off
    by the length it was asked for."""
    tail = samples[-max(1, round(END_SECONDS * rate)) :]
    if not tail:
        return FLOOR_DB
    rms = math.sqrt(sum(s * s for s in tail) / len(tail))
    return db(rms) - db(peak(samples)) if peak(samples) > 0 else FLOOR_DB


@dataclass(frozen=True)
class Analysis:
    """What a candidate measures, before any treatment. ``sound_seconds`` is its length without the silence before
    and after; ``end_db`` the level of its last 50 ms against its peak (``end_level``); ``loop_seam`` is the jump
    from its last sample back to its first (in full scale), which a loop plays."""

    rate: int
    channels: int
    seconds: float
    sound_seconds: float
    lead_seconds: float
    tail_seconds: float
    peak_dbfs: float
    loudness_lufs: float
    clipped_runs: int
    end_db: float
    loop_seam: float

    def as_dict(self) -> dict[str, float | int]:
        return {
            "rate": self.rate,
            "channels": self.channels,
            "seconds": round(self.seconds, 3),
            "sound_seconds": round(self.sound_seconds, 3),
            "lead_seconds": round(self.lead_seconds, 3),
            "tail_seconds": round(self.tail_seconds, 3),
            "peak_dbfs": round(self.peak_dbfs, 2),
            "loudness_lufs": round(self.loudness_lufs, 2),
            "clipped_runs": self.clipped_runs,
            "end_db": round(self.end_db, 1),
            "loop_seam": round(self.loop_seam, 4),
        }


def analyse(pcm: Pcm) -> Analysis:
    start, end = sound_bounds(pcm.samples)
    count = len(pcm.samples)
    rate = pcm.rate
    return Analysis(
        rate=rate,
        channels=pcm.channels,
        seconds=count / rate,
        sound_seconds=(end - start) / rate,
        lead_seconds=start / rate,
        tail_seconds=(count - end) / rate if end else count / rate,
        peak_dbfs=db(peak(pcm.samples)),
        loudness_lufs=loudness(pcm.samples, rate),
        clipped_runs=max(pcm.clipped, clipped_runs(pcm.samples)),
        end_db=end_level(pcm.samples, rate),
        loop_seam=abs(pcm.samples[-1] - pcm.samples[0]) if count else 0.0,
    )
