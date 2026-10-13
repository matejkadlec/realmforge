"""Headless room renders (A4e) through nucleation's renderer, with block models and textures from the official
client jar.

The renderer draws on Vulkan; without a GPU it needs a software driver (Mesa's lavapipe, package
``mesa-vulkan-drivers``; dev setup 5). The client jar is downloaded once into ``.cache/vanilla/<version>/`` and
read at runtime only: renders of vanilla blocks are fine for review, but no vanilla texture file is ever committed
(ADR 0028).
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from realmforge_tools.rooms.structure import AIR, Structure

# How high above the template's bottom the cutaway views cut the room, so the floor and the walls' lower part
# show. A lower room loses only its top layer (its ceiling).
CUT_HEIGHT = 5


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

    def render(self, structure: Structure, view: View, target: Path, cut: int = CUT_HEIGHT) -> None:
        nucleation = self._nucleation
        height = min(structure.size[1] - 1, cut) if view.cut else structure.size[1]
        schematic = nucleation.Schematic.create("room")
        for block in structure.blocks:
            if block.state.name != AIR and block.pos[1] < height:
                schematic.set_block_from_string(*block.pos, str(block.state))
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
