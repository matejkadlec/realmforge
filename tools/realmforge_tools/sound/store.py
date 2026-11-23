"""The sound store (Matěj, 2026-11-23; ADR 0079): every generated candidate, kept outside the repository and its
worktrees.

Generated audio never goes into git, and a worktree is removed after its merge, so candidates live in one durable
folder: ``~/realmforge-sounds/`` unless ``REALMFORGE_SOUND_STORE`` names another. Only a picked candidate, converted
to OGG, is committed (``art/sounds/``). Layout:

``candidates/<subject>/<event>/<nn>.wav``
    the audio exactly as ElevenLabs sent it (``.mp3`` for music the plan cannot have as WAV);
``candidates/<subject>/<event>/<nn>.yaml``
    its sidecar: the prompt and parameters, the date, the source, the ticket and sound event it is for, what it
    cost (the response's ``character-cost`` and the subscription's count before and after), and its measurements;
``ledger.jsonl``
    one line per paid request, the store's own record of what this billing period spent (the subscription's count
    lags behind by minutes, so the credit guard trusts whichever is higher);
``gallery/index.html``
    the review gallery, ``rf sound gallery``;
``plans/``
    the generation plans that ``rf sound gen --plan`` runs, kept with what they made.

A candidate's id is ``<subject>/<event>/<nn>``: ``frostbolt/impact/03``.
"""

from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from realmforge_tools.yamlio import YamlLoadError, dump_yaml, load_yaml

STORE_ENV = "REALMFORGE_SOUND_STORE"
DEFAULT_STORE = Path("~") / "realmforge-sounds"
CANDIDATES = "candidates"
LEDGER = "ledger.jsonl"
NAME = re.compile(r"^[a-z][a-z0-9_]*$")
CANDIDATE_ID = re.compile(r"^([a-z][a-z0-9_]*)/([a-z][a-z0-9_]*)/([0-9]{2,})$")
AUDIO_SUFFIXES = (".wav", ".mp3")
README = """# Realmforge sound store

Generated sound candidates and their sidecars (RLM-126, ADR 0079). Never commit anything from here: a pick is
converted into the repository's `art/sounds/` with `uv run rf sound pick <id>`. See `art/README.md` in the
repository ("Sound pipeline").

- `candidates/<subject>/<event>/<nn>.wav` and `<nn>.yaml`: a candidate and its sidecar
- `ledger.jsonl`: every paid request and its cost
- `gallery/index.html`: the review gallery (`uv run rf sound gallery`)
- `plans/`: generation plans (`uv run rf sound gen --plan <file>`)
"""


class StoreError(ValueError):
    """A candidate id, sidecar or store file that cannot be read."""


def store_root() -> Path:
    """The store's folder: ``REALMFORGE_SOUND_STORE``, or ``~/realmforge-sounds``."""
    return Path(os.environ.get(STORE_ENV) or DEFAULT_STORE).expanduser()


def ensure_store(store: Path) -> Path:
    (store / CANDIDATES).mkdir(parents=True, exist_ok=True)
    readme = store / "README.md"
    if not readme.exists():
        readme.write_text(README, encoding="utf-8")
    return store


class _Model(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Cost(_Model):
    """What a request cost: ``charged`` is the response's ``character-cost`` header, ``before`` and ``after`` the
    subscription's ``character_count`` around it (which can lag), ``limit`` and ``reset_unix`` the period's, and
    ``tier`` and ``status`` the plan it was made on, which decide whether its audio may ship (ADR 0079: an active
    paid plan only)."""

    charged: int | None = None
    before: int | None = None
    after: int | None = None
    limit: int | None = None
    reset_unix: int | None = None
    tier: str | None = None
    status: str | None = None


class Measured(_Model):
    """``wav.Analysis`` of the audio as it came."""

    rate: int
    channels: int
    seconds: float
    sound_seconds: float
    lead_seconds: float
    tail_seconds: float
    peak_dbfs: float
    loudness_lufs: float
    clipped_runs: int
    end_db: float = -120.0
    loop_seam: float


class Candidate(_Model):
    """A candidate's sidecar."""

    id: str
    subject: str
    event: str
    ticket: str = Field(description="The RLM ticket the candidate is for: RLM-445.")
    sound_event: str | None = Field(
        default=None,
        description="The sound event it is meant to play in, e.g. realmforge:spell.frostbolt.impact.",
    )
    note: str = Field(default="", description="The moment it is for, in a few words.")
    source: Literal["elevenlabs"] = "elevenlabs"
    kind: Literal["sfx", "music"] = "sfx"
    endpoint: str
    model: str
    prompt: str
    duration_seconds: float | None = None
    loop: bool = False
    prompt_influence: float | None = None
    music_length_ms: int | None = None
    force_instrumental: bool | None = None
    output_format: str
    file: str
    created: str
    request_id: str | None = None
    cost: Cost = Field(default_factory=Cost)
    measured: Measured | None = None
    review: Literal["new", "rejected"] = "new"
    review_note: str = ""


def parse_id(candidate_id: str) -> tuple[str, str, str]:
    match = CANDIDATE_ID.match(candidate_id)
    if not match:
        raise StoreError(
            f"{candidate_id}: a candidate id is <subject>/<event>/<nn>, e.g. frostbolt/impact/03"
        )
    return match.group(1), match.group(2), match.group(3)


def event_dir(store: Path, subject: str, event: str) -> Path:
    for name in (subject, event):
        if not NAME.match(name):
            raise StoreError(f"{name}: subjects and events are lowercase snake_case")
    return store / CANDIDATES / subject / event


def next_number(store: Path, subject: str, event: str) -> str:
    folder = event_dir(store, subject, event)
    taken = [int(p.stem) for p in folder.glob("*.yaml") if p.stem.isdigit()] if folder.is_dir() else []
    return f"{max(taken, default=0) + 1:02d}"


def sidecar_path(store: Path, candidate_id: str) -> Path:
    subject, event, number = parse_id(candidate_id)
    return event_dir(store, subject, event) / f"{number}.yaml"


def audio_path(store: Path, candidate: Candidate) -> Path:
    subject, event, _ = parse_id(candidate.id)
    return event_dir(store, subject, event) / candidate.file


def save(store: Path, candidate: Candidate, audio: bytes | None = None) -> Path:
    """Writes the sidecar, and the audio first when given, so a sidecar never names audio that is not there."""
    sidecar = sidecar_path(store, candidate.id)
    sidecar.parent.mkdir(parents=True, exist_ok=True)
    if audio is not None:
        (sidecar.parent / candidate.file).write_bytes(audio)
    sidecar.write_text(dump_yaml(candidate.model_dump(mode="json", exclude_none=True)), encoding="utf-8")
    return sidecar


def load(store: Path, candidate_id: str) -> Candidate:
    return _read(sidecar_path(store, candidate_id))


def _read(sidecar: Path) -> Candidate:
    try:
        return Candidate.model_validate(load_yaml(sidecar))
    except (OSError, YamlLoadError, ValidationError) as e:
        raise StoreError(f"{sidecar}: {e}") from e


def candidates(store: Path) -> list[Candidate]:
    """Every candidate in the store, by id."""
    root = store / CANDIDATES
    if not root.is_dir():
        return []
    found = [_read(sidecar) for sidecar in sorted(root.glob("*/*/*.yaml"))]
    return sorted(found, key=lambda c: c.id)


def append_ledger(store: Path, entry: dict[str, object]) -> None:
    ensure_store(store)
    with (store / LEDGER).open("a", encoding="utf-8") as ledger:
        ledger.write(json.dumps(entry, sort_keys=True) + "\n")


def ledger(store: Path) -> list[dict[str, object]]:
    file = store / LEDGER
    if not file.is_file():
        return []
    return [json.loads(line) for line in file.read_text(encoding="utf-8").splitlines() if line.strip()]


def _period(store: Path, reset_unix: int | None) -> list[dict[str, object]]:
    return [entry for entry in ledger(store) if entry.get("reset_unix") == reset_unix]


def spent(store: Path, reset_unix: int | None) -> int:
    """What the ledger records as charged in the billing period that resets at ``reset_unix``."""
    return sum(int(entry.get("charged") or 0) for entry in _period(store, reset_unix))  # type: ignore[call-overload]


def used(store: Path, reset_unix: int | None, count: int) -> int:
    """The period's credits used, as well as the store can tell: the subscription's ``count`` lags behind requests by
    minutes, so each ledger entry's own ``before`` count plus every charge since is a floor too. The highest floor
    counts, which also covers credits spent outside the tools before a request."""
    running = 0
    for entry in _period(store, reset_unix):
        before = int(entry.get("before") or 0)  # type: ignore[call-overload]
        running = max(running, before) + int(entry.get("charged") or 0)  # type: ignore[call-overload]
    return max(running, count)
