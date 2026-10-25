"""content/quests/: the quest book FTB Quests reads from pack/config/ftbquests/quests/ (architecture 16, Q1-1).

FTB Quests 26.1.2.9 reads JSON5. These tests read the generated files as JSON after their comment header and check
what FTB Quests needs from them (checked in its jar: BaseQuestFile#readDataFull and the objects' readData): the fields
it reads without a default, ids as 16 hex digits of a positive long, unique and stable, and every chapter reward a
per-player AdvancementReward that claims itself.
"""

from __future__ import annotations

import json
import re
import stat
from pathlib import Path
from typing import Any

import pytest

from realmforge_tools import cli, validate
from realmforge_tools.generate import generate_all, sync
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.quest import code_string, object_id
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

BOOK = "pack/config/ftbquests/quests"
LANG = f"{BOOK}/lang/en_us"
HEX_ID = re.compile(r"^[0-9A-F]{16}$")

FIRST = """\
id: first_steps
chapter: chapter_1
title: {en_us: First Steps}
subtitle: {en_us: Where it all begins}
description:
  - {en_us: Realmforge is wide.}
  - {en_us: Look around.}
position: {x: 0, y: 0}
tasks:
  - type: checkmark
    title: {en_us: I am ready}
rewards:
  - type: xp
    amount: 500
"""

SECOND = """\
id: stone_hunter
chapter: chapter_1
title: {en_us: Stone Hunter}
icon: minecraft:cobblestone
position: {x: 2, y: 0.5}
dependencies: [first_steps]
tasks:
  - type: item
    item: minecraft:cobblestone
    count: 16
  - type: kill
    entity: minecraft:zombie
    count: 10
rewards:
  - type: item
    item: minecraft:bread
    count: 4
"""

LAST = """\
id: into_the_undervault
chapter: chapter_1
title: {en_us: Into the Undervault}
position: {x: 4, y: 0}
dependencies: [stone_hunter]
completes_chapter: true
tasks:
  - type: advancement
    advancement: minecraft:story/mine_stone
  - type: stat
    stat: minecraft:mob_kills
    value: 20
  - type: dimension
    dimension: realmforge:stone
rewards:
  - type: advancement
    advancement: realmforge:realm/stone
"""


def add_quests(repo: RepoPaths, *quests: str) -> None:
    for quest in quests:
        quest_id = re.search(r"^id: (\w+)$", quest, re.MULTILINE)
        assert quest_id is not None
        write(repo.content / "quests" / f"{quest_id[1]}.yaml", quest)


def add_book(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, LAST)


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def json5(data: bytes) -> Any:
    """A generated file: comment lines, which JSON5 allows, then JSON."""
    text = data.decode("utf-8")
    lines = text.splitlines()
    assert lines[0] == "// GENERATED FILE - DO NOT EDIT."
    return json.loads("\n".join(line for line in lines if not line.startswith("//")))


def book(repo: RepoPaths) -> dict[str, Any]:
    return {path: json5(data) for path, data in generate(repo).items() if path.startswith(BOOK)}


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def quests_of(files: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """The quests of chapter 1, keyed by their English titles."""
    titles = files[f"{LANG}/chapters/chapter_1.json5"]
    return {
        titles[f"quest.{quest['id']}.title"]: quest
        for quest in files[f"{BOOK}/chapters/chapter_1.json5"]["quests"]
    }


def object_ids(chapter: dict[str, Any]) -> list[str]:
    """The ids of a chapter, its quests and their tasks and rewards (not the item stacks' ids)."""
    ids = [chapter["id"]]
    for quest in chapter["quests"]:
        ids += [
            quest["id"],
            *(task["id"] for task in quest["tasks"]),
            *(reward["id"] for reward in quest["rewards"]),
        ]
    return ids


def test_the_repository_has_no_quest_book_yet() -> None:
    content, _ = load_and_validate(RepoPaths())
    assert content.quests == ()
    paths = RepoPaths()
    assert not any(path.is_relative_to(paths.pack_quests) for path in generate_all(content, paths))


def test_the_book_is_written_as_ftb_quests_reads_it(repo: RepoPaths) -> None:
    add_book(repo)
    files = book(repo)
    assert sorted(files) == [
        f"{BOOK}/chapter_groups.json5",
        f"{BOOK}/chapters/chapter_1.json5",
        f"{BOOK}/data.json5",
        f"{LANG}/chapter.json5",
        f"{LANG}/chapters/chapter_1.json5",
        f"{LANG}/reward.json5",
    ]
    data = files[f"{BOOK}/data.json5"]
    # BaseQuestFile#readData reads these without a default; version 13 is FTB Quests 26.1.2.9's own.
    assert data["version"] == 13
    for field in (
        "default_reward_team",
        "default_consume_items",
        "default_autoclaim_rewards",
        "default_quest_shape",
        "default_quest_disable_jei",
        "drop_loot_crates",
    ):
        assert field in data
    assert (data["default_reward_team"], data["default_autoclaim_rewards"]) == (False, "disabled")
    assert "suppress_all_autoclaiming" not in data
    assert files[f"{BOOK}/chapter_groups.json5"] == {"chapter_groups": []}

    chapter = files[f"{BOOK}/chapters/chapter_1.json5"]
    assert (chapter["group"], chapter["order_index"], chapter["filename"]) == ("", 0, "chapter_1")
    assert (chapter["quest_links"], chapter["images"]) == ([], [])
    assert files[f"{LANG}/chapter.json5"] == {f"chapter.{chapter['id']}.title": "The Ominous Tale"}


def test_quests_tasks_and_rewards(repo: RepoPaths) -> None:
    add_book(repo)
    files = book(repo)
    quests = quests_of(files)
    first, second, last = quests["First Steps"], quests["Stone Hunter"], quests["Into the Undervault"]

    assert (first["x"], first["y"], second["x"], second["y"]) == (0.0, 0.0, 2.0, 0.5)
    assert "dependencies" not in first
    assert second["dependencies"] == [first["id"]]
    assert last["dependencies"] == [second["id"]]
    assert second["icon"] == {"id": "minecraft:cobblestone", "count": 1}

    def without_ids(objects: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return [{k: v for k, v in o.items() if k != "id"} for o in objects]

    assert without_ids(first["tasks"]) == [{"type": "checkmark"}]
    assert without_ids(second["tasks"]) == [
        {
            "type": "item",
            "item": {"id": "minecraft:cobblestone", "count": 1},
            "count": 16,
            "consume_items": False,
        },
        {"type": "kill", "entity": "minecraft:zombie", "value": 10},
    ]
    assert without_ids(last["tasks"]) == [
        {"type": "advancement", "advancement": "minecraft:story/mine_stone", "criterion": ""},
        {"type": "stat", "stat": "minecraft:mob_kills", "value": 20},
        {"type": "dimension", "dimension": "realmforge:stone"},
    ]
    assert without_ids(first["rewards"]) == [
        {"type": "command", "command": "/realmforge xp add {p} 500", "permission_level": 2, "silent": True}
    ]
    assert without_ids(second["rewards"]) == [
        {"type": "item", "item": {"id": "minecraft:bread", "count": 1}, "count": 4}
    ]
    assert without_ids(last["rewards"]) == [
        {
            "type": "advancement",
            "team_reward": False,
            "auto": "enabled",
            "advancement": "realmforge:realm/stone",
            "criterion": "",
        },
        {
            "type": "advancement",
            "team_reward": False,
            "auto": "enabled",
            "advancement": "realmforge:chapter/chapter_1",
            "criterion": "",
        },
    ]


def test_every_chapter_reward_is_per_player_and_claims_itself(repo: RepoPaths) -> None:
    """Architecture 16, RLM-73: a team reward would reach only the player who completes the quest."""
    add_book(repo)
    files = book(repo)
    chapter_rewards = [
        reward
        for path, chapter in files.items()
        if path.startswith(f"{BOOK}/chapters/")
        for quest in chapter["quests"]
        for reward in quest["rewards"]
        if reward.get("advancement", "").startswith("realmforge:chapter/")
    ]
    assert [reward["advancement"] for reward in chapter_rewards] == ["realmforge:chapter/chapter_1"]
    for reward in chapter_rewards:
        assert reward["type"] == "advancement"
        assert reward["team_reward"] is False
        assert reward["auto"] not in ("disabled", "default")
        assert reward["criterion"] == ""
    assert "suppress_all_autoclaiming" not in files[f"{BOOK}/data.json5"]


def test_texts_go_into_ftb_quests_lang_files(repo: RepoPaths) -> None:
    add_book(repo)
    files = book(repo)
    quests = quests_of(files)
    first = quests["First Steps"]
    texts = files[f"{LANG}/chapters/chapter_1.json5"]
    assert texts[f"quest.{first['id']}.quest_subtitle"] == "Where it all begins"
    assert texts[f"quest.{first['id']}.quest_desc"] == ["Realmforge is wide.", "", "Look around."]
    assert texts[f"task.{first['tasks'][0]['id']}.title"] == "I am ready"
    assert f"quest.{quests['Stone Hunter']['id']}.quest_desc" not in texts
    xp_title = files[f"{LANG}/reward.json5"][f"reward.{first['rewards'][0]['id']}.title"]
    assert json.loads(xp_title) == [
        {"translate": "ftbquests.reward.ftbquests.xp"},
        ": ",
        {"text": "+500", "color": "green"},
    ]


def test_ids_are_hex_longs_unique_and_derived_from_keys(repo: RepoPaths) -> None:
    add_book(repo)
    files = book(repo)
    ids = object_ids(files[f"{BOOK}/chapters/chapter_1.json5"])
    assert len(ids) == len(set(ids)) == 1 + 3 + 6 + 4  # the chapter, its quests, their tasks and rewards
    for id_ in ids:
        assert HEX_ID.match(id_)
        assert 1 < int(id_, 16) <= 2**63 - 1
    quests = quests_of(files)
    assert quests["First Steps"]["id"] == code_string(object_id("realmforge:quest/first_steps"))
    assert quests["Stone Hunter"]["tasks"][0]["id"] == code_string(
        object_id("realmforge:quest/stone_hunter/task/item:minecraft:cobblestone")
    )
    # The derivation itself must never change: players' saved progress points at these ids.
    assert quests["First Steps"]["id"] == "6D5CA6BB6C3E7C1D"
    assert files[f"{BOOK}/chapters/chapter_1.json5"]["id"] == "5CAF44F33D37A4F5"


def test_ids_survive_reordering_and_retuning(repo: RepoPaths) -> None:
    add_book(repo)
    before = book(repo)
    retuned = (
        SECOND.replace("x: 2, y: 0.5", "x: 3, y: 1")
        .replace("count: 16", "count: 32")
        .replace("Stone Hunter", "Stone Seeker")
        .replace(
            "  - type: item\n    item: minecraft:cobblestone\n    count: 32\n  - type: kill\n"
            "    entity: minecraft:zombie\n    count: 10\n",
            "  - type: kill\n    entity: minecraft:zombie\n    count: 10\n  - type: item\n"
            "    item: minecraft:cobblestone\n    count: 32\n",
        )
    )
    add_quests(repo, retuned)
    after = book(repo)
    old, new = quests_of(before)["Stone Hunter"], quests_of(after)["Stone Seeker"]
    assert new["id"] == old["id"]
    assert {task["id"] for task in new["tasks"]} == {task["id"] for task in old["tasks"]}
    assert [r["id"] for r in new["rewards"]] == [r["id"] for r in old["rewards"]]


def test_a_new_target_gets_a_new_id_unless_its_key_keeps_the_old_one(repo: RepoPaths) -> None:
    add_book(repo)
    old = quests_of(book(repo))["Stone Hunter"]["tasks"][0]["id"]
    add_quests(
        repo,
        SECOND.replace("item: minecraft:cobblestone\n    count: 16", "item: minecraft:stone\n    count: 16"),
    )
    assert quests_of(book(repo))["Stone Hunter"]["tasks"][0]["id"] != old
    add_quests(
        repo,
        SECOND.replace(
            "  - type: item\n    item: minecraft:cobblestone\n",
            "  - type: item\n    key: item:minecraft:cobblestone\n    item: minecraft:stone\n",
        ),
    )
    assert quests_of(book(repo))["Stone Hunter"]["tasks"][0]["id"] == old


def test_two_tasks_of_one_kind_need_their_own_keys(repo: RepoPaths) -> None:
    add_book(repo)
    twice = FIRST.replace("    title: {en_us: I am ready}\n", "  - type: checkmark\n")
    add_quests(repo, twice)
    assert any("two tasks share the key checkmark" in p for p in problems_for(repo))
    add_quests(
        repo,
        twice.replace(
            "  - type: checkmark\n  - type: checkmark\n",
            "  - type: checkmark\n  - type: checkmark\n    key: second\n",
        ),
    )
    files = book(repo)
    assert len({task["id"] for task in quests_of(files)["First Steps"]["tasks"]}) == 2


def test_nothing_is_written_without_quests_and_stale_files_go(repo: RepoPaths) -> None:
    add_book(repo)
    content, _ = load_and_validate(repo)
    sync(generate_all(content, repo), repo)
    assert (repo.pack_quests / "data.json5").exists()
    for path in (repo.content / "quests").iterdir():
        path.unlink()
    content, _ = load_and_validate(repo)
    result = sync(generate_all(content, repo), repo)
    assert f"{BOOK}/data.json5" in result.removed
    assert not repo.pack_quests.exists() or not any(repo.pack_quests.rglob("*.json5"))


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (lambda q: q.replace("chapter: chapter_1", "chapter: chapter_9"), "unknown chapter 'chapter_9'"),
        (lambda q: q.replace("[stone_hunter]", "[stone_huntr]"), "unknown quest 'stone_huntr'"),
        (
            lambda q: q.replace("minecraft:story/mine_stone", "minecraft:story/mine_stones"),
            "minecraft:advancement",
        ),
        (lambda q: q.replace("minecraft:mob_kills", "minecraft:mob_kill"), "minecraft:custom_stat"),
        (
            lambda q: q.replace("dimension: realmforge:stone", "dimension: realmforge:stones"),
            "minecraft:dimension",
        ),
        (lambda q: q.replace("realmforge:realm/stone", "minecraft:story/mine_stone"), "is not ours"),
        (
            lambda q: q.replace("realmforge:realm/stone", "realmforge:realm/sky"),
            "unknown id 'realmforge:realm/sky'",
        ),
        (
            lambda q: q.replace("realmforge:realm/stone", "realmforge:chapter/chapter_2"),
            "a chapter's advancement comes from completes_chapter",
        ),
        (lambda q: q.replace("x: 4, y: 0", "x: 2, y: 0.5"), "quests of a chapter stand apart"),
        (lambda q: q.replace("completes_chapter: true", "completes_chapter: false"), "found: none"),
        (
            lambda q: q.replace("completes_chapter: true", "completes_chapter: true\noptional: true"),
            "cannot be optional",
        ),
        (lambda q: q.replace("[stone_hunter]", "[into_the_undervault]"), "cannot depend on itself"),
    ],
)
def test_validation_refuses_a_broken_quest(repo: RepoPaths, change: Any, expected: str) -> None:
    add_quests(repo, FIRST, SECOND, change(LAST))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


def test_validation_refuses_unknown_items_and_entities(repo: RepoPaths) -> None:
    add_quests(
        repo,
        FIRST,
        SECOND.replace("minecraft:bread", "minecraft:breadd")
        .replace("minecraft:zombie", "minecraft:zombi")
        .replace("icon: minecraft:cobblestone", "icon: minecraft:cobblestones"),
        LAST,
    )
    problems = problems_for(repo)
    for location in ("rewards.0.item", "tasks.1.entity", "icon"):
        assert any(f"stone_hunter.yaml: {location}: unknown id" in p for p in problems), (location, problems)


def test_a_dimension_task_has_no_consume(repo: RepoPaths) -> None:
    add_quests(
        repo,
        FIRST,
        SECOND,
        LAST.replace("dimension: realmforge:stone\n", "dimension: realmforge:stone\n    consume: false\n"),
    )
    assert any("consume" in p and "into_the_undervault" in p for p in problems_for(repo))


def test_dependency_cycles_are_refused(repo: RepoPaths) -> None:
    add_quests(repo, FIRST.replace("tasks:", "dependencies: [into_the_undervault]\ntasks:"), SECOND, LAST)
    assert any(
        "cycle" in p and "first_steps -> into_the_undervault -> stone_hunter -> first_steps" in p
        for p in problems_for(repo)
    )


def test_two_completing_quests_in_one_chapter_are_refused(repo: RepoPaths) -> None:
    add_quests(repo, FIRST.replace("tasks:", "completes_chapter: true\ntasks:"), SECOND, LAST)
    assert any("found: first_steps, into_the_undervault" in p for p in problems_for(repo))


def test_rf_gen_refreshes_the_pack_when_it_changed_it(
    repo: RepoPaths, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(cli.shutil, "which", lambda name: None)
    assert cli._refresh_pack(repo) == 1
    (repo.root / "pack").mkdir()
    bin_dir = tmp_path / "bin"
    packwiz = write(bin_dir / "packwiz", '#!/bin/sh\necho "$1" > refreshed\n')
    packwiz.chmod(packwiz.stat().st_mode | stat.S_IEXEC)
    monkeypatch.setattr(cli.shutil, "which", lambda name: str(packwiz) if name == "packwiz" else None)
    assert cli._refresh_pack(repo) == 0
    assert (repo.root / "pack" / "refreshed").read_text().strip() == "refresh"


def test_a_shared_id_is_refused(repo: RepoPaths, monkeypatch: pytest.MonkeyPatch) -> None:
    """Two keys with one id would make FTB Quests replace one id with a random one, and its progress would be lost."""
    add_book(repo)
    monkeypatch.setattr(validate, "object_id", lambda key: 42)
    assert any("share the quest book id 000000000000002A" in p for p in problems_for(repo))


def test_each_chapter_gets_its_file_in_story_order(repo: RepoPaths) -> None:
    add_book(repo)
    add_quests(
        repo,
        FIRST.replace("id: first_steps", "id: weight_of_stone")
        .replace("chapter: chapter_1", "chapter: chapter_2")
        .replace("tasks:", "completes_chapter: true\ntasks:"),
    )
    files = book(repo)
    second = files[f"{BOOK}/chapters/chapter_2.json5"]
    assert (second["order_index"], second["filename"]) == (1, "chapter_2")
    assert files[f"{BOOK}/chapters/chapter_1.json5"]["order_index"] == 0
    assert [reward.get("advancement") for reward in second["quests"][0]["rewards"]] == [
        None,
        "realmforge:chapter/chapter_2",
    ]
    assert sorted(files[f"{LANG}/chapter.json5"].values()) == ["Chapter 2", "The Ominous Tale"]
    assert f"{LANG}/chapters/chapter_2.json5" in files


def test_a_long_chain_of_quests_is_checked_without_recursion(repo: RepoPaths) -> None:
    """A campaign's dependency chain may be longer than Python's recursion limit (1,000 frames by default)."""
    add_book(repo)
    previous = "into_the_undervault"
    for n in range(1, 1201):
        add_quests(
            repo,
            FIRST.replace("id: first_steps", f"id: chain_{n}")
            .replace("x: 0, y: 0", f"x: {n % 500}, y: {9 + n // 500}")
            .replace("tasks:", f"dependencies: [{previous}]\ntasks:"),
        )
        previous = f"chain_{n}"
    content, _ = load_and_validate(repo)
    assert len(content.quests) == 1203
