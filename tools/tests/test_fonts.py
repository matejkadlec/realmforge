"""Our fonts (RLM-340): the symbols font's provider and texture, drawn on the default font's grid."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.art.palette import DEFAULT_PALETTE, load_gpl
from realmforge_tools.formats.png import decode_rgba
from realmforge_tools.generate import fonts, generate_all
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate

ASSETS = "mod/src/generated/resources/assets/realmforge"


def generated(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def test_the_symbols_font_holds_the_skull_and_the_step_back(repo: RepoPaths) -> None:
    files = generated(repo)
    font = json.loads(files[f"{ASSETS}/font/symbols.json"])
    assert font == {
        "providers": [
            {"type": "bitmap", "file": "realmforge:font/symbols.png", "ascent": 7, "chars": ["\ue000"]},
            {"type": "space", "advances": {"\ue001": -3}},
        ]
    }
    width, height, pixels = decode_rgba(files[f"{ASSETS}/textures/font/symbols.png"])
    assert (width, height) == (8, 8)
    rows = ["".join(_letter(pixels[y * width + x]) for x in range(width)) for y in range(height)]
    assert rows == list(fonts.SKULL_ROWS)


def test_the_skull_keeps_to_the_letters_height_and_the_palette() -> None:
    assert fonts.SKULL_ROWS[-1] == "........", (
        "the bottom row is below the baseline, where letters leave it empty"
    )
    assert all(row[-1] == "." for row in fonts.SKULL_ROWS), "a column of space after it, as after a letter"
    palette = {color.rgb for color in load_gpl(RepoPaths().root / DEFAULT_PALETTE).colors}
    for letter, rgba in fonts.PIXELS.items():
        assert rgba[3] == 0 or rgba[:3] in palette, f"{letter!r} is not a palette colour"


def test_a_symbol_must_fill_its_cell(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(fonts, "SYMBOLS", (("\ue001", ("WW",) * 8),))
    with pytest.raises(ValueError, match="symbol U\\+E001 is not 8x8 pixels"):
        fonts.texture()


def _letter(rgba: tuple[int, int, int, int]) -> str:
    return next(
        letter for letter, colour in fonts.PIXELS.items() if colour == rgba or (rgba[3] == 0 == colour[3])
    )
