"""Placeholder textures, used until real art exists for a piece of content."""

from __future__ import annotations

import colorsys
import hashlib

from realmforge_tools.formats.png import Rgba, encode_rgba

SIZE = 16


def placeholder_texture(seed: str) -> bytes:
    """A 16x16 checkered tile with an outline, colored by a hash of ``seed`` (the content id).

    Deliberately unlike vanilla's magenta/black "missing texture", so a placeholder is easy to tell apart
    from a broken texture reference.
    """
    hue = hashlib.sha256(seed.encode("utf-8")).digest()[0] / 256
    light = _rgb(hue, 0.45, 0.90)
    dark = _rgb(hue, 0.55, 0.65)
    outline = _rgb(hue, 0.70, 0.30)
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
