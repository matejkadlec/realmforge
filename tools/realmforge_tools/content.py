"""Loading ``content/`` into validated models (step 1 of validation: schema)."""

from __future__ import annotations

import json
import re
from collections.abc import Callable
from dataclasses import dataclass, field
from functools import cached_property
from operator import attrgetter
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ValidationError

from realmforge_tools.problems import Problem, ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.rooms.cache import RoomCache, template_key
from realmforge_tools.rooms.structure import Pos, Structure, StructureError, read_structure
from realmforge_tools.schema import (
    CONTENT_TYPES,
    WORLDGEN_DIR,
    ChapterDef,
    ClassDef,
    CurveDef,
    DimensionRulesDef,
    DungeonTypeDef,
    ItemDef,
    KillXpMultiplierDef,
    RaceDef,
    SpellDef,
    SpellSchoolDef,
    StatDef,
    StatRuleDef,
    TextGroupDef,
)
from realmforge_tools.schema.room import (
    GPL_FILE,
    GPL_TITLE,
    LGPL_TITLE,
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
    key: str
    """The template's cache key (``rooms/cache.py``); empty when the template was not read."""
    load: Callable[[], Structure] = field(repr=False, compare=False)
    cache: RoomCache = field(repr=False, compare=False)

    @property
    def size(self) -> Pos:
        """The template's size, without decoding it when the cache knows it."""
        return self.derived("size", attrgetter("size"))

    @cached_property
    def structure(self) -> Structure:
        """The template's blocks and entities, decoded (or rebuilt from the cache) on first use."""
        return self.load()

    def derived[T](self, kind: str, compute: Callable[[Structure], T]) -> T:
        """``compute(self.structure)``, cached under ``kind`` for this template: a warm cache answers without the
        blocks. ``kind`` must name everything besides the structure that the value depends on."""
        if not self.key:
            return compute(self.structure)
        return self.cache.derived(kind, self.key, lambda: compute(self.structure))


@dataclass(frozen=True)
class RoomReview:
    """A ``review.yaml`` in a folder of ``content/rooms/``: decisions keyed by room path below that folder."""

    source: str
    folder: str
    """The folder's path below ``content/rooms/`` ("" for the folder itself)."""
    decisions: dict[str, str]


@dataclass(frozen=True)
class RoomNotice:
    """A third-party source's ``LICENSE``, ``NOTICE`` or (LGPL-3.0) ``COPYING`` in
    ``content/rooms/third_party/<source>/``, which the jar carries under ``META-INF/third_party/<source>/``
    (ADR 0028)."""

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
    spell_schools: tuple[Entry[SpellSchoolDef], ...]
    dimensions: tuple[Entry[DimensionRulesDef], ...]
    races: tuple[Entry[RaceDef], ...]
    classes: tuple[Entry[ClassDef], ...]
    texts: tuple[Entry[TextGroupDef], ...]
    dungeons: tuple[Entry[DungeonTypeDef], ...]
    chapters: tuple[Entry[ChapterDef], ...]
    kill_xp_multipliers: tuple[Entry[KillXpMultiplierDef], ...]
    spells: tuple[Entry[SpellDef], ...]
    worldgen: tuple[WorldgenFile, ...]
    rooms: tuple[RoomEntry, ...] = ()
    room_reviews: tuple[RoomReview, ...] = ()
    room_notices: tuple[RoomNotice, ...] = ()

    def entries(self, type_dir: str) -> tuple[Entry, ...]:
        """The entries of the YAML content type in ``content/<type_dir>/``."""
        return getattr(self, type_dir)


def load_content(paths: RepoPaths, *, read_templates: bool = True) -> Content:
    """Load and schema-validate every file in ``content/``. Raises ValidationFailed listing all problems.

    A room template is decoded only on a miss of the room cache (``rooms/cache.py``); a hit leaves the decoding to
    the first use of ``RoomEntry.structure``. Without ``read_templates`` the templates are not even read, so a broken
    one goes unreported: for callers that need only the rooms' ids and sidecars."""
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

    rooms, reviews, notices = _load_rooms(room_files, paths, problems, read_templates=read_templates)
    if problems:
        raise ValidationFailed(problems)

    def by_id(entries: list[Entry]) -> tuple[Entry, ...]:
        return tuple(sorted(entries, key=lambda e: (e.model.id, e.source)))

    return Content(
        items=by_id(loaded["items"]),
        curves=by_id(loaded["curves"]),
        stats=by_id(loaded["stats"]),
        stat_rules=by_id(loaded["stat_rules"]),
        spell_schools=by_id(loaded["spell_schools"]),
        dimensions=by_id(loaded["dimensions"]),
        races=by_id(loaded["races"]),
        classes=by_id(loaded["classes"]),
        texts=by_id(loaded["texts"]),
        dungeons=by_id(loaded["dungeons"]),
        chapters=by_id(loaded["chapters"]),
        kill_xp_multipliers=by_id(loaded["kill_xp_multipliers"]),
        spells=by_id(loaded["spells"]),
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
    files: list[Path], paths: RepoPaths, problems: list[Problem], *, read_templates: bool
) -> tuple[tuple[RoomEntry, ...], tuple[RoomReview, ...], tuple[RoomNotice, ...]]:
    """Pair every template with its sidecar, read both, and read the review files."""
    root = paths.content / ROOMS_DIR
    cache = RoomCache(paths.cache / "rooms" / "parsed" if read_templates else None)
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
        if file.name in (*LICENSE_FILES, GPL_FILE):
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
        loaded = _load_template(template, cache, read_templates)
        if isinstance(loaded, str):
            problems.append(Problem(paths.rel(template), "", f"not a usable structure template: {loaded}"))
            continue
        if entry is not None:
            key, load = loaded
            rooms.append(RoomEntry(room_id, entry.source, paths.rel(template), entry.model, key, load, cache))
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
    for source in sorted({source for source, _ in license_files}):
        problems += _gpl_problems(source, license_files, paths)
    notices = tuple(
        RoomNotice(source, name, file.read_bytes()) for (source, name), file in sorted(license_files.items())
    )
    return tuple(rooms), tuple(sorted(reviews, key=lambda r: r.folder)), notices


def _load_template(template: Path, cache: RoomCache, read: bool) -> tuple[str, Callable[[], Structure]] | str:
    """The template's cache key and how to get its structure, or why it is unusable. A template is decoded here only
    when the cache lacks it (a cached one decoded before); one that is not read is decoded on first use."""

    def decode() -> Structure:
        return read_structure(template.read_bytes())

    if not read:
        return "", decode
    data = template.read_bytes()
    key = template_key(data)
    if cache.has_structure(key):
        return key, lambda: cache.structure(key) or decode()
    try:
        structure = read_structure(data)
    except StructureError as e:
        return str(e)
    cache.put_structure(key, structure)
    return key, lambda: structure


def _gpl_problems(source: str, license_files: dict[tuple[str, str], Path], paths: RepoPaths) -> list[Problem]:
    """An LGPL-3.0 source needs the GPL-3.0 text as ``COPYING``, which no other source has (ADR 0028)."""
    folder = f"content/{ROOMS_DIR}/third_party/{source}/"
    license_file, gpl_file = license_files.get((source, "LICENSE")), license_files.get((source, GPL_FILE))
    lgpl = license_file is not None and _starts_with_title(license_file, LGPL_TITLE)
    if lgpl and gpl_file is None:
        return [Problem(folder, "", f"an LGPL-3.0 source needs the GPL-3.0 text as {GPL_FILE} (ADR 0028)")]
    if gpl_file is not None and not lgpl:
        return [Problem(paths.rel(gpl_file), "", f"only an LGPL-3.0 source (its LICENSE) carries {GPL_FILE}")]
    if gpl_file is not None and not _starts_with_title(gpl_file, GPL_TITLE):
        return [Problem(paths.rel(gpl_file), "", f"{GPL_FILE} must hold the GPL-3.0 text")]
    return []


def _starts_with_title(file: Path, title: str) -> bool:
    """Whether the license text's first non-empty line is ``title`` (license files indent it)."""
    for line in file.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip():
            return line.strip() == title
    return False


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
