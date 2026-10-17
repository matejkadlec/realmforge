"""The room generator (A4d, RLM-121): room specs and themes, the generator's style rules, the walk and light checks,
the candidates for the gallery and freezing the review's decisions (architecture 15.4)."""

from __future__ import annotations

from collections import Counter
from pathlib import Path

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.id_universe import load_vanilla
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import REPO_ROOT, RepoPaths
from realmforge_tools.rooms.candidates import MARKER, CandidatesError, freeze, write_candidates
from realmforge_tools.rooms.check import check_structure, markers
from realmforge_tools.rooms.connectors import connectors, could_attach
from realmforge_tools.rooms.generator import (
    DOOR,
    LIGHT_SPACING,
    RoomGeneratorError,
    candidate_seeds,
    generate_room,
    room_id,
    spec_tags,
    theme_blocks,
    unknown_blocks,
)
from realmforge_tools.rooms.structure import (
    AIR,
    BlockState,
    Structure,
    StructureBlock,
    read_structure,
    write_structure,
)
from realmforge_tools.rooms.walk import Cells, dark_cells, reach, walk_problems, walkable
from realmforge_tools.schema.room_spec import RoomSpecDef, RoomThemeDef
from realmforge_tools.validate import load_and_validate
from realmforge_tools.yamlio import load_yaml

from .conftest import write

SPECS = sorted((REPO_ROOT / "content/room_specs").glob("*.yaml"))
THEME = RoomThemeDef.model_validate(load_yaml(REPO_ROOT / "content/room_themes/stone_vault.yaml"))
STAIRS = THEME.blocks.stairs
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedRoomExpectations.java"
)


def spec(**changes: object) -> RoomSpecDef:
    data: dict[str, object] = {
        "id": "test_room",
        "kind": "combat",
        "theme": "realmforge:stone_vault",
        "footprint": [11, 11],
        "height": 7,
        "connectors": ["west", "east"],
    }
    data.update(changes)
    return RoomSpecDef.model_validate(data)


def content_spec(path: Path) -> RoomSpecDef:
    return RoomSpecDef.model_validate(load_yaml(path))


def at(structure: Structure) -> dict[tuple[int, int, int], StructureBlock]:
    return {block.pos: block for block in structure.blocks}


# --- schema ---------------------------------------------------------------------------------------------------


@pytest.mark.parametrize(
    ("changes", "message"),
    [
        ({"kind": "corridor", "connectors": ["north", "east"]}, "opposite sides"),
        ({"kind": "curve", "connectors": ["north", "south"]}, "adjacent sides"),
        ({"kind": "t_junction", "connectors": ["north", "south"]}, "3 connector"),
        ({"kind": "dead_end", "connectors": ["north", "south"]}, "1 connector"),
        ({"connectors": ["north", "north"]}, "duplicate"),
        ({"connectors": []}, "at least 1"),
        ({"footprint": [4, 11]}, "greater than or equal to 5"),
        ({"footprint": [17, 11]}, "less than or equal to 16"),
        ({"features": ["pit"], "footprint": [8, 11]}, "pit needs a footprint"),
        ({"features": ["pit"], "height": 5}, "height of at least 6"),
        ({"features": ["balcony"], "connectors": ["north"], "height": 8}, "height of at least 9"),
        (
            {"features": ["balcony"], "connectors": ["north", "east", "south", "west"], "height": 9},
            "has none",
        ),
        ({"features": ["alcoves"], "connectors": ["north", "east", "south", "west"]}, "has none"),
        ({"features": ["pillars"], "footprint": [6, 11]}, "pillars need"),
        ({"seeds": [3, 4], "rejected_seeds": [4]}, "both kept and rejected: 4"),
        ({"seeds": [0]}, "greater than or equal to 1"),
    ],
)
def test_spec_refuses_what_cannot_be_built(changes: dict[str, object], message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        spec(**changes)


def test_theme_needs_a_block_of_its_own_per_role() -> None:
    data = THEME.model_dump()
    data["blocks"]["floor_variant"] = data["blocks"]["floor"]
    with pytest.raises(ValidationError, match=r"minecraft:polished_andesite \(floor, floor_variant\)"):
        RoomThemeDef.model_validate(data)


def test_tags_follow_the_kind_and_the_connectors() -> None:
    assert spec_tags(spec(kind="corridor", connectors=["north", "south"])) == ["hallway", "straight"]
    assert spec_tags(spec(kind="curve", connectors=["south", "east"])) == ["hallway", "curved"]
    assert spec_tags(spec(kind="arena", connectors=["south"])) == ["boss", "dead_end"]
    assert spec_tags(spec(kind="entrance", connectors=["north", "east", "south"])) == [
        "entrance",
        "t_junction",
    ]


def test_candidate_seeds_skip_decided_ones() -> None:
    assert candidate_seeds(spec(seeds=[2], rejected_seeds=[1, 4]), 3) == [3, 5, 6]


# --- the generator --------------------------------------------------------------------------------------------


@pytest.mark.parametrize("path", SPECS, ids=[path.stem for path in SPECS])
def test_every_content_spec_builds_playable_rooms(path: Path) -> None:
    room_spec = content_spec(path)
    universe = load_vanilla(RepoPaths())  # a generated room names only vanilla ids
    for seed in range(1, 5):
        structure = generate_room(room_spec, THEME, seed)
        assert check_structure(structure, universe) == [], seed
        assert walk_problems(structure) == [], seed
        assert unknown_blocks(structure, theme_blocks(THEME)) == [], seed
        assert structure.size == (room_spec.footprint[0], room_spec.height, room_spec.footprint[1])


def test_same_seed_same_room_and_seeds_differ() -> None:
    room_spec = spec(features=["pillars", "rubble"])
    first = write_structure(generate_room(room_spec, THEME, 7))
    assert write_structure(generate_room(room_spec, THEME, 7)) == first
    others = {write_structure(generate_room(room_spec, THEME, seed)) for seed in range(1, 7)}
    assert len(others) == 6


def test_doorways_carry_connectors_like_the_imported_rooms() -> None:
    structure = generate_room(spec(connectors=["north", "west"], footprint=[9, 11]), THEME, 1)
    found = {c.front: c for c in connectors(structure)}
    assert set(found) == {"north", "west"}
    assert found["north"].pos == (4, 1, 0) and found["west"].pos == (0, 1, 5)
    assert all(c.name == DOOR and c.target == DOOR and c.pool == "minecraft:empty" for c in found.values())
    assert could_attach(found["north"], found["west"])  # and so with an imported realmforge:door
    blocks = at(structure)
    for x in (3, 4, 5):  # three wide, three high, over a threshold of the floor border
        assert [blocks[(x, y, 0)].state.name for y in (2, 3)] == [AIR, AIR]
        assert blocks[(x, 0, 0)].state.name == THEME.blocks.floor_border
    assert blocks[(3, 1, 0)].state.name == AIR and blocks[(2, 1, 0)].state.name == THEME.blocks.wall_base


def test_walls_have_three_layers_and_patches() -> None:
    structure = generate_room(spec(height=8), THEME, 3)
    blocks = at(structure)
    column = [blocks[(0, y, 2)].state.name for y in range(8)]
    assert column[1] == THEME.blocks.wall_base and column[6] == THEME.blocks.wall_cornice
    assert column[7] == THEME.blocks.ceiling
    body = {THEME.blocks.wall_body, THEME.blocks.wall_body_cracked, THEME.blocks.wall_body_mossy}
    assert set(column[2:6]) <= body
    all_body = Counter(b.state.name for b in structure.blocks if b.state.name in body)
    assert len(all_body) == 3  # cracked and mossy patches among the plain body


def test_moulding_faces_the_wall_with_inner_corners() -> None:
    for seed in range(1, 20):
        structure = generate_room(spec(connectors=["south"]), THEME, seed)
        blocks = at(structure)
        top = 5
        if blocks[(5, top, 1)].state.name == STAIRS:
            break
    else:
        pytest.fail("no seed gave a moulding of stairs")
    north = blocks[(5, top, 1)].state
    assert (north.get("facing"), north.get("half"), north.get("shape")) == ("north", "top", "straight")
    assert blocks[(1, top, 5)].state.get("facing") == "west"
    corner = blocks[(1, top, 1)].state
    assert corner.name == STAIRS and corner.get("shape") in ("inner_left", "inner_right")


def test_pit_sinks_the_middle_behind_a_step_of_stairs() -> None:
    room_spec = spec(features=["pit"], footprint=[11, 11], connectors=["north", "south"])
    structure = generate_room(room_spec, THEME, 1)
    blocks = at(structure)
    assert all(c.pos[1] == 2 for c in connectors(structure))  # the floor is two blocks thick
    rim = blocks[(3, 1, 5)].state  # the pit spans 3..7; its west edge steps up to the west
    assert (rim.name, rim.get("facing"), rim.get("half")) == (STAIRS, "west", "bottom")
    assert blocks[(5, 1, 5)].state.name == AIR and blocks[(5, 0, 5)].state.name != AIR
    stands = walkable(Cells.of(structure))
    assert (5, 1, 5) in stands and (3, 2, 5) in stands
    assert blocks[(3, 1, 3)].state.get("shape") in ("inner_left", "inner_right")


def test_balcony_has_a_deck_of_top_slabs_and_stairs_up() -> None:
    room_spec = spec(kind="arena", features=["balcony"], footprint=[13, 13], height=9, connectors=["south"])
    structure = generate_room(room_spec, THEME, 2)
    cells = Cells.of(structure)
    slabs = [
        b for b in structure.blocks if b.state.name == THEME.blocks.slab and b.state.get("type") == "top"
    ]
    deck = [b for b in slabs if b.pos[1] == 4]  # three blocks above the floor; a slab moulding may sit higher
    along_a_wall = [(2, {1, 2}), (0, {1, 2}), (0, {10, 11})]  # north, west or east: south has the doorway
    assert len(deck) >= 2 * 10
    assert any(all(b.pos[axis] in rows for b in deck) for axis, rows in along_a_wall)
    steps = sorted(
        (b for b in structure.blocks if b.state.name == STAIRS and b.state.get("half") == "bottom"),
        key=lambda b: b.pos[1],
    )
    assert [b.pos[1] for b in steps] == [1, 2, 3]
    door = connectors(structure)[0].pos
    reached = reach(cells, door, walkable(cells))
    assert any(pos[1] == 5 for pos in reached), "the deck is reachable from the doorway"
    mob = [pos for name, pos in markers(structure) if name == "mob"]
    assert len(mob) == 4 and any(pos[1] == 5 for pos in mob)


def test_markers_follow_the_kind() -> None:
    entrance = generate_room(spec(kind="entrance", connectors=["north"]), THEME, 1)
    assert [name for name, _ in markers(entrance)] == ["entrance"]
    treasure = generate_room(spec(kind="treasure", connectors=["south"], features=["alcoves"]), THEME, 1)
    chests = [pos for name, pos in markers(treasure) if name == "chest"]
    assert chests and all(pos[2] == 1 or pos[0] in (1, 9) for pos in chests)  # in the niches along the walls
    hallway = generate_room(spec(kind="corridor", connectors=["north", "south"], footprint=[5, 9]), THEME, 1)
    assert markers(hallway) == []


def test_lights_cover_every_spawnable_cell_and_keep_their_spacing() -> None:
    structure = generate_room(spec(footprint=[16, 16], connectors=["north"]), THEME, 4)
    cells = Cells.of(structure)
    assert dark_cells(cells) == []
    torches = [b for b in structure.blocks if b.state.name == THEME.blocks.wall_light]
    assert torches
    for i, torch in enumerate(torches):
        dx, dz = {"north": (0, 1), "south": (0, -1), "east": (-1, 0), "west": (1, 0)}[
            torch.state.get("facing")
        ]
        behind = (torch.pos[0] + dx, torch.pos[1], torch.pos[2] + dz)
        assert cells.holds(behind), f"the light at {torch.pos} hangs on nothing"
        for other in torches[i + 1 :]:
            assert max(abs(a - b) for a, b in zip(torch.pos, other.pos, strict=True)) >= LIGHT_SPACING


def test_paths_stay_clear_of_pillars_and_rubble() -> None:
    for seed in range(1, 9):
        structure = generate_room(
            spec(features=["pillars", "rubble"], connectors=["north", "east"]), THEME, seed
        )
        blocks = at(structure)
        for z in range(1, 6):  # the strip from the north doorway to the middle
            for x in (4, 5, 6):
                assert blocks[(x, 1, z)].state.name in (AIR, "minecraft:structure_block"), (seed, x, z)


def test_a_spec_without_room_for_its_pillars_is_refused() -> None:
    crossing = spec(
        kind="crossing", connectors=["north", "east", "south", "west"], footprint=[7, 7], features=["pillars"]
    )
    with pytest.raises(RoomGeneratorError, match="no place for a pillar"):
        generate_room(crossing, THEME, 1)


# --- the walk and light checks --------------------------------------------------------------------------------


def _with(structure: Structure, changes: dict[tuple[int, int, int], str]) -> Structure:
    blocks = tuple(
        StructureBlock(b.pos, BlockState.parse(changes[b.pos]), None) if b.pos in changes else b
        for b in structure.blocks
    )
    return Structure(structure.size, structure.data_version, blocks)


def test_walk_check_finds_a_blocked_doorway_a_wall_and_low_headroom() -> None:
    room = generate_room(
        spec(kind="corridor", connectors=["north", "south"], footprint=[5, 9], height=6), THEME, 1
    )
    assert walk_problems(room) == []
    blocked = _with(room, {(2, 2, 0): "minecraft:stone"})
    assert any("headroom" in message for _, message in walk_problems(blocked))
    wall = _with(room, {(x, y, 4): "minecraft:stone" for x in (1, 2, 3) for y in (1, 2, 3, 4)})
    assert any("cannot be walked to" in message for _, message in walk_problems(wall))
    low = _with(room, {(x, 3, 4): "minecraft:stone" for x in (1, 2, 3)})
    assert any("cannot be walked to" in message for _, message in walk_problems(low))


def test_walk_check_finds_dark_cells() -> None:
    room = generate_room(spec(), THEME, 1)
    lights = {
        b.pos: AIR
        for b in room.blocks
        if b.state.name in (THEME.blocks.wall_light, THEME.blocks.ceiling_light)
    }
    problems = walk_problems(_with(room, lights))
    assert problems and "mob could spawn here in the dark" in problems[-1][1]


def test_one_step_up_is_walkable_two_are_not() -> None:
    room = generate_room(
        spec(kind="corridor", connectors=["north", "south"], footprint=[5, 9], height=7), THEME, 1
    )
    step = _with(room, {(x, 1, 4): "minecraft:stone" for x in (1, 2, 3)})
    assert walk_problems(step) == [] or all("dark" in m for _, m in walk_problems(step))
    wall = _with(room, {(x, y, 4): "minecraft:stone" for x in (1, 2, 3) for y in (1, 2)})
    assert any("cannot be walked to" in m for _, m in walk_problems(wall))


# --- content, validation and rf gen ---------------------------------------------------------------------------


def write_spec(repo: RepoPaths, spec_id: str, body: str) -> Path:
    header = f"id: {spec_id}\ntheme: realmforge:stone_vault\n"
    return write(repo.content / "room_specs" / f"{spec_id}.yaml", header + body)


def test_kept_seeds_become_rooms_in_the_jar(repo: RepoPaths) -> None:
    for path in (repo.content / "room_specs").glob("*.yaml"):
        path.unlink()
    write_spec(
        repo,
        "hall",
        "kind: combat\nfootprint: [9, 9]\nheight: 6\nconnectors: [north, south]\nseeds: [2, 5]\nrejected_seeds: [1]\n",
    )
    content, _ = load_and_validate(repo)
    assert [room.id for room in content.rooms] == ["generated/hall/seed_2", "generated/hall/seed_5"]
    assert content.rooms[0].model.tags == ["combat", "straight"]
    files = generate_all(content, repo)
    folder = repo.mod_generated / "resources/data/realmforge/structure/rooms/generated/hall"
    data = files[folder / "seed_2.nbt"]
    assert read_structure(data) == read_structure(
        write_structure(generate_room(content.room_specs[0].model, THEME, 2))
    )
    assert '"realmforge:rooms/generated/hall/seed_5", 9, 6, 9, 2,' in files[repo.root / EXPECTATIONS].decode()
    assert room_id("hall", 2) == "generated/hall/seed_2"


def test_generated_rooms_can_make_up_a_dungeon_pool(repo: RepoPaths) -> None:
    for path in (repo.content / "room_specs").glob("*.yaml"):
        path.unlink()
    pool = "pools: [realmforge:gen_vault]\nseeds: [1]\n"
    write_spec(repo, "start", f"kind: entrance\nfootprint: [9, 9]\nheight: 6\nconnectors: [north]\n{pool}")
    write_spec(
        repo, "hall", f"kind: corridor\nfootprint: [5, 9]\nheight: 5\nconnectors: [north, south]\n{pool}"
    )
    write_spec(
        repo,
        "fork",
        f"kind: t_junction\nfootprint: [9, 9]\nheight: 6\nconnectors: [east, south, west]\n{pool}",
    )
    write_spec(repo, "end", f"kind: dead_end\nfootprint: [5, 7]\nheight: 5\nconnectors: [north]\n{pool}")
    write_spec(
        repo,
        "lair",
        f"kind: arena\nfootprint: [13, 13]\nheight: 9\nconnectors: [south]\nfeatures: [balcony]\n{pool}",
    )
    write(
        repo.content / "dungeons/gen.yaml",
        "id: gen\nname:\n  en_us: Gen\npool: realmforge:gen_vault\nlayout:\n  main_path: [2, 4]\n"
        "  branches: [1, 1]\n  branch_rooms: [1, 2]\n  ending: boss\n",
    )
    content, _ = load_and_validate(repo)
    assert len(content.rooms) == 5


@pytest.mark.parametrize(
    ("file", "text", "message"),
    [
        (
            "room_specs/bad.yaml",
            "id: bad\ntheme: realmforge:nope\nkind: dead_end\nfootprint: [5, 5]\nheight: 5\n"
            "connectors: [north]\n",
            "no theme 'realmforge:nope'",
        ),
        ("rooms/generated/x.yaml", "tags: []\n", "reserved for the rooms of content/room_specs/"),
    ],
)
def test_validation_refuses_bad_specs_and_reserved_folders(
    repo: RepoPaths, file: str, text: str, message: str
) -> None:
    write(repo.content / file, text)
    with pytest.raises(ValidationFailed) as failed:
        load_and_validate(repo)
    assert any(message in problem.message for problem in failed.value.problems)


def test_validation_checks_what_each_theme_role_holds(repo: RepoPaths) -> None:
    theme = repo.content / "room_themes/stone_vault.yaml"
    text = theme.read_text(encoding="utf-8")
    text = text.replace("stairs: minecraft:stone_brick_stairs", "stairs: minecraft:oak_planks")
    text = text.replace("ceiling: minecraft:stone", "ceiling: minecraft:no_such_block")
    theme.write_text(text, encoding="utf-8")
    with pytest.raises(ValidationFailed) as failed:
        load_and_validate(repo)
    messages = {(p.location, p.message) for p in failed.value.problems}
    assert ("blocks.stairs", "minecraft:oak_planks is not a stairs block") in messages
    assert ("blocks.ceiling", "unknown block 'minecraft:no_such_block'") in messages


# --- candidates and freezing ----------------------------------------------------------------------------------


def test_candidates_are_the_next_undecided_seeds(repo: RepoPaths, tmp_path: Path) -> None:
    for path in (repo.content / "room_specs").glob("*.yaml"):
        path.unlink()
    write_spec(
        repo,
        "nook",
        "kind: dead_end\nfootprint: [5, 7]\nheight: 5\nconnectors: [north]\nseeds: [1]\n"
        "rejected_seeds: [3]\n",
    )
    content, universe = load_and_validate(repo)
    out = tmp_path / "candidates"
    results = write_candidates(content, universe, out, 3)
    assert [(r.spec, r.written, r.refused) for r in results] == [("nook", [2, 4, 5], [])]
    assert sorted(p.relative_to(out).as_posix() for p in out.rglob("*.nbt")) == [
        "nook/seed_2.nbt",
        "nook/seed_4.nbt",
        "nook/seed_5.nbt",
    ]
    assert "tags: [hallway, dead_end]" in (out / "nook/seed_2.yaml").read_text(encoding="utf-8")
    assert (out / MARKER).is_file()
    write_candidates(content, universe, out, 1)  # an earlier candidates folder is replaced
    assert sorted(p.name for p in (out / "nook").glob("*.nbt")) == ["seed_2.nbt"]
    foreign = tmp_path / "foreign"
    write(foreign / "keep.txt", "mine")
    with pytest.raises(CandidatesError, match="not an earlier candidates folder"):
        write_candidates(content, universe, foreign, 1)
    with pytest.raises(CandidatesError, match="no room spec ghost"):
        write_candidates(content, universe, out, 1, ["ghost"])


def test_freeze_writes_the_decisions_into_the_specs(repo: RepoPaths) -> None:
    for path in (repo.content / "room_specs").glob("*.yaml"):
        path.unlink()
    nook = write_spec(
        repo,
        "nook",
        "# a comment that stays\nkind: dead_end\nfootprint: [5, 7]\nheight: 5\n"
        "connectors: [north]\nseeds: [1]  # kept\nrejected_seeds: []\n",
    )
    hall = write_spec(
        repo, "hall", "kind: corridor\nfootprint: [5, 9]\nheight: 5\nconnectors: [north, south]\n"
    )
    frozen = freeze({"nook/seed_2": "keep", "nook/seed_1": "reject", "hall/seed_4": "keep"}, repo)
    assert [(f.spec, f.kept, f.rejected) for f in frozen] == [("hall", (4,), ()), ("nook", (2,), (1,))]
    text = nook.read_text(encoding="utf-8")
    assert "# a comment that stays" in text and "seeds: [2]\n" in text and "rejected_seeds: [1]\n" in text
    assert hall.read_text(encoding="utf-8").endswith("seeds: [4]\nrejected_seeds: []\n")
    load_and_validate(repo)


def test_freeze_refuses_bad_keys_and_block_lists_and_writes_nothing(repo: RepoPaths) -> None:
    for path in (repo.content / "room_specs").glob("*.yaml"):
        path.unlink()
    block = write_spec(
        repo, "block", "kind: dead_end\nfootprint: [5, 7]\nheight: 5\nconnectors: [north]\nseeds:\n  - 1\n"
    )
    plain = write_spec(repo, "plain", "kind: dead_end\nfootprint: [5, 7]\nheight: 5\nconnectors: [north]\n")
    before = plain.read_text(encoding="utf-8")
    with pytest.raises(CandidatesError, match="flow list"):
        freeze({"plain/seed_2": "keep", "block/seed_2": "keep"}, repo)
    assert plain.read_text(encoding="utf-8") == before and block.read_text(encoding="utf-8").endswith(
        "  - 1\n"
    )
    with pytest.raises(CandidatesError, match="names no candidate"):
        freeze({"plain/2": "keep"}, repo)
    with pytest.raises(CandidatesError, match="no room spec ghost"):
        freeze({"ghost/seed_1": "keep"}, repo)
