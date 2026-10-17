"""The room cache (RLM-165): parsed templates and the values read from them, keyed by the template and the code."""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from realmforge_tools import content as content_module
from realmforge_tools import project
from realmforge_tools.content import load_content
from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.project import RepoPaths
from realmforge_tools.rooms import cache, check
from realmforge_tools.rooms.cache import RoomCache, template_key, universe_digest
from realmforge_tools.rooms.structure import BlockState, StructureBlock, read_structure, write_structure
from realmforge_tools.validate import load_and_validate

from .test_rooms import add_room, problems, small_room


@pytest.fixture
def decodes(monkeypatch: pytest.MonkeyPatch) -> list[int]:
    """Counts the templates ``load_content`` decodes."""
    calls: list[int] = []

    def counting(data: bytes, **kwargs: object):
        calls.append(len(data))
        return read_structure(data, **kwargs)

    monkeypatch.setattr(content_module, "read_structure", counting)
    return calls


def test_a_packed_structure_comes_back_equal(tmp_path: Path) -> None:
    room = small_room()  # a chest with block entity data and an armor stand
    rooms = RoomCache(tmp_path)
    rooms.put_structure("k", room)
    assert rooms.structure("k") == room
    assert rooms.structure("other") is None


def test_an_unchanged_room_is_decoded_once(repo: RepoPaths, decodes: list[int]) -> None:
    add_room(repo, "stone/hall")
    first = load_content(repo).rooms[0].structure
    assert len(decodes) == 1
    second = load_content(repo).rooms[0]
    assert second.structure == first and len(decodes) == 1  # rebuilt from the cache
    version = cache.code_version()[:16]
    assert (repo.cache / "rooms" / "parsed" / version / "structure" / f"{second.key}.pickle").is_file()


def test_a_changed_template_misses(repo: RepoPaths, decodes: list[int]) -> None:
    path = add_room(repo, "stone/hall")
    key = load_content(repo).rooms[0].key
    changed = small_room()
    changed = type(changed)(
        changed.size,
        changed.data_version,
        (*changed.blocks[1:], StructureBlock((0, 0, 0), BlockState("minecraft:dirt"))),
    )
    path.write_bytes(write_structure(changed))
    room = load_content(repo).rooms[0]
    assert room.key != key and len(decodes) == 2
    assert BlockState("minecraft:dirt") in {block.state for block in room.structure.blocks}


def test_a_new_cache_version_misses(
    repo: RepoPaths, decodes: list[int], monkeypatch: pytest.MonkeyPatch
) -> None:
    add_room(repo, "stone/hall")
    key = load_content(repo).rooms[0].key
    monkeypatch.setattr(cache, "CACHE_VERSION", cache.CACHE_VERSION + 1)
    cache.code_version.cache_clear()
    try:
        assert load_content(repo).rooms[0].key != key and len(decodes) == 2
    finally:
        monkeypatch.undo()
        cache.code_version.cache_clear()


def test_the_key_follows_the_template_bytes() -> None:
    data = write_structure(small_room())
    assert template_key(data) == template_key(bytes(data))
    assert template_key(data) != template_key(data + b"\0")


def test_a_derived_value_is_computed_once_per_kind(tmp_path: Path) -> None:
    rooms, calls = RoomCache(tmp_path), []

    def compute() -> list[tuple[str, str]]:
        calls.append(1)
        return [("pos 1 2 3", "a problem")]

    assert rooms.derived("check-a", "k", compute) == [("pos 1 2 3", "a problem")]
    assert rooms.derived("check-a", "k", compute) == [("pos 1 2 3", "a problem")] and len(calls) == 1
    rooms.derived("check-b", "k", compute)  # another universe digest
    assert len(calls) == 2


def test_a_broken_entry_is_computed_again(tmp_path: Path) -> None:
    rooms = RoomCache(tmp_path)
    rooms.derived("size", "k", lambda: (5, 4, 5))
    assert rooms.folder is not None
    (rooms.folder / "size" / "k.pickle").write_bytes(b"not a pickle")
    assert rooms.derived("size", "k", lambda: (7, 7, 7)) == (7, 7, 7)
    (rooms.folder / "structure").mkdir()
    (rooms.folder / "structure" / "k.pickle").write_bytes(b"\x80\x05junk")
    assert rooms.structure("k") is None


def test_no_folder_keeps_nothing(tmp_path: Path) -> None:
    rooms, calls = RoomCache(None), []
    for _ in range(2):
        rooms.derived("size", "k", lambda: calls.append(1))
    assert len(calls) == 2 and not rooms.has_structure("k")


def test_the_universe_digest_follows_ids_and_tags() -> None:
    universe = IdUniverse({"minecraft:block": {"minecraft:stone"}}, {"minecraft:block": {"minecraft:logs"}})
    same = IdUniverse({"minecraft:block": {"minecraft:stone"}}, {"minecraft:block": {"minecraft:logs"}})
    assert universe_digest(universe) == universe_digest(same)
    same.add("minecraft:block", "minecraft:dirt")
    assert universe_digest(universe) != universe_digest(same)
    tagged = IdUniverse({"minecraft:block": {"minecraft:stone"}}, {"minecraft:block": {"minecraft:planks"}})
    assert universe_digest(universe) != universe_digest(tagged)


def test_a_warm_validation_reports_the_same_problems(repo: RepoPaths) -> None:
    bad = small_room()
    bad = type(bad)(
        bad.size, bad.data_version, (*bad.blocks, StructureBlock((9, 9, 9), BlockState("minecraft:stone")))
    )
    add_room(repo, "stone/hall", bad)
    cold = problems(repo)
    assert cold and problems(repo) == cold  # the second run answers from the cache


def test_a_warm_validation_needs_no_blocks(repo: RepoPaths, monkeypatch: pytest.MonkeyPatch) -> None:
    add_room(repo, "stone/hall")
    load_and_validate(repo)
    monkeypatch.setattr(
        cache, "_unpack", lambda packed: pytest.fail("a warm validation rebuilt a room's blocks")
    )
    load_and_validate(repo)


def test_rooms_read_for_their_ids_only_are_not_decoded(repo: RepoPaths, decodes: list[int]) -> None:
    path = add_room(repo, "stone/hall")
    path.write_bytes(b"not a template")  # unreported, as nothing reads it
    (room,) = load_content(repo, read_templates=False).rooms
    assert room.id == "stone/hall" and decodes == []
    assert not (repo.cache / "rooms").exists()


def test_a_new_data_version_or_id_universe_code_misses(
    repo: RepoPaths, decodes: list[int], monkeypatch: pytest.MonkeyPatch
) -> None:
    add_room(repo, "stone/hall")
    key = load_content(repo).rooms[0].key
    changes = (
        ("DATA_VERSION", project, project.DATA_VERSION + 1),
        ("_TOOLS", cache, _tools_with_changed_id_universe(repo.root.parent)),
    )
    for name, module, value in changes:
        before = len(decodes)
        with monkeypatch.context() as patch:  # undoes only this change, not the fixture's
            patch.setattr(module, name, value)
            cache.code_version.cache_clear()
            try:
                assert load_content(repo).rooms[0].key != key and len(decodes) == before + 1, name
            finally:
                cache.code_version.cache_clear()


def _tools_with_changed_id_universe(scratch: Path) -> Path:
    """A copy of the hashed sources whose ``id_universe.py`` differs."""
    tools = Path(cache.__file__).resolve().parent.parent
    copy = scratch / "tools-copy"
    for folder in ("rooms", "formats"):
        shutil.copytree(tools / folder, copy / folder, ignore=shutil.ignore_patterns("__pycache__"))
    (copy / "id_universe.py").write_text((tools / "id_universe.py").read_text() + "\n# changed\n")
    return copy


def test_check_results_follow_the_data_version(repo: RepoPaths, monkeypatch: pytest.MonkeyPatch) -> None:
    add_room(repo, "stone/hall")
    load_and_validate(repo)  # valid, and cached
    with monkeypatch.context() as patch:
        patch.setattr(check, "DATA_VERSION", 2724)  # the room is now newer than the game
        patch.setattr(project, "DATA_VERSION", 2724)
        cache.code_version.cache_clear()
        try:
            assert any("is newer than the game's (2724)" in problem for problem in problems(repo))
        finally:
            cache.code_version.cache_clear()


def test_a_write_deletes_other_versions_and_universes(tmp_path: Path) -> None:
    stale_version = tmp_path / "0123456789abcdef" / "size"
    stale_version.mkdir(parents=True)
    (stale_version / "k.pickle").write_bytes(b"old")
    legacy = tmp_path / "structure"  # the layout before version folders
    legacy.mkdir()
    rooms = RoomCache(tmp_path)
    assert rooms.folder is not None
    other_universe = rooms.folder / "check-old"
    other_universe.mkdir(parents=True)
    rooms.derived("size", "k", lambda: (5, 4, 5))
    assert sorted(p.name for p in tmp_path.iterdir()) == [rooms.folder.name]
    assert other_universe.is_dir(), "only a check result deletes other universes' results"
    rooms.derived("check-new", "k", list)
    assert sorted(p.name for p in rooms.folder.iterdir()) == ["check-new", "size"]
    assert rooms.derived("size", "k", lambda: pytest.fail("the current version's entry is kept")) == (5, 4, 5)
