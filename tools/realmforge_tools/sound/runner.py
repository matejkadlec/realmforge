"""``rf sound gen``: candidates from ElevenLabs into the sound store, each request behind the credit guard.

A spec asks for one sound event's candidates: a prompt, its length, whether it loops, the prompt influence, how many
variants, and the ticket and event they are for. A plan (``rf sound gen --plan``) is a YAML file of specs. It is
resumable: an entry makes only the variants its prompt still lacks in the store, so running a plan again after a stop
spends nothing on what is already there.
"""

from __future__ import annotations

import re
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from realmforge_tools.sound import convert, elevenlabs, store
from realmforge_tools.sound.store import Candidate, Cost, Measured
from realmforge_tools.sound.wav import WavError, analyse, read_wav
from realmforge_tools.yamlio import YamlLoadError, load_yaml

TICKET = re.compile(r"^RLM-[0-9]+$")


class RunnerError(ValueError):
    """A spec or plan that cannot run."""


class Spec(BaseModel):
    """What to generate for one sound event."""

    model_config = ConfigDict(extra="forbid")

    subject: str = Field(
        description="The content id the sound belongs to: a spell, mob, block, or a theme like ui."
    )
    event: str = Field(description="The event's name: impact, hurt, ambient, loop.")
    prompt: str = Field(min_length=3)
    ticket: str | None = Field(
        default=None, description="The RLM ticket it is for (the plan's when left out)."
    )
    sound_event: str | None = Field(default=None, description="The sound event id it is meant for.")
    note: str = ""
    kind: Literal["sfx", "music"] = "sfx"
    duration: float | None = Field(
        default=None, description="Seconds: 0.5 to 30 for an effect, 3 to 600 for music."
    )
    loop: bool = False
    influence: float | None = Field(default=None, ge=0, le=1)
    variants: int = Field(default=4, ge=1, le=10)
    model: str | None = None
    output_format: str | None = None
    instrumental: bool = True

    @model_validator(mode="after")
    def _ranges(self) -> Spec:
        for name in (self.subject, self.event):
            if not store.NAME.match(name):
                raise ValueError(f"{name}: subjects and events are lowercase snake_case")
        if self.kind == "sfx" and self.duration is not None and not 0.5 <= self.duration <= 30:
            raise ValueError("an effect's duration is 0.5 to 30 seconds")
        if self.kind == "music" and (self.duration is None or not 3 <= self.duration <= 600):
            raise ValueError("music needs a duration of 3 to 600 seconds")
        if self.ticket is not None and not TICKET.match(self.ticket):
            raise ValueError(f"{self.ticket}: a ticket is RLM-<n>")
        return self


class Plan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticket: str | None = Field(default=None, description="The ticket of every entry that names none.")
    entries: list[Spec]


def load_plan(path: Path) -> list[Spec]:
    try:
        plan = Plan.model_validate(load_yaml(path))
    except (OSError, YamlLoadError, ValidationError) as e:
        raise RunnerError(f"{path}: {e}") from e
    specs = [
        spec if spec.ticket else spec.model_copy(update={"ticket": plan.ticket}) for spec in plan.entries
    ]
    missing = [f"{s.subject}/{s.event}" for s in specs if not s.ticket]
    if missing:
        raise RunnerError(f"{path}: no ticket for {', '.join(missing)} (give the plan or the entry one)")
    return specs


def existing(found: list[Candidate], spec: Spec) -> list[Candidate]:
    """The store's candidates of ``spec``'s event made from the same prompt."""
    return [
        c for c in found if c.subject == spec.subject and c.event == spec.event and c.prompt == spec.prompt
    ]


def measure(body: bytes) -> tuple[Measured | None, str]:
    """The audio's measurements and its file suffix: WAV as it came, anything else through ffmpeg."""
    if body[:4] == b"RIFF":
        try:
            return Measured(**analyse(read_wav(body)).as_dict()), ".wav"
        except WavError:
            return None, ".wav"
    suffix = ".mp3" if body[:3] == b"ID3" or body[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2") else ".bin"
    try:
        return Measured(**analyse(convert.decode(body)).as_dict()), suffix
    except convert.ConvertError:
        return None, suffix


def run(
    spec: Spec,
    root: Path,
    client: elevenlabs.Client,
    *,
    reserve: int,
    count: int | None = None,
    say: Callable[[str], None] = print,
) -> list[Candidate]:
    """Makes ``count`` candidates of ``spec`` (its variants when not given), each behind the credit guard. Raises
    ``CreditsReserved`` before a request that would go below ``reserve``; the candidates made until then stay."""
    if not spec.ticket:
        raise RunnerError(f"{spec.subject}/{spec.event}: no ticket")
    store.ensure_store(root)
    model = spec.model or (elevenlabs.MUSIC_MODEL if spec.kind == "music" else elevenlabs.SFX_MODEL)
    output_format = spec.output_format or (
        elevenlabs.MUSIC_FORMAT if spec.kind == "music" else elevenlabs.SFX_FORMAT
    )
    made: list[Candidate] = []
    for _ in range(spec.variants if count is None else count):
        before = client.subscription()
        history = store.ledger(root)
        left = elevenlabs.credits_left(before, store.used(root, before.reset_unix, before.count))
        elevenlabs.check_reserve(left, elevenlabs.estimate(spec.kind, model, spec.duration, history), reserve)
        if spec.kind == "music":
            assert spec.duration is not None
            audio = client.music(
                spec.prompt,
                length_ms=round(spec.duration * 1000),
                instrumental=spec.instrumental,
                model=model,
                output_format=output_format,
            )
            endpoint = "/v1/music"
        else:
            audio = client.sound_effect(
                spec.prompt,
                duration=spec.duration,
                loop=spec.loop,
                influence=spec.influence,
                model=model,
                output_format=output_format,
            )
            endpoint = "/v1/sound-generation"
        after = client.subscription()
        measured, suffix = measure(audio.body)
        number = store.next_number(root, spec.subject, spec.event)
        candidate = Candidate(
            id=f"{spec.subject}/{spec.event}/{number}",
            subject=spec.subject,
            event=spec.event,
            ticket=spec.ticket,
            sound_event=spec.sound_event,
            note=spec.note,
            kind=spec.kind,
            endpoint=endpoint,
            model=model,
            prompt=spec.prompt,
            duration_seconds=spec.duration if spec.kind == "sfx" else None,
            loop=spec.loop,
            prompt_influence=spec.influence if spec.kind == "sfx" else None,
            music_length_ms=round(spec.duration * 1000) if spec.kind == "music" and spec.duration else None,
            force_instrumental=spec.instrumental if spec.kind == "music" else None,
            output_format=output_format,
            file=f"{number}{suffix}",
            created=datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
            request_id=audio.request_id,
            cost=Cost(
                charged=audio.charged,
                before=before.count,
                after=after.count,
                limit=after.limit,
                reset_unix=after.reset_unix,
            ),
            measured=measured,
        )
        store.save(root, candidate, audio.body)
        seconds = spec.duration if spec.duration is not None else (measured.seconds if measured else None)
        store.append_ledger(
            root,
            {
                "time": candidate.created,
                "id": candidate.id,
                "kind": spec.kind,
                "model": model,
                "seconds": seconds,
                "charged": audio.charged,
                "before": before.count,
                "after": after.count,
                "limit": after.limit,
                "reset_unix": after.reset_unix,
                "ticket": spec.ticket,
            },
        )
        left_after = elevenlabs.credits_left(after, store.used(root, after.reset_unix, after.count))
        length = f"{measured.seconds:.2f} s" if measured else "unmeasured"
        say(
            f"{candidate.id}: {audio.charged if audio.charged is not None else '?'} credits ({length}); "
            f"{left_after} left, resets {after.reset_date()}"
        )
        made.append(candidate)
    return made


def run_plan(
    specs: list[Spec],
    root: Path,
    client: elevenlabs.Client,
    *,
    reserve: int,
    dry_run: bool = False,
    say: Callable[[str], None] = print,
) -> list[Candidate]:
    """Each spec's missing variants, in order. A dry run only says what it would make and roughly cost."""
    made: list[Candidate] = []
    total = 0
    history = store.ledger(root)
    for spec in specs:
        have = len(existing(store.candidates(root), spec))
        missing = max(0, spec.variants - have)
        model = spec.model or (elevenlabs.MUSIC_MODEL if spec.kind == "music" else elevenlabs.SFX_MODEL)
        cost = missing * elevenlabs.estimate(spec.kind, model, spec.duration, history)
        total += cost
        if dry_run or missing == 0:
            say(f"{spec.subject}/{spec.event}: {have} made, {missing} to make (about {cost} credits)")
            continue
        made += run(spec, root, client, reserve=reserve, count=missing, say=say)
    if dry_run:
        say(f"About {total} credits in all.")
    return made
