"""Our sound files (RLM-126, ADR 0079): the mono OGG files in ``art/sounds/`` that ``rf gen`` ships, and where
each comes from.

``art/sounds/<subject>/<name>.ogg`` is a pick, converted by ``rf sound pick`` (``convert.py``), or a third-party
sound we may ship. A sound event of ``content/sounds/`` plays it by ``file: <subject>/<name>``, and ``rf gen`` copies
it to ``assets/realmforge/sounds/<subject>/<name>.ogg``. ``art/sounds/sources.yaml`` says where every file comes
from: for ElevenLabs output the store's candidate, its prompt and model; for anyone else's sound the author, the URL
and the licence, which are our credits. The licence rule (``art/README.md``, ADR 0079): ElevenLabs output from the
paid plan, CC0, CC BY 4.0 with its credit, and Stable Audio output under the Stability AI Community License. Nothing
else: no CC BY-NC, CC BY-SA, GPL, Pixabay or Sonniss GDC sounds, no ElevenLabs free-plan output, and never a copy of
a vanilla sound.
"""

from __future__ import annotations

from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from realmforge_tools.content import Content
from realmforge_tools.problems import Problem
from realmforge_tools.project import RepoPaths
from realmforge_tools.sound import convert, elevenlabs, store
from realmforge_tools.sound.store import Candidate
from realmforge_tools.sound.wav import read_wav
from realmforge_tools.yamlio import YamlLoadError, dump_yaml, load_yaml

SOUNDS_DIR = Path("art") / "sounds"
SOURCES_FILE = "sources.yaml"
SOURCES_HEADER = (
    "# Where every sound in art/sounds/ comes from (RLM-126, ADR 0079): `rf sound pick` writes ElevenLabs picks here;\n"
    "# a third-party sound gets its author, URL and licence by hand (our credits). The licence rule: art/README.md.\n"
)


class SoundsError(ValueError):
    """A pick that cannot be made."""


class SourceDef(BaseModel):
    """Where one sound file comes from."""

    model_config = ConfigDict(extra="forbid")

    source: Literal["elevenlabs", "stable_audio", "freesound", "kenney", "opengameart", "own"]
    license: Literal["elevenlabs-paid", "stability-community", "cc0", "cc-by-4.0"]
    author: str | None = None
    url: str | None = None
    candidate: str | None = Field(default=None, description="The store's candidate id, for generated sounds.")
    prompt: str | None = None
    model: str | None = None
    created: str | None = None
    ticket: str | None = None
    picked_by: str = Field(description="Matěj, or an agent's provisional pick and the ticket for his review.")
    stereo: bool = False
    conversion: dict[str, object] | None = None

    @model_validator(mode="after")
    def _credits(self) -> SourceDef:
        if self.source in ("elevenlabs", "stable_audio") and not (
            self.candidate and self.prompt and self.model
        ):
            raise ValueError("a generated sound names its candidate, prompt and model")
        if self.source == "elevenlabs" and self.license != "elevenlabs-paid":
            raise ValueError("ElevenLabs output ships only from the paid plan (license: elevenlabs-paid)")
        if self.license == "cc-by-4.0" and not (self.author and self.url):
            raise ValueError("a CC BY 4.0 sound needs its author and URL: they are its credit")
        if self.license == "cc0" and not self.url:
            raise ValueError("a CC0 sound names where it comes from (url)")
        return self


def sounds_dir(paths: RepoPaths) -> Path:
    return paths.root / SOUNDS_DIR


def files(paths: RepoPaths) -> dict[str, Path]:
    """Every sound file, by its name ``<subject>/<name>``."""
    root = sounds_dir(paths)
    if not root.is_dir():
        return {}
    return {f"{p.parent.name}/{p.stem}": p for p in sorted(root.glob("*/*.ogg"))}


def read_sources(paths: RepoPaths) -> dict[str, SourceDef]:
    """``sources.yaml``; raises ``YamlLoadError``, ``ValidationError`` or ``ValueError``."""
    file = sounds_dir(paths) / SOURCES_FILE
    if not file.is_file():
        return {}
    data = load_yaml(file) or {}
    if not isinstance(data, dict):
        raise ValueError("sources.yaml maps <subject>/<name> to its source")
    return {str(name): SourceDef.model_validate(entry) for name, entry in data.items()}


def write_sources(paths: RepoPaths, sources: dict[str, SourceDef]) -> None:
    file = sounds_dir(paths) / SOURCES_FILE
    file.parent.mkdir(parents=True, exist_ok=True)
    body = {name: sources[name].model_dump(mode="json", exclude_none=True) for name in sorted(sources)}
    file.write_text(SOURCES_HEADER + dump_yaml(body), encoding="utf-8")


def _played(content: Content) -> dict[str, list[tuple[str, str]]]:
    """Every file a sound event plays: name -> (source, path) of each reference."""
    played: dict[str, list[tuple[str, str]]] = {}
    for entry in content.sounds:
        for name, event in entry.model.events.items():
            for n, ref in enumerate(event.sounds):
                if ref.file is not None:
                    played.setdefault(ref.file, []).append((entry.source, f"events.{name}.sounds.{n}.file"))
    return played


def problems(paths: RepoPaths, content: Content) -> list[Problem]:
    """Every file is a Vorbis stream (mono unless its source says stereo), has a source, and plays in an event;
    every file an event names exists."""
    found: list[Problem] = []
    where = (SOUNDS_DIR / SOURCES_FILE).as_posix()
    try:
        sources = read_sources(paths)
    except (YamlLoadError, ValidationError, ValueError) as e:
        return [Problem(where, "", str(e))]
    on_disk = files(paths)
    played = _played(content)
    for name, file in on_disk.items():
        rel = (SOUNDS_DIR / f"{name}.ogg").as_posix()
        if not store.NAME.match(file.parent.name) or not store.NAME.match(file.stem):
            found.append(Problem(rel, "", "a sound file is art/sounds/<subject>/<name>.ogg, both snake_case"))
        source = sources.get(name)
        if source is None:
            found.append(Problem(rel, "", f"no entry in {where}: say where it comes from"))
        try:
            info = convert.ogg_info(file.read_bytes())
        except ValueError as e:
            found.append(Problem(rel, "", str(e)))
            continue
        if info.codec != "vorbis":
            found.append(Problem(rel, "", f"{info.codec} audio: the game plays Ogg Vorbis"))
        elif info.channels != 1 and not (source and source.stereo):
            found.append(
                Problem(rel, "", f"{info.channels} channels: positional sounds are mono (stereo never fades)")
            )
        if name not in played:
            found.append(Problem(rel, "", "no sound event plays it: name it in content/sounds/ or remove it"))
    for name in sources:
        if name not in on_disk:
            found.append(Problem(where, name, f"art/sounds/{name}.ogg does not exist"))
    for name, refs in played.items():
        if name not in on_disk:
            for source_file, path in refs:
                found.append(Problem(source_file, path, f"{name}: no file art/sounds/{name}.ogg"))
    return found


def next_name(paths: RepoPaths, subject: str, event: str) -> str:
    taken = set(files(paths))
    n = 1
    while f"{subject}/{event}_{n}" in taken:
        n += 1
    return f"{event}_{n}"


def pick(
    paths: RepoPaths,
    root: Path,
    candidate: Candidate,
    *,
    name: str | None = None,
    loudness: float | None = None,
    loop: bool | None = None,
    picked_by: str,
    replace: bool = False,
) -> tuple[str, convert.TreatmentReport]:
    """Converts ``candidate`` into ``art/sounds/<subject>/<name>.ogg`` and records its source. Returns the file's
    name and what the treatment did."""
    if candidate.kind != "sfx":
        raise SoundsError(f"{candidate.id}: music is converted when RLM-622 wires it, not by rf sound pick")
    if not elevenlabs.is_paid(candidate.cost.tier, candidate.cost.status):
        raise SoundsError(
            f"{candidate.id}: made on {candidate.cost.tier or 'an unrecorded tier'} "
            f"({candidate.cost.status or 'unrecorded status'}); only an active paid plan's output may ship (ADR 0079)"
        )
    name = name or next_name(paths, candidate.subject, candidate.event)
    if not store.NAME.match(name):
        raise SoundsError(f"{name}: a sound file's name is lowercase snake_case")
    key = f"{candidate.subject}/{name}"
    target = sounds_dir(paths) / candidate.subject / f"{name}.ogg"
    if target.exists() and not replace:
        raise SoundsError(f"art/sounds/{key}.ogg exists: pass --replace to overwrite it")
    body = store.audio_path(root, candidate).read_bytes()
    pcm = read_wav(body) if body[:4] == b"RIFF" else convert.decode(body)
    looping = candidate.loop if loop is None else loop
    if looping:
        treatment = convert.Treatment.for_loop(loudness if loudness is not None else convert.LOOP_LOUDNESS)
    else:
        treatment = convert.Treatment(loudness=loudness if loudness is not None else convert.SFX_LOUDNESS)
    treated, report = convert.treat(pcm, treatment)
    ogg = convert.encode(treated)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(ogg)
    sources = read_sources(paths)
    sources[key] = SourceDef(
        source="elevenlabs",
        license="elevenlabs-paid",
        candidate=candidate.id,
        prompt=candidate.prompt,
        model=candidate.model,
        created=candidate.created[:10],
        ticket=candidate.ticket,
        picked_by=picked_by,
        conversion={
            "loop": looping,
            "loudness_target": treatment.loudness,
            "ceiling_dbfs": treatment.ceiling,
            **report.as_dict(),
            "encoder": f"libvorbis q{convert.VORBIS_QUALITY}",
            "ffmpeg": convert.ffmpeg_version(),
        },
    )
    write_sources(paths, sources)
    return key, report
