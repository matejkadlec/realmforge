"""The looks of a realm portal's frame as art (RLM-301): their colours, and how one bricks tile becomes each look's
bricks and sealstone top. ``generate/portal_frame.py`` writes the textures; validation checks the colours here.

Every look is made from one tile, the Gatewright Bricks' approved art (``art/textures/block/<bricks id>.png``; Matěj,
2026-10-20: "the main texture would stay the same"):

- **the bricks:** the tile with its colours mapped onto the look's ramp by lightness, its darkest colour onto the
  ramp's first and its lightest onto the last, as ``art/variants.py`` recolours a group, but the whole tile at once;
- **the sealstone's top:** the look's bricks with the signet's socket drawn in, a round hole with a rim, empty in a
  dark frame and holding a stone in the open realm's colour in a lit one (Matěj: "this hole would act as a real
  hole").
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from realmforge_tools.art.palette import DEFAULT_PALETTE, Palette, PaletteError, Rgb, load_gpl, oklab
from realmforge_tools.art.texture import Image
from realmforge_tools.formats.png import Rgba
from realmforge_tools.problems import Problem
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.realm import FRAME_LOOKS

SIZE = 16
# The socket on the sealstone's top, measured from the middle of the face in pixels: the stone (or the hole) within
# the first, its rim out to the second.
SOCKET_CORE = 3.0
SOCKET_RIM = 4.6


@dataclass(frozen=True)
class FrameLook:
    """A look's colours, as names of the project palette.

    ``ramp`` is the bricks', dark to light. ``rim`` is the socket's rim, its shadowed upper left and its lit lower
    right, as a hole's edge is lit from the upper left. ``core`` is what the socket holds, its lit upper left and its
    shadowed rest: the dark of an empty hole, or the stone of the open realm.
    """

    ramp: tuple[str, ...]
    rim: tuple[str, str]
    core: tuple[str, str]


# Washed: pale and flat, "washed out bricks" (Matěj), the socket empty. Stone: Undervault's, the dark half of the
# stone ramp so it reads heavy, with a stone in the colour of Undervault's portal (slate blue) in the socket.
LOOKS: dict[str, FrameLook] = {
    "washed": FrameLook(
        ramp=("faded 1", "faded 2", "faded 3", "faded 4", "faded 5"),
        rim=("faded 1", "faded 4"),
        core=("stone 1", "black"),
    ),
    "stone": FrameLook(
        ramp=("stone 1", "stone 2", "stone 3", "stone 4", "stone 5"),
        rim=("black", "stone 5"),
        core=("slate 6", "slate 4"),
    ),
}
assert tuple(LOOKS) == FRAME_LOOKS, "art/frame_looks.py colours exactly the looks of schema/realm.py"


def problems(paths: RepoPaths) -> list[Problem]:
    """A palette colour a look names that the project palette does not have: ``rf gen`` could not draw the look."""
    try:
        names = {color.name for color in load_gpl(paths.root / DEFAULT_PALETTE).colors}
    except (OSError, PaletteError):
        # approved.problems reports a palette that cannot be read.
        return []
    found = []
    for look, colours in LOOKS.items():
        for name in dict.fromkeys((*colours.ramp, *colours.rim, *colours.core)):
            if name not in names:
                found.append(
                    Problem(
                        "tools/realmforge_tools/art/frame_looks.py",
                        f"LOOKS.{look}",
                        f"the project palette has no colour {name!r}",
                    )
                )
    return found


def bricks(tile: Image, look: str, palette: Palette) -> Image:
    """``tile`` in ``look``: each of its colours takes the colour of the look's ramp at its share of the way from the
    tile's darkest colour to its lightest (OKLab lightness)."""
    ramp = _colours(LOOKS[look].ramp, palette)
    present = sorted({pixel[:3] for pixel in tile.pixels}, key=lambda rgb: (oklab(rgb)[0], rgb))
    darkest, lightest = oklab(present[0])[0], oklab(present[-1])[0]
    span = lightest - darkest
    mapping: dict[Rgb, Rgb] = {}
    for rgb in present:
        # A tile of one colour takes the middle of the ramp.
        share = (oklab(rgb)[0] - darkest) / span if span > 0 else 0.5
        mapping[rgb] = ramp[round(share * (len(ramp) - 1))]
    return Image(tile.width, tile.height, tuple((*mapping[pixel[:3]], 255) for pixel in tile.pixels))


def sealstone_top(look_bricks: Image, look: str, palette: Palette) -> Image:
    """The sealstone's top in ``look``: the look's bricks with the socket in the middle, its rim shadowed on the upper
    left and lit on the lower right, and inside it the hole or the stone, lit on the upper left."""
    rim_shadow, rim_light = _colours(LOOKS[look].rim, palette)
    core_light, core_dark = _colours(LOOKS[look].core, palette)
    pixels: list[Rgba] = list(look_bricks.pixels)
    middle = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - middle, y + 0.5 - middle
            distance = math.hypot(dx, dy)
            if distance < SOCKET_CORE:
                pixels[y * SIZE + x] = (*(core_light if dx + dy < -1 else core_dark), 255)
            elif distance < SOCKET_RIM:
                pixels[y * SIZE + x] = (*(rim_shadow if dx + dy < 0 else rim_light), 255)
    return Image(SIZE, SIZE, tuple(pixels))


def _colours(names: tuple[str, ...], palette: Palette) -> tuple[Rgb, ...]:
    by_name = {color.name: color.rgb for color in palette.colors}
    return tuple(by_name[name] for name in names)
