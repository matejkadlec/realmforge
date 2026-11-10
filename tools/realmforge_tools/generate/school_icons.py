"""Drawn spell school icons: 8x8 pixel rows, as the font's symbols are (``fonts.py``), in the palette's colours.

A school's icon goes inline before its numbers in spell tooltips, as a glyph the font's height
(``combat/SpellSchools#icon``), so it has to read at 8 px: too small for the texture generator, which is why these are
drawn by hand (``art/README.md``). The last column and the last row stay empty, the gap before the number (Matěj's
C3b playtest, 2026-10-21, RLM-46). ``spell_schools.py`` writes a school's drawn icon to
``textures/gui/sprites/school/<id>.png`` instead of its placeholder diamond. His picks: Fire a flame, Frost a
snowflake, Arcane a purple arcane symbol, Physical a burst.
"""

from __future__ import annotations

from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.generate.textures import SCHOOL_ICON_SIZE

# Pixel colours, each a colour of art/palette/realmforge.gpl (its name in the comment).
PIXELS: dict[str, Rgba] = {
    ".": (0, 0, 0, 0),
    "W": (255, 255, 255, 255),  # white
    "a": (146, 58, 22, 255),  # fire 1
    "b": (196, 95, 30, 255),  # fire 2
    "c": (232, 144, 46, 255),  # fire 3
    "d": (250, 195, 80, 255),  # fire 4
    "e": (255, 236, 160, 255),  # fire 5
    "4": (52, 113, 196, 255),  # frost 4
    "5": (88, 160, 226, 255),  # frost 5
    "6": (140, 204, 244, 255),  # frost 6
    "7": (200, 236, 252, 255),  # frost 7
    "p": (96, 14, 180, 255),  # arcane 2
    "q": (130, 50, 220, 255),  # arcane 3
    "r": (170, 110, 240, 255),  # arcane 4
    "s": (222, 192, 240, 255),  # shadow 7
}

# Fire: a flame, dark orange outside, yellow and almost white at its heart.
FIRE_ROWS = (
    "...b....",
    "..bc....",
    "..bcb...",
    ".bcdcb..",
    "bcdedcb.",
    "bcdedcb.",
    ".bcdcb..",
    "........",
)
# Frost: a snowflake of six arms round a white middle.
FROST_ROWS = (
    "...7....",
    ".5.6.5..",
    "..565...",
    "766W667.",
    "..565...",
    ".5.6.5..",
    "...7....",
    "........",
)
# Arcane: a rune of our own, a diamond in a diamond with a bright eye (Matěj: "a purple arcane symbol").
ARCANE_ROWS = (
    "...q....",
    "..qrq...",
    ".qp.pq..",
    "qr.s.rq.",
    ".qp.pq..",
    "..qrq...",
    "...q....",
    "........",
)
# Physical: the burst of a blow, rays out of a bright middle, in the school's orange-brown.
PHYSICAL_ROWS = (
    "a..b..a.",
    ".b.c.b..",
    "..cdc...",
    "bcdedcb.",
    "..cdc...",
    ".b.c.b..",
    "a..b..a.",
    "........",
)
# A school's id -> its rows.
ICONS: dict[str, tuple[str, ...]] = {
    "fire": FIRE_ROWS,
    "frost": FROST_ROWS,
    "arcane": ARCANE_ROWS,
    "physical": PHYSICAL_ROWS,
}


def drawn_icon(school_id: str) -> bytes | None:
    """The school's drawn icon as a PNG, or None for a school whose icon is still its placeholder."""
    rows = ICONS.get(school_id)
    if rows is None:
        return None
    if len(rows) != SCHOOL_ICON_SIZE or any(len(row) != SCHOOL_ICON_SIZE for row in rows):
        raise ValueError(f"the {school_id} icon is not {SCHOOL_ICON_SIZE} rows of {SCHOOL_ICON_SIZE} pixels")
    if any(row[-1] != "." for row in rows) or set(rows[-1]) != {"."}:
        raise ValueError(f"the {school_id} icon's last column and row stay empty, the gap before the number")
    return encode_rgba(SCHOOL_ICON_SIZE, SCHOOL_ICON_SIZE, [PIXELS[letter] for row in rows for letter in row])
