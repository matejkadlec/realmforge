"""Placeholder textures, used until real art exists for a piece of content."""

from __future__ import annotations

import colorsys
import hashlib
import math

from realmforge_tools.formats.png import Rgba, encode_rgba

SIZE = 16


def placeholder_texture(seed: str, color: int | None = None) -> bytes:
    """A 16x16 checkered tile with an outline, colored by a hash of ``seed`` (the content id), or in ``color``
    (0xRRGGBB) when given.

    Deliberately unlike vanilla's magenta/black "missing texture", so a placeholder is easy to tell apart
    from a broken texture reference.
    """
    if color is None:
        hue = hashlib.sha256(seed.encode("utf-8")).digest()[0] / 256
        light = _rgb(hue, 0.45, 0.90)
        dark = _rgb(hue, 0.55, 0.65)
        outline = _rgb(hue, 0.70, 0.30)
    else:
        base = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
        light, dark, outline = _shade(base, 1.2), _shade(base, 0.85), _shade(base, 0.4)
    transparent: Rgba = (0, 0, 0, 0)

    pixels: list[Rgba] = []
    last = SIZE - 1
    for y in range(SIZE):
        for x in range(SIZE):
            on_edge_x, on_edge_y = x in (0, last), y in (0, last)
            if on_edge_x and on_edge_y:
                pixels.append(transparent)  # rounded corners
            elif on_edge_x or on_edge_y:
                pixels.append(outline)
            else:
                pixels.append(light if (x // 4 + y // 4) % 2 == 0 else dark)
    return encode_rgba(SIZE, SIZE, pixels)


def _rgb(hue: float, saturation: float, value: float) -> Rgba:
    r, g, b = colorsys.hsv_to_rgb(hue, saturation, value)
    return round(r * 255), round(g * 255), round(b * 255), 255


def placeholder_block_texture(seed: str) -> bytes:
    """A 16x16 opaque, speckled tile for a full block, its grey tinted by a hash of ``seed`` (the content id).

    A block's faces render without transparency, so it has no see-through corners like ``placeholder_texture``; the
    speckles come from the same hash, so the tile is the same on every machine.
    """
    digest = hashlib.sha256(seed.encode("utf-8")).digest()
    hue = digest[0] / 256
    base = 0.42 + digest[1] / 256 * 0.30
    pixels: list[Rgba] = []
    for y in range(SIZE):
        for x in range(SIZE):
            cell = hashlib.sha256(f"{seed}/{x}/{y}".encode()).digest()[0]
            # Mostly the base value, with lighter and darker flecks.
            shade = (
                base + ((cell % 9) - 4) * 0.025 + (0.12 if cell > 236 else 0.0) - (0.10 if cell < 16 else 0.0)
            )
            pixels.append(_rgb(hue, 0.12, min(max(shade, 0.0), 1.0)))
    return encode_rgba(SIZE, SIZE, pixels)


def placeholder_portal_texture(seed: str) -> bytes:
    """A 16x16 see-through greyscale swirl for a portal block, which the client tints with a realm's colour.

    Its pixels are partly transparent, so the game renders the block translucent, as vanilla's nether portal; the
    swirl's phase comes from a hash of ``seed``, so the tile is the same on every machine.
    """
    phase = hashlib.sha256(seed.encode("utf-8")).digest()[0] / 256 * 2 * math.pi
    centre = (SIZE - 1) / 2
    pixels: list[Rgba] = []
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - centre, y - centre
            radius = math.hypot(dx, dy)
            # Bands that wind outwards: bright where the angle and the distance from the middle line up.
            band = (math.cos(math.atan2(dy, dx) * 2 + radius * 0.9 + phase) + 1) / 2
            grey = round(150 + 105 * band)
            alpha = round(150 + 70 * band)
            pixels.append((grey, grey, grey, alpha))
    return encode_rgba(SIZE, SIZE, pixels)


EFFECT_ICON_SIZE = 18


def placeholder_effect_icon(color: int, category: str) -> bytes:
    """An 18x18 status effect icon (vanilla's size) until real art exists: a disc in the effect's colour (``color``,
    0xRRGGBB) with a darker rim and a lighter upper left, marked with a white arrow up for a beneficial effect, a
    black arrow down for a harmful one and a dot for a neutral one, on a transparent background."""
    base = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    centre = (EFFECT_ICON_SIZE - 1) / 2
    marks = _effect_mark(category)
    mark_colour: Rgba = (0, 0, 0, 255) if category == "harmful" else (255, 255, 255, 255)
    pixels: list[Rgba] = []
    for y in range(EFFECT_ICON_SIZE):
        for x in range(EFFECT_ICON_SIZE):
            radius = math.hypot(x - centre, y - centre)
            if radius > 8.4:
                pixels.append((0, 0, 0, 0))
            elif (x, y) in marks:
                pixels.append(mark_colour)
            elif radius > 7.2:
                pixels.append(_shade(base, 0.45))
            else:
                # Lighter toward the upper left, as if lit from there.
                pixels.append(_shade(base, 1.15 - (x + y) / (4 * EFFECT_ICON_SIZE)))
    return encode_rgba(EFFECT_ICON_SIZE, EFFECT_ICON_SIZE, pixels)


SCHOOL_ICON_SIZE = 8


def placeholder_school_icon(color: int) -> bytes:
    """An 8x8 spell school icon until real art exists, drawn inline before a spell's numbers as a text glyph (8 px, the
    font's height): a diamond in the school's colour (``color``, 0xRRGGBB) with a darker rim and a lighter upper left, 7
    px across like a capital letter, and an empty last column and row so it keeps a pixel from the number."""
    base = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    pixels: list[Rgba] = []
    for y in range(SCHOOL_ICON_SIZE):
        for x in range(SCHOOL_ICON_SIZE):
            distance = abs(x - 3) + abs(y - 3)
            if x == SCHOOL_ICON_SIZE - 1 or y == SCHOOL_ICON_SIZE - 1 or distance > 3:
                pixels.append((0, 0, 0, 0))
            elif distance == 3:
                pixels.append(_shade(base, 0.55))
            else:
                # Lighter toward the upper left, as if lit from there.
                pixels.append(_shade(base, 1.3 - (x + y) / 12))
    return encode_rgba(SCHOOL_ICON_SIZE, SCHOOL_ICON_SIZE, pixels)


def _shade(rgb: tuple[int, int, int], factor: float) -> Rgba:
    r, g, b = (min(255, max(0, round(channel * factor))) for channel in rgb)
    return r, g, b, 255


def _effect_mark(category: str) -> set[tuple[int, int]]:
    """The pixels of the icon's mark: an arrow up (beneficial) or down (harmful), or a dot (neutral)."""
    if category == "neutral":
        return {(x, y) for x in range(7, 11) for y in range(7, 11)}
    marks: set[tuple[int, int]] = set()
    for row in range(4):
        # The arrow's head: rows of 2, 4, 6 and 8 pixels, centred like the 2-pixel shaft.
        for x in range(8 - row, 10 + row):
            marks.add((x, 4 + row))
    for y in range(8, 14):
        marks.update({(8, y), (9, y)})
    if category == "harmful":
        marks = {(x, EFFECT_ICON_SIZE - 1 - y) for x, y in marks}
    return marks


ORB_TEXTURE_SIZE = (64, 32)


def placeholder_orb_texture(seed: str, color: int) -> bytes:
    """The 64x32 texture of a spell projectile's orb (vanilla's shulker bullet model, whose three slabs take its
    upper left), until real art exists: opaque speckled stone in ``color`` (0xRRGGBB), the flecks from a hash of
    ``seed`` (the spell's id), so it is the same on every machine."""
    base = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    width, height = ORB_TEXTURE_SIZE
    pixels: list[Rgba] = []
    for y in range(height):
        for x in range(width):
            cell = hashlib.sha256(f"{seed}/{x}/{y}".encode()).digest()[0]
            factor = (
                1.0 + ((cell % 9) - 4) * 0.04 + (0.2 if cell > 236 else 0.0) - (0.25 if cell < 20 else 0.0)
            )
            pixels.append(_shade(base, factor))
    return encode_rgba(width, height, pixels)


EQUIPMENT_TEXTURE_SIZE = (64, 32)


def placeholder_equipment_texture(seed: str, color: int | None = None) -> bytes:
    """The 64x32 texture of worn armor (vanilla's humanoid armor layout, one for the body and one for the leggings),
    until real art exists: opaque cloth in ``color`` (0xRRGGBB), or a colour from a hash of ``seed`` (the look's id),
    woven in 2-pixel checks with a darker weft every fourth row, so it is the same on every machine."""
    if color is None:
        hue = hashlib.sha256(seed.encode("utf-8")).digest()[0] / 256
        light, dark, weft = _rgb(hue, 0.45, 0.85), _rgb(hue, 0.55, 0.70), _rgb(hue, 0.65, 0.45)
    else:
        base = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
        light, dark, weft = _shade(base, 1.1), _shade(base, 0.9), _shade(base, 0.6)
    width, height = EQUIPMENT_TEXTURE_SIZE
    pixels: list[Rgba] = []
    for y in range(height):
        for x in range(width):
            if y % 4 == 3:
                pixels.append(weft)
            else:
                pixels.append(light if (x // 2 + y // 2) % 2 == 0 else dark)
    return encode_rgba(width, height, pixels)
