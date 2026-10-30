"""Drawn status effect icons: 18x18 pixel rows (vanilla's effect icon size) for the effects that should not wait for
generated art, in the palette's colours, as the font's symbols are (``fonts.py``). ``effects.py`` writes an effect's
drawn icon to ``textures/mob_effect/<id>.png`` instead of its placeholder disc. Matěj asked for these on 2026-10-30
(C4's playtest): Battle Shout a shouting man, Ice Barrier an ice barrier.
"""

from __future__ import annotations

from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.generate.textures import EFFECT_ICON_SIZE

# Pixel colours, each a colour of art/palette/realmforge.gpl (its name in the comment).
PIXELS: dict[str, Rgba] = {
    ".": (0, 0, 0, 0),
    "K": (11, 11, 14, 255),  # black
    "W": (255, 255, 255, 255),  # white
    "w": (230, 230, 231, 255),  # iron 3
    "d": (134, 90, 66, 255),  # skin 2
    "s": (182, 132, 101, 255),  # skin 3
    "S": (222, 178, 144, 255),  # skin 4
    "h": (87, 63, 38, 255),  # wood 4
    "H": (133, 101, 64, 255),  # wood 6
    "m": (50, 12, 14, 255),  # blood 1
    "t": (163, 41, 36, 255),  # blood 4
    "r": (222, 30, 44, 255),  # ruby 3
    "1": (15, 24, 56, 255),  # frost 1
    "2": (24, 44, 98, 255),  # frost 2
    "3": (33, 72, 145, 255),  # frost 3
    "4": (52, 113, 196, 255),  # frost 4
    "5": (88, 160, 226, 255),  # frost 5
    "6": (140, 204, 244, 255),  # frost 6
    "7": (200, 236, 252, 255),  # frost 7
}

# Battle Shout: a man's face, brows down, mouth wide open in a war cry, the cry's waves in red on both sides.
BATTLE_SHOUT_ROWS = (
    "..................",
    "......KKKKKK......",
    ".....KhHHHhhK.....",
    "....KhHhhhhhhK....",
    "....KhhhhhhhhK....",
    "....KsKssssKsK....",
    "..r.KSsKssKsSK.r..",
    ".r..KSwKssKwSK..r.",
    "r...KSssssssSK...r",
    "r...KsSsddssdK...r",
    "r..KdssKKKKssdK..r",
    ".r.KdsKwmmwKsdK.r.",
    "..rKdsKmttmKsdKr..",
    "...KddKmttmKddK...",
    "....KdsKmmKsdK....",
    ".....KddKKddK.....",
    "......KKKKKK......",
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
