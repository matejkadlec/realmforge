"""Set pieces: ``content/set_pieces/<id>.nbt`` -> ``data/realmforge/structure/set_pieces/<id>.nbt``, normalized
(ADR 0050), and what varies in a piece, for the dungeon type entries (ADR 0077).

The template is written back normalized (``rooms/structure.py``), so the game knows it as the structure
``realmforge:set_pieces/<id>``. The fit rule's variants become data the dungeon type entry carries with the piece
(``variants``, written by ``generate/dungeons.py``): one entry per block of the template that varies, with where it
is, the chance that it changes and the block state it then becomes:

- **chance:** a block that is there only by chance becomes air at one minus its chance;
- **swaps:** a block that may become another becomes the swap's state at the swap's chance, with the properties the
  swap ``keep`` names taken over from the block it replaces.

The game draws each of them from the layout's seed when it plans a run (``SetPieces``), so a piece varies from run to
run even where it stands in the same place: vanilla's ``rule`` processor, which wrote these until RLM-282, draws from
the block's position alone. Where the pieces fit is written with the dungeon types too. The QA mod gets the pieces
as expectations (``ContentSetPieceGameTests`` loads each one) and the masks the workshop jig builds
(``rooms/set_pieces.py`` ``NAMED_MASKS``).
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content, SetPieceEntry
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.rooms.set_pieces import NAMED_MASKS
from realmforge_tools.rooms.structure import AIR, BlockState, Structure, write_structure
from realmforge_tools.schema.set_piece import SET_PIECES_DIR, SetPieceDef

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"


def template_id(piece: SetPieceEntry) -> str:
    """The structure the jar gives the piece."""
    return f"{MOD_ID}:{SET_PIECES_DIR}/{piece.id}"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    structures = data_dir(paths) / "structure" / SET_PIECES_DIR
    for piece in content.set_pieces:
        files[structures / f"{piece.id}.nbt"] = piece.derived("normalized", write_structure)
    java = paths.qa_generated / "java"
    files[java_file_path(java, QA_GENERATED_PACKAGE, "GeneratedSetPieceExpectations")] = _expectations(
        content
    )
    files[java_file_path(java, QA_GENERATED_PACKAGE, "GeneratedSetPieceMasks")] = _masks()
    return files


def variants(model: SetPieceDef, structure: Structure) -> list[dict[str, Any]]:
    """The blocks of the piece that vary, in template order (by y, x, z): where each is, the chance that it changes,
    and the block state it then becomes (air for a block that is there only by chance)."""
    found: list[dict[str, Any]] = []
    for block in sorted(structure.blocks, key=lambda b: (b.pos[1], b.pos[0], b.pos[2])):
        chance = model.variants.chance.get(block.state.name)
        swap = model.variants.swaps.get(block.state.name)
        if chance is not None:
            found.append({"pos": list(block.pos), "chance": round(1 - chance, 6), "state": {"Name": AIR}})
        elif swap is not None:
            target = BlockState.parse(swap.to)
            properties = dict(target.properties)
            properties.update(
                {name: value for name in swap.keep if (value := block.state.get(name)) is not None}
            )
            state = BlockState(target.name, tuple(sorted(properties.items())))
            found.append({"pos": list(block.pos), "chance": swap.chance, "state": _state_json(state)})
    return found


def _state_json(state: BlockState) -> dict[str, Any]:
    if not state.properties:
        return {"Name": state.name}
    return {"Name": state.name, "Properties": dict(state.properties)}


def _expectations(content: Content) -> bytes:
    rows = []
    for piece in content.set_pieces:
        x, y, z = piece.structure.size
        rows.append(
            f"            new SetPieceExpectation({string_literal(template_id(piece))}, {x}, {y}, {z}, "
            f"{len(variants(piece.model, piece.structure))})"
        )
    pieces = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), "content/set_pieces/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/set_pieces/} holds, so a GameTest can check that the game loads every piece. */\n"
        "public final class GeneratedSetPieceExpectations {\n"
        "    /**\n"
        "     * One piece: its structure id ({@code realmforge:set_pieces/<id>}), its size and how many of its blocks vary\n"
        "     * (chance blocks and swaps).\n"
        "     */\n"
        "    public record SetPieceExpectation(String id, int sizeX, int sizeY, int sizeZ, int variants) {\n"
        "    }\n"
        "\n"
        f"    public static final List<SetPieceExpectation> SET_PIECES = {pieces};\n"
        "\n"
        "    private GeneratedSetPieceExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _masks() -> bytes:
    rows = []
    for named in NAMED_MASKS.values():
        lines = ", ".join(string_literal(" ".join(row)) for row in named.mask.rows)
        rows.append(
            f"            new NamedMask({string_literal(named.name)}, List.of({lines}), "
            f"{'true' if named.builds_walls else 'false'})"
        )
    masks = "List.of(\n" + ",\n".join(rows) + ")"
    source = (
        header(Path(__file__), "tools/realmforge_tools/rooms/set_pieces.py (NAMED_MASKS)")
        + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** The masks the set-piece workshop jig builds ({@code /qa setpiece jig <mask>}), as the mask report names them. */\n"
        "public final class GeneratedSetPieceMasks {\n"
        "    /**\n"
        "     * A mask: its name, its rows (the first the wall side, north; cells separated by spaces: W wall, . piece, _\n"
        "     * clearance, ? anything) and whether the piece builds its own W cells.\n"
        "     */\n"
        "    public record NamedMask(String name, List<String> rows, boolean buildsWalls) {\n"
        "    }\n"
        "\n"
        f"    public static final List<NamedMask> MASKS = {masks};\n"
        "\n"
        "    private GeneratedSetPieceMasks() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
