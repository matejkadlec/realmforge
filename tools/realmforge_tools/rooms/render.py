"""Headless room renders (A4e) through nucleation's renderer, with block models and textures from the official
client jar.

The renderer draws on Vulkan; without a GPU it needs a software driver (Mesa's lavapipe, package
``mesa-vulkan-drivers``; dev setup 5). The client jar is downloaded once into ``.cache/vanilla/<version>/`` and
read at runtime only: renders of vanilla blocks are fine for review, but no vanilla texture file is ever committed
(ADR 0028).

Data markers (architecture 15.3) are part of what a room is for, so every view shows them, labelled
([ADR 0035](../../../docs/decisions/0035-room-galleries-show-every-marker-with-its-label.md)): a marker is drawn as a
block in its label's colour (``MARKER_STYLES``), even above a cutaway's cut, so a marker on a balcony shows in the
plan too. The game never places a marker; the colour stands for the spot. Set-piece spots (ADR 0050) show the same
way: each spot's floor cells as stained glass in its fit's colour (``SPOT_STYLES``), and a room with spots gets a
fifth view, ``DRESSING``, with a piece in every pattern.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from realmforge_tools.rooms.check import DATA_MODE, STRUCTURE_BLOCK
from realmforge_tools.rooms.structure import AIR, Pos, Structure, StructureBlock

# How high above the template's bottom the cutaway views cut the room, so the floor and the walls' lower part
# show. A lower room loses only its top layer (its ceiling).
CUT_HEIGHT = 5


@dataclass(frozen=True)
class MarkerStyle:
    """How the gallery shows a marker: the block drawn at its spot, the colour of its label on the page, and what
    the dungeon does there."""

    block: str
    colour: str
    meaning: str


# Every marker of rooms/check.py MARKERS has a style (a test holds new markers to it), each its own colour.
MARKER_STYLES = {
    "entrance": MarkerStyle(
        "minecraft:lime_concrete",
        "#5ea918",
        "where players appear when they enter the dungeon (one, in an entrance room)",
    ),
    "mob": MarkerStyle(
        "minecraft:red_concrete",
        "#c4473a",
        "a spot where the dungeon's encounters put a monster when the run starts",
    ),
    "chest": MarkerStyle(
        "minecraft:yellow_concrete",
        "#e0b030",
        "a spot where the dungeon's loot puts a chest when the run starts",
    ),
    "exit": MarkerStyle("minecraft:light_blue_concrete", "#3aafd9", "where the dungeon's way out stands"),
}
# A structure block that is no known marker (validation refuses it in content; an imported file may hold one).
UNKNOWN_MARKER = MarkerStyle(
    "minecraft:magenta_concrete", "#c74ebd", "not a marker the dungeon knows: validation refuses it"
)


def marker_label(block: StructureBlock) -> str | None:
    """The label of a data marker (its metadata; "" when it has none), or None for any other block."""
    data = block.nbt or {}
    if block.state.name != STRUCTURE_BLOCK or data.get("mode") != DATA_MODE:
        return None
    label = data.get("metadata")
    return label if isinstance(label, str) else ""


def marker_style(label: str) -> MarkerStyle:
    return MARKER_STYLES.get(label, UNKNOWN_MARKER)


@dataclass(frozen=True)
class View:
    name: str
    label: str
    cut: bool
    yaw: float | None = None
    top: bool = False


VIEWS = (
    View("outside", "Outside", cut=False),
    View("cutaway", "Cutaway", cut=True),
    View("cutaway_back", "Cutaway, other corner", cut=True, yaw=225.0),
    View("plan", "Plan", cut=True, top=True),
)
# The room with a set piece in every pattern of spots (seed 0), for a room that has spots.
DRESSING = View("dressing", "Sample dressing", cut=True)

# The colours of set-piece spots, one per fit in a room's order: none of them a marker's.
SPOT_STYLES = (
    ("minecraft:purple_stained_glass", "#8932b8"),
    ("minecraft:cyan_stained_glass", "#169c9c"),
    ("minecraft:orange_stained_glass", "#f9801d"),
    ("minecraft:blue_stained_glass", "#3c44aa"),
    ("minecraft:pink_stained_glass", "#f38baa"),
    ("minecraft:brown_stained_glass", "#835432"),
)


def drawn_blocks(
    structure: Structure, view: View, cut: int = CUT_HEIGHT, outlines: tuple[tuple[Pos, str], ...] = ()
) -> list[tuple[Pos, str]]:
    """The blocks a view draws, as (position, block state): no air, nothing at or above a cutaway's cut, and every
    data marker as its label's block and every outline block (a set-piece spot's) wherever it is."""
    height = min(structure.size[1] - 1, cut) if view.cut else structure.size[1]
    drawn = []
    outlined = {pos for pos, _ in outlines}
    for block in structure.blocks:
        label = marker_label(block)
        if label is not None:
            drawn.append((block.pos, marker_style(label).block))
        elif block.state.name != AIR and block.pos[1] < height and block.pos not in outlined:
            drawn.append((block.pos, str(block.state)))
    drawn += outlines
    return drawn


class RenderError(RuntimeError):
    """nucleation could not render; usually the missing Vulkan driver."""


class RoomRenderer:
    def __init__(self, client_jar: Path, size: int = 512) -> None:
        import nucleation  # deferred: a large native module that only rf rooms needs

        self._nucleation = nucleation
        self.size = size
        try:
            self._pack = nucleation.ResourcePack.from_bytes(client_jar.read_bytes())
        except nucleation.NucleationError as e:
            raise RenderError(f"cannot read {client_jar} as a resource pack: {e}") from e

    def render(
        self,
        structure: Structure,
        view: View,
        target: Path,
        cut: int = CUT_HEIGHT,
        outlines: tuple[tuple[Pos, str], ...] = (),
    ) -> None:
        nucleation = self._nucleation
        schematic = nucleation.Schematic.create("room")
        for pos, state in drawn_blocks(structure, view, cut, outlines):
            schematic.set_block_from_string(*pos, state)
        config = nucleation.RenderConfig.create(self.size, self.size)
        if view.top:
            config.set_orthographic(True)
            config.set_pitch(89.0)
            config.set_yaw(0.0)
        else:
            config.set_isometric()
            if view.yaw is not None:
                config.set_yaw(view.yaw)
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            nucleation.Renderer.render_to_file_with_pack(schematic, self._pack, config, str(target))
        except nucleation.NucleationError as e:
            raise RenderError(
                f"rendering failed ({e}); the renderer needs Vulkan, in WSL or a container the software driver "
                "lavapipe: `sudo apt-get install mesa-vulkan-drivers` (dev setup 5)"
            ) from e
