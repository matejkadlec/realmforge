"""Loading ``content/`` into validated models (step 1 of validation: schema)."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ValidationError

from realmforge_tools.problems import Problem, ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.rooms.structure import Structure, StructureError, read_structure
from realmforge_tools.schema import (
    CONTENT_TYPES,
    WORLDGEN_DIR,
    CurveDef,
    ItemDef,
    StatDef,
    StatRuleDef,
    TextGroupDef,
)
from realmforge_tools.schema.room import (
    LICENSE_FILES,
    REVIEW_FILE,
    ROOM_SEGMENT,
    ROOMS_DIR,
    ReviewFile,
    RoomDef,
)
from realmforge_tools.schema.worldgen import WORLDGEN_REGISTRIES
from realmforge_tools.yamlio import YamlLoadError, load_yaml

# Files allowed in content/ besides YAML entries.
_ALLOWED_OTHER_FILES = {"README.md"}
_WORLDGEN_ID = re.compile(r"^[a-z][a-z0-9_]*$")
_ROOM_PATH = re.compile(rf"^{ROOM_SEGMENT}(/{ROOM_SEGMENT})*$")


@dataclass(frozen=True)
class Entry[M: BaseModel]:
    """A content entry and the repository-relative file it came from."""

    source: str
    model: M


@dataclass(frozen=True)
class WorldgenFile:
    """A worldgen JSON file from ``content/worldgen/<directory>/<id>.json``, copied byte for byte."""

    source: str
    directory: str
    id: str
    data: dict[str, Any]
    raw: bytes


@dataclass(frozen=True)
class RoomEntry:
    """A room in ``content/rooms/``: its template and its sidecar. The id is the path below ``content/rooms/``
    without the suffix."""

    id: str
    source: str
    """The sidecar, repository-relative."""
    template: str
    """The template, repository-relative."""
    model: RoomDef
    structure: Structure


@dataclass(frozen=True)
class RoomReview:
    """A ``review.yaml`` in a folder of ``content/rooms/``: decisions keyed by room path below that folder."""

    source: str
    folder: str
    """The folder's path below ``content/rooms/`` ("" for the folder itself)."""
    decisions: dict[str, str]


@dataclass(frozen=True)
class RoomNotice:
    """A third-party source's ``LICENSE`` or ``NOTICE`` in ``content/rooms/third_party/<source>/``, which the jar
    carries under ``META-INF/third_party/<source>/`` (ADR 0028)."""

    source: str
    name: str
    raw: bytes


@dataclass(frozen=True)
class Content:
    """All content, sorted by id within each type."""

    items: tuple[Entry[ItemDef], ...]
    curves: tuple[Entry[CurveDef], ...]
    stats: tuple[Entry[StatDef], ...]
    stat_rules: tuple[Entry[StatRuleDef], ...]
    texts: tuple[Entry[TextGroupDef], ...]
    worldgen: tuple[WorldgenFile, ...]
    rooms: tuple[RoomEntry, ...] = ()
    room_reviews: tuple[RoomReview, ...] = ()
    room_notices: tuple[RoomNotice, ...] = ()

    def entries(self, type_dir: str) -> tuple[Entry, ...]:
        """The entries of the YAML content type in ``content/<type_dir>/``."""
        return getattr(self, type_dir)


def load_content(paths: RepoPaths) -> Content:
    """Load and schema-validate every file in ``content/``. Raises ValidationFailed listing all problems."""
    problems: list[Problem] = []
    loaded: dict[str, list[Entry]] = {name: [] for name in CONTENT_TYPES}
    worldgen: list[WorldgenFile] = []
    room_files: list[Path] = []

    for file in sorted(p for p in paths.content.rglob("*") if p.is_file()):
        rel = paths.rel(file)
        type_dir = file.relative_to(paths.content).parts[0]
        if file.name in _ALLOWED_OTHER_FILES:
            continue
        if type_dir == WORLDGEN_DIR:
            loaded_file = _load_worldgen(file, rel, paths, problems)
            if loaded_file is not None:
                worldgen.append(loaded_file)
            continue
        if type_dir == ROOMS_DIR:
            room_files.append(file)
            continue
        if file.suffix == ".yml":
            problems.append(Problem(rel, "", "use the .yaml extension, not .yml"))
            continue
        if file.suffix != ".yaml":
            problems.append(Problem(rel, "", "unexpected file in content/ (only .yaml entries are allowed)"))
            continue
        if type_dir not in CONTENT_TYPES or file.parent.parent != paths.content:
            known = ", ".join(f"content/{name}/" for name in CONTENT_TYPES)
            problems.append(Problem(rel, "", f"unknown content location; entries go directly into {known}"))
            continue

        entry = _load_entry(file, rel, CONTENT_TYPES[type_dir], problems)
        if entry is not None:
            loaded[type_dir].append(entry)

    rooms, reviews, notices = _load_rooms(room_files, paths, problems)
    if problems:
        raise ValidationFailed(problems)

    def by_id(entries: list[Entry]) -> tuple[Entry, ...]:
        return tuple(sorted(entries, key=lambda e: (e.model.id, e.source)))

    return Content(
        items=by_id(loaded["items"]),
        curves=by_id(loaded["curves"]),
        stats=by_id(loaded["stats"]),
        stat_rules=by_id(loaded["stat_rules"]),
        texts=by_id(loaded["texts"]),
        worldgen=tuple(sorted(worldgen, key=lambda f: (f.directory, f.id))),
        rooms=rooms,
        room_reviews=reviews,
        room_notices=notices,
    )


def _load_entry(file: Path, rel: str, model: type[BaseModel], problems: list[Problem]) -> Entry | None:
    try:
        data = load_yaml(file)
    except YamlLoadError as e:
        problems.append(Problem(rel, f"line {e.line}" if e.line else "", str(e)))
        return None
    if not isinstance(data, dict):
        problems.append(Problem(rel, "", "expected a mapping at the top level"))
        return None
    try:
        return Entry(rel, model.model_validate(data))
    except ValidationError as e:
        for err in e.errors():
            location = ".".join(str(part) for part in err["loc"])
            problems.append(Problem(rel, location, err["msg"]))
        return None


def _load_worldgen(file: Path, rel: str, paths: RepoPaths, problems: list[Problem]) -> WorldgenFile | None:
    parts = file.relative_to(paths.content / WORLDGEN_DIR).parts
    if len(parts) != 2 or parts[0] not in WORLDGEN_REGISTRIES:
        known = ", ".join(f"content/{WORLDGEN_DIR}/{name}/" for name in WORLDGEN_REGISTRIES)
        problems.append(Problem(rel, "", f"unknown worldgen location; files go directly into {known}"))
        return None
    if file.suffix != ".json":
        problems.append(
            Problem(rel, "", "unexpected file in content/worldgen/ (only .json files are allowed)")
        )
        return None
    if not _WORLDGEN_ID.match(file.stem):
        problems.append(Problem(rel, "", f"file name {file.stem!r} is not a lowercase snake_case id"))
        return None
    raw = file.read_bytes()
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        problems.append(Problem(rel, "", f"invalid JSON: {e}"))
        return None
    if not isinstance(data, dict):
        problems.append(Problem(rel, "", "expected a JSON object at the top level"))
        return None
    return WorldgenFile(rel, parts[0], file.stem, data, raw)


def _load_rooms(
    files: list[Path], paths: RepoPaths, problems: list[Problem]
) -> tuple[tuple[RoomEntry, ...], tuple[RoomReview, ...], tuple[RoomNotice, ...]]:
    """Pair every template with its sidecar, read both, and read the review files."""
    root = paths.content / ROOMS_DIR
    templates: dict[str, Path] = {}
    sidecars: dict[str, Path] = {}
    reviews: list[RoomReview] = []
    license_files: dict[tuple[str, str], Path] = {}
    for file in files:
        rel = paths.rel(file)
        parts = file.relative_to(root).parts
        if file.name == REVIEW_FILE:
            review = _load_review(file, rel, problems)
            if review is not None:
                reviews.append(RoomReview(rel, "/".join(parts[:-1]), review))
            continue
        if file.name in LICENSE_FILES:
            if len(parts) != 3 or parts[0] != "third_party":
                problems.append(
                    Problem(rel, "", f"{file.name} belongs directly in content/rooms/third_party/<source>/")
                )
            else:
                license_files[parts[1], file.name] = file
            continue
        if file.suffix not in (".nbt", ".yaml"):
            problems.append(
                Problem(
                    rel,
                    "",
                    "unexpected file in content/rooms/ (rooms are a .nbt template and a .yaml sidecar)",
                )
            )
            continue
        room_id = file.relative_to(root).with_suffix("").as_posix()
        if not _ROOM_PATH.match(room_id) or file.stem == REVIEW_FILE.removesuffix(".yaml"):
            problems.append(
                Problem(
                    rel,
                    "",
                    f"room path {room_id!r} must be lowercase snake_case folders and names, not 'review'",
                )
            )
            continue
        (templates if file.suffix == ".nbt" else sidecars)[room_id] = file

    rooms: list[RoomEntry] = []
    for room_id in sorted(templates.keys() | sidecars.keys()):
        template, sidecar = templates.get(room_id), sidecars.get(room_id)
        if template is None:
            problems.append(
                Problem(paths.rel(sidecars[room_id]), "", "the room has no .nbt template next to it")
            )
            continue
        if sidecar is None:
            problems.append(Problem(paths.rel(template), "", "the room has no .yaml sidecar next to it"))
            continue
        entry = _load_entry(sidecar, paths.rel(sidecar), RoomDef, problems)
        try:
            structure = read_structure(template.read_bytes())
        except StructureError as e:
            problems.append(Problem(paths.rel(template), "", f"not a usable structure template: {e}"))
            continue
        if entry is not None:
            rooms.append(RoomEntry(room_id, entry.source, paths.rel(template), entry.model, structure))
    sources = sorted({room_id.split("/")[1] for room_id in templates if room_id.startswith("third_party/")})
    for source in sources:
        for name in LICENSE_FILES:
            if (source, name) not in license_files:
                problems.append(
                    Problem(
                        f"content/{ROOMS_DIR}/third_party/{source}/",
                        "",
                        f"third-party rooms need the source's {name} file next to them (ADR 0028)",
                    )
                )
    notices = tuple(
        RoomNotice(source, name, file.read_bytes()) for (source, name), file in sorted(license_files.items())
    )
    return tuple(rooms), tuple(sorted(reviews, key=lambda r: r.folder)), notices


def _load_review(file: Path, rel: str, problems: list[Problem]) -> dict[str, str] | None:
    try:
        data = load_yaml(file)
    except YamlLoadError as e:
        problems.append(Problem(rel, f"line {e.line}" if e.line else "", str(e)))
        return None
    try:
        return ReviewFile.model_validate(data or {}).root
    except ValidationError as e:
        for err in e.errors():
            problems.append(Problem(rel, ".".join(str(part) for part in err["loc"]), err["msg"]))
        return None
