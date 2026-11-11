"""Spell models (RLM-640, ADR 0073, ADR 0075): the GeckoLib models of the spell models whose shape is ``cubes``.

A model's source is ``art/models/spell/<id>.geo.json``, Bedrock geometry as Blockbench exports it for GeckoLib (or as
a script wrote it: a jagged chunk is geometry, not painting). ``rf gen`` checks it and writes the game's model,
``assets/realmforge/geckolib/models/spell/<id>.geo.json``:

- **At most 64 cubes** (``MAX_CUBES``), each with a size above 0: the cap keeps a crowd of casters cheap, and a
  forming model grows cube by cube.
- **One material for every model:** the source's own UVs are dropped. Every face of every cube is mapped onto the
  look's 16 x 16 material pixel for pixel, as a block's faces take a block texture, each cube from another corner of
  the tile, so a model needs no texture of its own (``geometry``).
- **Bones are not rotated:** the game draws the cubes themselves (``client/render/SpellMeshes``), not the bones, so a
  rotated group in Blockbench would be lost. Rotate the cubes.
- **Nothing is inflated:** a cube is written and drawn at its own size, so Blockbench's ``inflate`` on a cube or a
  bone would be lost and the cube thinner in the game than its source shows. Give the cube that size instead.

The model points along -z, Blockbench's north, the way an entity model faces: a ``forward`` model's tip is there.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.problems import Problem
from realmforge_tools.project import MOD_ID, RepoPaths

MODELS_DIR = Path("art") / "models" / "spell"
SUFFIX = ".geo.json"
MAX_CUBES = 64
# A look's material is one 16 x 16 tile (ADR 0073).
MATERIAL_SIZE = 16
CUBES = "cubes"
_FACES = ("north", "east", "south", "west", "up", "down")

Triple = tuple[float, float, float]


class SpellModelError(Exception):
    """A model source that ``rf gen`` cannot use, with what is wrong."""


@dataclass(frozen=True)
class Cube:
    """One cube of a source, in Bedrock's terms: its lowest corner, its size, and its rotation in degrees about its
    pivot (the pixels of the model, 16 a block)."""

    origin: Triple
    size: Triple
    pivot: Triple | None = None
    rotation: Triple | None = None


@dataclass(frozen=True)
class Source:
    """A model source: its cubes in the file's order."""

    cubes: tuple[Cube, ...]


def models_dir(paths: RepoPaths) -> Path:
    return paths.root / MODELS_DIR


def source_path(paths: RepoPaths, model_id: str) -> Path:
    """Where the source of the model ``model_id`` is: ``art/models/spell/<id>.geo.json``."""
    return models_dir(paths) / f"{model_id}{SUFFIX}"


def asset_path(model_id: str) -> str:
    """The game's model below ``assets/realmforge/``: ``geckolib/models/spell/<id>.geo.json``."""
    return f"geckolib/models/spell/{model_id}{SUFFIX}"


def ids(paths: RepoPaths) -> tuple[str, ...]:
    """The ids that have a model source, sorted."""
    directory = models_dir(paths)
    if not directory.is_dir():
        return ()
    return tuple(sorted(file.name.removesuffix(SUFFIX) for file in directory.glob(f"*{SUFFIX}")))


def _triple(value: Any, what: str) -> Triple:
    if (
        not isinstance(value, list)
        or len(value) != 3
        or not all(isinstance(number, int | float) and not isinstance(number, bool) for number in value)
    ):
        raise SpellModelError(f"{what} is three numbers, not {value!r}")
    return (float(value[0]), float(value[1]), float(value[2]))


def _no_inflate(part: dict[str, Any], where: str) -> None:
    """Refuses a bone or cube that Blockbench inflates: the cubes are written and drawn at their own size, so an
    inflated one would be thinner in the game than its source shows."""
    inflate = part.get("inflate", 0)
    if isinstance(inflate, bool) or not isinstance(inflate, int | float):
        raise SpellModelError(f"{where}: inflate is a number, not {inflate!r}")
    if inflate != 0:
        raise SpellModelError(
            f"{where} is inflated by {inflate}: the game draws a spell model's cubes at their own size, so give the "
            "cube that size instead"
        )


def parse(data: Any) -> Source:
    """The cubes of Bedrock geometry ``data``. Raises ``SpellModelError`` for anything the game would not draw as the
    source shows it."""
    geometries = data.get("minecraft:geometry") if isinstance(data, dict) else None
    if not isinstance(geometries, list) or len(geometries) != 1 or not isinstance(geometries[0], dict):
        raise SpellModelError('a model source is Bedrock geometry with one entry in "minecraft:geometry"')
    bones = geometries[0].get("bones")
    if not isinstance(bones, list):
        raise SpellModelError('the geometry has no "bones"')
    cubes: list[Cube] = []
    for bone in bones:
        if not isinstance(bone, dict):
            raise SpellModelError("a bone is an object")
        name = bone.get("name", "?")
        if "rotation" in bone and any(_triple(bone["rotation"], f"bone {name}: rotation")):
            raise SpellModelError(
                f"bone {name} is rotated: the game draws a spell model's cubes, not its bones, so rotate the cubes"
            )
        _no_inflate(bone, f"bone {name}")
        for index, cube in enumerate(bone.get("cubes", [])):
            where = f"bone {name}, cube {index}"
            if not isinstance(cube, dict):
                raise SpellModelError(f"{where} is an object")
            _no_inflate(cube, where)
            origin = _triple(cube.get("origin"), f"{where}: origin")
            size = _triple(cube.get("size"), f"{where}: size")
            if min(size) <= 0:
                raise SpellModelError(f"{where}: every side of its size is above 0, not {list(size)}")
            pivot = _triple(cube["pivot"], f"{where}: pivot") if "pivot" in cube else None
            rotation = _triple(cube["rotation"], f"{where}: rotation") if "rotation" in cube else None
            cubes.append(Cube(origin, size, pivot, rotation))
    if not cubes:
        raise SpellModelError("the model has no cube")
    if len(cubes) > MAX_CUBES:
        raise SpellModelError(f"the model has {len(cubes)} cubes; a spell model has at most {MAX_CUBES}")
    return Source(tuple(cubes))


def read(path: Path) -> Source:
    """The source at ``path``. Raises ``SpellModelError`` when it cannot be read or used."""
    try:
        data = json.loads(path.read_text("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as e:
        raise SpellModelError(f"cannot be read as JSON: {e}") from e
    return parse(data)


def problems(paths: RepoPaths, content: Content) -> list[Problem]:
    """What is wrong between ``content/spell_models/`` and ``art/models/spell/``: a ``cubes`` model without its
    source, a source the game cannot draw, a source no ``cubes`` model names, and a file there that is no source."""
    found: list[Problem] = []
    wanted = {entry.model.id: entry for entry in content.spell_models if entry.model.shape == CUBES}
    for model_id, entry in wanted.items():
        path = source_path(paths, model_id)
        if not path.is_file():
            found.append(
                Problem(
                    entry.source,
                    "shape",
                    f"a cubes model has its source at {paths.rel(path)} (art/README.md): the file is missing",
                )
            )
            continue
        try:
            read(path)
        except SpellModelError as e:
            found.append(Problem(paths.rel(path), "", str(e)))
    directory = models_dir(paths)
    if directory.is_dir():
        for file in sorted(p for p in directory.rglob("*") if p.is_file()):
            rel = paths.rel(file)
            if file.parent != directory or not file.name.endswith(SUFFIX):
                if file.name != "README.md":
                    found.append(
                        Problem(rel, "", f"a spell model's source is {MODELS_DIR.as_posix()}/<id>{SUFFIX}")
                    )
            elif file.name.removesuffix(SUFFIX) not in wanted:
                found.append(
                    Problem(
                        rel,
                        "",
                        f"no spell model of shape cubes has the id {file.name.removesuffix(SUFFIX)!r} "
                        "(content/spell_models/)",
                    )
                )
    return found


def _number(value: float) -> float | int:
    """Whole numbers as ints, so the JSON reads as Blockbench writes it."""
    return int(value) if value == int(value) else value


def _numbers(values: Triple) -> list[float | int]:
    return [_number(value) for value in values]


def _face_sizes(size: Triple) -> dict[str, tuple[float, float]]:
    """Each face's width and height in pixels: the sides run along x and y, z and y, or x and z."""
    x, y, z = size
    return {"north": (x, y), "south": (x, y), "east": (z, y), "west": (z, y), "up": (x, z), "down": (x, z)}


def _material_uv(cube_index: int, size: Triple) -> dict[str, dict[str, list[float | int]]]:
    """The cube's faces on the material tile, pixel for pixel: each face a rectangle of its own size (at most the
    tile), each cube and face from another corner of the tile, so neighbouring cubes do not repeat one patch."""
    uv: dict[str, dict[str, list[float | int]]] = {}
    for face_index, (face, (width, height)) in enumerate(_face_sizes(size).items()):
        width = min(width, MATERIAL_SIZE)
        height = min(height, MATERIAL_SIZE)
        # Whole-pixel corners, spread by two small primes: the same in every run and on every machine.
        room_u = int(MATERIAL_SIZE - width)
        room_v = int(MATERIAL_SIZE - height)
        u = (cube_index * 7 + face_index * 5) % (room_u + 1)
        v = (cube_index * 11 + face_index * 3) % (room_v + 1)
        uv[face] = {"uv": [u, v], "uv_size": [_number(width), _number(height)]}
    return uv


def geometry(model_id: str, source: Source) -> dict[str, object]:
    """The game's model of ``source``: its cubes as they are, in one bone, their faces mapped onto the material."""
    cubes: list[dict[str, object]] = []
    for index, cube in enumerate(source.cubes):
        data: dict[str, object] = {"origin": _numbers(cube.origin), "size": _numbers(cube.size)}
        if cube.pivot is not None:
            data["pivot"] = _numbers(cube.pivot)
        if cube.rotation is not None:
            data["rotation"] = _numbers(cube.rotation)
        data["uv"] = _material_uv(index, cube.size)
        cubes.append(data)
    assert set(_FACES) == set(_face_sizes((1, 1, 1)))
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": f"geometry.{MOD_ID}.spell.{model_id}",
                    "texture_width": MATERIAL_SIZE,
                    "texture_height": MATERIAL_SIZE,
                },
                "bones": [{"name": "model", "pivot": [0, 0, 0], "cubes": cubes}],
            }
        ],
    }
