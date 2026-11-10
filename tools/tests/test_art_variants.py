"""Variants scripted from one base (ADR 0007, RLM-505): the recolouring, `art/textures/variants.yaml`, what `rf gen`
writes for a variant and what validation refuses."""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from realmforge_tools.art import approved, variants
from realmforge_tools.art.palette import DEFAULT_PALETTE, Palette, PaletteColor, load_gpl, ramps
from realmforge_tools.art.texture import Image
from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import REPO_ROOT, RepoPaths
from realmforge_tools.validate import load_and_validate

ASSETS = "mod/src/generated/resources/assets/realmforge"
CLEAR: Rgba = (0, 0, 0, 0)
BLUE = [(10, 10, 40), (20, 20, 80), (40, 40, 160), (90, 90, 230)]
RED = [(60, 10, 10), (160, 30, 30), (240, 90, 90)]
GREY = (128, 128, 128)
PALETTE = Palette(
    "test",
    (
        *(PaletteColor(f"blue {n}", rgb) for n, rgb in enumerate(BLUE, start=1)),
        *(PaletteColor(f"red {n}", rgb) for n, rgb in enumerate(RED, start=1)),
        PaletteColor("grey", GREY),
    ),
)


def _variant(*recolor: dict[str, object], base: str = "ruby") -> variants.VariantDef:
    return variants.VariantDef.model_validate({"base": base, "recolor": list(recolor)})


def _image(*pixels: tuple[int, int, int] | None) -> Image:
    return Image(len(pixels), 1, tuple(CLEAR if pixel is None else (*pixel, 255) for pixel in pixels))


def _rgbs(image: Image) -> list[tuple[int, int, int] | None]:
    return [pixel[:3] if pixel[3] else None for pixel in image.pixels]


def test_the_palettes_ramps_are_its_numbered_colours_in_order() -> None:
    assert {name: [color.rgb for color in colors] for name, colors in ramps(PALETTE).items()} == {
        "blue": BLUE,
        "red": RED,
    }
    shuffled = Palette(
        "test", (PaletteColor("a 2", BLUE[1]), PaletteColor("a 10", BLUE[2]), PaletteColor("a 1", BLUE[0]))
    )
    assert [color.name for color in ramps(shuffled)["a"]] == ["a 1", "a 2", "a 10"], "by number, not by text"


def test_a_group_takes_its_colours_by_lightness() -> None:
    groups = variants.groups(_variant({"from": ["blue"], "to": "red"}), PALETTE)
    # The darkest blue in the picture takes the first red, the lightest the last, one between them the middle.
    recoloured = variants.recolor(_image(BLUE[0], BLUE[3], BLUE[2], GREY, None), groups)
    assert _rgbs(recoloured) == [RED[0], RED[2], RED[1], GREY, None]
    # The picture's own darkest and lightest count, not the ramp's.
    assert _rgbs(variants.recolor(_image(BLUE[1], BLUE[2]), groups)) == [RED[0], RED[2]]
    # One colour of the group in the picture takes the middle.
    assert _rgbs(variants.recolor(_image(BLUE[3], GREY), groups)) == [RED[1], GREY]


def test_a_group_may_name_its_colours_one_by_one_and_groups_do_not_mix() -> None:
    groups = variants.groups(
        _variant({"from": ["blue"], "to": ["grey", "red 3"]}, {"from": ["red"], "to": "blue"}), PALETTE
    )
    recoloured = variants.recolor(_image(BLUE[0], BLUE[3], RED[0], RED[2]), groups)
    # Blue went to red 3, but that red is not recoloured again by the second group.
    assert _rgbs(recoloured) == [GREY, RED[2], BLUE[0], BLUE[3]]


def test_a_group_of_several_ramps_is_one_role() -> None:
    groups = variants.groups(_variant({"from": ["blue", "red"], "to": ["grey", "blue 4"]}), PALETTE)
    # The darkest of both ramps together takes the first colour.
    assert _rgbs(variants.recolor(_image(BLUE[0], RED[2]), groups)) == [GREY, BLUE[3]]


@pytest.mark.parametrize(
    ("recolor", "message"),
    [
        ([{"from": ["green"], "to": "red"}], "recolor 1: the palette has no ramp 'green'"),
        ([{"from": ["blue"], "to": "green"}], "recolor 1: the palette has no ramp 'green'"),
        ([{"from": ["blue"], "to": ["grey", "pink"]}], "recolor 1: the palette has no colour 'pink'"),
        (
            [{"from": ["blue"], "to": "red"}, {"from": ["red", "blue"], "to": "red"}],
            "recolor 2: recolor 1 already takes the ramp 'blue'",
        ),
    ],
)
def test_a_variant_names_only_what_the_palette_has(recolor: list[dict[str, object]], message: str) -> None:
    with pytest.raises(variants.VariantError, match=message):
        variants.groups(_variant(*recolor), PALETTE)


@pytest.fixture
def art_repo(repo: RepoPaths) -> RepoPaths:
    """The scratch checkout with the project palette, and no approved art yet."""
    palette = repo.root / DEFAULT_PALETTE
    palette.parent.mkdir(parents=True)
    shutil.copy(REPO_ROOT / DEFAULT_PALETTE, palette)
    return repo


def _palette_pixel(name: str) -> Rgba:
    palette = load_gpl(REPO_ROOT / DEFAULT_PALETTE)
    return (*next(color.rgb for color in palette.colors if color.name == name), 255)


def _art(repo: RepoPaths, item: str, pixels: list[Rgba]) -> Path:
    file = repo.root / "art" / "textures" / "item" / f"{item}.png"
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(encode_rgba(16, 16, pixels))
    return file


def _variants(repo: RepoPaths, text: str) -> Path:
    file = repo.root / "art" / "textures" / variants.VARIANTS_FILE
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(text, "utf-8")
    return file


def _problems(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(problem) for problem in excinfo.value.problems]


RUBY_TO_SAPPHIRE = "item:\n  sapphire:\n    base: ruby\n    recolor:\n      - {from: [ruby], to: frost}\n"


def test_a_variant_becomes_its_items_texture(art_repo: RepoPaths) -> None:
    dark, light = _palette_pixel("ruby 1"), _palette_pixel("ruby 5")
    _art(art_repo, "ruby", [dark] * 128 + [light] * 120 + [CLEAR] * 8)
    _variants(art_repo, RUBY_TO_SAPPHIRE)
    content, _ = load_and_validate(art_repo)
    files = {art_repo.rel(path): data for path, data in generate_all(content, art_repo).items()}
    frost = [color.rgb for color in ramps(load_gpl(REPO_ROOT / DEFAULT_PALETTE))["frost"]]
    expected = [(*frost[0], 255)] * 128 + [(*frost[-1], 255)] * 120 + [CLEAR] * 8
    assert files[f"{ASSETS}/textures/item/sapphire.png"] == encode_rgba(16, 16, expected)
    # The base keeps its own texture.
    assert files[f"{ASSETS}/textures/item/ruby.png"] == encode_rgba(
        16, 16, [dark] * 128 + [light] * 120 + [CLEAR] * 8
    )
    assert approved.ids(art_repo, approved.ITEM) == ("ruby", "sapphire")
    assert approved.ids(art_repo, approved.SPELL) == ()


def test_validation_refuses_a_variant_that_cannot_be_made(art_repo: RepoPaths) -> None:
    ruby = _art(art_repo, "ruby", [_palette_pixel("ruby 1")] * 256)

    _variants(art_repo, RUBY_TO_SAPPHIRE.replace("sapphire", "no_such_item"))
    assert _problems(art_repo) == [
        "art/textures/variants.yaml: item.no_such_item: no item has the id 'no_such_item'"
    ]

    _variants(art_repo, RUBY_TO_SAPPHIRE.replace("base: ruby", "base: topaz"))
    assert _problems(art_repo) == [
        "art/textures/variants.yaml: item.sapphire.base: no approved texture art/textures/item/topaz.png"
    ]

    _variants(art_repo, RUBY_TO_SAPPHIRE.replace("to: frost", "to: no_such_ramp"))
    assert _problems(art_repo) == [
        "art/textures/variants.yaml: item.sapphire: recolor 1: the palette has no ramp 'no_such_ramp'"
    ]

    _variants(art_repo, RUBY_TO_SAPPHIRE)
    sapphire = _art(art_repo, "sapphire", [_palette_pixel("frost 1")] * 256)
    assert _problems(art_repo) == [
        "art/textures/variants.yaml: item.sapphire: sapphire has its own texture, "
        "art/textures/item/sapphire.png: remove one"
    ]
    sapphire.unlink()

    _variants(art_repo, "item:\n  sapphire: {base: ruby}\n")
    assert "art/textures/variants.yaml: item.sapphire.recolor: Field required" in _problems(art_repo)[0]

    _variants(art_repo, "item: [\n")
    assert _problems(art_repo)[0].startswith("art/textures/variants.yaml: invalid YAML")

    _variants(art_repo, RUBY_TO_SAPPHIRE)
    ruby.unlink()
    assert _problems(art_repo) == [
        "art/textures/variants.yaml: item.sapphire.base: no approved texture art/textures/item/ruby.png"
    ]


def test_the_repositorys_variants_have_their_bases_and_ramps() -> None:
    paths = RepoPaths()
    content, _ = load_and_validate(paths)
    assert approved.problems(paths, content) == []
    # Each variant of the repository gives a texture of its base's size.
    for item in approved._variants(paths).item:
        art = approved.image(paths, approved.ITEM, item)
        assert art is not None and (art.width, art.height) == (16, 16), item
