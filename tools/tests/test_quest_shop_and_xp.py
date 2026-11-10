"""Buy quests (Q1-4, RLM-424), quest XP by level and type (RLM-189) and the quest pipeline's follow-ups (RLM-472).

A buy quest takes coins and gives an item, up to its limit: FTB Quests 26.1.2.9 has no repeat limit (``Quest#readData``
reads only a ``repeat_cooldown``), so the pipeline writes one quest per purchase, each opening with the one before it.
A quest's character XP is never typed: the pipeline computes it from the quest's level and type, and the game lowers it
for a player far above the quest when it pays (``/realmforge xp quest``).
"""

from __future__ import annotations

import json
from typing import Any

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.quests import castable_sample, sample_tasks
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.leveling import KILL_XP, QUEST_XP_BELOW_PLAYER, quest_xp_paid
from realmforge_tools.schema.quest import (
    QUEST_TYPE_FACTORS,
    SpellTaskDef,
    code_string,
    object_id,
    purchase_ids,
    quest_xp,
)
from realmforge_tools.validate import load_and_validate
from tests.conftest import write
from tests.test_quests import BOOK, FIRST, LANG, LAST, SECOND, add_book, add_quests, book, problems_for
from tests.test_quests_from_data import WELL_EQUIPPED

SIGNET = "realmforge:undervault_signet"

# The quest shop's lost signet (realm portal design, "Realm signets"): 50 copper, up to 3 times, after the quest that
# opens the realm.
SHOP = """\
id: lost_signet
chapter: chapter_1
realm: overworld
title: {en_us: A Lost Signet}
description:
  - {en_us: The merchant keeps a spare.}
position: {x: 6, y: 0}
dependencies: [into_the_undervault]
optional: true
buy:
  item: realmforge:undervault_signet
  price: 50
  limit: 3
"""

# The quest that opens Undervault also hands out its signet, once.
GIVES_SIGNET = LAST + "  - type: item\n    item: realmforge:undervault_signet\n"


def code(key: str) -> str:
    return code_string(object_id(key))


def chapter_1(files: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Chapter 1's quests in the book, by their quest book ids."""
    return {quest["id"]: quest for quest in files[f"{BOOK}/chapters/chapter_1.json5"]["quests"]}


def curve(repo: RepoPaths, curve_id: str) -> Any:
    content, _ = load_and_validate(repo)
    return next(entry.model for entry in content.curves if entry.model.id == curve_id)


# ---- Buy quests (Q1-4, RLM-424) ----


def test_a_buy_quest_becomes_a_quest_per_purchase(repo: RepoPaths) -> None:
    add_book(repo)
    add_quests(repo, SHOP)
    files = book(repo)
    quests = chapter_1(files)
    opens_the_realm = code("realmforge:quest/into_the_undervault")
    first, second, third = (
        code(f"realmforge:quest/{id_}") for id_ in ("lost_signet", "lost_signet_2", "lost_signet_3")
    )
    assert len(quests) == 3 + 3
    for n, purchase in enumerate((first, second, third)):
        quest = quests[purchase]
        # The coin task consumes: the quest takes the price.
        assert [{k: v for k, v in task.items() if k != "id"} for task in quest["tasks"]] == [
            {
                "type": "item",
                "item": {"id": "realmforge:copper_coin", "count": 1},
                "count": 50,
                "consume_items": True,
            }
        ]
        # The item is its only reward: a purchase gives no character XP.
        assert [{k: v for k, v in reward.items() if k != "id"} for reward in quest["rewards"]] == [
            {"type": "item", "item": {"id": SIGNET, "count": 1}, "count": 1}
        ]
        assert quest["optional"] is True
        assert (quest["x"], quest["y"]) == (6.0 + n, 0.0)
    # The lock: the shop opens with the quest it names. The repeat limit: each later purchase opens with the one
    # before it and shows only then, and nothing follows the third.
    assert quests[first]["dependencies"] == [opens_the_realm]
    assert "hide_until_deps_complete" not in quests[first]
    assert quests[second]["dependencies"] == [first]
    assert quests[third]["dependencies"] == [second]
    assert quests[second]["hide_until_deps_complete"] is quests[third]["hide_until_deps_complete"] is True
    assert not any(third in quest.get("dependencies", []) for quest in quests.values())
    # Every purchase has ids of its own, so FTB Quests keeps each one's progress.
    ids = [quest["tasks"][0]["id"] for quest in (quests[first], quests[second], quests[third])]
    ids += [quest["rewards"][0]["id"] for quest in (quests[first], quests[second], quests[third])]
    assert len(set(ids)) == 6

    texts = files[f"{LANG}/chapters/chapter_1.json5"]
    for n, purchase in enumerate((first, second, third), start=1):
        assert texts[f"quest.{purchase}.title"] == "A Lost Signet"
        description = texts[f"quest.{purchase}.quest_desc"]
        assert description[:2] == ["The merchant keeps a spare.", ""]
        assert json.loads(description[2]) == {"translate": "quest.realmforge.purchase", "with": [n, 3]}
        assert len(description) == 3  # a shop is nowhere: no "where, with what" block


def test_a_price_is_paid_in_the_largest_coins(repo: RepoPaths) -> None:
    add_book(repo)
    add_quests(repo, SHOP.replace("price: 50", "price: 20250").replace("  limit: 3\n", "  count: 2\n"))
    files = book(repo)
    quests = chapter_1(files)
    assert len(quests) == 3 + 1  # bought once by default
    quest = quests[code("realmforge:quest/lost_signet")]
    assert [(task["item"]["id"], task["count"], task["consume_items"]) for task in quest["tasks"]] == [
        ("realmforge:gold_coin", 2, True),
        ("realmforge:silver_coin", 2, True),
        ("realmforge:copper_coin", 50, True),
    ]
    assert quest["rewards"][0]["count"] == 2
    # Bought once, it says nothing about purchases.
    assert files[f"{LANG}/chapters/chapter_1.json5"][f"quest.{quest['id']}.quest_desc"] == [
        "The merchant keeps a spare."
    ]


def test_the_purchases_of_a_buy_quest_are_its_book_ids(repo: RepoPaths) -> None:
    add_book(repo)
    add_quests(repo, SHOP)
    content, _ = load_and_validate(repo)
    shop = next(entry.model for entry in content.quests if entry.model.id == "lost_signet")
    assert purchase_ids(shop) == ["lost_signet", "lost_signet_2", "lost_signet_3"]
    assert [quest.purchase for quest in content.book_quests() if quest.buy is not None] == [1, 2, 3]
    assert all(purchase_ids(entry.model) == [] for entry in content.quests if entry.model.buy is None)


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (lambda q: q.replace("optional: true\n", ""), "a quest that buys something is optional"),
        (
            lambda q: q + "tasks:\n  - type: checkmark\n",
            "has no tasks, rewards or xp of its own",
        ),
        (lambda q: q + "xp: 100\n", "has no tasks, rewards or xp of its own"),
        (lambda q: q.replace("undervault_signet", "undervault_signett"), "buy.item: unknown id"),
        (lambda q: q.replace("limit: 3", "limit: 10"), "less than or equal to 9"),
        (lambda q: q.replace("price: 50", "price: 0"), "greater than or equal to 1"),
        # Its second purchase would stand on the quest at x 4.
        (lambda q: q.replace("x: 6, y: 0", "x: 3, y: 0"), "quests of a chapter stand apart"),
    ],
)
def test_validation_refuses_a_broken_buy_quest(repo: RepoPaths, change: Any, expected: str) -> None:
    add_book(repo)
    add_quests(repo, change(SHOP))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


def test_a_purchase_cannot_take_another_quests_id(repo: RepoPaths) -> None:
    add_book(repo)
    add_quests(
        repo,
        SHOP,
        FIRST.replace("id: first_steps", "id: lost_signet_2").replace("x: 0, y: 0", "x: 0, y: 5"),
    )
    problems = problems_for(repo)
    assert any(
        "lost_signet.yaml: buy.limit" in p and "lost_signet_2 is another quest's id" in p for p in problems
    ), problems


def test_a_price_needs_coins(repo: RepoPaths) -> None:
    add_book(repo)
    add_quests(repo, SHOP)
    for coin in ("copper", "silver", "gold", "platinum"):
        (repo.content / "items" / f"{coin}_coin.yaml").unlink()
    assert any("our coins cannot pay 50 exactly" in p for p in problems_for(repo))


def test_a_quest_needs_a_task_unless_it_buys(repo: RepoPaths) -> None:
    add_quests(
        repo, FIRST.replace("tasks:\n  - type: checkmark\n    title: {en_us: I am ready}\n", ""), SECOND, LAST
    )
    assert any("a quest needs at least one task" in p for p in problems_for(repo))


# ---- The signet source check (RLM-424; realm portal design, "Data") ----


def test_a_signet_given_once_needs_a_renewable_source(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, GIVES_SIGNET)
    problems = problems_for(repo)
    assert any(
        "into_the_undervault.yaml: rewards.1.item" in p
        and f"{SIGNET}, the signet of the realm 'stone', comes only from a quest's reward" in p
        for p in problems
    ), problems


def test_the_shop_is_a_signets_renewable_source(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, GIVES_SIGNET, SHOP)
    load_and_validate(repo)


def test_a_recipe_is_a_signets_renewable_source(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, GIVES_SIGNET)
    signet = repo.content / "items" / "undervault_signet.yaml"
    recipe = "recipe:\n  pattern: ['SSS', 'SDS', 'SSS']\n  key:\n    S: minecraft:stone\n    D: minecraft:diamond\n"
    signet.write_text(signet.read_text(encoding="utf-8") + recipe, encoding="utf-8")
    load_and_validate(repo)


def test_a_bosss_guaranteed_drop_is_a_signets_renewable_source(repo: RepoPaths) -> None:
    add_quests(repo, FIRST, SECOND, GIVES_SIGNET)
    boss = repo.content / "mobs" / "astone.yaml"
    shard = "  - {item: realmforge:ice_signet_shard}\n"
    text = boss.read_text(encoding="utf-8")
    assert shard in text
    # A drop that may fail is no source to count on.
    boss.write_text(
        text.replace(shard, shard + f"  - {{item: {SIGNET}, chance_percent: 50}}\n"), encoding="utf-8"
    )
    assert any("comes only from a quest's reward" in p for p in problems_for(repo))
    boss.write_text(text.replace(shard, shard + f"  - {{item: {SIGNET}}}\n"), encoding="utf-8")
    load_and_validate(repo)


def test_a_signet_no_quest_gives_is_not_checked_yet(repo: RepoPaths) -> None:
    """Until Q2's quests hand it out, the Signet of the Undervault has no source in play at all (realm portal
    design): the check starts with the quest that gives it."""
    add_book(repo)
    load_and_validate(repo)


# ---- Quest XP by level and type (RLM-189) ----


def test_quest_xp_follows_the_quests_level_and_type(repo: RepoPaths) -> None:
    kill_xp = curve(repo, KILL_XP)
    # Matěj's table for a standard quest (2026-10-15): (10 + 0.25 x level) kills of 45 + 5 x level, to the nearest 10.
    standard = {1: 510, 10: 1190, 20: 2180, 30: 3410, 40: 4900, 50: 6640, 60: 8630}
    assert {level: quest_xp(kill_xp, level, "standard") for level in standard} == standard
    assert QUEST_TYPE_FACTORS == {"small": 0.5, "standard": 1.0, "main": 1.5, "dungeon": 2.0, "finale": 4.0}
    # The type scales the XP before the rounding: a finale gives 4 times a standard quest's 512.5 at level 1.
    assert {type_: quest_xp(kill_xp, 1, type_) for type_ in QUEST_TYPE_FACTORS} == {
        "small": 260,
        "standard": 510,
        "main": 770,
        "dungeon": 1030,
        "finale": 2050,
    }
    assert quest_xp(kill_xp, 60, "finale") == 34500


@pytest.mark.parametrize(
    ("change", "command"),
    [
        (lambda q: q, "/realmforge xp quest {p} 1020 8"),
        (lambda q: q + "type: finale\n", "/realmforge xp quest {p} 4080 8"),
        (lambda q: q + "type: small\n", "/realmforge xp quest {p} 510 8"),
        # The override, for a special quest: the amount as written, still lowered for a player far above level 8.
        (lambda q: q + "type: finale\nxp: 123\n", "/realmforge xp quest {p} 123 8"),
        (lambda q: q + "xp: 0\n", None),
    ],
)
def test_a_quests_xp_reward_is_computed_or_overridden(
    repo: RepoPaths, change: Any, command: str | None
) -> None:
    add_quests(repo, FIRST, SECOND, change(LAST))
    quest = chapter_1(book(repo))[code("realmforge:quest/into_the_undervault")]
    commands = [reward["command"] for reward in quest["rewards"] if reward["type"] == "command"]
    assert commands == ([] if command is None else [command])


def test_a_quest_without_a_level_gives_xp_only_through_its_override(repo: RepoPaths) -> None:
    add_quests(repo, FIRST.replace("xp: 500\n", ""), SECOND, LAST)
    first = chapter_1(book(repo))[code("realmforge:quest/first_steps")]
    assert first["rewards"] == []
    add_quests(repo, FIRST)
    first = chapter_1(book(repo))[code("realmforge:quest/first_steps")]
    # Without a level there is nothing to compare the player's with: the plain command pays it in full.
    assert [reward["command"] for reward in first["rewards"]] == ["/realmforge xp add {p} 500"]


def test_character_xp_is_never_written_as_a_reward(repo: RepoPaths) -> None:
    add_quests(repo, FIRST + "rewards:\n  - type: xp\n    amount: 500\n", SECOND, LAST)
    assert any("character XP is no reward to write" in p for p in problems_for(repo))
    # Nor may another reward take its key, which would cost the XP reward its id.
    add_quests(
        repo, FIRST + "rewards:\n  - type: item\n    item: minecraft:bread\n    key: xp\n", SECOND, LAST
    )
    assert any("the reward key 'xp' is taken" in p for p in problems_for(repo))


def test_a_quest_far_below_the_player_pays_less(repo: RepoPaths) -> None:
    below = curve(repo, QUEST_XP_BELOW_PLAYER)
    paid = {player: quest_xp_paid(below, 1000, 10, player, 1.0) for player in (5, 10, 14, 15, 16, 17, 18, 40)}
    # Full XP up to 4 levels above the quest, then 75, 50 and 25 %, and 10 % from 8 above: never nothing.
    assert paid == {5: 1000, 10: 1000, 14: 1000, 15: 750, 16: 500, 17: 250, 18: 100, 40: 100}
    # The player's XP multiplier applies after the reduction.
    assert quest_xp_paid(below, 1000, 10, 15, 1.5) == 1125


def test_the_quest_xp_curve_is_needed_and_never_negative(repo: RepoPaths) -> None:
    path = repo.content / "curves" / "quest_xp_below_player.yaml"
    text = path.read_text(encoding="utf-8")
    path.write_text(text.replace("value: 0.1", "value: -0.1"), encoding="utf-8")
    assert any("is below 0" in p for p in problems_for(repo))
    path.unlink()
    assert any(
        "quest_xp_below_player.yaml" in p and "leveling needs this curve" in p for p in problems_for(repo)
    )


# ---- The follow-ups of the quest pipeline's review (RLM-472) ----


def test_a_biome_of_ours_needs_a_name_for_the_where_line(repo: RepoPaths) -> None:
    ours = WELL_EQUIPPED.replace(
        "  structure: minecraft:trial_chambers\n  name: {en_us: the Trial Chambers}\n", ""
    ).replace("minecraft:dripstone_caves", "realmforge:undervault")
    add_quests(repo, FIRST, SECOND, LAST, ours)
    problems = problems_for(repo)
    assert any(
        "well_equipped.yaml: place.biome: realmforge:undervault has no name" in p
        and "biome.realmforge.undervault" in p
        for p in problems
    ), problems
    # A text of the group "biome" names it: biome.realmforge.undervault, the key the game reads a biome's name from.
    write(repo.content / "texts" / "biome.yaml", "id: biome\ntexts:\n  undervault: {en_us: Undervault}\n")
    files = book(repo)
    quest = chapter_1(files)[code("realmforge:quest/well_equipped")]
    where = json.loads(files[f"{LANG}/chapters/chapter_1.json5"][f"quest.{quest['id']}.quest_desc"][0])
    assert where["with"][1] == {"translate": "biome.realmforge.undervault"}
    content, _ = load_and_validate(repo)
    lang = next(
        json.loads(data)
        for path, data in generate_all(content, repo).items()
        if repo.rel(path).endswith("assets/realmforge/lang/en_us.json")
    )
    assert lang["biome.realmforge.undervault"] == "Undervault"


def test_a_smithing_upgrade_is_a_recipe_to_craft(repo: RepoPaths) -> None:
    """Every netherite tool and armor piece has only ``<item>_smithing`` as its recipe."""
    crafted = WELL_EQUIPPED.replace(
        "craft: [realmforge:arcane_silk, minecraft:iron_ingot]",
        "craft: [minecraft:netherite_sword, minecraft:netherite_chestplate, minecraft:iron_ingot]",
    )
    add_quests(repo, FIRST, SECOND, LAST, crafted)
    load_and_validate(repo)
    add_quests(repo, crafted.replace("minecraft:netherite_sword", "minecraft:ancient_debris"))
    assert any("no recipe makes minecraft:ancient_debris" in p for p in problems_for(repo))


SHOUT = """\
id: {id}
name: {{en_us: Sample Shout}}
description: {{en_us: A shout.}}
school: realmforge:physical
scales_with: attack_damage
targeting: {{type: realmforge:self}}
cast: {cast}
{cost}effects:
  - type: realmforge:heal
    amount: 1
"""


def test_the_sample_spell_stays_one_a_bare_caster_casts_at_once(repo: RepoPaths) -> None:
    """The QA GameTest casts the sample with mana alone and expects it to go off at once: a self-targeted rage shout
    or a self channel that sorts first must not become the sample."""
    content, _ = load_and_validate(repo)
    before = dict(sample_tasks(content))["quest_task/spell"]
    assert isinstance(before, SpellTaskDef)
    write(
        repo.content / "spells" / "aaa_rage_shout.yaml",
        SHOUT.format(
            id="aaa_rage_shout",
            cast="{type: instant}",
            cost="cost: {resource: realmforge:rage, amount: 10}\n",
        ),
    )
    write(
        repo.content / "spells" / "aab_self_channel.yaml",
        SHOUT.format(
            id="aab_self_channel", cast="{type: channel, duration_seconds: 4, interval_seconds: 0.5}", cost=""
        ),
    )
    content, _ = load_and_validate(repo)
    spells = {f"realmforge:{entry.model.id}": entry.model for entry in content.spells}
    assert not castable_sample(spells["realmforge:aaa_rage_shout"])
    assert not castable_sample(spells["realmforge:aab_self_channel"])
    after = dict(sample_tasks(content))["quest_task/spell"]
    assert isinstance(after, SpellTaskDef)
    assert after.spell == before.spell
    sample = spells[after.spell]
    assert sample.targeting.type == "realmforge:self"
    assert sample.cast.type == "instant"
    assert sample.cost is None or sample.cost.resource == "realmforge:mana"
    assert not sample.requires and not sample.npc_only and sample.is_cast
    # A free instant self spell that sorts first does become the sample: the choice is by what it needs, not by name.
    write(
        repo.content / "spells" / "aac_free_shout.yaml",
        SHOUT.format(id="aac_free_shout", cast="{type: instant}", cost=""),
    )
    content, _ = load_and_validate(repo)
    assert dict(sample_tasks(content))["quest_task/spell"].spell == "realmforge:aac_free_shout"
