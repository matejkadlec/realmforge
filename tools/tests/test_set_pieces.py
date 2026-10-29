"""Set pieces (RLM-280, RLM-281; docs/design/set-pieces.md, ADR 0050): the mask, the matcher's rules and patterns,
the content type and its validation, the spots in the dungeon type entries, the processor lists, the mask report,
the gallery's spots and the import of the workshop jig's saves."""

from __future__ import annotations

import json
import shutil
from dataclasses import replace
from pathlib import Path

import pytest

from realmforge_tools import cli
from realmforge_tools.content import load_content
from realmforge_tools.id_universe import load_vanilla
from realmforge_tools.project import DATA_VERSION, REPO_ROOT, RepoPaths
from realmforge_tools.rooms import gallery
from realmforge_tools.rooms import set_piece_import as piece_import
from realmforge_tools.rooms.render import DRESSING, VIEWS, View, drawn_blocks
from realmforge_tools.rooms.set_pieces import (
    NAMED_MASKS,
    Fit,
    Mask,
    MaskError,
    ReportRoom,
    RoomView,
    Spot,
    dressed,
    mask_report,
    match,
    pattern_cells,
    piece_problems,
    place_piece,
    placement,
    transform,
    transform_state,
)
from realmforge_tools.rooms.structure import (
    BlockState,
    Structure,
    StructureBlock,
    StructureEntity,
    read_structure,
    write_structure,
)
from realmforge_tools.validate import load_and_validate

from .conftest import write
from .test_dungeons import crypt, generate, problems_for

Pos = tuple[int, int, int]
DATA = "mod/src/generated/resources/data/realmforge"
STONE, BRICKS, AIR = "minecraft:stone", "minecraft:stone_bricks", "minecraft:air"


def box_room(
    width: int = 9,
    depth: int = 9,
    height: int = 5,
    *,
    doors: tuple[str, ...] = ("north", "south"),
    wall: str = BRICKS,
    floor: str = STONE,
    blocks: dict[Pos, str] | None = None,
    entities: tuple[StructureEntity, ...] = (),
) -> Structure:
    """A room: a floor at y 0, a ceiling on top, walls around, air inside, and a three-wide doorway with a connector at
    its foot in the middle of each side in ``doors``. ``blocks`` replaces cells."""
    cells: dict[Pos, str] = {}
    for x in range(width):
        for z in range(depth):
            for y in range(height):
                edge = x in (0, width - 1) or z in (0, depth - 1)
                cells[x, y, z] = floor if y == 0 else STONE if y == height - 1 else wall if edge else AIR
    data: dict[Pos, dict] = {}
    middle = {
        "north": (width // 2, 0),
        "south": (width // 2, depth - 1),
        "west": (0, depth // 2),
        "east": (width - 1, depth // 2),
    }
    for side in doors:
        mx, mz = middle[side]
        along_x = side in ("north", "south")
        for offset in (-1, 0, 1):
            for y in range(1, 4):
                cells[(mx + offset, y, mz) if along_x else (mx, y, mz + offset)] = AIR
        cells[mx, 1, mz] = f"minecraft:jigsaw[orientation={side}_up]"
        data[mx, 1, mz] = {"id": "minecraft:jigsaw", "name": "realmforge:door", "target": "realmforge:door"}
    cells.update(blocks or {})
    built = tuple(
        StructureBlock(pos, BlockState.parse(name), data.get(pos)) for pos, name in sorted(cells.items())
    )
    return Structure((width, height, depth), DATA_VERSION, built, entities)


def fit(name: str, **kwargs) -> Fit:
    named = NAMED_MASKS[name]
    return Fit(named.mask, builds_walls=named.builds_walls, **kwargs)


def singles(
    structure: Structure, piece: Fit, theme: dict[str, str] | None = None, *, light: bool = False
) -> list[Spot]:
    return [p.spots[0] for p in match(RoomView(structure, theme), [piece], patterns=False, light=light)]


# --- the mask ---------------------------------------------------------------------------------------------------


def test_masks_read_matejs_notation() -> None:
    mask = Mask.parse("W W W\nW . W\n_ _ _\n")
    assert (mask.width, mask.depth) == (3, 3)
    assert mask.rows[1] == ("W", ".", "W")
    assert mask.text == "W W W\nW . W\n_ _ _"
    assert mask.mirror_symmetric
    assert not Mask.parse("? W\nW .\n? _").mirror_symmetric


@pytest.mark.parametrize(
    ("text", "message"),
    [
        ("W  W\n. .", "single spaces"),
        ("WW\n..", "one character each"),
        ("W X\n. .", "unknown cell X"),
        ("W W\n.", "same number of cells"),
        ("W W\n_ _", "no piece cell"),
        ("\n", "empty"),
    ],
)
def test_masks_refuse_anything_else(text: str, message: str) -> None:
    with pytest.raises(MaskError, match=message):
        Mask.parse(text)


def test_the_jigs_masks_are_the_design_reports() -> None:
    assert NAMED_MASKS["wall_3x2"].mask.text == "W W W\n. . .\n. . .\n_ _ _"
    assert NAMED_MASKS["corner_1x1"].mask.text == "? W\nW .\n? _"
    assert NAMED_MASKS["niche_2x1"].mask.text == "? W W ?\nW . . W\n? _ _ ?"
    assert NAMED_MASKS["free_1x1"].mask.text == "_ _ _\n_ . _\n_ _ _"
    built = NAMED_MASKS["built_niche_3x1"]
    assert built.builds_walls and built.mask.text == "W W W W W\nW . . . W\n_ _ _ _ _"
    assert Fit(built.mask, builds_walls=True).template_size() == (5, 3, 2), "the walls are part of the piece"
    assert fit("wall_3x2").template_size() == (3, 3, 2)


def test_placements_turn_and_mirror_like_vanilla() -> None:
    # StructureTemplate#transform with the pivot at the origin: a FRONT_BACK mirror (x to -x), then clockwise turns.
    assert transform(2, 1, 0, False) == (2, 1)
    assert transform(2, 1, 1, False) == (-1, 2)
    assert transform(2, 1, 2, False) == (-2, -1)
    assert transform(2, 1, 3, False) == (1, -2)
    assert transform(2, 1, 0, True) == (-2, 1)
    piece = fit("wall_2x1")
    assert [placement(piece, turns, False).facing for turns in range(4)] == ["south", "west", "north", "east"]
    turned = placement(piece, 1, False)
    assert turned.size == (1, 2), "turned, a 2x1 piece lies along z"
    assert (1, 0, "W") in turned.cells and (0, 0, "P") in turned.cells, "facing west, its wall is to the east"


# --- the matcher's rules ----------------------------------------------------------------------------------------


def test_a_wall_piece_stands_against_a_wall_away_from_the_doorways() -> None:
    spots = singles(box_room(), fit("wall_1x1"))
    north = sorted(spot.pos for spot in spots if spot.facing == "south" and spot.pos[2] == 1)
    assert north == [(1, 1, 1), (2, 1, 1), (6, 1, 1), (7, 1, 1)], (
        "two blocks or more from the connector at x 4"
    )
    assert all(spot.pos[1] == 1 for spot in spots), "on the floor"
    view = RoomView(box_room())
    piece = fit("wall_1x1")
    assert not view.fits(piece, placement(piece, 0, False), (4, 1, 4), view.walls(piece)), (
        "no wall in mid-room"
    )


@pytest.mark.parametrize(
    ("change", "reason"),
    [
        ({"wall": "minecraft:barrier"}, "a barrier is no wall"),
        ({"floor": "minecraft:barrier"}, "a barrier is no floor"),
        ({"height": 4}, "three blocks of air under a ceiling three high"),
    ],
)
def test_a_wall_piece_needs_wall_floor_and_air(change: dict, reason: str) -> None:
    assert not singles(box_room(**change), fit("wall_1x1")), reason


def test_a_lower_piece_fits_a_lower_wall() -> None:
    low = box_room(blocks={(x, 3, 0): AIR for x in range(9)})  # the north wall is two blocks high

    def north(piece: Fit) -> list[Spot]:
        return [spot for spot in singles(low, piece) if spot.facing == "south"]

    assert not north(fit("wall_1x1")), "a piece three high needs wall three high behind it"
    assert north(fit("wall_1x1", height=2))


def test_entities_and_mob_markers_keep_their_cells() -> None:
    stand = StructureEntity((1.5, 1.0, 1.5), (1, 1, 1), {"id": "minecraft:armor_stand"})
    spots = singles(box_room(entities=(stand,)), fit("wall_1x1"))
    assert all((1, 1, 1) not in pattern_cells(fit("wall_1x1"), spot)[0] for spot in spots)
    marker = box_room(blocks={(2, 1, 2): "minecraft:structure_block[mode=data]"})
    marker = Structure(
        marker.size,
        marker.data_version,
        tuple(
            StructureBlock(
                b.pos, b.state, {"id": "minecraft:structure_block", "mode": "DATA", "metadata": "mob"}
            )
            if b.pos == (2, 1, 2)
            else b
            for b in marker.blocks
        ),
    )
    near = [s.pos for s in singles(marker, fit("wall_1x1")) if max(abs(s.pos[0] - 2), abs(s.pos[2] - 2)) <= 1]
    assert near == [], "one block or more from a mob marker"


def test_the_clearance_must_stay_where_players_stand() -> None:
    blocked = box_room(blocks={(1, 1, 2): BRICKS, (2, 1, 2): BRICKS})
    spots = singles(blocked, fit("wall_1x1"))
    assert all(spot.pos not in ((1, 1, 1), (2, 1, 1)) or spot.facing != "south" for spot in spots)


def test_clearance_is_never_shared() -> None:
    piece = fit("free_1x1")
    spots = singles(box_room(13, 13), piece)
    assert len(spots) > 1
    clear = [pattern_cells(piece, spot)[1] for spot in spots]
    filled = [pattern_cells(piece, spot)[0] for spot in spots]
    for i, cells in enumerate(clear):
        for j, other in enumerate(clear):
            if i != j:
                assert not cells & other, "no clearance cell serves two spots"
                assert not cells & filled[j], "no piece stands on another spot's clearance"


def test_a_piece_never_cuts_a_doorway_off() -> None:
    corridor = box_room(3, 9, doors=())
    corridor = box_room(3, 9, doors=("north", "south"), blocks={})
    lone = Fit(Mask.parse("."))
    assert RoomView(corridor).doors, "the corridor's doorways stand"
    assert singles(corridor, lone) == [], "every cell of a one-wide corridor joins its doorways"
    assert singles(box_room(5, 9), lone), "a wider room leaves room around a piece"


def test_a_low_piece_in_a_lit_room_leaves_no_dark_cell_on_top() -> None:
    lantern = {(4, 3, 4): "minecraft:lantern"}  # hangs in the middle of a room six high
    room = box_room(9, 9, 6, doors=(), blocks=lantern)
    low = Fit(Mask.parse("W\n.\n_"), height=1)
    found = singles(room, low, light=True)
    assert found, "the lantern lights the tops of the low pieces"
    dark = box_room(9, 9, 6, doors=())
    assert singles(dark, low, light=True), "in a dark room the floor a piece covers was dark already"


def test_the_light_check_keeps_lit_cells_lit() -> None:
    torch = {(1, 1, 1): "minecraft:torch"}
    corridor = box_room(3, 11, doors=(), blocks=torch)
    lone = Fit(Mask.parse("."))
    assert [spot.pos for spot in singles(corridor, lone, light=True)] == [(1, 1, 9)], (
        "a piece across a one-wide corridor darkens the rest of it, but not at its end"
    )
    assert (1, 1, 2) in [spot.pos for spot in singles(corridor, lone, light=False)]


def test_a_generated_rooms_walls_count_by_their_role() -> None:
    theme = {"wall_body": BRICKS, "pillar": "minecraft:tuff_bricks"}
    tuff = box_room(wall="minecraft:tuff_bricks")
    assert not singles(tuff, fit("wall_1x1"), theme), "tuff bricks are the theme's pillars, not its walls"
    assert singles(tuff, fit("wall_1x1", walls=("pillar",)), theme)
    assert singles(tuff, fit("wall_1x1", walls=("minecraft:tuff_bricks",)), theme), (
        "a block id names it directly"
    )
    assert singles(tuff, fit("wall_1x1")), "an imported room takes any full block for a role"
    assert not singles(tuff, fit("wall_1x1", walls=("minecraft:deepslate_tiles",))), "but not for a block id"


def test_pieces_keep_off_a_generated_rooms_paths() -> None:
    lone = Fit(Mask.parse("."))
    path = frozenset((x, z) for x in range(3, 6) for z in range(1, 8))
    spots = [p.spots[0] for p in match(RoomView(box_room(), None, path), [lone], patterns=False, light=False)]
    assert spots and not any((s.pos[0], s.pos[2]) in path for s in spots)


def test_a_piece_with_its_own_walls_needs_a_flat_wall_and_free_space() -> None:
    flat = box_room(11, 9)
    assert singles(flat, fit("built_niche_3x1")), "a five-wide stretch of flat wall"
    assert not singles(flat, fit("niche_3x1")), "a real niche needs walls beside it"


# --- patterns ---------------------------------------------------------------------------------------------------


def test_spots_come_in_the_rooms_mirrored_patterns() -> None:
    piece = fit("wall_1x1")
    view = RoomView(box_room())
    found = match(view, [piece])
    first = found[0]
    assert first.kind == "four"
    assert sorted((s.pos, s.facing, s.mirror) for s in first.spots) == [
        ((1, 1, 1), "south", False),
        ((1, 1, 7), "north", True),
        ((7, 1, 1), "south", True),
        ((7, 1, 7), "north", False),
    ]
    assert view.mirrors == (8, 8)
    spot, image = first.spots[0], view.image(piece, first.spots[0], True, False)
    assert image is not None
    assert pattern_cells(piece, image)[0] == {(8 - x, y, z) for x, y, z in pattern_cells(piece, spot)[0]}
    cells = [cell for pattern in found for spot in pattern.spots for cell in pattern_cells(piece, spot)[0]]
    assert len(cells) == len(set(cells)), "the patterns a room offers never overlap"


def test_a_spot_on_a_walls_axis_is_a_pattern_of_its_own() -> None:
    piece = fit("wall_3x1")
    found = match(RoomView(box_room(11, 9, doors=("north",))), [piece])
    centred = [p for p in found if p.kind == "centred"]
    assert centred and all(p.spots[0].pos[0] == 4 for p in centred), "x 4 to 6 lies on the middle of x 1 to 9"


def test_a_mask_that_mirroring_changes_gets_no_mirrored_spot_without_a_mirrorable_piece() -> None:
    piece = fit("corner_1x1")
    found = match(RoomView(box_room()), [piece], mirrorable=[False])
    assert found and all(not spot.mirror for p in found for spot in p.spots)
    assert any(spot.mirror for p in match(RoomView(box_room()), [piece]) for spot in p.spots)


# --- placing pieces ---------------------------------------------------------------------------------------------


def test_states_turn_and_flip_like_vanilla() -> None:
    stairs = BlockState.parse("minecraft:oak_stairs[facing=north,half=bottom,shape=outer_left]")
    assert transform_state(stairs, 1, False).get("facing") == "east"
    flipped = transform_state(BlockState.parse("minecraft:oak_stairs[facing=east,shape=outer_left]"), 0, True)
    assert (flipped.get("facing"), flipped.get("shape")) == ("west", "outer_right")
    skull = BlockState.parse("minecraft:skeleton_skull[rotation=4]")
    assert transform_state(skull, 0, True).get("rotation") == "12", "Mirror.FRONT_BACK#mirror(4, 16)"
    assert transform_state(skull, 1, False).get("rotation") == "8"
    door = transform_state(BlockState.parse("minecraft:oak_door[facing=east,hinge=left]"), 0, True)
    assert (door.get("facing"), door.get("hinge")) == ("west", "right")
    assert transform_state(BlockState.parse("minecraft:oak_log[axis=x]"), 1, False).get("axis") == "z"


def test_a_piece_lands_in_its_spot_turned() -> None:
    piece = Structure(
        (2, 1, 1),
        DATA_VERSION,
        (
            StructureBlock((0, 0, 0), BlockState.parse("minecraft:oak_stairs[facing=south]")),
            StructureBlock((1, 0, 0), BlockState.parse(STONE)),
        ),
    )
    blocks: dict[Pos, StructureBlock] = {}
    place_piece(blocks, piece, Spot((5, 1, 3), 1, False), True)
    assert sorted((pos, str(block.state)) for pos, block in blocks.items()) == [
        ((5, 1, 3), "minecraft:oak_stairs[facing=west]"),
        ((5, 1, 4), STONE),
    ], "turned clockwise, x runs along z, and the box starts at the spot"
    room = box_room()
    found = match(RoomView(room), [fit("wall_1x1", height=1)])
    barrel = Structure(
        (1, 1, 1), DATA_VERSION, (StructureBlock((0, 0, 0), BlockState.parse("minecraft:barrel")),)
    )
    with_pieces = dressed(room, found, lambda pattern: (barrel, True))
    placed = [b.pos for b in with_pieces.blocks if b.state.name == "minecraft:barrel"]
    assert sorted(placed) == sorted(spot.pos for p in found for spot in p.spots)


# --- the content type -------------------------------------------------------------------------------------------

BARREL_NBT = Structure(
    (1, 2, 1),
    DATA_VERSION,
    (
        StructureBlock(
            (0, 0, 0), BlockState.parse("minecraft:barrel[facing=up,open=false]"), {"id": "minecraft:barrel"}
        ),
        StructureBlock(
            (0, 1, 0), BlockState.parse("minecraft:skeleton_skull[rotation=0]"), {"id": "minecraft:skull"}
        ),
    ),
)
BARREL_RULE = "id: {id}\nmask: |\n  W\n  .\n  _\nheight: 2\ntags: [storage]\n"


def copy_test_piece(repo: RepoPaths) -> None:
    """Copy the test piece, content/set_pieces/test_barrel, into the scratch checkout (which leaves set pieces out)."""
    folder = repo.content / "set_pieces"
    folder.mkdir(parents=True, exist_ok=True)
    for suffix in (".nbt", ".yaml"):
        shutil.copyfile(
            REPO_ROOT / "content" / "set_pieces" / f"test_barrel{suffix}", folder / f"test_barrel{suffix}"
        )


def add_piece(
    repo: RepoPaths, piece_id: str, rule: str = BARREL_RULE, structure: Structure = BARREL_NBT
) -> None:
    folder = repo.content / "set_pieces"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{piece_id}.nbt").write_bytes(write_structure(structure))
    write(folder / f"{piece_id}.yaml", rule.format(id=piece_id))


def test_the_test_piece_validates(repo: RepoPaths) -> None:
    copy_test_piece(repo)
    content, _ = load_and_validate(repo)
    assert [piece.id for piece in content.set_pieces] == ["test_barrel"]
    assert content.set_pieces[0].model.fit().template_size() == (1, 2, 1)


@pytest.mark.parametrize(
    ("rule", "structure", "message"),
    [
        (
            BARREL_RULE.replace("height: 2", "height: 3"),
            BARREL_NBT,
            "size: 1 2 1, but the mask's piece cells and the height make 1 3 1",
        ),
        (BARREL_RULE.replace("  W\n  .\n", "  W W\n  . .\n").replace("  _\n", "  _ _\n"), BARREL_NBT, "size"),
        (
            BARREL_RULE,
            Structure(
                (1, 2, 1), DATA_VERSION, (StructureBlock((0, 0, 0), BlockState.parse("minecraft:spawner")),)
            ),
            "spawners belong to the dungeon",
        ),
        (
            BARREL_RULE,
            Structure(
                (1, 2, 1), DATA_VERSION, (StructureBlock((0, 0, 0), BlockState.parse("minecraft:lever")),)
            ),
            "no redstone",
        ),
        (
            BARREL_RULE,
            Structure(
                (1, 2, 1),
                DATA_VERSION,
                (StructureBlock((0, 0, 0), BlockState.parse("minecraft:oak_slab[waterlogged=true]")),),
            ),
            "no fluids",
        ),
        (
            BARREL_RULE,
            Structure(
                (1, 2, 1),
                DATA_VERSION,
                (
                    StructureBlock(
                        (0, 0, 0),
                        BlockState.parse("minecraft:chest"),
                        {"id": "minecraft:chest", "LootTable": "minecraft:chests/simple_dungeon"},
                    ),
                ),
            ),
            "holds no loot table",
        ),
        (
            BARREL_RULE + "variants:\n  chance:\n    minecraft:candle: 0.5\n",
            BARREL_NBT,
            "the template holds no such block",
        ),
        (
            BARREL_RULE
            + "variants:\n  swaps:\n    minecraft:barrel:\n      to: minecraft:chest\n      keep: [rotation]\n",
            BARREL_NBT,
            "has no property 'rotation'",
        ),
        (
            BARREL_RULE + "variants:\n  swaps:\n    minecraft:barrel:\n      to: minecraft:nope\n",
            BARREL_NBT,
            "unknown block 'minecraft:nope'",
        ),
        (BARREL_RULE + "walls: [minecraft:nope]\n", BARREL_NBT, "unknown block 'minecraft:nope'"),
        (BARREL_RULE + "walls: [floorish]\n", BARREL_NBT, "neither a theme role"),
        (BARREL_RULE.replace("tags: [storage]", "tags: []"), BARREL_NBT, "at least 1 item"),
        (BARREL_RULE + "builds_walls: true\n", BARREL_NBT, "size"),
        (
            BARREL_RULE.replace("  W\n  .\n  _\n", "  .\n  _\n").replace(
                "height: 2", "height: 2\nbuilds_walls: true"
            ),
            BARREL_NBT,
            "W cells in the first row",
        ),
        (BARREL_RULE.replace("  W\n", "  W  \n"), BARREL_NBT, ""),
        (BARREL_RULE + "roles: [lobby]\n", BARREL_NBT, "roles"),
        (
            BARREL_RULE + "variants:\n  chance:\n    minecraft:skeleton_skull: 1.0\n",
            BARREL_NBT,
            "less than 1",
        ),
    ],
)
def test_validation_refuses_a_broken_piece(
    repo: RepoPaths, rule: str, structure: Structure, message: str
) -> None:
    add_piece(repo, "broken", rule, structure)
    if not message:  # trailing spaces on a mask row are fine
        load_and_validate(repo)
        return
    problems = problems_for(repo)
    assert any(message in problem for problem in problems), problems


def test_a_block_outside_the_piece_cells_is_refused(repo: RepoPaths) -> None:
    corner = "id: {id}\nmask: |\n  ? W\n  W .\n  ? _\nheight: 1\ntags: [storage]\n"
    add_piece(
        repo,
        "corner",
        corner,
        Structure((1, 1, 1), DATA_VERSION, (StructureBlock((0, 0, 0), BlockState.parse(BRICKS)),)),
    )
    load_and_validate(repo)
    lshape = "id: {id}\nmask: |\n  W W\n  . ?\n  . .\n  _ _\nheight: 1\ntags: [storage]\n"
    air_at_any = Structure(
        (2, 1, 2),
        DATA_VERSION,
        tuple(StructureBlock((x, 0, z), BlockState.parse(AIR)) for x in range(2) for z in range(2)),
    )
    add_piece(repo, "lshape", lshape, air_at_any)
    assert any(
        "lies outside the piece's cells of the mask" in p and "block 1 0 0" in p for p in problems_for(repo)
    )


def test_a_piece_needs_both_files(repo: RepoPaths) -> None:
    write(repo.content / "set_pieces" / "lonely.yaml", BARREL_RULE.format(id="lonely"))
    (repo.content / "set_pieces" / "orphan.nbt").write_bytes(write_structure(BARREL_NBT))
    write(repo.content / "set_pieces" / "sub" / "x.yaml", "")
    problems = problems_for(repo)
    assert any("lonely.yaml" in p and "no .nbt template" in p for p in problems)
    assert any("orphan.nbt" in p and "no .yaml fit rule" in p for p in problems)
    assert any("no folders" in p for p in problems)


def test_a_pieces_id_is_its_file_name(repo: RepoPaths) -> None:
    add_piece(repo, "named", BARREL_RULE.replace("{id}", "other"))
    assert any("named.yaml" in p and "must equal the file name" in p for p in problems_for(repo))


# --- spots in the dungeon types ---------------------------------------------------------------------------------


def walled_hall(repo: RepoPaths) -> None:
    """The crypt pool with a hall that has walls, floor and ceiling (the others are empty boxes: no spots)."""
    crypt(repo)
    (repo.content / "rooms" / "crypt" / "hall.nbt").write_bytes(write_structure(box_room(7, 7, 5)))
    copy_test_piece(repo)


def test_rf_gen_writes_each_rooms_spots_into_its_dungeon_type(repo: RepoPaths) -> None:
    walled_hall(repo)
    files = generate(repo)
    data = json.loads(files[f"{DATA}/realmforge/dungeon_type/crypt.json"])
    hall = next(room for room in data["rooms"] if room["template"] == "realmforge:rooms/crypt/hall")
    patterns = hall["set_piece_spots"]
    assert patterns and all(pattern["pieces"] == ["realmforge:test_barrel"] for pattern in patterns)
    first = patterns[0]["spots"][0]
    assert set(first) == {"pos", "facing", "mirror"} and first["facing"] in ("north", "south", "east", "west")
    assert all("set_piece_spots" not in room for room in data["rooms"] if room["role"] != "hallway"), (
        "the empty boxes have no walls, and the test piece reaches hallways and combat rooms only"
    )
    assert data["set_pieces"] == {
        "realmforge:test_barrel": {
            "template": "realmforge:set_pieces/test_barrel",
            "processors": "realmforge:set_pieces/test_barrel",
            "weight": 1,
            "tags": ["test", "storage"],
            "mirror": True,
        }
    }
    assert data["set_piece_density"]["hallway"] == [0, 1] and data["set_piece_density"]["combat"] == [1, 2]
    assert f"{DATA}/structure/set_pieces/test_barrel.nbt" in files
    assert read_structure(files[f"{DATA}/structure/set_pieces/test_barrel.nbt"]).size == (1, 2, 1)
    assert "tools/schemas/set_pieces.schema.json" in files
    java = files[
        "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedSetPieceExpectations.java"
    ]
    assert (
        b'new SetPieceExpectation("realmforge:set_pieces/test_barrel", 1, 2, 1, "realmforge:set_pieces/test_barrel")'
        in java
    )
    masks = files[
        "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedSetPieceMasks.java"
    ]
    assert b'new NamedMask("wall_3x1", List.of("W W W", ". . .", "_ _ _"), false)' in masks


def test_a_piece_reaches_only_its_roles(repo: RepoPaths) -> None:
    walled_hall(repo)
    (repo.content / "set_pieces" / "test_barrel.yaml").write_text(
        BARREL_RULE.format(id="test_barrel") + "roles: [combat]\n", encoding="utf-8"
    )
    data = json.loads(generate(repo)[f"{DATA}/realmforge/dungeon_type/crypt.json"])
    assert all("set_piece_spots" not in room for room in data["rooms"])
    assert "set_pieces" not in data


def test_only_mirrorable_pieces_take_a_mirrored_spot_of_an_asymmetric_mask(repo: RepoPaths) -> None:
    walled_hall(repo)
    corner = "id: {id}\nmask: |\n  ? W\n  W .\n  ? _\nheight: 1\ntags: [storage]\n"
    one = Structure(
        (1, 1, 1), DATA_VERSION, (StructureBlock((0, 0, 0), BlockState.parse("minecraft:barrel")),)
    )
    add_piece(repo, "corner_plain", corner, one)
    add_piece(repo, "corner_mirror", corner + "variants:\n  mirror: true\n", one)
    data = json.loads(generate(repo)[f"{DATA}/realmforge/dungeon_type/crypt.json"])
    hall = next(room for room in data["rooms"] if room["template"] == "realmforge:rooms/crypt/hall")
    corners = [p for p in hall["set_piece_spots"] if "realmforge:corner_mirror" in p["pieces"]]
    assert corners
    for pattern in corners:
        mirrored = any(spot["mirror"] for spot in pattern["spots"])
        assert ("realmforge:corner_plain" in pattern["pieces"]) == (not mirrored)


def test_the_processor_list_carries_chance_blocks_and_swaps(repo: RepoPaths) -> None:
    rule = BARREL_RULE + (
        "variants:\n  chance:\n    minecraft:skeleton_skull: 0.25\n  swaps:\n"
        "    minecraft:barrel:\n      to: minecraft:barrel[open=true]\n      chance: 0.5\n      keep: [facing]\n"
    )
    add_piece(repo, "swapping", rule)
    add_piece(repo, "plain", BARREL_RULE)
    files = generate(repo)
    folder = f"{DATA}/worldgen/processor_list/set_pieces"
    assert f"{folder}/plain.json" not in files, "a piece without variants needs no processor list"
    rules = json.loads(files[f"{folder}/swapping.json"])["processors"][0]["rules"]
    assert rules[0]["input_predicate"] == {
        "block": "minecraft:skeleton_skull",
        "predicate_type": "minecraft:random_block_match",
        "probability": 0.75,
    }, "the skull is there a quarter of the time: it goes three times in four"
    assert rules[0]["output_state"] == {"Name": "minecraft:air"}
    assert rules[1]["input_predicate"]["block_state"] == {
        "Name": "minecraft:barrel",
        "Properties": {"facing": "up", "open": "false"},
    }
    assert rules[1]["output_state"] == {
        "Name": "minecraft:barrel",
        "Properties": {"facing": "up", "open": "true"},
    }


# --- the mask report --------------------------------------------------------------------------------------------


def test_the_mask_report_counts_rooms_and_spots_per_group() -> None:
    rooms = [
        ReportRoom("a", "one", box_room()),
        ReportRoom("b", "one", box_room(doors=("north",))),
        ReportRoom("c", "two", box_room(3, 5, doors=())),
    ]
    row = mask_report(rooms, [NAMED_MASKS["wall_1x1"]])[0]
    assert row.groups["two"] == (1, 1), "a one-wide room's two ends would share their clearance: one spot"
    assert row.groups["one"][0] == 2 and row.total[0] == 3
    assert row.total[1] == row.groups["one"][1] + 1
    assert row.materials["stone_brick"] == row.total[1]


DESIGN_TOTALS = {"wall_1x1": (112, 1569), "corner_1x1": (73, 332), "free_3x3": (32, 68)}


def test_the_mask_report_reproduces_the_designs_counts() -> None:
    """The design's mask report (docs/design/set-pieces.md, 2026-10-19), within the changes to the rooms since."""
    content = load_content(RepoPaths())
    rooms = [ReportRoom(room.id, "all", room.structure) for room in content.rooms]
    for row in mask_report(rooms, [NAMED_MASKS[name] for name in DESIGN_TOTALS]):
        rooms_then, spots_then = DESIGN_TOTALS[row.name]
        assert abs(row.total[0] - rooms_then) <= 0.15 * rooms_then, row
        assert abs(row.total[1] - spots_then) <= 0.2 * spots_then, row


def test_rf_set_pieces_report_prints_the_table(capsys: pytest.CaptureFixture[str]) -> None:
    assert cli.main(["set-pieces", "report", "niche_3x1"]) == 0
    out = capsys.readouterr().out
    rooms = len(
        load_content(RepoPaths()).rooms
    )  # whatever rooms were imported, rejected or kept since (RLM-390)
    assert "| Mask |" in out and "| 3x1 niche (`niche_3x1`) |" in out and f"All ({rooms})" in out
    assert cli.main(["set-pieces", "report", "nope"]) == 1


# --- the gallery ------------------------------------------------------------------------------------------------


class FakeRenderer:
    def __init__(self) -> None:
        self.calls: list[tuple[str, int]] = []

    def render(self, structure: Structure, view: View, target: Path, cut: int = 5, outlines=()) -> None:
        self.calls.append((view.name, len(outlines)))
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"png")


def test_the_gallery_draws_spots_and_a_sample_dressing(tmp_path: Path) -> None:
    rooms = tmp_path / "rooms"
    rooms.mkdir()
    (rooms / "hall.nbt").write_bytes(write_structure(box_room()))
    (rooms / "bare.nbt").write_bytes(write_structure(box_room()))
    group = gallery.SpotGroup("test_barrel", 0, "four", (((1, 1, 1), "south"),), ((1, 1, 1),))

    def spots_of(room: gallery.GalleryRoom):
        return ((group,), box_room()) if room.key == "hall" else ((), None)

    renderer = FakeRenderer()
    page, found = gallery.build(rooms, tmp_path / "out", renderer, "Rooms", spots_of=spots_of)  # type: ignore[arg-type]
    hall = next(room for room in found if room.key == "hall")
    assert hall.outlines() == (((1, 1, 1), "minecraft:purple_stained_glass"),)
    assert sorted(renderer.calls) == sorted(
        [(view.name, 1) for view in VIEWS] + [(DRESSING.name, 0)] + [(view.name, 0) for view in VIEWS]
    )
    text = page.read_text(encoding="utf-8")
    assert "Set-piece spots" in text and "<code>test_barrel</code> four: 1 1 1 south" in text
    assert 'src="hall/dressing.png"' in text and 'src="bare/dressing.png"' not in text
    drawn = dict(drawn_blocks(box_room(), VIEWS[3], outlines=hall.outlines()))
    assert drawn[(1, 1, 1)] == "minecraft:purple_stained_glass"


# --- importing the jig's saves ----------------------------------------------------------------------------------


def save(world: Path, name: str, structure: Structure) -> Path:
    path = world / piece_import.JIG_FOLDER / f"{name}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(write_structure(structure))
    return path


SHELF = Structure(
    (3, 3, 1),
    DATA_VERSION,
    tuple(
        StructureBlock((x, y, 0), BlockState.parse("minecraft:bookshelf" if y == 0 else AIR))
        for x in range(3)
        for y in range(3)
    ),
)


def test_an_imported_save_becomes_a_piece_with_a_drafted_fit_rule(tmp_path: Path) -> None:
    universe = load_vanilla(RepoPaths())
    target = tmp_path / "set_pieces"
    source = save(tmp_path / "world", "wall_3x1_1", SHELF)
    result = piece_import.import_piece(source, "shelf", target, universe)
    assert result.written and result.drafted and not result.problems
    piece = read_structure((target / "shelf.nbt").read_bytes())
    assert piece.size == (3, 1, 3 - 2), "the air layers on top are dropped: the piece is one block high"
    rule = (target / "shelf.yaml").read_text(encoding="utf-8")
    assert "mask: |\n  W W W\n  . . .\n  _ _ _\n" in rule
    assert (
        "height: 1\n" in rule
        and "tags: []\n" in rule
        and "source: realmforge_qa:set_piece/wall_3x1_1\n" in rule
    )
    write(target / "shelf.yaml", rule.replace("tags: []", "tags: [library]"))
    again = piece_import.import_piece(source, "shelf", target, universe)
    assert again.written and not again.drafted, "importing again keeps the fit rule"
    assert "tags: [library]" in (target / "shelf.yaml").read_text(encoding="utf-8")
    assert piece_import.imported_sources(target) == {"realmforge_qa:set_piece/wall_3x1_1"}


def test_a_drafted_fit_rule_validates_once_its_tags_are_filled_in(repo: RepoPaths, tmp_path: Path) -> None:
    universe = load_vanilla(repo)
    built = Structure(
        (5, 3, 2),
        DATA_VERSION,
        tuple(
            StructureBlock(
                (x, 0, z), BlockState.parse(BRICKS if z == 0 or x in (0, 4) else "minecraft:hay_block")
            )
            for x in range(5)
            for z in range(2)
        ),
    )
    source = save(tmp_path / "world", "built_niche_3x1_2", built)
    result = piece_import.import_piece(source, "hay_niche", repo.content / "set_pieces", universe)
    assert result.written
    rule = repo.content / "set_pieces" / "hay_niche.yaml"
    assert "builds_walls: true" in rule.read_text(encoding="utf-8")
    assert any("hay_niche.yaml: tags" in p for p in problems_for(repo))
    rule.write_text(rule.read_text(encoding="utf-8").replace("tags: []", "tags: [stable]"), encoding="utf-8")
    content, _ = load_and_validate(repo)
    assert next(p for p in content.set_pieces if p.id == "hay_niche").model.fit().builds_walls


@pytest.mark.parametrize(
    ("name", "structure", "message"),
    [
        ("shelf", SHELF, "names no jig mask"),
        ("wall_9x9_1", SHELF, "names no jig mask"),
        (
            "wall_3x1_1",
            Structure(
                (3, 5, 1),
                DATA_VERSION,
                tuple(StructureBlock((x, 4, 0), BlockState.parse(STONE)) for x in range(3)),
            ),
            "5 blocks high",
        ),
        ("wall_1x1_1", SHELF, "size"),
        (
            "wall_3x1_1",
            Structure(
                (3, 1, 1), DATA_VERSION, (StructureBlock((0, 0, 0), BlockState.parse("minecraft:spawner")),)
            ),
            "spawners",
        ),
    ],
)
def test_an_import_refuses_what_is_no_piece(
    tmp_path: Path, name: str, structure: Structure, message: str
) -> None:
    result = piece_import.import_piece(
        save(tmp_path / "world", name, structure), "x", tmp_path / "out", load_vanilla(RepoPaths())
    )
    assert not result.written and any(message in problem for problem in result.problems), result.problems
    assert not (tmp_path / "out" / "x.nbt").exists()


def test_from_world_imports_every_new_save(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    world, target = tmp_path / "world", tmp_path / "pieces"
    save(world, "wall_3x1_1", SHELF)
    assert cli.main(["set-pieces", "import", "--from-world", str(world), "--to", str(target)]) == 0
    save(world, "wall_3x1_2", SHELF)
    assert cli.main(["set-pieces", "import", "--from-world", str(world), "--to", str(target)]) == 0
    out = capsys.readouterr().out
    assert out.count("OK   ") == 2, "the second run imports only the new save"
    assert sorted(path.name for path in target.iterdir()) == [
        "wall_3x1_1.nbt",
        "wall_3x1_1.yaml",
        "wall_3x1_2.nbt",
        "wall_3x1_2.yaml",
    ]
    assert cli.main(["set-pieces", "import", "--from-world", str(world), "--to", str(target)]) == 0
    assert "No new saves" in capsys.readouterr().out
    assert cli.main(["set-pieces", "import", str(world), "--from-world", str(world)]) == 1


def test_a_theme_of_our_own_blocks_gets_the_same_spots() -> None:
    # RLM-473: validation takes our own simple blocks as a theme's full blocks, so the matcher takes them as walls and
    # floors too, though no name rule knows them.
    vault = "realmforge:vaultstone"
    vanilla = singles(box_room(), fit("wall_1x1"), {"wall_body": BRICKS, "floor": STONE, "ceiling": STONE})
    own = singles(box_room(wall=vault, floor=vault), fit("wall_1x1"), {"wall_body": vault, "floor": vault})
    assert vanilla and own == vanilla
    assert (
        singles(box_room(wall=vault, floor=vault), fit("wall_1x1"), {"wall_body": BRICKS, "floor": STONE})
        == []
    )


# --- RLM-390: unique save names, decorative entities only, other mods' blocks --------------------------------------


@pytest.mark.parametrize(
    ("name", "mask"),
    [
        ("wall_3x1_k3j9z1", "wall_3x1"),
        ("built_niche_3x1_0a0a0a", "built_niche_3x1"),
        ("wall_3x1_2", "wall_3x1"),
    ],
)
def test_a_saves_mask_comes_before_its_token(name: str, mask: str) -> None:
    named = piece_import.mask_of(Path(f"{name}.nbt"))
    assert named is not None and named.name == mask


def with_entity(entity_id: str) -> Structure:
    shelf = Structure(
        (3, 1, 1),
        DATA_VERSION,
        tuple(StructureBlock((x, 0, 0), BlockState.parse("minecraft:bookshelf")) for x in range(3)),
    )
    return replace(shelf, entities=(StructureEntity((1.5, 0.5, 0.5), (1, 0, 0), {"id": entity_id}),))


@pytest.mark.parametrize(
    "entity_id",
    ["minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting", "minecraft:armor_stand"],
)
def test_a_piece_keeps_its_decorative_entities(entity_id: str, tmp_path: Path) -> None:
    fit = Fit(NAMED_MASKS["wall_3x1"].mask, 1)
    assert piece_problems(fit, with_entity(entity_id)) == []
    result = piece_import.import_piece(
        save(tmp_path / "world", "wall_3x1_abc", with_entity(entity_id)),
        "x",
        tmp_path / "out",
        load_vanilla(RepoPaths()),
    )
    assert result.written, result.problems


@pytest.mark.parametrize("entity_id", ["minecraft:zombie", "minecraft:item", "minecraft:experience_orb"])
def test_a_piece_refuses_a_mob_or_a_dropped_item(entity_id: str, tmp_path: Path) -> None:
    fit = Fit(NAMED_MASKS["wall_3x1"].mask, 1)
    assert [location for location, _ in piece_problems(fit, with_entity(entity_id))] == [
        f"entity {entity_id} at 1 0 0"
    ]
    result = piece_import.import_piece(
        save(tmp_path / "world", "wall_3x1_abc", with_entity(entity_id)),
        "x",
        tmp_path / "out",
        load_vanilla(RepoPaths()),
    )
    assert not result.written
    assert any("only decorative entities" in problem for problem in result.problems), result.problems


def test_from_world_imports_a_second_worlds_saves(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    # Each jig names its save with a token of its own, so a second world's saves are new saves.
    first, second, target = tmp_path / "first", tmp_path / "second", tmp_path / "pieces"
    save(first, "wall_3x1_aaaaaa", SHELF)
    save(second, "wall_3x1_bbbbbb", SHELF)
    for world in (first, second):
        assert cli.main(["set-pieces", "import", "--from-world", str(world), "--to", str(target)]) == 0
    assert capsys.readouterr().out.count("OK   ") == 2
    assert piece_import.imported_sources(target) == {
        "realmforge_qa:set_piece/wall_3x1_aaaaaa",
        "realmforge_qa:set_piece/wall_3x1_bbbbbb",
    }


def test_an_import_never_replaces_a_piece_from_another_save(tmp_path: Path) -> None:
    universe, target = load_vanilla(RepoPaths()), tmp_path / "pieces"
    assert piece_import.import_piece(
        save(tmp_path / "w", "wall_3x1_aaaaaa", SHELF), "shelf", target, universe
    ).written
    before = (target / "shelf.nbt").read_bytes()
    other = save(tmp_path / "w", "wall_3x1_bbbbbb", with_entity("minecraft:painting"))
    again = piece_import.import_piece(other, "shelf", target, universe)
    assert not again.written
    assert again.problems == [
        "the piece shelf came from realmforge_qa:set_piece/wall_3x1_aaaaaa, not from this save: import it under "
        "another --id"
    ]
    assert (target / "shelf.nbt").read_bytes() == before


def test_an_import_says_why_other_mods_blocks_are_refused(tmp_path: Path) -> None:
    table = Structure(
        (3, 1, 1),
        DATA_VERSION,
        tuple(StructureBlock((x, 0, 0), BlockState.parse("mcwfurnitures:oak_table")) for x in range(3)),
    )
    result = piece_import.import_piece(
        save(tmp_path / "w", "wall_3x1_abc", table), "x", tmp_path / "out", load_vanilla(RepoPaths())
    )
    assert not result.written
    assert any(
        p.startswith("blocks of other mods (mcwfurnitures): build with vanilla blocks only")
        for p in result.problems
    )
