"""Our GUI icons (RLM-497): sprites drawn as pixel rows, such as the spellbook's gear."""

from __future__ import annotations

import pytest

from realmforge_tools.art.palette import DEFAULT_PALETTE, load_gpl
from realmforge_tools.formats.png import decode_rgba
from realmforge_tools.generate import generate_all, gui_icons
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate

SPRITES = "mod/src/generated/resources/assets/realmforge/textures/gui/sprites/icon"


def test_the_gear_is_a_sprite_drawn_from_its_rows(repo: RepoPaths) -> None:
    content, _ = load_and_validate(repo)
    files = {repo.rel(path): data for path, data in generate_all(content, repo).items()}
    width, height, pixels = decode_rgba(files[f"{SPRITES}/gear.png"])
    assert (width, height) == (12, 12)
    rows = ["".join(_letter(pixels[y * width + x]) for x in range(width)) for y in range(height)]
    assert rows == list(gui_icons.GEAR_ROWS)


def test_the_gear_is_symmetric_with_a_hole_and_in_palette_colours() -> None:
    shape = ["".join("#" if letter != "." else "." for letter in row) for row in gui_icons.GEAR_ROWS]
    assert shape == [row[::-1] for row in shape], "mirrored left to right"
    assert shape == shape[::-1], "mirrored top to bottom"
    assert gui_icons.GEAR_ROWS[5][5:7] == "..", "the hole in the middle"
    palette = {color.rgb for color in load_gpl(RepoPaths().root / DEFAULT_PALETTE).colors}
    for letter, rgba in gui_icons.PIXELS.items():
        assert rgba[3] == 0 or rgba[:3] in palette, f"{letter!r} is not a palette colour"


def test_an_icon_needs_rows_of_one_length() -> None:
    with pytest.raises(ValueError, match="icon 'bad' has rows of different lengths"):
        gui_icons.texture("bad", ("KK", "K"))


def _letter(rgba: tuple[int, int, int, int]) -> str:
    return next(
        letter for letter, colour in gui_icons.PIXELS.items() if colour == rgba or (rgba[3] == 0 == colour[3])
    )
