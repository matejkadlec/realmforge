"""GUI icons of our own, drawn as pixel rows (RLM-497), as sprites under ``textures/gui/sprites/icon/``.

A sprite there is ``realmforge:icon/<name>`` to the game's GUI sprite atlas (``GuiGraphicsExtractor#blitSprite``,
``SpriteIconButton``). Small icons in the vanilla GUI style are drawn here by hand, like the font's symbols
(``fonts.py``), in colours of ``art/palette/realmforge.gpl``, instead of going through the texture generator. The
game reads them from ``client/screen/SpellbookScreen`` (the gear).
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.project import MOD_ID, RepoPaths

# Pixel colours, each a colour of art/palette/realmforge.gpl.
PIXELS: dict[str, Rgba] = {
    ".": (0, 0, 0, 0),
    "K": (44, 44, 50, 255),  # stone 2: the outline
    "L": (211, 211, 213, 255),  # iron 2: the metal
    "D": (135, 135, 140, 255),  # stone 6: the metal's shaded side
}
# The spellbook's options button (RLM-497; Matěj, 2026-10-29: a gear like ⚙️ instead of the word): a gear of eight
# teeth with a hole in the middle, lit from the top left.
GEAR_ROWS = (
    "....KKKK....",
    ".KK.KLLK.KK.",
    ".KLKKLLKKLK.",
    "..KLLLLLDK..",
    "KKKLLKKLDKKK",
    "KLLLK..KDDDK",
    "KLLLK..KDDDK",
    "KKKLDKKDDKKK",
    "..KLDDDDDK..",
    ".KLKKDDKKDK.",
    ".KK.KDDK.KK.",
    "....KKKK....",
)
# Each icon: its name (the sprite realmforge:icon/<name>) and its rows.
ICONS: tuple[tuple[str, tuple[str, ...]], ...] = (("gear", GEAR_ROWS),)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    sprites = paths.mod_generated / "resources" / "assets" / MOD_ID / "textures" / "gui" / "sprites" / "icon"
    return {sprites / f"{name}.png": texture(name, rows) for name, rows in ICONS}


def texture(name: str, rows: tuple[str, ...]) -> bytes:
    """One icon's sprite, its pixels as its rows draw them."""
    width = len(rows[0]) if rows else 0
    if width == 0 or any(len(row) != width for row in rows):
        raise ValueError(f"icon {name!r} has rows of different lengths")
    pixels = [PIXELS[letter] for row in rows for letter in row]
    return encode_rgba(width, len(rows), pixels)
