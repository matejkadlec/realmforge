"""Set pieces: ``content/set_pieces/<id>.nbt`` -> ``data/realmforge/structure/set_pieces/<id>.nbt``, normalized, and
the piece's processor list ``data/realmforge/worldgen/processor_list/set_pieces/<id>.json`` (ADR 0050).

The template is written back normalized (``rooms/structure.py``), so the game knows it as the structure
``realmforge:set_pieces/<id>``. The processor list carries the fit rule's variants as vanilla ``rule`` processors,
which the builder applies with the piece (and the game turns their output with it):

- **chance:** a block that is there only by chance becomes air at one minus its chance (``random_block_match``);
- **swaps:** a block that may become another gets a rule per state the template holds it in
  (``random_blockstate_match``), whose output takes over the properties the swap ``keep`` names, as a rule's
  output is a fixed state (architecture 15.2, the block-state pitfall).

A piece without variants gets no processor list. Where the pieces fit is written with the dungeon types
(``generate/dungeons.py``). The QA mod gets the pieces as expectations (``ContentSetPieceGameTests`` loads each one)
and the masks the workshop jig builds (``rooms/set_pieces.py`` ``NAMED_MASKS``).
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content, SetPieceEntry
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.rooms.set_pieces import NAMED_MASKS
from realmforge_tools.rooms.structure import BlockState, Structure, write_structure
from realmforge_tools.schema.set_piece import SET_PIECES_DIR, SetPieceDef
from realmforge_tools.schema.worldgen import data_path

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
_ALWAYS = {"predicate_type": "minecraft:always_true"}


def template_id(piece: SetPieceEntry) -> str:
    """The structure the jar gives the piece."""
    return f"{MOD_ID}:{SET_PIECES_DIR}/{piece.id}"


def processor_list_id(piece: SetPieceEntry) -> str | None:
    """The piece's processor list, or None when it has no variants that need one."""
    variants = piece.model.variants
    return f"{MOD_ID}:{SET_PIECES_DIR}/{piece.id}" if variants.chance or variants.swaps else None


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    structures = data_dir(paths) / "structure" / SET_PIECES_DIR
    processors = data_dir(paths) / data_path("processor_list") / SET_PIECES_DIR
    for piece in content.set_pieces:
        files[structures / f"{piece.id}.nbt"] = piece.derived("normalized", write_structure)
        if processor_list_id(piece) is not None:
            files[processors / f"{piece.id}.json"] = dump_json(processor_list(piece.model, piece.structure))
    java = paths.qa_generated / "java"
    files[java_file_path(java, QA_GENERATED_PACKAGE, "GeneratedSetPieceExpectations")] = _expectations(
        content
    )
    files[java_file_path(java, QA_GENERATED_PACKAGE, "GeneratedSetPieceMasks")] = _masks()
    return files


def processor_list(model: SetPieceDef, structure: Structure) -> dict[str, Any]:
    """The rule processor of the piece's chance blocks and swaps."""
    rules: list[dict[str, Any]] = []
    for block, chance in sorted(model.variants.chance.items()):
        rules.append(
            {
                "input_predicate": {
                    "block": block,
                    "predicate_type": "minecraft:random_block_match",
                    "probability": round(1 - chance, 6),
                },
                "location_predicate": _ALWAYS,
                "output_state": {"Name": "minecraft:air"},
            }
        )
    for block, swap in sorted(model.variants.swaps.items()):
        target = BlockState.parse(swap.to)
        for state in sorted({b.state for b in structure.blocks if b.state.name == block}):
            properties = dict(target.properties)
            properties.update({name: value for name in swap.keep if (value := state.get(name)) is not None})
            rules.append(
                {
                    "input_predicate": {
                        "block_state": _state_json(state),
                        "predicate_type": "minecraft:random_blockstate_match",
                        "probability": swap.chance,
                    },
                    "location_predicate": _ALWAYS,
                    "output_state": _state_json(BlockState(target.name, tuple(sorted(properties.items())))),
                }
            )
    return {"processors": [{"processor_type": "minecraft:rule", "rules": rules}]}


def _state_json(state: BlockState) -> dict[str, Any]:
    if not state.properties:
        return {"Name": state.name}
    return {"Name": state.name, "Properties": dict(state.properties)}


def _expectations(content: Content) -> bytes:
    rows = []
    for piece in content.set_pieces:
        x, y, z = piece.structure.size
        processors = processor_list_id(piece) or ""
        rows.append(
            f"            new SetPieceExpectation({string_literal(template_id(piece))}, {x}, {y}, {z}, "
            f"{string_literal(processors)})"
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
        "     * One piece: its structure id ({@code realmforge:set_pieces/<id>}), its size and its processor list (empty\n"
        "     * when it has none).\n"
        "     */\n"
        "    public record SetPieceExpectation(String id, int sizeX, int sizeY, int sizeZ, String processorList) {\n"
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
