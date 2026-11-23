"""A picked candidate becomes the mono OGG Vorbis file the game plays (RLM-126, ADR 0079).

Two steps. ``treat`` works on the samples in plain Python: the silence before and after is cut (never a loop's, which
must stay seamless) with short fades so the cuts never click, then one gain brings the sound to its loudness target
unless its sample peak would pass the ceiling first. ``encode`` hands the result to ffmpeg's libvorbis with the
bitexact flags, so the Ogg stream has no random serial number and no metadata: the same WAV gives the same bytes on
every run. The ffmpeg binary is the one the ``imageio-ffmpeg`` package pins in ``uv.lock``, so every checkout encodes
with the same build.

The OGG is committed as an art source (``art/sounds/``) and ``rf gen`` only copies it, because CI regenerates every
generated file and fails on a byte of difference: an encoder on another machine may write other bytes.
"""

from __future__ import annotations

import struct
import subprocess
import tempfile
from dataclasses import dataclass
from functools import cache
from pathlib import Path

from realmforge_tools.sound.wav import Pcm, db, loudness, peak, sound_bounds, wav_bytes

# Loudness targets in LUFS (Matěj, 2026-11-23: "a loudness target"), set by vanilla's own sounds as measured on
# 2026-11-23: its one-shots (mob hurt and death, impacts, swings, UI) have a median of about -19 LUFS with peaks at -1
# to 0 dBFS, its ambient loops sit at -26 and below. A one-shot aims a little above that median and a loop at the
# cave ambience's level; a peaky sound stops at the ceiling first and ends quieter, as vanilla's peaky ones do.
SFX_LOUDNESS = -18.0
LOOP_LOUDNESS = -26.0
CEILING_DBFS = -1.0
# What a cut keeps around the sound, and its fades (seconds).
PRE_ROLL = 0.005
POST_ROLL = 0.03
FADE_IN = 0.002
FADE_OUT = 0.015
# libvorbis's variable bitrate quality, -1 to 10: 5 is about 80 kbit/s for mono.
VORBIS_QUALITY = 5


class ConvertError(RuntimeError):
    """ffmpeg is missing or refused the input."""


@dataclass(frozen=True)
class Treatment:
    """How a pick is treated: its loudness target, the peak ceiling, and whether its silence is cut."""

    loudness: float = SFX_LOUDNESS
    ceiling: float = CEILING_DBFS
    trim: bool = True

    @staticmethod
    def for_loop(loudness_lufs: float = LOOP_LOUDNESS) -> Treatment:
        return Treatment(loudness=loudness_lufs, trim=False)


@dataclass(frozen=True)
class TreatmentReport:
    """What ``treat`` did: the seconds cut before and after, the gain, and the loudness and peak it ended at."""

    cut_before: float
    cut_after: float
    gain_db: float
    loudness_lufs: float
    peak_dbfs: float
    limited_by_peak: bool

    def as_dict(self) -> dict[str, float | bool]:
        return {
            "cut_before": round(self.cut_before, 3),
            "cut_after": round(self.cut_after, 3),
            "gain_db": round(self.gain_db, 2),
            "loudness_lufs": round(self.loudness_lufs, 2),
            "peak_dbfs": round(self.peak_dbfs, 2),
            "limited_by_peak": self.limited_by_peak,
        }


def _cut(samples: tuple[float, ...], rate: int) -> tuple[list[float], int, int]:
    """The sound without the silence around it, keeping ``PRE_ROLL`` before and ``POST_ROLL`` after, faded in and
    out at its ends (``FADE_IN``, ``FADE_OUT``), so neither a cut nor a sound that runs to the end of its file
    clicks."""
    start, end = sound_bounds(samples)
    if end == 0:
        return list(samples), 0, 0
    start = max(0, start - round(PRE_ROLL * rate))
    end = min(len(samples), end + round(POST_ROLL * rate))
    out = list(samples[start:end])
    fade = min(len(out), max(1, round(FADE_IN * rate)))
    for i in range(fade):
        out[i] *= i / fade
    fade = min(len(out), max(1, round(FADE_OUT * rate)))
    for i in range(fade):
        out[len(out) - 1 - i] *= i / fade
    return out, start, len(samples) - end


def treat(pcm: Pcm, treatment: Treatment) -> tuple[Pcm, TreatmentReport]:
    """``pcm`` cut (unless ``treatment.trim`` is off) and brought to its loudness target, its peak never above the
    ceiling."""
    if treatment.trim:
        samples, before, after = _cut(pcm.samples, pcm.rate)
    else:
        samples, before, after = list(pcm.samples), 0, 0
    level = loudness(samples, pcm.rate)
    top = db(peak(samples))
    gain = treatment.loudness - level
    limited = top + gain > treatment.ceiling
    if limited:
        gain = treatment.ceiling - top
    factor = 10 ** (gain / 20)
    out = tuple(s * factor for s in samples)
    return Pcm(pcm.rate, out), TreatmentReport(
        cut_before=before / pcm.rate,
        cut_after=after / pcm.rate,
        gain_db=gain,
        loudness_lufs=loudness(out, pcm.rate),
        peak_dbfs=db(peak(out)),
        limited_by_peak=limited,
    )


@cache
def ffmpeg() -> str:
    """The pinned ffmpeg binary of ``imageio-ffmpeg`` (a dependency in ``pyproject.toml``)."""
    try:
        import imageio_ffmpeg
    except ImportError as e:  # pragma: no cover - a dependency of the project
        raise ConvertError("imageio-ffmpeg is not installed: run `uv sync`") from e
    return imageio_ffmpeg.get_ffmpeg_exe()


@cache
def ffmpeg_version() -> str:
    """The first line of ``ffmpeg -version``, recorded with every pick."""
    result = subprocess.run([ffmpeg(), "-version"], capture_output=True, text=True, check=False)
    return result.stdout.splitlines()[0] if result.stdout else "unknown"


def encode(pcm: Pcm) -> bytes:
    """``pcm`` as mono OGG Vorbis, byte for byte the same on every run (ffmpeg's bitexact flags, no metadata)."""
    with tempfile.TemporaryDirectory(prefix="rf-sound-") as tmp:
        source = Path(tmp) / "in.wav"
        target = Path(tmp) / "out.ogg"
        source.write_bytes(wav_bytes(pcm))
        command = [
            ffmpeg(),
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-map_metadata",
            "-1",
            "-ac",
            "1",
            "-c:a",
            "libvorbis",
            "-q:a",
            str(VORBIS_QUALITY),
            "-fflags",
            "+bitexact",
            "-flags:a",
            "+bitexact",
            str(target),
        ]
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        if result.returncode != 0 or not target.is_file():
            raise ConvertError(f"ffmpeg failed: {result.stderr.strip() or result.returncode}")
        return target.read_bytes()


def decode(data: bytes, rate: int | None = None) -> Pcm:
    """An OGG (or any file ffmpeg reads) back to mono samples, for checks and the gallery's measurements."""
    from realmforge_tools.sound.wav import read_wav

    with tempfile.TemporaryDirectory(prefix="rf-sound-") as tmp:
        source = Path(tmp) / "in"
        target = Path(tmp) / "out.wav"
        source.write_bytes(data)
        command = [ffmpeg(), "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source)]
        command += ["-ac", "1", "-c:a", "pcm_s16le"] + (["-ar", str(rate)] if rate else []) + [str(target)]
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        if result.returncode != 0 or not target.is_file():
            raise ConvertError(f"ffmpeg could not decode it: {result.stderr.strip() or result.returncode}")
        return read_wav(target.read_bytes())


@dataclass(frozen=True)
class OggInfo:
    """What an OGG file's headers say: its codec, channels, sample rate and length."""

    codec: str
    channels: int
    rate: int
    seconds: float


def ogg_info(data: bytes) -> OggInfo:
    """Reads an Ogg stream's first page (the Vorbis identification header) and its last granule position. Raises
    ``ValueError`` for a file that is no Ogg stream."""
    if data[:4] != b"OggS" or len(data) < 28:
        raise ValueError("not an Ogg stream (no OggS page at the start)")
    segments = data[26]
    packet = data[27 + segments :]
    if packet[:7] != b"\x01vorbis" or len(packet) < 16:
        codec = "opus" if packet[:8] == b"OpusHead" else "unknown"
        return OggInfo(codec, packet[9] if codec == "opus" and len(packet) > 9 else 0, 0, 0.0)
    channels = packet[11]
    rate = struct.unpack_from("<I", packet, 12)[0]
    last = data.rfind(b"OggS")
    granule = struct.unpack_from("<q", data, last + 6)[0] if last + 14 <= len(data) else 0
    return OggInfo("vorbis", channels, rate, granule / rate if rate and granule > 0 else 0.0)
