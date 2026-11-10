"""Approved art (ADR 0007, RLM-642): the cleaned textures in ``art/textures/`` that take a generated placeholder's
place.

The last step of the texture pipeline (``art/README.md``): the candidate picked in the review gallery is cleaned with
``uv run rf art clean <candidate>.png art/textures/<kind>/<id>.png``, and from then on ``rf gen`` writes that texture
instead of the placeholder. A subject without a file there keeps its placeholder.

==========  ===================================  =====================================================
Kind        File                                 Becomes
==========  ===================================  =====================================================
``item``    ``art/textures/item/<item>.png``     the item's texture, 16 x 16
``spell``   ``art/textures/spell/<spell>.png``   the spell's icon, 16 x 16, the GUI sprite ``spell/<id>``
``effect``  ``art/textures/effect/<effect>.png`` the status effect's icon: 18 x 18, or 16 x 16 centred on it
``gui``     ``art/textures/gui/<name>.png``      a part of a GUI texture a generator composes (``GUI_PARTS``)
==========  ===================================  =====================================================

Every file must be what the cleanup step writes: the kind's size, pixels fully opaque or fully transparent, and only
colours of the project palette. ``problems`` reports what is not, and a texture that names no content. The game never
reads these files: generators re-encode them (``texture``), so the output is byte-identical on every machine.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.art.palette import DEFAULT_PALETTE, PaletteError, load_gpl
from realmforge_tools.art.texture import Image
from realmforge_tools.content import Content
from realmforge_tools.formats.png import PngError, Rgba, decode_rgba, encode_rgba
from realmforge_tools.problems import Problem
from realmforge_tools.project import RepoPaths

APPROVED_DIR = Path("art") / "textures"
ITEM, SPELL, EFFECT, GUI = "item", "spell", "effect", "gui"
ICON_SIZE = 16
EFFECT_ICON_SIZE = 18
# The parts of GUI textures that generators compose, and their sizes: the spellbook's page and cover tiles (RLM-609).
GUI_PARTS: dict[str, tuple[int, int]] = {"spellbook_page": (64, 64), "spellbook_cover": (16, 16)}
# The sizes a kind's texture may have; a GUI part's come from GUI_PARTS.
_SIZES: dict[str, tuple[tuple[int, int], ...]] = {
    ITEM: ((ICON_SIZE, ICON_SIZE),),
    SPELL: ((ICON_SIZE, ICON_SIZE),),
    EFFECT: ((EFFECT_ICON_SIZE, EFFECT_ICON_SIZE), (ICON_SIZE, ICON_SIZE)),
}
KINDS = (ITEM, SPELL, EFFECT, GUI)
TRANSPARENT: Rgba = (0, 0, 0, 0)


def approved_dir(paths: RepoPaths) -> Path:
    return paths.root / APPROVED_DIR


def ids(paths: RepoPaths, kind: str) -> tuple[str, ...]:
    """The ids that have approved art of ``kind``, sorted."""
    directory = approved_dir(paths) / kind
    return tuple(sorted(file.stem for file in directory.glob("*.png"))) if directory.is_dir() else ()


def image(paths: RepoPaths, kind: str, art_id: str) -> Image | None:
    """The approved texture of ``art_id``, or None without one. Raises ``PngError`` for a file that is no PNG."""
    file = approved_dir(paths) / kind / f"{art_id}.png"
    if not file.is_file():
        return None
    width, height, pixels = decode_rgba(file.read_bytes())
    return Image(width, height, tuple(pixels))


def texture(paths: RepoPaths, kind: str, art_id: str) -> bytes | None:
    """The approved texture of ``art_id`` as the game gets it, or None without one: re-encoded by our own encoder,
    and a 16 x 16 effect icon centred on vanilla's 18 x 18."""
    art = image(paths, kind, art_id)
    if art is None:
        return None
    if kind == EFFECT and (art.width, art.height) == (ICON_SIZE, ICON_SIZE):
        art = _centred(art, EFFECT_ICON_SIZE)
    return art.png()


def _centred(art: Image, size: int) -> Image:
    left, top = (size - art.width) // 2, (size - art.height) // 2
    pixels = [TRANSPARENT] * (size * size)
    for y in range(art.height):
        for x in range(art.width):
            pixels[(top + y) * size + left + x] = art.pixels[y * art.width + x]
    return Image(size, size, tuple(pixels))


def problems(paths: RepoPaths, content: Content) -> list[Problem]:
    """What is wrong in ``art/textures/``: a file that is no texture of a known kind, that names no content, or that
    the cleanup step did not write (its size, half-transparent pixels, colours outside the palette)."""
    root = approved_dir(paths)
    if not root.is_dir():
        return []
    known: dict[str, set[str]] = {
        ITEM: {entry.model.id for entry in content.items},
        SPELL: {entry.model.id for entry in content.spells},
        EFFECT: {entry.model.id for entry in content.effects},
        GUI: set(GUI_PARTS),
    }
    found: list[Problem] = []
    palette = None
    for file in sorted(p for p in root.rglob("*") if p.is_file()):
        rel = paths.rel(file)
        parts = file.relative_to(root).parts
        if len(parts) != 2 or parts[0] not in KINDS or file.suffix != ".png":
            found.append(
                Problem(rel, "", f"approved art is art/textures/<{'|'.join(KINDS)}>/<id>.png (art/README.md)")
            )
            continue
        kind, art_id = parts[0], file.stem
        if art_id not in known[kind]:
            what = "GUI part" if kind == GUI else kind
            found.append(Problem(rel, "", f"no {what} has the id {art_id!r}"))
            continue
        try:
            art = image(paths, kind, art_id)
        except PngError as e:
            found.append(Problem(rel, "", f"not a PNG the pipeline reads: {e}"))
            continue
        assert art is not None
        sizes = (GUI_PARTS[art_id],) if kind == GUI else _SIZES[kind]
        if (art.width, art.height) not in sizes:
            wanted = " or ".join(f"{width} x {height}" for width, height in sizes)
            found.append(Problem(rel, "", f"is {art.width} x {art.height}; this texture is {wanted}"))
            continue
        if palette is None:
            try:
                palette = {color.rgb for color in load_gpl(paths.root / DEFAULT_PALETTE).colors}
            except (OSError, PaletteError) as e:
                return [*found, Problem(paths.rel(paths.root / DEFAULT_PALETTE), "", f"cannot be read: {e}")]
        if any(pixel[3] not in (0, 255) for pixel in art.pixels):
            found.append(Problem(rel, "", _unclean("it has half-transparent pixels")))
        elif any(pixel[3] == 255 and pixel[:3] not in palette for pixel in art.pixels):
            found.append(Problem(rel, "", _unclean("it has colours outside the project palette")))
        elif kind == GUI and not art.is_opaque():
            found.append(Problem(rel, "", "a GUI part is a tile without transparent pixels"))
    return found


def _unclean(what: str) -> str:
    return f"{what}: write it with `uv run rf art clean <candidate>.png <this file>`"


def placeholder_or_approved(paths: RepoPaths, kind: str, art_id: str, placeholder: bytes) -> bytes:
    """The texture ``rf gen`` writes for ``art_id``: its approved art, or ``placeholder`` until it has one."""
    approved = texture(paths, kind, art_id)
    return approved if approved is not None else placeholder


def solid(width: int, height: int, color: Rgba) -> bytes:
    """A flat tile, for a generator's stand-in where a GUI part has no art yet."""
    return encode_rgba(width, height, [color] * (width * height))
