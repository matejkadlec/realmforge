"""Worn armor textures (RLM-505): a look's two 64 x 32 layers, composed from its cloth tile and its trim tile.

A piece of armor is drawn on the wearer with vanilla's humanoid armor model, which reads two textures of vanilla's
classic 64 x 32 layout (``HumanoidModel#createMesh``): ``humanoid`` for the helmet (head and hat), the chestplate
(body and arms) and the boots (legs), and ``humanoid_leggings`` for the leggings (body and legs, a little slimmer).
A texture generator cannot paint that layout, so the pipeline composes it, as ADR 0023 keeps it for entity textures
(the model first, a texture per face): each face is filled from the look's **cloth** tile, and the bands that make it
clothing come from its **trim** tile. Both are approved art (``art/approved.py``, kinds ``armor`` and ``armor_trim``):
16 x 16 tileable textures from the texture generator, cleaned to the project palette.

Our armor is the caster's (architecture 13), so the layout is a robe's:

==========  ==========================================================================================
Cap         the top of the head in cloth, its sides down to the brow, and a trim band round it that the
            hat layer repeats one pixel lower as a brim
Robes       body and sleeves in cloth: a trim collar, a belt with its shadow, a darker hem, a seam down
            the front, sleeves that end in a trim cuff above the hands
Pants       the legs in cloth down to a trim hem above the ankles, and a trim waistband
Sandals     a dark sole, a trim strap round the foot and thin straps up the ankle
==========  ==========================================================================================

Shadows are the cloth's own colour one or two steps down its palette ramp (``Ramps``), so a composed texture holds
only project palette colours when its tiles do. A look without a cloth tile keeps its placeholder
(``textures.placeholder_equipment_texture``); one without a trim tile has its bands in darker cloth.
"""

from __future__ import annotations

from dataclasses import dataclass

from realmforge_tools.art.palette import Palette
from realmforge_tools.art.palette import ramps as palette_ramps
from realmforge_tools.art.texture import Image
from realmforge_tools.formats.png import Rgba, encode_rgba

WIDTH, HEIGHT = 64, 32
TILE = 16
TRANSPARENT: Rgba = (0, 0, 0, 0)


@dataclass(frozen=True)
class Box:
    """A box of the humanoid model in the texture: its offset and its size in pixels, as ``texOffs(u, v)`` and
    ``addBox(..., w, h, d)`` give them. Its faces unwrap as vanilla's do: the top and the bottom side by side above a
    strip of the four sides (right, front, left, back)."""

    u: int
    v: int
    w: int
    h: int
    d: int

    @property
    def top(self) -> tuple[int, int, int, int]:
        return (self.u + self.d, self.v, self.w, self.d)

    @property
    def bottom(self) -> tuple[int, int, int, int]:
        return (self.u + self.d + self.w, self.v, self.w, self.d)

    @property
    def strip(self) -> tuple[int, int, int, int]:
        """The four sides as one band: x, y, width, height."""
        return (self.u, self.v + self.d, 2 * (self.w + self.d), self.h)

    @property
    def front(self) -> tuple[int, int, int, int]:
        return (self.u + self.d, self.v + self.d, self.w, self.h)

    @property
    def back(self) -> tuple[int, int, int, int]:
        return (self.u + 2 * self.d + self.w, self.v + self.d, self.w, self.h)


# HumanoidModel#createMesh: the head and its hat overlay, the body, the right arm and the right leg; the left arm
# and leg mirror the right ones' texture.
HEAD = Box(0, 0, 8, 8, 8)
HAT = Box(32, 0, 8, 8, 8)
BODY = Box(16, 16, 8, 12, 4)
ARM = Box(40, 16, 4, 12, 4)
LEG = Box(0, 16, 4, 12, 4)

# Rows of a box's side strip, counted from its top.
CAP_ROWS = 2  # cloth above the cap's band
BELT_ROW = 8
HEM_ROW = 11
CUFF_ROW = 9  # the sleeve ends here; the two rows below it show the hand
PANTS_HEM_ROW = 7  # the pants end here; the rows below show the ankle and the sandal
WAIST_ROW = 7  # the leggings layer covers the body from here down
STRAP_ROW = 10
SOLE_ROW = 11


class Ramps:
    """The palette's ramps: colours named ``<ramp> <n>`` in order, dark to light. ``darker`` steps a colour down its
    ramp, which is how the composer shades cloth without leaving the palette."""

    def __init__(self, palette: Palette) -> None:
        self._steps: dict[tuple[int, int, int], list[tuple[int, int, int]]] = {}
        for members in palette_ramps(palette).values():
            ordered = [color.rgb for color in members]
            for position, rgb in enumerate(ordered):
                # A colour listed in two ramps keeps its first one.
                self._steps.setdefault(rgb, ordered[: position + 1])

    def darker(self, pixel: Rgba, steps: int = 1) -> Rgba:
        """``pixel`` ``steps`` down its ramp, or the ramp's darkest colour; itself when it is in no ramp."""
        below = self._steps.get(pixel[:3])
        if below is None or pixel[3] == 0:
            return pixel
        r, g, b = below[max(0, len(below) - 1 - steps)]
        return (r, g, b, 255)


class _Canvas:
    def __init__(self, cloth: Image, trim: Image | None, ramps: Ramps) -> None:
        self.pixels: list[Rgba] = [TRANSPARENT] * (WIDTH * HEIGHT)
        self._cloth = cloth
        self._trim = trim
        self._ramps = ramps

    def cloth(self, x: int, y: int, darker: int = 0) -> Rgba:
        pixel = self._cloth.pixels[(y % TILE) * TILE + x % TILE]
        return self._ramps.darker(pixel, darker) if darker else pixel

    def trim(self, x: int, y: int) -> Rgba:
        """The trim tile's pixel, or the cloth two steps darker for a look without a trim tile."""
        if self._trim is None:
            return self.cloth(x, y, 2)
        return self._trim.pixels[(y % TILE) * TILE + x % TILE]

    def put(self, x: int, y: int, pixel: Rgba) -> None:
        self.pixels[y * WIDTH + x] = pixel

    def fill_cloth(self, rect: tuple[int, int, int, int], darker: int = 0) -> None:
        x0, y0, width, height = rect
        for y in range(y0, y0 + height):
            for x in range(x0, x0 + width):
                self.put(x, y, self.cloth(x, y, darker))

    def row_cloth(self, strip: tuple[int, int, int, int], row: int, darker: int = 0) -> None:
        x0, y0, width, _ = strip
        for x in range(x0, x0 + width):
            self.put(x, y0 + row, self.cloth(x, y0 + row, darker))

    def row_trim(self, strip: tuple[int, int, int, int], row: int) -> None:
        x0, y0, width, _ = strip
        for x in range(x0, x0 + width):
            self.put(x, y0 + row, self.trim(x, y0 + row))

    def clear_rows(self, strip: tuple[int, int, int, int], first: int) -> None:
        x0, y0, width, height = strip
        for y in range(y0 + first, y0 + height):
            for x in range(x0, x0 + width):
                self.put(x, y, TRANSPARENT)

    def png(self) -> bytes:
        return encode_rgba(WIDTH, HEIGHT, self.pixels)


def outer_layer(cloth: Image, trim: Image | None, ramps: Ramps) -> bytes:
    """The ``humanoid`` layer: the cap on the head, the robes on the body and the arms, the sandals on the legs."""
    canvas = _Canvas(cloth, trim, ramps)
    _cap(canvas)
    _robes(canvas)
    _sandals(canvas)
    return canvas.png()


def inner_layer(cloth: Image, trim: Image | None, ramps: Ramps) -> bytes:
    """The ``humanoid_leggings`` layer: the pants on the legs and their waist on the body."""
    canvas = _Canvas(cloth, trim, ramps)
    canvas.fill_cloth(LEG.top)
    canvas.fill_cloth(LEG.strip)
    canvas.row_trim(LEG.strip, PANTS_HEM_ROW)
    canvas.clear_rows(LEG.strip, PANTS_HEM_ROW + 1)
    body_x, body_y, body_w, body_h = BODY.strip
    canvas.fill_cloth((body_x, body_y + WAIST_ROW, body_w, body_h - WAIST_ROW))
    canvas.fill_cloth(BODY.bottom, 1)
    canvas.row_trim(BODY.strip, WAIST_ROW)
    return canvas.png()


def _cap(canvas: _Canvas) -> None:
    canvas.fill_cloth(HEAD.top)
    for row in range(CAP_ROWS):
        canvas.row_cloth(HEAD.strip, row)
    canvas.row_trim(HEAD.strip, CAP_ROWS)
    # The brim: the band again on the hat layer, which stands half a pixel out from the head, one row lower.
    canvas.row_trim(HAT.strip, CAP_ROWS + 1)


def _robes(canvas: _Canvas) -> None:
    canvas.fill_cloth(BODY.top)
    canvas.fill_cloth(BODY.bottom, 1)
    canvas.fill_cloth(BODY.strip)
    canvas.row_trim(BODY.strip, BELT_ROW)
    canvas.row_cloth(BODY.strip, BELT_ROW + 1, 1)  # the belt's shadow
    canvas.row_cloth(BODY.strip, HEM_ROW, 1)
    front_x, front_y, front_w, _ = BODY.front
    back_x, back_y, back_w, _ = BODY.back
    middle = front_x + front_w // 2
    # The collar: a shallow V at the front, a straight band at the back.
    for x in range(front_x + 2, front_x + front_w - 2):
        canvas.put(x, front_y, canvas.trim(x, front_y))
    for x in (middle - 1, middle):
        canvas.put(x, front_y + 1, canvas.trim(x, front_y + 1))
    for x in range(back_x + 2, back_x + back_w - 2):
        canvas.put(x, back_y, canvas.trim(x, back_y))
    # The seam where the robe closes, from under the collar to the belt and from the belt's shadow to the hem.
    for row in (*range(2, BELT_ROW), HEM_ROW - 1, HEM_ROW):
        canvas.put(middle, front_y + row, canvas.cloth(middle, front_y + row, 2))

    canvas.fill_cloth(ARM.top)
    canvas.fill_cloth(ARM.bottom, 2)  # the sleeve's opening
    canvas.fill_cloth(ARM.strip)
    canvas.row_cloth(ARM.strip, CUFF_ROW - 1, 1)
    canvas.row_trim(ARM.strip, CUFF_ROW)
    canvas.clear_rows(ARM.strip, CUFF_ROW + 1)


def _sandals(canvas: _Canvas) -> None:
    strip_x, strip_y, strip_w, _ = LEG.strip
    canvas.row_trim(LEG.strip, STRAP_ROW)
    for x in range(strip_x, strip_x + strip_w):
        # Thin straps up the ankle: every other pixel, so the skin shows between them.
        if (x - strip_x) % 2 == 0:
            canvas.put(x, strip_y + STRAP_ROW - 1, canvas.trim(x, strip_y + STRAP_ROW - 1))
    canvas.row_cloth(LEG.strip, SOLE_ROW, 2)
    canvas.fill_cloth(LEG.bottom, 2)
