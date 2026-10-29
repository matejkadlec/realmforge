"""``rf set-pieces import`` (RLM-281): a template the workshop jig's structure block saved -> a set piece in
``content/set_pieces/`` with a drafted fit rule (the [building workflow](../../../docs/design/set-pieces.md#the-building-workflow)).

``/qa setpiece jig <mask>`` names its structure block ``realmforge_qa:set_piece/<mask>_<token>``, a random token
no other jig of any world takes (RLM-390), so pressing Save writes
``<world>/generated/realmforge_qa/structure/set_piece/<mask>_<token>.nbt`` (``StructureTemplateManager#save``). The
import reads the mask from that name (``rooms/set_pieces.py`` ``NAMED_MASKS``) and the height from the template's
size, less the layers on top that hold only air (the jig sets the structure block's box as high as a piece may be,
so a lower piece needs no change to it), checks the piece (``piece_problems`` and the ids,
``rooms/check.py``), writes the template normalized as ``content/set_pieces/<id>.nbt`` and drafts
``<id>.yaml``: the mask, the height, ``builds_walls`` where the mask's piece brings its walls, ``tags`` left empty
to fill in (validation refuses the fit rule until then), the default roles and weight, and the save it came from.
An existing fit rule is kept: importing a save again replaces only the template, and a piece that came from another
save is never replaced. ``--from-world`` imports every save of a world that no piece names as its source yet, each
under its save's name as its id. Saves of older jigs, numbered ``<mask>_<n>``, import the same way.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field, replace
from pathlib import Path

from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.rooms.check import check_structure
from realmforge_tools.rooms.set_pieces import (
    AIR_BLOCKS,
    MAX_HEIGHT,
    NAMED_MASKS,
    Fit,
    NamedMask,
    piece_problems,
)
from realmforge_tools.rooms.structure import Structure, StructureError, read_structure, write_structure
from realmforge_tools.schema.set_piece import SET_PIECES_DIR, SetPieceDef
from realmforge_tools.yamlio import YamlLoadError, load_yaml

# Where the jig's structure block saves, below a world's folder, and the namespace of its names.
JIG_NAMESPACE = "realmforge_qa"
JIG_FOLDER = Path("generated") / JIG_NAMESPACE / "structure" / "set_piece"
_SAVE_NAME = re.compile(r"^(?P<mask>[a-z0-9_]+?)_(?P<token>[a-z0-9]+)$")
# Namespaces whose blocks a piece may hold: the rest wait for the full-pack registry dump (CLAUDE.md rule 3).
_KNOWN_NAMESPACES = ("minecraft", "realmforge")
_ID = re.compile(r"^[a-z][a-z0-9_]*$")


class SetPieceImportError(ValueError):
    """An import that cannot start: no such world or file, an id that is no content id."""


@dataclass
class ImportResult:
    source: Path
    id: str
    template: Path
    written: bool = False
    drafted: bool = False
    problems: list[str] = field(default_factory=list)


def save_name(path: Path) -> str:
    """The structure block's name of a jig save: ``realmforge_qa:set_piece/<file name>``."""
    return f"{JIG_NAMESPACE}:set_piece/{path.stem}"


def mask_of(path: Path) -> NamedMask | None:
    """The jig's mask a save is named after (``wall_3x1_k3j9z1`` or an older jig's ``wall_3x1_2`` -> ``wall_3x1``), or
    None."""
    match = _SAVE_NAME.match(path.stem)
    return NAMED_MASKS.get(match["mask"]) if match else None


def world_saves(world: Path) -> list[Path]:
    """Every save of the jig in a world, by name."""
    folder = world / JIG_FOLDER
    if not world.is_dir():
        raise SetPieceImportError(f"no world folder {world}")
    return sorted(folder.glob("*.nbt")) if folder.is_dir() else []


def imported_sources(target: Path) -> set[str]:
    """The saves the fit rules in ``target`` name as their source."""
    rules = sorted(target.glob("*.yaml")) if target.is_dir() else []
    return {source for rule in rules if (source := _source_of(rule)) is not None}


def import_piece(source: Path, piece_id: str, target: Path, universe: IdUniverse) -> ImportResult:
    """Import one save as the set piece ``piece_id`` into ``target`` (``content/set_pieces/``)."""
    if not _ID.match(piece_id):
        raise SetPieceImportError(f"{piece_id!r} is not a content id (lowercase snake_case)")
    result = ImportResult(source, piece_id, target / f"{piece_id}.nbt")
    named = mask_of(source)
    if named is None:
        names = ", ".join(sorted(NAMED_MASKS))
        result.problems.append(
            f"the file name {source.stem!r} names no jig mask: a save is <mask>_<n>.nbt, the mask one of {names}"
        )
        return result
    try:
        structure = read_structure(source.read_bytes())
    except (OSError, StructureError) as e:
        result.problems.append(f"not a readable structure template: {e}")
        return result
    structure = lowered(structure)
    height = structure.size[1]
    if not 1 <= height <= MAX_HEIGHT:
        result.problems.append(f"the template is {height} blocks high; a piece is 1 to {MAX_HEIGHT}")
        return result
    fit = Fit(named.mask, height, named.builds_walls)
    for location, message in piece_problems(fit, structure) + check_structure(structure, universe):
        result.problems.append(f"{location}: {message}")
    other_mods = sorted(
        {
            block.state.name.partition(":")[0]
            for block in structure.blocks
            if block.state.name.partition(":")[0] not in _KNOWN_NAMESPACES
        }
    )
    if other_mods:
        result.problems.append(
            f"blocks of other mods ({', '.join(other_mods)}): build with vanilla blocks only; the import and rf gen "
            "refuse other mods' blocks until the full-pack registry dump exists (CLAUDE.md rule 3)"
        )
    rule = target / f"{piece_id}.yaml"
    taken_by = _source_of(rule)
    if taken_by is not None and taken_by != save_name(source):
        result.problems.append(
            f"the piece {piece_id} came from {taken_by}, not from this save: import it under another --id"
        )
    if result.problems:
        return result
    target.mkdir(parents=True, exist_ok=True)
    result.template.write_bytes(write_structure(structure))
    result.written = True
    if not rule.exists():
        rule.write_text(draft_rule(piece_id, named, height, save_name(source)), encoding="utf-8")
        result.drafted = True
    return result


def _source_of(rule: Path) -> str | None:
    """The save a fit rule names as its source, or None (no rule, or one without a source)."""
    if not rule.is_file():
        return None
    try:
        data = load_yaml(rule)
    except YamlLoadError:
        return None
    return data["source"] if isinstance(data, dict) and isinstance(data.get("source"), str) else None


def lowered(structure: Structure) -> Structure:
    """The template without its top layers that hold only air (and no entity), down to one layer."""
    solid = [block.pos[1] for block in structure.blocks if block.state.name not in AIR_BLOCKS]
    solid += [int(entity.pos[1]) for entity in structure.entities]
    height = max(1, max(solid, default=0) + 1)
    if height >= structure.size[1]:
        return structure
    x, _, z = structure.size
    return replace(
        structure,
        size=(x, height, z),
        blocks=tuple(block for block in structure.blocks if block.pos[1] < height),
    )


def draft_rule(piece_id: str, named: NamedMask, height: int, source: str) -> str:
    """The drafted fit rule: what the save tells, and the rest to fill in."""
    mask = "\n".join(f"  {' '.join(row)}" for row in named.mask.rows)
    lines = [
        f"# yaml-language-server: $schema=../../tools/schemas/{SET_PIECES_DIR}.schema.json",
        f"# Imported by rf set-pieces import from {source} ({named.label}). Fill in the tags (what the piece is:",
        "# storage, prison, crypt, shrine, ...), check the roles and the weight, then run uv run rf gen.",
        f"id: {piece_id}",
        "mask: |",
        mask,
        f"height: {height}",
    ]
    if named.builds_walls:
        lines.append("builds_walls: true")
    lines += [
        "tags: []",
        f"roles: [{', '.join(SetPieceDef.model_fields['roles'].get_default(call_default_factory=True))}]",
        "weight: 1",
        "variants:",
        "  mirror: false",
        f"source: {source}",
    ]
    return "\n".join(lines) + "\n"
