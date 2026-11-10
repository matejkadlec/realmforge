"""The spellbook's book texture (RLM-609): one open page in a leather cover, as a nine-slice GUI sprite.

Matěj's note of 2026-10-21: "a real spellbook, a background texture of a book with a single page open". The window
(``client/screen/SpellbookScreen``) has one size on both tabs, but a nine-slice sprite fits any: the game repeats the
page in the middle and the cover along the edges (``GuiSpriteScaling.NineSlice`` without ``stretch_inner``), so
nothing is stretched.

The sprite ``realmforge:spellbook/book`` is composed here from two approved tiles (``art/approved.py``, kind
``gui``): ``spellbook_page``, a 64 x 64 tile of paper, and ``spellbook_cover``, a 16 x 16 tile of leather, both from
the texture generator. The rest is drawn in palette colours: the cover's outline and inner shadow, the spine on the
left with its ribs, and the edges of the pages under the open one. Until the tiles exist, flat palette colours stand
in for them.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.art import approved
from realmforge_tools.art.texture import Image
from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.project import MOD_ID, RepoPaths

PAGE, COVER = "spellbook_page", "spellbook_cover"
PAGE_SIZE = approved.GUI_PARTS[PAGE][0]
COVER_SIZE = approved.GUI_PARTS[COVER][0]
# The sprite's borders, which the window draws outside its content: the spine on the left is wider than the cover's
# other three edges. client/screen/SpellbookScreen keeps the same numbers (BOOK_LEFT, BOOK_EDGE).
SPINE = 14
EDGE = 10
WIDTH = SPINE + PAGE_SIZE + EDGE
HEIGHT = EDGE + PAGE_SIZE + EDGE

# Colours of art/palette/realmforge.gpl.
OUTLINE: Rgba = (11, 11, 14, 255)  # black
COVER_SHADOW: Rgba = (30, 22, 16, 255)  # wood 1
COVER_LIGHT: Rgba = (109, 81, 50, 255)  # wood 5
RIB: Rgba = (47, 33, 21, 255)  # wood 2
PAGES_DARK: Rgba = (200, 176, 124, 255)  # parchment 1
PAGES_MID: Rgba = (220, 200, 150, 255)  # parchment 2
PAGES_LIGHT: Rgba = (236, 220, 174, 255)  # parchment 3
FLAT_PAGE: Rgba = (246, 230, 184, 255)  # parchment 4, the stand-in for the page tile
FLAT_COVER: Rgba = (66, 47, 29, 255)  # wood 3, the stand-in for the cover tile
TRANSPARENT: Rgba = (0, 0, 0, 0)
# How far in from the sprite's edge a row of the cover begins at a corner: the outline's corner pixel is cut.
SPINE_RIBS = (4, 9)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    sprites = (
        paths.mod_generated / "resources" / "assets" / MOD_ID / "textures" / "gui" / "sprites" / "spellbook"
    )
    return {
        sprites / "book.png": book(_tile(paths, PAGE, FLAT_PAGE), _tile(paths, COVER, FLAT_COVER)),
        sprites / "book.png.mcmeta": dump_json(scaling()),
    }


def scaling() -> dict[str, object]:
    """The sprite's ``.mcmeta``: nine slices, the middle and the edges repeated, never stretched."""
    return {
        "gui": {
            "scaling": {
                "type": "nine_slice",
                "width": WIDTH,
                "height": HEIGHT,
                "border": {"left": SPINE, "top": EDGE, "right": EDGE, "bottom": EDGE},
            }
        }
    }


def _tile(paths: RepoPaths, part: str, flat: Rgba) -> Image:
    art = approved.image(paths, approved.GUI, part)
    if art is not None:
        return art
    width, height = approved.GUI_PARTS[part]
    return Image(width, height, (flat,) * (width * height))


def book(page: Image, cover: Image) -> bytes:
    """The sprite: ``page`` in the middle, ``cover`` round it, the spine on the left."""
    if (page.width, page.height) != (PAGE_SIZE, PAGE_SIZE) or (cover.width, cover.height) != (
        COVER_SIZE,
        COVER_SIZE,
    ):
        raise ValueError(
            f"the spellbook's page tile is {PAGE_SIZE} x {PAGE_SIZE} and its cover tile {COVER_SIZE} x {COVER_SIZE}"
        )
    pixels = [_pixel(x, y, page, cover) for y in range(HEIGHT) for x in range(WIDTH)]
    return encode_rgba(WIDTH, HEIGHT, pixels)


def _pixel(x: int, y: int, page: Image, cover: Image) -> Rgba:
    right, bottom = WIDTH - 1 - x, HEIGHT - 1 - y
    # How deep the pixel lies in the cover's top, right and bottom edges, and in the spine: 0 is the outline.
    depth = min(y, right, bottom)
    if x in (0, WIDTH - 1) and y in (0, HEIGHT - 1):
        return TRANSPARENT  # the cover's corners are cut
    if depth == 0 or x == 0:
        return OUTLINE
    leather = cover.pixels[(y % COVER_SIZE) * COVER_SIZE + x % COVER_SIZE]
    if x < SPINE - 4 and depth >= 1:
        # The spine: leather with a lit edge, two ribs across it, and a shadow where the pages begin.
        if x == 1:
            return COVER_LIGHT
        return RIB if x in SPINE_RIBS else leather
    if depth <= 5:
        # The cover's edge: lit just inside the outline at the top, in shadow at the bottom and the right.
        if depth == 1:
            return COVER_LIGHT if y == 1 else COVER_SHADOW if bottom == 1 or right == 1 else leather
        return leather
    if depth == 6 or x == SPINE - 4:
        return COVER_SHADOW
    if depth in (7, 8) or x in (SPINE - 3, SPINE - 2):
        # The edges of the pages under the open one, and the gutter by the spine.
        stripes = depth if depth in (7, 8) else x
        return PAGES_DARK if stripes % 2 else PAGES_MID
    if depth == 9 or x == SPINE - 1:
        return PAGES_LIGHT
    return page.pixels[((y - EDGE) % PAGE_SIZE) * PAGE_SIZE + (x - SPINE) % PAGE_SIZE]
