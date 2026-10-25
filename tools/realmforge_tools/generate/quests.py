"""Quests: the quest book FTB Quests reads from ``config/ftbquests/quests/`` (architecture 16, ADR 0006).

FTB Quests 26.1.2.9 reads its quest book as JSON5 (``BaseQuestFile#readDataFull``, checked in its jar), not as 1.21.1's
SNBT. Every file here is JSON after a comment header, which JSON5 allows, in ``pack/config/ftbquests/quests/``:

- ``data.json5``: the book's settings: FTB Quests' file version and its defaults (rewards per player and claimed by
  hand, linear progression), never ``suppress_all_autoclaiming``;
- ``chapter_groups.json5``: no groups, so every chapter is in the default group;
- ``chapters/<chapter>.json5``: a chapter of content/chapters/, ordered by its ``order``, with its quests, their tasks
  and their rewards;
- ``lang/en_us/``: the texts, split as FTB Quests saves them (``TranslationTable#splitAndSerialize``): the chapters'
  titles in ``chapter.json5``, the quests' titles, subtitles and descriptions and the tasks' titles in
  ``chapters/<chapter>.json5``, the rewards' titles in ``reward.json5``. A key is ``<type>.<id>.<field>``
  (``TranslationManager#makeKey``).

A chapter is written once it has a quest, and nothing at all while content/quests/ is empty, so the pack keeps no quest
book of ours until Q2 adds quests. Ids come from ``schema/quest.object_id``. A character XP reward is a
``CommandReward`` that runs ``/realmforge xp add`` for the claiming player (FTB Quests puts the name in ``{p}``) with
permission level 2, our command's level (FTB Quests' default is 0), quietly; its title reads as FTB Quests' own XP
reward does ("XP: +500", in FTB Quests' translation of "XP"). An advancement reward is per player
(``team_reward: false``) and claims itself (``auto: "enabled"``), as architecture 16 explains.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.javagen import header
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.chapter import ChapterDef
from realmforge_tools.schema.quest import (
    AdvancementRewardDef,
    AdvancementTaskDef,
    CheckmarkTaskDef,
    DimensionTaskDef,
    ItemRewardDef,
    ItemTaskDef,
    KillTaskDef,
    QuestDef,
    RewardDef,
    StatTaskDef,
    TaskDef,
    XpRewardDef,
    chapter_key,
    code_string,
    object_id,
    quest_key,
    quest_rewards,
    reward_key,
    task_key,
)

SOURCES = "content/quests/ and content/chapters/"
LOCALE = "en_us"
# BaseQuestFile.VERSION in FTB Quests 26.1.2.9: a file of another version is marked changed and saved again on load.
FILE_VERSION = 13
# CommandReward#permissionLevel: 2 is LevelBasedPermissionSet.GAMEMASTER, the level /realmforge xp needs.
COMMAND_PERMISSION_LEVEL = 2
# FTB Quests' translation key of its own XP reward's name (assets/ftbquests/lang/en_us.json: "XP").
XP_NAME_KEY = "ftbquests.reward.ftbquests.xp"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    if not content.quests:
        return {}
    root = paths.pack_quests
    lang = root / "lang" / LOCALE
    chapters = {entry.model.id: entry.model for entry in content.chapters}
    by_chapter: dict[str, list[QuestDef]] = {}
    for entry in content.quests:
        by_chapter.setdefault(entry.model.chapter, []).append(entry.model)

    files: dict[Path, bytes] = {
        root / "data.json5": _json5(_data()),
        root / "chapter_groups.json5": _json5({"chapter_groups": []}),
    }
    chapter_titles: dict[str, Any] = {}
    reward_titles: dict[str, Any] = {}
    for chapter in sorted((chapters[chapter_id] for chapter_id in by_chapter), key=lambda c: c.order):
        quests = sorted(by_chapter[chapter.id], key=lambda quest: quest.id)
        files[root / "chapters" / f"{chapter.id}.json5"] = _json5(_chapter(chapter, quests))
        chapter_titles[_translation_key("chapter", chapter_key(chapter.id), "title")] = chapter.name.en_us
        texts: dict[str, Any] = {}
        for quest in quests:
            texts.update(_quest_texts(quest))
            reward_titles.update(_reward_titles(quest))
        files[lang / "chapters" / f"{chapter.id}.json5"] = _json5(texts)
    files[lang / "chapter.json5"] = _json5(chapter_titles)
    if reward_titles:
        files[lang / "reward.json5"] = _json5(reward_titles)
    return files


def _json5(data: dict[str, Any]) -> bytes:
    return header(Path(__file__), SOURCES).encode("utf-8") + dump_json(data)


def _code(key: str) -> str:
    return code_string(object_id(key))


def _translation_key(object_type: str, key: str, field: str) -> str:
    return f"{object_type}.{_code(key)}.{field}"


def _data() -> dict[str, Any]:
    """The book's settings, in the order FTB Quests writes them (``BaseQuestFile#writeData``); its constructor's
    defaults except the file version."""
    return {
        "version": FILE_VERSION,
        "default_reward_team": False,
        "default_consume_items": False,
        "default_autoclaim_rewards": "disabled",
        "default_quest_shape": "circle",
        "default_quest_disable_jei": False,
        "emergency_items_cooldown": 300,
        "drop_loot_crates": False,
        "disable_gui": False,
        "grid_scale": 0.5,
        "pause_game": False,
        "lock_message": "",
        "progression_mode": "linear",
        "detection_delay": 20,
        "show_lock_icons": False,
        "drop_book_on_death": False,
        "hide_excluded_quests": False,
        "fallback_locale": LOCALE,
        "verify_on_load": False,
    }


def _chapter(chapter: ChapterDef, quests: list[QuestDef]) -> dict[str, Any]:
    return {
        "id": _code(chapter_key(chapter.id)),
        "group": "",
        "order_index": chapter.order,
        "filename": chapter.id,
        "default_quest_shape": "",
        "default_hide_dependency_lines": False,
        "quests": [_quest(quest) for quest in quests],
        "quest_links": [],
        "images": [],
    }


def _item_stack(item: str) -> dict[str, Any]:
    return {"id": item, "count": 1}


def _quest(quest: QuestDef) -> dict[str, Any]:
    data: dict[str, Any] = {"id": _code(quest_key(quest.id)), "x": quest.position.x, "y": quest.position.y}
    if quest.icon is not None:
        data["icon"] = _item_stack(quest.icon)
    if quest.dependencies:
        data["dependencies"] = [_code(quest_key(dependency)) for dependency in quest.dependencies]
    if quest.optional:
        data["optional"] = True
    data["tasks"] = [_task(quest, task) for task in quest.tasks]
    data["rewards"] = [_reward(quest, reward) for reward in quest_rewards(quest)]
    return data


def _task(quest: QuestDef, task: TaskDef) -> dict[str, Any]:
    data: dict[str, Any] = {"id": _code(task_key(quest.id, task.effective_key()))}
    match task:
        case ItemTaskDef():
            data |= {
                "type": "item",
                "item": _item_stack(task.item),
                "count": task.count,
                "consume_items": task.consume,
            }
        case AdvancementTaskDef():
            data |= {"type": "advancement", "advancement": task.advancement, "criterion": task.criterion}
        case StatTaskDef():
            data |= {"type": "stat", "stat": task.stat, "value": task.value}
        case KillTaskDef():
            data |= {"type": "kill", "entity": task.entity, "value": task.count}
        case DimensionTaskDef():
            data |= {"type": "dimension", "dimension": task.dimension}
        case CheckmarkTaskDef():
            data |= {"type": "checkmark"}
    return data


def _reward(quest: QuestDef, reward: RewardDef) -> dict[str, Any]:
    data: dict[str, Any] = {"id": _code(reward_key(quest.id, reward.effective_key()))}
    match reward:
        case ItemRewardDef():
            data |= {"type": "item", "item": _item_stack(reward.item), "count": reward.count}
        case XpRewardDef():
            data |= {
                "type": "command",
                "command": f"/realmforge xp add {{p}} {reward.amount}",
                "permission_level": COMMAND_PERMISSION_LEVEL,
                "silent": True,
            }
        case AdvancementRewardDef():
            data |= {
                "type": "advancement",
                "team_reward": False,
                "auto": "enabled",
                "advancement": reward.advancement,
                "criterion": "",
            }
    return data


def _quest_texts(quest: QuestDef) -> dict[str, Any]:
    key = quest_key(quest.id)
    texts: dict[str, Any] = {_translation_key("quest", key, "title"): quest.title.en_us}
    if quest.subtitle is not None:
        texts[_translation_key("quest", key, "quest_subtitle")] = quest.subtitle.en_us
    if quest.description:
        # One line per paragraph, an empty line between two paragraphs.
        lines: list[str] = []
        for paragraph in quest.description:
            lines += [*([""] if lines else []), paragraph.en_us]
        texts[_translation_key("quest", key, "quest_desc")] = lines
    for task in quest.tasks:
        if task.title is not None:
            texts[_translation_key("task", task_key(quest.id, task.effective_key()), "title")] = (
                task.title.en_us
            )
    return texts


def _reward_titles(quest: QuestDef) -> dict[str, Any]:
    titles: dict[str, Any] = {}
    for reward in quest_rewards(quest):
        if isinstance(reward, XpRewardDef):
            # A text component, which FTB Quests reads from a title that is a JSON array (TextUtils#parseRawText).
            title = [{"translate": XP_NAME_KEY}, ": ", {"text": f"+{reward.amount}", "color": "green"}]
            key = reward_key(quest.id, reward.effective_key())
            titles[_translation_key("reward", key, "title")] = json.dumps(title, ensure_ascii=False)
    return titles
