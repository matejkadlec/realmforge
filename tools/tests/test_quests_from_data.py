"""content/quests/: our own quest tasks (Q1-2) and the quests' where, how and with what (Q1-3), architecture 16.

Our tasks (a level, a dungeon, a spell) become advancements without a display in the core mod's data, completed by our
triggers, which FTB Quests' advancement task reads (ADR 0006). Every quest names its realm, an errand its recommended
level within the realm's band, and the generator adds a "where, with what" block to its description; a realm's final
boss quest waits for every mandatory quest of its realm in its chapter (RLM-367).
"""

from __future__ import annotations

import json
from typing import Any

import pytest

from realmforge_tools.generate.quests import task_title
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.quest import DungeonTaskDef, LevelTaskDef, SpellTaskDef, code_string, object_id
from tests.test_quests import (
    BOOK,
    FIRST,
    LANG,
    LAST,
    SECOND,
    add_quests,
    book,
    generate,
    problems_for,
    quests_of,
)

ADVANCEMENTS = "mod/src/generated/resources/data/realmforge/advancement"
QA = "qa-mod/src/generated"
EXPECTATIONS = f"{QA}/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedQuestBookExpectations.java"

# A quest of our own tasks: a level, any dungeon, and a spell a player may cast.
TRAINING = """\
id: training
chapter: chapter_1
realm: overworld
title: {en_us: Training}
position: {x: 0, y: 2}
dependencies: [first_steps]
tasks:
  - type: level
    level: 5
  - type: dungeon
  - type: spell
    spell: realmforge:test_bolt
  - type: stat
    stat: realmforge:dungeons_completed
    value: 3
level: 5
"""

# Undervault's quests in chapter 2: two mandatory ones, an optional one, the final boss quest and one after it.
UNDERVAULT = {
    "enter_undervault": """\
id: enter_undervault
chapter: chapter_2
realm: stone
title: {en_us: Enter the Undervault}
position: {x: 0, y: 0}
tasks:
  - type: dimension
    dimension: realmforge:stone
level: 6
""",
    "stone_collector": """\
id: stone_collector
chapter: chapter_2
realm: stone
title: {en_us: Stone Collector}
position: {x: 2, y: 0}
dependencies: [enter_undervault]
tasks:
  - type: item
    item: realmforge:vaultstone
    count: 16
level: 8
""",
    "side_cave": """\
id: side_cave
chapter: chapter_2
realm: stone
title: {en_us: A Side Cave}
position: {x: 2, y: 2}
optional: true
dependencies: [enter_undervault]
tasks:
  - type: checkmark
""",
    "the_unbroken": """\
id: the_unbroken
chapter: chapter_2
realm: stone
title: {en_us: The Unbroken}
position: {x: 4, y: 0}
dependencies: [enter_undervault]
requires_mandatory: true
tasks:
  - type: kill
    entity: minecraft:zombie
level: 18
""",
    "home_again": """\
id: home_again
chapter: chapter_2
realm: stone
title: {en_us: Home Again}
position: {x: 6, y: 0}
dependencies: [the_unbroken]
completes_chapter: true
tasks:
  - type: checkmark
""",
}

# A quest that says where and with what in full: a structure in a biome, the items to bring and to craft.
WELL_EQUIPPED = """\
id: well_equipped
chapter: chapter_1
realm: overworld
title: {en_us: Well Equipped}
position: {x: 0, y: -2}
place:
  structure: minecraft:trial_chambers
  name: {en_us: the Trial Chambers}
  biome: minecraft:dripstone_caves
level: 9
bring: [minecraft:iron_pickaxe, minecraft:torch, minecraft:wheat]
craft: [realmforge:arcane_silk, minecraft:iron_ingot]
tasks:
  - type: item
    item: minecraft:copper_ingot
"""


def chapter_quests(files: dict[str, Any], chapter: str) -> dict[str, dict[str, Any]]:
    """The quests of a chapter, keyed by their English titles."""
    titles = files[f"{LANG}/chapters/{chapter}.json5"]
    return {
        titles[f"quest.{quest['id']}.title"]: quest
        for quest in files[f"{BOOK}/chapters/{chapter}.json5"]["quests"]
    }


def code(key: str) -> str:
    return code_string(object_id(key))


# ---- Our own tasks (Q1-2) ----


def test_our_tasks_become_advancements_that_ftb_quests_reads(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, LAST, TRAINING)
    files = generate(repo)
    quest = quests_of(
        {path: json.loads(_body(data)) for path, data in files.items() if path.startswith(BOOK)}
    )
    tasks = quest["Training"]["tasks"]
    assert [{k: v for k, v in task.items() if k != "id"} for task in tasks] == [
        {"type": "advancement", "advancement": "realmforge:quest/training/level/5", "criterion": ""},
        {"type": "advancement", "advancement": "realmforge:quest/training/dungeon", "criterion": ""},
        {
            "type": "advancement",
            "advancement": "realmforge:quest/training/spell/realmforge/test_bolt",
            "criterion": "",
        },
        {"type": "stat", "stat": "realmforge:dungeons_completed", "value": 3},
    ]
    # A task keeps the id of its key, as every other task does.
    assert tasks[0]["id"] == code("realmforge:quest/training/task/level:5")

    def advancement(path: str) -> dict[str, Any]:
        return json.loads(files[f"{ADVANCEMENTS}/quest/training/{path}.json"])

    # No display: no toast and no entry in the advancement screen; one criterion of our triggers.
    assert advancement("level/5") == {
        "criteria": {"done": {"trigger": "realmforge:level_reached", "conditions": {"level": {"min": 5}}}},
        "requirements": [["done"]],
    }
    assert advancement("dungeon") == {
        "criteria": {"done": {"trigger": "realmforge:dungeon_completed"}},
        "requirements": [["done"]],
    }
    assert advancement("spell/realmforge/test_bolt")["criteria"] == {
        "done": {"trigger": "realmforge:spell_cast", "conditions": {"spell": "realmforge:test_bolt"}}
    }
    assert sorted(path for path in files if path.startswith(f"{ADVANCEMENTS}/quest/")) == [
        f"{ADVANCEMENTS}/quest/training/{path}.json"
        for path in ("dungeon", "level/5", "spell/realmforge/test_bolt")
    ]


def test_our_tasks_are_titled_from_data(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, LAST, TRAINING)
    files = book(repo)
    tasks = quests_of(files)["Training"]["tasks"]
    texts = files[f"{LANG}/chapters/chapter_1.json5"]
    titles = [json.loads(texts[f"task.{task['id']}.title"]) for task in tasks[:3]]
    assert titles == [
        {"translate": "quest.realmforge.task_level", "with": [5]},
        {"translate": "quest.realmforge.task_any_dungeon"},
        {
            "translate": "quest.realmforge.task_spell",
            "with": [{"translate": "spell.realmforge.test_bolt.name"}],
        },
    ]
    # FTB Quests titles the stat task itself.
    assert f"task.{tasks[3]['id']}.title" not in texts
    assert task_title(DungeonTaskDef(type="dungeon", dungeon="realmforge:test_dungeon")) == {
        "translate": "quest.realmforge.task_dungeon",
        "with": [{"translate": "dungeon_type.realmforge.test_dungeon.name"}],
    }
    assert task_title(SpellTaskDef(type="spell")) == {"translate": "quest.realmforge.task_any_spell"}
    assert task_title(LevelTaskDef(type="level", level=12))["with"] == [12]


def test_every_text_our_titles_use_exists(repo: RepoPaths) -> None:
    files = generate(repo)
    lang = json.loads(files["mod/src/generated/resources/assets/realmforge/lang/en_us.json"])
    for name in (
        "task_level",
        "task_dungeon",
        "task_any_dungeon",
        "task_spell",
        "task_any_spell",
        "where",
        "level",
    ):
        assert f"quest.realmforge.{name}" in lang
    assert lang["stat.realmforge.dungeons_completed"] == "Dungeons Completed"


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (
            lambda q: q.replace(
                "  - type: dungeon\n", "  - type: dungeon\n    dungeon: realmforge:nowhere\n"
            ),
            "tasks.1.dungeon: unknown id 'realmforge:nowhere'",
        ),
        (lambda q: q.replace("realmforge:test_bolt", "realmforge:test_boltt"), "tasks.2.spell: unknown id"),
        (lambda q: q.replace("realmforge:test_bolt", "realmforge:test_npc_bolt"), "is npc_only"),
        (
            lambda q: q.replace("realmforge:dungeons_completed", "realmforge:dungeons_won"),
            "minecraft:custom_stat",
        ),
        (
            lambda q: q.replace("level: 5\n  - type: dungeon", "level: 1001\n  - type: dungeon"),
            "less than or equal",
        ),
        (
            lambda q: q.replace(
                "  - type: dungeon\n", "  - type: dungeon\n    key: spell/realmforge/test_bolt\n"
            ),
            "share the advancement realmforge:quest/training/spell/realmforge/test_bolt",
        ),
        (
            lambda q: q.replace("  - type: dungeon\n", "  - type: dungeon\n    key: dungeon/../x\n"),
            "'..' part",
        ),
    ],
)
def test_validation_refuses_a_broken_task_of_ours(repo: RepoPaths, change: Any, expected: str) -> None:
    add_quests(repo, FIRST, SECOND, LAST, change(TRAINING))
    problems = problems_for(repo)
    assert any(expected in p for p in problems), problems


def test_the_qa_mod_gets_sample_task_advancements_and_the_expected_book(repo: RepoPaths) -> None:
    files = generate(repo)
    samples = {path: json.loads(data) for path, data in files.items() if "/advancement/quest_task/" in path}
    root = f"{QA}/resources/data/realmforge_qa/advancement/quest_task"
    # The test repository has no dungeon type (tests/conftest.py), so the typed dungeon sample is left out.
    assert sorted(samples) == [
        f"{root}/{name}.json" for name in ("any_dungeon", "any_spell", "level", "spell")
    ]
    assert samples[f"{root}/spell.json"]["criteria"]["done"]["conditions"] == {
        "spell": "realmforge:battle_shout"
    }
    expectations = files[EXPECTATIONS].decode()
    assert "public static final List<ChapterExpectation> CHAPTERS = List.of();" in expectations
    assert 'new TaskSample("realmforge_qa:quest_task/level", "level", "5")' in expectations
    assert 'List.of("realmforge:dungeons_completed")' in expectations

    add_quests(repo, FIRST, SECOND, LAST)
    expectations = generate(repo)[EXPECTATIONS].decode()
    chapter = code("realmforge:chapter/chapter_1")
    assert (
        f'new ChapterExpectation(\n                    "realmforge:chapter_1", "{chapter}",' in expectations
    )
    first = code("realmforge:quest/first_steps")
    stone_hunter = code("realmforge:quest/stone_hunter")
    assert (
        f'"realmforge:stone_hunter", "{stone_hunter}", false,\n                            List.of("{first}")'
        in (expectations)
    )
    xp = code("realmforge:quest/first_steps/reward/xp")
    assert f'new RewardExpectation("{xp}", "command", false, "disabled")' in expectations
    chapter_reward = code(
        "realmforge:quest/into_the_undervault/reward/advancement:realmforge:chapter/chapter_1"
    )
    assert f'new RewardExpectation("{chapter_reward}", "advancement", false, "enabled")' in expectations
    dimension = code("realmforge:quest/into_the_undervault/task/dimension:realmforge:stone")
    assert f'new TaskExpectation("{dimension}", "dimension")' in expectations


# ---- Where, how and with what (Q1-3) ----


def test_a_quest_says_where_and_with_what(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, LAST, WELL_EQUIPPED)
    files = book(repo)
    quest = quests_of(files)["Well Equipped"]
    lines = files[f"{LANG}/chapters/chapter_1.json5"][f"quest.{quest['id']}.quest_desc"]
    assert [json.loads(line) for line in lines] == [
        {
            "translate": "quest.realmforge.where_place",
            "with": [
                {"translate": "realm.realmforge.overworld.name"},
                {
                    "translate": "quest.realmforge.structure_in_biome",
                    "with": [
                        {"text": "the Trial Chambers"},
                        {"translate": "biome.minecraft.dripstone_caves"},
                    ],
                },
            ],
        },
        {"translate": "quest.realmforge.level", "with": [9]},
        {
            "translate": "quest.realmforge.bring",
            "with": [
                [
                    "",
                    {"translate": "item.minecraft.iron_pickaxe"},
                    ", ",
                    # A block's item is named after the block, but wheat is an item of its own.
                    {"translate": "block.minecraft.torch"},
                    ", ",
                    {"translate": "item.minecraft.wheat"},
                ]
            ],
        },
        {
            "translate": "quest.realmforge.craft",
            "with": [
                [
                    "",
                    {"translate": "item.realmforge.arcane_silk"},
                    ", ",
                    {"translate": "item.minecraft.iron_ingot"},
                ]
            ],
        },
    ]
    # FTB Quests reads a line that is a JSON object as a text component: one line, no line breaks.
    assert all(line.startswith("{") and line.endswith("}") and "\n" not in line for line in lines)


def test_a_place_may_be_a_biome_alone(repo: RepoPaths) -> None:
    add_quests(
        repo,
        FIRST,
        SECOND,
        LAST,
        WELL_EQUIPPED.replace(
            "  structure: minecraft:trial_chambers\n  name: {en_us: the Trial Chambers}\n", ""
        ),
    )
    files = book(repo)
    quest = quests_of(files)["Well Equipped"]
    first = json.loads(files[f"{LANG}/chapters/chapter_1.json5"][f"quest.{quest['id']}.quest_desc"][0])
    assert first["with"][1] == {"translate": "biome.minecraft.dripstone_caves"}


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (lambda q: q.replace("level: 9\n", ""), "its item task sends the player somewhere"),
        (lambda q: q.replace("realm: overworld", "realm: skyhearth"), "unknown realm 'skyhearth'"),
        (lambda q: q.replace("level: 9", "level: 19"), "above chapter_1's cap once completed, 18"),
        (
            lambda q: q.replace("  biome: minecraft:dripstone_caves\n", "").replace(
                "  structure: minecraft:trial_chambers\n  name: {en_us: the Trial Chambers}\n", "  {}\n"
            ),
            "a place names a structure, a biome or both",
        ),
        (lambda q: q.replace("  name: {en_us: the Trial Chambers}\n", ""), "a structure needs its name"),
        (
            lambda q: q.replace("minecraft:trial_chambers", "minecraft:trial_chamber"),
            "place.structure: unknown id 'minecraft:trial_chamber'",
        ),
        (lambda q: q.replace("minecraft:dripstone_caves", "minecraft:drip_caves"), "place.biome: unknown id"),
        (lambda q: q.replace("minecraft:torch,", "minecraft:torches,"), "bring.1: unknown id"),
        (
            lambda q: q.replace("minecraft:iron_ingot]", "minecraft:ender_pearl]"),
            "craft.1: no recipe makes minecraft:ender_pearl",
        ),
        (
            lambda q: q.replace("realmforge:arcane_silk", "realmforge:undervault_signet"),
            "craft.0: no recipe makes",
        ),
        (lambda q: q.replace("Well Equipped", "Well Equipped at Level 9"), "title types a level by hand"),
        (
            lambda q: q.replace(
                "title: {en_us: Well Equipped}",
                "title: {en_us: Well Equipped}\ndescription:\n  - {en_us: Come back at lvl.12.}",
            ),
            "description.0 types a level by hand",
        ),
        (
            lambda q: q.replace(
                "    item: minecraft:copper_ingot\n",
                "    item: minecraft:copper_ingot\n    title: {en_us: Lv 3 copper}\n",
            ),
            "tasks.0.title types a level by hand",
        ),
        (
            lambda q: q.replace("bring: [minecraft:iron_pickaxe,", "bring: [minecraft:torch,"),
            "duplicate entries",
        ),
    ],
)
def test_validation_refuses_a_quest_without_where_or_with_what(
    repo: RepoPaths, change: Any, expected: str
) -> None:
    add_quests(repo, FIRST, SECOND, LAST, change(WELL_EQUIPPED))
    problems = problems_for(repo)
    assert any(expected in p for p in problems), problems


def test_a_checkmark_level_or_spell_quest_needs_no_level(repo: RepoPaths) -> None:
    """Only an errand sends the player somewhere: a checkmark, a level and a spell are done anywhere."""
    anywhere = (
        "id: anywhere\nchapter: chapter_1\nrealm: overworld\ntitle: {en_us: Anywhere}\nposition: {x: 0, y: 4}\n"
        "tasks:\n  - type: checkmark\n  - type: level\n    level: 3\n  - type: spell\n"
    )
    add_quests(repo, FIRST, SECOND, LAST, anywhere)
    assert "Anywhere" in quests_of(book(repo))
    add_quests(repo, anywhere.replace("  - type: spell\n", "  - type: dungeon\n"))
    assert any("its dungeon task sends the player somewhere" in p for p in problems_for(repo))


def test_a_quest_level_lies_in_its_realms_band(repo: RepoPaths) -> None:
    quests = dict(UNDERVAULT)
    add_quests(repo, FIRST, SECOND, LAST, *quests.values())
    book(repo)
    add_quests(repo, quests["stone_collector"].replace("level: 8", "level: 5"))
    assert any("level 5 lies outside stone's level band 6-18" in p for p in problems_for(repo))
    add_quests(repo, quests["stone_collector"].replace("level: 8", "level: 19"))
    assert any("level 19 lies outside stone's level band 6-18" in p for p in problems_for(repo))


def test_a_typed_level_word_without_a_number_is_fine(repo: RepoPaths) -> None:
    add_quests(
        repo,
        FIRST.replace("Realmforge is wide.", "Every level-up makes you stronger; the next level waits."),
        SECOND,
        LAST,
    )
    assert book(repo)


# ---- Mandatory quests (RLM-367) ----


def test_a_final_boss_quest_waits_for_its_realms_mandatory_quests(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, LAST, *UNDERVAULT.values())
    files = book(repo)
    quests = chapter_quests(files, "chapter_2")
    boss = quests["The Unbroken"]
    # Its own dependency first, then the mandatory quests of Undervault in chapter 2 it does not name: not the optional
    # side cave, not the quest after it, and none of chapter 1's.
    assert boss["dependencies"] == [
        code("realmforge:quest/enter_undervault"),
        code("realmforge:quest/stone_collector"),
    ]
    assert quests["Home Again"]["dependencies"] == [code("realmforge:quest/the_unbroken")]
    assert quests["Stone Collector"]["dependencies"] == [code("realmforge:quest/enter_undervault")]


def test_mandatory_quests_are_of_its_realm_and_chapter_only(repo: RepoPaths) -> None:
    quests = dict(UNDERVAULT)
    # An Overworld quest in chapter 2, and a second Undervault quest in chapter 1.
    elsewhere = (
        quests["stone_collector"]
        .replace("id: stone_collector", "id: home_errand")
        .replace("realm: stone", "realm: overworld")
        .replace("x: 2, y: 0", "x: 2, y: -2")
        .replace("Stone Collector", "Home Errand")
    )
    early = (
        quests["side_cave"]
        .replace("id: side_cave", "id: early_cave")
        .replace("chapter_2", "chapter_1")
        .replace("optional: true\ndependencies: [enter_undervault]\n", "")
        .replace("x: 2, y: 2", "x: 6, y: 6")
        .replace("A Side Cave", "An Early Cave")
    )
    add_quests(repo, FIRST, SECOND, LAST, *quests.values(), elsewhere, early)
    files = book(repo)
    boss = chapter_quests(files, "chapter_2")["The Unbroken"]
    assert boss["dependencies"] == [
        code("realmforge:quest/enter_undervault"),
        code("realmforge:quest/stone_collector"),
    ]


def test_a_final_boss_quest_cannot_be_optional(repo: RepoPaths) -> None:
    quests = dict(UNDERVAULT)
    quests["the_unbroken"] = quests["the_unbroken"].replace(
        "requires_mandatory: true", "requires_mandatory: true\noptional: true"
    )
    add_quests(repo, FIRST, SECOND, LAST, *quests.values())
    assert any("cannot be optional" in p for p in problems_for(repo))


def test_other_final_boss_quests_and_the_quests_after_one_are_left_out(repo: RepoPaths) -> None:
    """A second final boss quest after the first: neither waits for the other through the mandatory rule, and the first
    does not wait for the quests after it, so the rule never closes a cycle."""
    quests = dict(UNDERVAULT)
    quests["home_again"] = quests["home_again"].replace("completes_chapter: true\n", "")
    quests["second_boss"] = (
        quests["the_unbroken"]
        .replace("id: the_unbroken", "id: second_boss")
        .replace("The Unbroken", "The Second")
        .replace("x: 4, y: 0", "x: 8, y: 0")
        .replace("dependencies: [enter_undervault]", "dependencies: [home_again]")
        .replace("requires_mandatory: true", "requires_mandatory: true\ncompletes_chapter: true")
    )
    add_quests(repo, FIRST, SECOND, LAST, *quests.values())
    quests_2 = chapter_quests(book(repo), "chapter_2")
    assert quests_2["The Unbroken"]["dependencies"] == [
        code("realmforge:quest/enter_undervault"),
        code("realmforge:quest/stone_collector"),
    ]
    assert quests_2["The Second"]["dependencies"] == [
        code("realmforge:quest/home_again"),
        code("realmforge:quest/enter_undervault"),
        code("realmforge:quest/stone_collector"),
    ]


def test_an_explicit_cycle_through_a_final_boss_quest_is_still_refused(repo: RepoPaths) -> None:
    quests = dict(UNDERVAULT)
    quests["enter_undervault"] = quests["enter_undervault"].replace(
        "realm: stone\n", "realm: stone\ndependencies: [the_unbroken]\n"
    )
    add_quests(repo, FIRST, SECOND, LAST, *quests.values())
    assert any("the dependencies form a cycle" in p for p in problems_for(repo))


def _body(data: bytes) -> str:
    return "\n".join(line for line in data.decode().splitlines() if not line.startswith("//"))
