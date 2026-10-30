"""Drawn status effect icons: 18x18 pixel rows (vanilla's effect icon size) for the effects that should not wait for
generated art, in the palette's colours, as the font's symbols are (``fonts.py``). ``effects.py`` writes an effect's
drawn icon to ``textures/mob_effect/<id>.png`` instead of its placeholder disc. Matěj asked for these on 2026-10-30
(C4's playtest): Battle Shout a shouting head, Ice Barrier an ice barrier.
"""

from __future__ import annotations

from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.generate.textures import EFFECT_ICON_SIZE

# Pixel colours, each a colour of art/palette/realmforge.gpl (its name in the comment).
PIXELS: dict[str, Rgba] = {
    ".": (0, 0, 0, 0),
    "K": (11, 11, 14, 255),  # black
    "W": (255, 255, 255, 255),  # white
    "A": (28, 16, 44, 255),  # shadow 1
    "B": (48, 26, 76, 255),  # shadow 2
    "C": (73, 40, 110, 255),  # shadow 3
    "D": (102, 59, 146, 255),  # shadow 4
    "E": (138, 92, 182, 255),  # shadow 5
    "e": (250, 230, 140, 255),  # gold 6
    "m": (50, 12, 14, 255),  # blood 1
    "y": (255, 236, 160, 255),  # fire 5
    "o": (250, 195, 80, 255),  # fire 4
    "f": (232, 144, 46, 255),  # fire 3
    "1": (15, 24, 56, 255),  # frost 1
    "2": (24, 44, 98, 255),  # frost 2
    "3": (33, 72, 145, 255),  # frost 3
    "4": (52, 113, 196, 255),  # frost 4
    "5": (88, 160, 226, 255),  # frost 5
    "6": (140, 204, 244, 255),  # frost 6
    "7": (200, 236, 252, 255),  # frost 7
}

# Battle Shout: a head in profile, turned left, its eye aglow and its mouth wide open, a war cry bursting from it in
# rays of light (WoW's Battle Shout icon as Matěj's reference, 2026-10-30).
BATTLE_SHOUT_ROWS = (
    "..................",
    "o.........KKKKK...",
    ".f.......KEEDDCK..",
    "..f.....KEDDDCCCK.",
    "o..f...KEDDDCCCCK.",
    ".o..o..KDeKDCCCBK.",
    "..o.o.KDDDDCCCBK..",
    "yy..yKDDDDCCCBK...",
    "..yyyKKmmKCCCBAK..",
    "yyyyyyymmKCCCBBAK.",
    "..yyyKKmmKCCCBBAK.",
    "yy..yKDDDKCCBBAK..",
    "..o.o.KKDDCCBBAK..",
    ".o..o...KDCCBBAK..",
    "o..f....KDCBBAK...",
    "..f......KBBAK....",
    ".f........KKK.....",
    "..................",
)

# Ice Barrier: a shield of ice, lit from the upper left, a crack of light across it.
ICE_BARRIER_ROWS = (
    "..................",
    "........KK........",
    "......KK77KK......",
    "....KK776655KK....",
    "..KK7766W5544KK...",
    "..K776655W54433K..",
    "..K765554W44333K..",
    "..K76555W444332K..",
    "..K6555W4444332K..",
    "..K655W54443322K..",
    "..K65W554443321K..",
    "...K6555444332K...",
    "...K655544433K....",
    "....K6554432K.....",
    ".....K65432K......",
    "......K543K.......",
    ".......KKK........",
    "..................",
)

# Each drawn icon, by the effect's id.
ICONS: dict[str, tuple[str, ...]] = {
    "battle_shout": BATTLE_SHOUT_ROWS,
    "ice_barrier": ICE_BARRIER_ROWS,
}


def drawn_icon(effect_id: str) -> bytes | None:
    """The effect's drawn icon as a PNG, or None when it has none (its placeholder stays)."""
    rows = ICONS.get(effect_id)
    if rows is None:
        return None
    if len(rows) != EFFECT_ICON_SIZE or any(len(row) != EFFECT_ICON_SIZE for row in rows):
        raise ValueError(f"the drawn icon of {effect_id} is not {EFFECT_ICON_SIZE}x{EFFECT_ICON_SIZE} pixels")
    pixels = [PIXELS[char] for row in rows for char in row]
    return encode_rgba(EFFECT_ICON_SIZE, EFFECT_ICON_SIZE, pixels)
