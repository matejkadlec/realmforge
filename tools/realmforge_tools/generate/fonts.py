"""Our fonts: glyphs of our own that text draws (RLM-340), as a bitmap font provider and its texture.

``assets/realmforge/font/symbols.json`` is the font ``realmforge:symbols``; each symbol is one private-use code point
in it, drawn on the default Minecraft font's grid (8x8 cells, ascent 7: rows 0 to 6 above the baseline, the bottom
row below it), so it sits beside letters at their height and pixel size. A bitmap glyph takes the text colour as a
tint: its white pixels take the colour of the text that draws it, its black pixels stay black. Spaces of our own,
narrow or negative, move the next glyph (a ``space`` provider in the same font). The game reads the font and the code
points from ``client/compat/MobLevelLabel`` (``SYMBOLS``, ``STEP_BACK``, ``SKULL``).
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.project import MOD_ID, RepoPaths

FONT = "symbols"
CELL = 8
ASCENT = 7
# Pixel colours, each a colour of art/palette/realmforge.gpl.
PIXELS: dict[str, Rgba] = {
    ".": (0, 0, 0, 0),
    "W": (255, 255, 255, 255),  # white: drawn in the text's colour
    "K": (11, 11, 14, 255),  # black: stays black in any colour
}
# The skull of the mob level label (RLM-340), shown for a mob five or more levels above the player (RLM-338): a skull
# in the label's blood red with black eyes (Matěj's playtest, 2026-10-22).
SKULL_ROWS = (
    ".WWWWW..",
    "WWWWWWW.",
    "WKKWKKW.",
    "WKKWKKW.",
    "WWW.WWW.",
    ".WWWWW..",
    ".W.W.W..",
    "........",
)
# Each symbol: its code point and its rows, in the order the texture holds them, left to right.
SYMBOLS: tuple[tuple[str, tuple[str, ...]], ...] = (("\ue000", SKULL_ROWS),)
# Each space: its code point and its advance in pixels. U+E001 steps back over the bold space (4 pixels and the bold
# pixel) that the label's "Lv. " ends with, so the skull follows the dot as the next letter would (Matěj, 2026-10-22).
SPACES: tuple[tuple[str, int], ...] = (("\ue001", -5),)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    assets = paths.mod_generated / "resources" / "assets" / MOD_ID
    return {
        assets / "font" / f"{FONT}.json": dump_json(
            {
                "providers": [
                    {
                        "type": "bitmap",
                        "file": f"{MOD_ID}:font/{FONT}.png",
                        "ascent": ASCENT,
                        "chars": ["".join(char for char, _ in SYMBOLS)],
                    },
                    {"type": "space", "advances": dict(SPACES)},
                ]
            }
        ),
        assets / "textures" / "font" / f"{FONT}.png": texture(),
    }


def texture() -> bytes:
    """The symbols side by side, one 8x8 cell each."""
    for char, rows in SYMBOLS:
        if len(rows) != CELL or any(len(row) != CELL for row in rows):
            raise ValueError(f"symbol U+{ord(char):04X} is not {CELL}x{CELL} pixels")
    pixels = [PIXELS[rows[y][x]] for y in range(CELL) for _, rows in SYMBOLS for x in range(CELL)]
    return encode_rgba(CELL * len(SYMBOLS), CELL, pixels)
