"""The realm portal's look (RLM-300, RLM-301): the portal block's animated textures, the frame's looks made from the
Gatewright Bricks' tile, a block's tile as approved art, and a realm's ``portal`` fields."""

from __future__ import annotations

import itertools
import json
import shutil
from pathlib import Path

import pytest

from realmforge_tools.art import approved, frame_looks, gallery
from realmforge_tools.art.palette import DEFAULT_PALETTE, load_gpl
from realmforge_tools.art.texture import Image
from realmforge_tools.formats.png import Rgba, decode_rgba, encode_rgba
from realmforge_tools.generate import generate_all, portal_frame, portal_textures
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import REPO_ROOT, RepoPaths
from realmforge_tools.schema.realm import FRAME_LOOKS, PORTAL_ANIMATIONS
from realmforge_tools.validate import load_and_validate

ASSETS = "mod/src/generated/resources/assets/realmforge"
REALMS = "mod/src/generated/resources/data/realmforge/realmforge/realm"
SIZE = 16


@pytest.fixture
def art_repo(repo: RepoPaths) -> RepoPaths:
    """The scratch checkout with the project palette, and no approved art yet."""
    palette = repo.root / DEFAULT_PALETTE
    palette.parent.mkdir(parents=True)
    shutil.copy(REPO_ROOT / DEFAULT_PALETTE, palette)
    return repo


def _generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def _problems(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(problem) for problem in excinfo.value.problems]


def _colours(*names: str) -> list[tuple[int, int, int]]:
    by_name = {color.name: color.rgb for color in load_gpl(REPO_ROOT / DEFAULT_PALETTE).colors}
    return [by_name[name] for name in names]


def _tile(repo: RepoPaths, block: str, pixels: list[Rgba]) -> Path:
    file = repo.root / "art" / "textures" / "block" / f"{block}.png"
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(encode_rgba(SIZE, SIZE, pixels))
    return file


def _bricks_tile() -> list[Rgba]:
    """A tile of three purples in rows: a dark, a middle and a light one."""
    dark, middle, light = _colours("shadow 1", "shadow 3", "shadow 5")
    return [(*(dark, middle, light)[(y // 2) % 3], 255) for y in range(SIZE) for _ in range(SIZE)]


def _at(pixels: list[Rgba], x: int, y: int) -> Rgba:
    return pixels[y * SIZE + x]


# The portal block's animations -----------------------------------------------------------------------------------


@pytest.mark.parametrize("animation", PORTAL_ANIMATIONS)
def test_a_portal_animation_is_a_strip_of_see_through_palette_greys(animation: str) -> None:
    width, height, pixels = decode_rgba(portal_textures.strip(animation))
    frames = portal_textures.STREAM_FRAMES if animation == "stream" else portal_textures.SWIRL_FRAMES
    assert (width, height) == (SIZE, SIZE * frames)
    palette = {color.rgb for color in load_gpl(REPO_ROOT / DEFAULT_PALETTE).colors}
    assert set(portal_textures.GREYS) <= palette, "the portal's greys are colours of the project palette"
    assert {pixel[:3] for pixel in pixels} <= set(portal_textures.GREYS)
    # See-through enough to be drawn translucent, opaque enough to read as a surface over the whole opening.
    assert all(portal_textures.ALPHA_DIM <= pixel[3] <= portal_textures.ALPHA_BRIGHT for pixel in pixels)
    assert len({pixel[:3] for pixel in pixels}) >= 4, "more than a flat grey"


def test_the_stream_flows_up_and_to_the_right_and_loops() -> None:
    frames = [portal_textures.stream_frame(frame) for frame in range(portal_textures.STREAM_FRAMES + 1)]
    for before, after in itertools.pairwise(frames):
        # Each pixel comes from the one to its lower left, across the tile's edges too: the frames join their
        # neighbours on every side.
        assert all(
            _at(after, x, y) == _at(before, (x - 1) % SIZE, (y + 1) % SIZE)
            for x in range(SIZE)
            for y in range(SIZE)
        )
    assert frames[-1] == frames[0], "the last frame leads into the first"
    assert len({tuple(frame) for frame in frames[:-1]}) == portal_textures.STREAM_FRAMES
    # Streaks run along the diagonal: a bright pixel's neighbour up and to the right, or down and to the left, is lit.
    first = frames[0]
    bright = [
        (x, y) for x in range(SIZE) for y in range(SIZE) if _at(first, x, y)[:3] == portal_textures.GREYS[-1]
    ]
    assert bright
    dim = portal_textures.GREYS[0]
    assert all(
        _at(first, (x + 1) % SIZE, (y - 1) % SIZE)[:3] != dim
        or _at(first, (x - 1) % SIZE, (y + 1) % SIZE)[:3] != dim
        for x, y in bright
    )


def test_the_swirl_turns_round_the_blocks_corners_and_loops() -> None:
    frames = [portal_textures.swirl_frame(frame) for frame in range(portal_textures.SWIRL_FRAMES + 1)]
    assert frames[-1] == frames[0], "the last frame leads into the first"
    assert len({tuple(frame) for frame in frames[:-1]}) == portal_textures.SWIRL_FRAMES, (
        "every frame turns it on"
    )
    for frame in frames[:-1]:
        # Two arms: a pixel and the one opposite it through a corner of the block, which lies in the block diagonally
        # next to it, are alike, so the four quarters round a corner make one whole swirl.
        assert all(
            _at(frame, x, y) == _at(frame, SIZE - 1 - x, SIZE - 1 - y)
            for x in range(SIZE)
            for y in range(SIZE)
        )
        # The swirls do not reach the middle of the block: it is calm, the same in every frame.
        assert [_at(frame, x, y) for x in (7, 8) for y in (7, 8)] == [
            _at(frames[0], x, y) for x in (7, 8) for y in (7, 8)
        ]
    # Near a corner the picture does turn.
    assert len({_at(frame, 2, 1) for frame in frames}) > 1


def test_an_animations_metadata_and_texture_name() -> None:
    assert json.loads(portal_textures.mcmeta("stream")) == {
        "animation": {"frametime": portal_textures.STREAM_FRAME_TIME}
    }
    assert json.loads(portal_textures.mcmeta("swirl")) == {
        "animation": {"frametime": portal_textures.SWIRL_FRAME_TIME}
    }
    # The shared animation carries the block's own name, as the frame's first look does.
    assert PORTAL_ANIMATIONS[0] == "swirl" and FRAME_LOOKS[0] == "washed"
    assert portal_textures.texture_name("realm_portal", "swirl") == "realm_portal"
    assert portal_textures.texture_name("realm_portal", "stream") == "realm_portal_stream"
    assert portal_frame.texture_name("gatewright_bricks", "washed") == "gatewright_bricks"
    assert portal_frame.texture_name("gatewright_bricks", "stone") == "gatewright_bricks_stone"
    assert portal_frame.top_name("gatewright_sealstone", "washed") == "gatewright_sealstone_top"
    assert portal_frame.top_name("gatewright_sealstone", "stone") == "gatewright_sealstone_top_stone"


# The frame's looks ------------------------------------------------------------------------------------------------


def test_a_look_maps_the_bricks_tile_onto_its_ramp_by_lightness() -> None:
    palette = load_gpl(REPO_ROOT / DEFAULT_PALETTE)
    tile = Image(SIZE, SIZE, tuple(_bricks_tile()))
    stone = frame_looks.bricks(tile, "stone", palette)
    first, middle, last = _colours("stone 1", "stone 3", "stone 5")
    # The tile's darkest colour takes the ramp's first, its lightest the last, the one between them a colour between.
    assert stone.pixels[0][:3] == first
    assert stone.pixels[4 * SIZE][:3] == last
    assert stone.pixels[2 * SIZE][:3] in _colours("stone 2", "stone 3", "stone 4")
    assert {pixel[:3] for pixel in stone.pixels} <= set(_colours(*frame_looks.LOOKS["stone"].ramp))
    assert all(pixel[3] == 255 for pixel in stone.pixels)
    # The pattern stays: where the tile changes colour, the look does.
    assert [pixel[:3] != stone.pixels[0][:3] for pixel in stone.pixels] == [
        pixel[:3] != tile.pixels[0][:3] for pixel in tile.pixels
    ]
    # A tile of one colour takes the middle of the ramp.
    flat = Image(SIZE, SIZE, tuple([tile.pixels[0]] * (SIZE * SIZE)))
    assert {pixel[:3] for pixel in frame_looks.bricks(flat, "stone", palette).pixels} == {middle}


@pytest.mark.parametrize("look", FRAME_LOOKS)
def test_the_sealstones_top_is_the_looks_bricks_with_the_socket(look: str) -> None:
    palette = load_gpl(REPO_ROOT / DEFAULT_PALETTE)
    bricks = frame_looks.bricks(Image(SIZE, SIZE, tuple(_bricks_tile())), look, palette)
    top = frame_looks.sealstone_top(bricks, look, palette)
    colours = frame_looks.LOOKS[look]
    core, rim = set(_colours(*colours.core)), set(_colours(*colours.rim))
    # The middle four pixels are what the socket holds, the ring round them its rim, and the face's corners bricks.
    assert {top.pixels[y * SIZE + x][:3] for x in (7, 8) for y in (7, 8)} <= core
    assert top.pixels[8 * SIZE + 4][:3] in rim and top.pixels[8 * SIZE + 11][:3] in rim
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (1, 8), (8, 14)):
        assert top.pixels[y * SIZE + x] == bricks.pixels[y * SIZE + x]
    assert all(pixel[3] == 255 for pixel in top.pixels)


def test_a_dark_frames_socket_is_empty_and_a_lit_ones_holds_the_realms_stone() -> None:
    dark = set(_colours(*frame_looks.LOOKS["washed"].core))
    assert dark <= set(_colours("black", "stone 1", "stone 2")), "an empty hole"
    # Undervault's portal is slate blue (content/realms/stone.yaml: #7A8FB0): the stone in its socket is too.
    assert all(name.startswith("slate ") for name in frame_looks.LOOKS["stone"].core)
    assert tuple(frame_looks.LOOKS) == FRAME_LOOKS


def test_every_look_names_colours_the_palette_has(art_repo: RepoPaths) -> None:
    assert frame_looks.problems(RepoPaths()) == []
    palette = art_repo.root / DEFAULT_PALETTE
    palette.write_text(
        "".join(
            line for line in palette.read_text("utf-8").splitlines(keepends=True) if "faded 3" not in line
        ),
        "utf-8",
    )
    problems = _problems(art_repo)
    assert problems == [
        "tools/realmforge_tools/art/frame_looks.py: LOOKS.washed: the project palette has no colour 'faded 3'"
    ]


def test_without_the_bricks_tile_the_frames_textures_are_placeholders(art_repo: RepoPaths) -> None:
    files = _generate(art_repo)
    names = (
        "gatewright_bricks",
        "gatewright_bricks_stone",
        "gatewright_sealstone_top",
        "gatewright_sealstone_top_stone",
    )
    textures = [files[f"{ASSETS}/textures/block/{name}.png"] for name in names]
    assert len(set(textures)) == len(names), "each placeholder is its own"
    for texture in textures:
        width, height, pixels = decode_rgba(texture)
        assert (width, height) == (SIZE, SIZE) and all(pixel[3] == 255 for pixel in pixels)


def test_the_frames_textures_are_made_from_the_bricks_tile(art_repo: RepoPaths) -> None:
    _tile(art_repo, "gatewright_bricks", _bricks_tile())
    files = _generate(art_repo)
    for look in FRAME_LOOKS:
        ramp = set(_colours(*frame_looks.LOOKS[look].ramp))
        _, _, bricks = decode_rgba(
            files[f"{ASSETS}/textures/block/{portal_frame.texture_name('gatewright_bricks', look)}.png"]
        )
        assert {pixel[:3] for pixel in bricks} <= ramp, look
        assert len({pixel[:3] for pixel in bricks}) == 3, "the tile's three colours stay three"
        _, _, top = decode_rgba(
            files[f"{ASSETS}/textures/block/{portal_frame.top_name('gatewright_sealstone', look)}.png"]
        )
        socket = set(_colours(*frame_looks.LOOKS[look].core, *frame_looks.LOOKS[look].rim))
        assert {pixel[:3] for pixel in top} <= ramp | socket
        assert {top[y * SIZE + x][:3] for x in (7, 8) for y in (7, 8)} <= set(
            _colours(*frame_looks.LOOKS[look].core)
        )
    # The purple of the tile itself is in none of them: no look shows it yet.
    washed = decode_rgba(files[f"{ASSETS}/textures/block/gatewright_bricks.png"])[2]
    assert not {pixel[:3] for pixel in washed} & set(_colours("shadow 1", "shadow 3", "shadow 5"))


# A block's tile as approved art -----------------------------------------------------------------------------------


def test_a_simple_blocks_tile_takes_its_placeholders_place(art_repo: RepoPaths) -> None:
    placeholder = _generate(art_repo)[f"{ASSETS}/textures/block/vaultstone.png"]
    grey, light = _colours("stone 3", "stone 5")
    pixels: list[Rgba] = [(*(grey if (x + y) % 3 else light), 255) for y in range(SIZE) for x in range(SIZE)]
    _tile(art_repo, "vaultstone", pixels)
    texture = _generate(art_repo)[f"{ASSETS}/textures/block/vaultstone.png"]
    assert texture != placeholder
    assert decode_rgba(texture) == (SIZE, SIZE, pixels)
    assert approved.ids(art_repo, approved.BLOCK) == ("vaultstone",)


def test_the_repositorys_bricks_tile_is_its_gallerys_pick() -> None:
    """The pick names a candidate of the batch, and the tile it installed is there, clean (validation's check). Its
    pixels are not compared with the pick cleaned again: the palette is a living file (ADR 0023)."""
    batch = REPO_ROOT / "art" / "realm-portal" / "candidates"
    info, _ = gallery.collect(batch, load_gpl(REPO_ROOT / DEFAULT_PALETTE), SIZE)
    subject = info.subjects["gatewright_bricks"]
    assert subject.pick and (batch / "gatewright_bricks" / f"{subject.pick}.png").is_file()
    paths = RepoPaths()
    content, _ = load_and_validate(paths)
    assert approved.problems(paths, content) == []
    tile = approved.image(paths, approved.BLOCK, "gatewright_bricks")
    assert tile is not None and (tile.width, tile.height) == (SIZE, SIZE)


# A realm's portal fields -------------------------------------------------------------------------------------------


def test_undervaults_portal_streams_storms_and_turns_its_frame_to_stone(repo: RepoPaths) -> None:
    files = _generate(repo)
    assert json.loads(files[f"{REALMS}/stone.json"])["portal"] == {
        "animation": "stream",
        "storm": True,
        "frame": "stone",
    }
    # Overworld's entry names none: the shared swirl, no storm, the washed-out frame.
    assert json.loads(files[f"{REALMS}/overworld.json"])["portal"] == {
        "animation": "swirl",
        "storm": False,
        "frame": "washed",
    }
    expectations = files[
        "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedRealmExpectations.java"
    ].decode()
    assert '"stream", true, "stone")' in expectations
    assert '"swirl", false, "washed")' in expectations


@pytest.mark.parametrize(
    ("field", "expected"),
    [
        ("animation: whirl", "portal.animation"),
        ("frame: gold", "portal.frame"),
        ("storm: sometimes", "portal.storm"),
        ("colour: blue", "portal.colour"),
    ],
)
def test_a_realms_portal_names_only_what_the_game_draws(repo: RepoPaths, field: str, expected: str) -> None:
    stone = repo.content / "realms" / "stone.yaml"
    text = stone.read_text("utf-8")
    assert "portal:\n  animation: stream\n  storm: true\n  frame: stone\n" in text
    stone.write_text(
        text.replace(
            "portal:\n  animation: stream\n  storm: true\n  frame: stone\n", f"portal:\n  {field}\n"
        ),
        "utf-8",
    )
    assert any("realms/stone.yaml" in problem and expected in problem for problem in _problems(repo))
