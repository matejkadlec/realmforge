"""Worn armor textures (RLM-505): the composer's layout, its shading from the palette's ramps, and what `rf gen`
writes for a look with and without its cloth tile."""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from realmforge_tools.art.palette import DEFAULT_PALETTE, Palette, PaletteColor, load_gpl
from realmforge_tools.art.texture import Image
from realmforge_tools.formats.png import Rgba, decode_rgba, encode_rgba
from realmforge_tools.generate import generate_all, worn_armor
from realmforge_tools.generate.textures import placeholder_equipment_texture
from realmforge_tools.generate.worn_armor import ARM, BODY, HAT, HEAD, LEG, Ramps
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import REPO_ROOT, RepoPaths
from realmforge_tools.validate import load_and_validate

ASSETS = "mod/src/generated/resources/assets/realmforge"
CLEAR: Rgba = (0, 0, 0, 0)
# A ramp of three and a colour in no ramp.
DARK, MID, LIGHT = (10, 10, 40), (20, 20, 80), (40, 40, 160)
GOLD = (230, 198, 77)
PALETTE = Palette(
    "test",
    (
        PaletteColor("blue 1", DARK),
        PaletteColor("blue 2", MID),
        PaletteColor("blue 3", LIGHT),
        PaletteColor("gold", GOLD),
    ),
)
CLOTH: Rgba = (*LIGHT, 255)
TRIM: Rgba = (*GOLD, 255)


def _tile(pixel: Rgba) -> Image:
    return Image(16, 16, (pixel,) * 256)


def _layer(data: bytes) -> list[list[Rgba]]:
    width, height, pixels = decode_rgba(data)
    assert (width, height) == (worn_armor.WIDTH, worn_armor.HEIGHT)
    return [list(pixels[y * width : (y + 1) * width]) for y in range(height)]


def _rows(layer: list[list[Rgba]], strip: tuple[int, int, int, int]) -> list[set[Rgba]]:
    """The colours of each row of a box's side strip."""
    x0, y0, width, height = strip
    return [set(layer[y][x0 : x0 + width]) for y in range(y0, y0 + height)]


def _rect(layer: list[list[Rgba]], rect: tuple[int, int, int, int]) -> set[Rgba]:
    x0, y0, width, height = rect
    return {pixel for row in layer[y0 : y0 + height] for pixel in row[x0 : x0 + width]}


def test_a_box_unwraps_as_vanillas_humanoid_model_does() -> None:
    # HumanoidModel#createMesh: body at texOffs(16, 16), 8 x 12 x 4.
    assert BODY.top == (20, 16, 8, 4)
    assert BODY.bottom == (28, 16, 8, 4)
    assert BODY.strip == (16, 20, 24, 12)
    assert BODY.front == (20, 20, 8, 12)
    assert BODY.back == (32, 20, 8, 12)
    assert HEAD.strip == (0, 8, 32, 8)
    assert HAT.strip == (32, 8, 32, 8)
    assert ARM.strip == (40, 20, 16, 12)
    assert LEG.strip == (0, 20, 16, 12)


def test_a_ramp_steps_down_to_its_darkest_colour() -> None:
    ramps = Ramps(PALETTE)
    assert ramps.darker((*LIGHT, 255)) == (*MID, 255)
    assert ramps.darker((*LIGHT, 255), 2) == (*DARK, 255)
    assert ramps.darker((*MID, 255), 5) == (*DARK, 255), "it stops at the ramp's darkest"
    assert ramps.darker((*DARK, 255)) == (*DARK, 255)
    assert ramps.darker(TRIM) == TRIM, "a colour in no ramp stays"
    assert ramps.darker(CLEAR) == CLEAR


def test_the_projects_palette_has_ramps_for_every_numbered_colour() -> None:
    palette = load_gpl(REPO_ROOT / DEFAULT_PALETTE)
    ramps = Ramps(palette)
    by_name = {color.name: color.rgb for color in palette.colors}
    assert ramps.darker((*by_name["slate 3"], 255)) == (*by_name["slate 2"], 255)
    assert ramps.darker((*by_name["royal 1"], 255)) == (*by_name["royal 1"], 255)


def test_the_outer_layer_is_a_cap_robes_and_sandals() -> None:
    ramps = Ramps(PALETTE)
    layer = _layer(worn_armor.outer_layer(_tile(CLOTH), _tile(TRIM), ramps))
    shade1, shade2 = (*MID, 255), (*DARK, 255)

    # The cap: cloth on top and down the sides, its band, nothing below it; the brim on the hat layer one row lower.
    assert _rect(layer, HEAD.top) == {CLOTH}
    assert _rect(layer, HEAD.bottom) == {CLEAR}
    head = _rows(layer, HEAD.strip)
    assert head[: worn_armor.CAP_ROWS] == [{CLOTH}] * worn_armor.CAP_ROWS
    assert head[worn_armor.CAP_ROWS] == {TRIM}
    assert all(row == {CLEAR} for row in head[worn_armor.CAP_ROWS + 1 :])
    hat = _rows(layer, HAT.strip)
    assert [row == {TRIM} for row in hat] == [n == worn_armor.CAP_ROWS + 1 for n in range(8)]
    assert all(row == {CLEAR} for n, row in enumerate(hat) if n != worn_armor.CAP_ROWS + 1)
    assert _rect(layer, HAT.top) == {CLEAR}

    # The robes: the belt and its shadow, a darker hem, a collar and a seam at the front.
    body = _rows(layer, BODY.strip)
    assert body[worn_armor.BELT_ROW] == {TRIM}
    assert body[worn_armor.BELT_ROW + 1] == {shade1}
    assert body[worn_armor.HEM_ROW] == {shade1, shade2}, "the hem, and the seam through it"
    front_x, front_y, _, _ = BODY.front
    assert layer[front_y][front_x + 2 : front_x + 6] == [TRIM] * 4, "the collar"
    assert layer[front_y][front_x] == CLOTH
    assert layer[front_y + 4][front_x + 4] == shade2, "the seam"
    assert layer[front_y + 4][front_x + 3] == CLOTH
    back_x, back_y, _, _ = BODY.back
    assert layer[back_y][back_x + 2 : back_x + 6] == [TRIM] * 4
    assert layer[back_y + 4][back_x + 4] == CLOTH, "no seam at the back"

    # The sleeves end in a cuff above the hands.
    arm = _rows(layer, ARM.strip)
    assert arm[worn_armor.CUFF_ROW] == {TRIM}
    assert all(row == {CLEAR} for row in arm[worn_armor.CUFF_ROW + 1 :])
    assert arm[0] == {CLOTH}
    assert _rect(layer, ARM.bottom) == {shade2}

    # The sandals: nothing on the leg, thin straps, the strap, the sole.
    leg = _rows(layer, LEG.strip)
    assert all(row == {CLEAR} for row in leg[: worn_armor.STRAP_ROW - 1])
    assert leg[worn_armor.STRAP_ROW - 1] == {TRIM, CLEAR}
    assert leg[worn_armor.STRAP_ROW] == {TRIM}
    assert leg[worn_armor.SOLE_ROW] == {shade2}
    assert _rect(layer, LEG.top) == {CLEAR}
    assert _rect(layer, LEG.bottom) == {shade2}

    # Nothing outside the model's boxes.
    assert _rect(layer, (56, 16, 8, 16)) == {CLEAR}
    assert _rect(layer, (0, 0, 8, 8)) == {CLEAR}


def test_the_inner_layer_is_the_pants_and_their_waist() -> None:
    layer = _layer(worn_armor.inner_layer(_tile(CLOTH), _tile(TRIM), Ramps(PALETTE)))
    leg = _rows(layer, LEG.strip)
    assert leg[: worn_armor.PANTS_HEM_ROW] == [{CLOTH}] * worn_armor.PANTS_HEM_ROW
    assert leg[worn_armor.PANTS_HEM_ROW] == {TRIM}
    assert all(row == {CLEAR} for row in leg[worn_armor.PANTS_HEM_ROW + 1 :])
    body = _rows(layer, BODY.strip)
    assert all(row == {CLEAR} for row in body[: worn_armor.WAIST_ROW])
    assert body[worn_armor.WAIST_ROW] == {TRIM}
    assert all(row == {CLOTH} for row in body[worn_armor.WAIST_ROW + 1 :])
    # The head and the arms are the outer layer's.
    assert _rect(layer, (0, 0, 64, 16)) == {CLEAR}
    assert _rect(layer, ARM.strip) == {CLEAR}


def test_a_look_without_a_trim_tile_has_its_bands_in_darker_cloth() -> None:
    layer = _layer(worn_armor.outer_layer(_tile(CLOTH), None, Ramps(PALETTE)))
    assert _rows(layer, BODY.strip)[worn_armor.BELT_ROW] == {(*DARK, 255)}
    assert _rows(layer, HEAD.strip)[worn_armor.CAP_ROWS] == {(*DARK, 255)}


def test_the_tiles_repeat_across_the_faces() -> None:
    # A tile with one marked pixel: it shows wherever the texture's own coordinates hit it, so faces continue.
    pixels = [CLOTH] * 256
    pixels[5 * 16 + 3] = (*MID, 255)
    layer = _layer(worn_armor.outer_layer(Image(16, 16, tuple(pixels)), _tile(TRIM), Ramps(PALETTE)))
    assert layer[21][19] == (*MID, 255)  # (19, 21): x % 16 == 3, y % 16 == 5, on the body's right side
    assert layer[21][51] == (*MID, 255)  # the same pixel of the tile, on the arm


@pytest.fixture
def art_repo(repo: RepoPaths) -> RepoPaths:
    """The scratch checkout with the project palette, and no approved art yet."""
    palette = repo.root / DEFAULT_PALETTE
    palette.parent.mkdir(parents=True)
    shutil.copy(REPO_ROOT / DEFAULT_PALETTE, palette)
    return repo


def _art(repo: RepoPaths, name: str, pixels: list[Rgba]) -> Path:
    file = repo.root / "art" / "textures" / name
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(encode_rgba(16, len(pixels) // 16, pixels))
    return file


def _look(repo: RepoPaths) -> str:
    """A look of the scratch checkout's items."""
    content, _ = load_and_validate(repo)
    return next(
        entry.model.gear.look
        for entry in content.items
        if entry.model.gear is not None and entry.model.gear.look is not None
    )


def _palette_pixel(name: str) -> Rgba:
    palette = load_gpl(REPO_ROOT / DEFAULT_PALETTE)
    return (*next(color.rgb for color in palette.colors if color.name == name), 255)


def _generated(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def _problems(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(problem) for problem in excinfo.value.problems]


def test_a_look_without_a_cloth_tile_keeps_its_placeholder(art_repo: RepoPaths) -> None:
    look = _look(art_repo)
    content, _ = load_and_validate(art_repo)
    color = next(
        entry.model.color
        for entry in content.items
        if entry.model.gear is not None and entry.model.gear.look == look
    )
    files = _generated(art_repo)
    placeholder = placeholder_equipment_texture(
        f"realmforge:{look}", int(color.removeprefix("#"), 16) if color else None
    )
    assert files[f"{ASSETS}/textures/entity/equipment/humanoid/{look}.png"] == placeholder
    assert files[f"{ASSETS}/textures/entity/equipment/humanoid_leggings/{look}.png"] == placeholder


def test_a_looks_cloth_and_trim_tiles_become_its_two_layers(art_repo: RepoPaths) -> None:
    look = _look(art_repo)
    cloth, trim = _palette_pixel("royal 3"), _palette_pixel("gold 4")
    _art(art_repo, f"armor/{look}.png", [cloth] * 256)
    _art(art_repo, f"armor_trim/{look}.png", [trim] * 256)
    files = _generated(art_repo)
    ramps = Ramps(load_gpl(REPO_ROOT / DEFAULT_PALETTE))
    outer = files[f"{ASSETS}/textures/entity/equipment/humanoid/{look}.png"]
    inner = files[f"{ASSETS}/textures/entity/equipment/humanoid_leggings/{look}.png"]
    assert outer == worn_armor.outer_layer(_tile(cloth), _tile(trim), ramps)
    assert inner == worn_armor.inner_layer(_tile(cloth), _tile(trim), ramps)
    assert outer != inner
    # The equipment asset still names one texture id for both layers: the game reads it from each layer's folder.
    assert f"{ASSETS}/equipment/{look}.json" in files


def test_validation_refuses_armor_tiles_that_fit_no_look(art_repo: RepoPaths) -> None:
    look = _look(art_repo)
    cloth = _palette_pixel("royal 3")
    stray = _art(art_repo, "armor/no_such_look.png", [cloth] * 256)
    assert "no armor look has the id 'no_such_look'" in _problems(art_repo)[0]
    stray.unlink()

    trim = _art(art_repo, f"armor_trim/{look}.png", [cloth] * 256)
    assert f"a look's trim needs its cloth, art/textures/armor/{look}.png" in _problems(art_repo)[0]
    trim.unlink()

    holes = [cloth] * 255 + [CLEAR]
    _art(art_repo, f"armor/{look}.png", holes)
    assert "an armor look's cloth or trim is a tile without transparent pixels" in _problems(art_repo)[0]
