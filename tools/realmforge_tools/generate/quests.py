"""Quests: the quest book FTB Quests reads from ``config/ftbquests/quests/`` (architecture 16, ADR 0006).

FTB Quests 26.1.2.9 reads its quest book as JSON5 (``BaseQuestFile#readDataFull``, checked in its jar), not as 1.21.1's
SNBT. Every file here is JSON after a comment header, which JSON5 allows, in ``pack/config/ftbquests/quests/``:

- ``data.json5``: the book's settings: FTB Quests' file version and its defaults (rewards per player and claimed by
  hand, linear progression), never ``suppress_all_autoclaiming``;
- ``chapter_groups.json5``: no groups, so every chapter is in the default group;
- ``chapters/<chapter>.json5``: a chapter of content/chapters/, placed by its ``order``, with its quests, their tasks
  and their rewards;
- ``lang/en_us/``: the texts, split as FTB Quests saves them (``TranslationTable#splitAndSerialize``): the chapters'
  titles in ``chapter.json5``, the quests' titles, subtitles and descriptions and the tasks' titles in
  ``chapters/<chapter>.json5``, the rewards' titles in ``reward.json5``. A key is ``<type>.<id>.<field>``
  (``TranslationManager#makeKey``).

A chapter is written once it has a quest, and nothing at all while content/quests/ is empty, so the pack keeps no quest
book of ours until Q2 adds quests. FTB Quests rewrites the book in the instance whenever it loads it
(``Chapter#addQuest`` marks it changed), in its own JSON5 style and with its defaults added, keeping every id and value
written here (checked with a GameTest server, 2026-10-25); packwiz-installer brings these files back at the next
launch. Ids come from ``schema/quest.object_id``. A character XP reward is a
``CommandReward`` that runs ``/realmforge xp add`` for the claiming player (FTB Quests puts the name in ``{p}``) with
permission level 2, our command's level (FTB Quests' default is 0), quietly; its title reads as FTB Quests' own XP
reward does ("XP: +500", in FTB Quests' translation of "XP"). An advancement reward is per player
(``team_reward: false``) and claims itself (``auto: "enabled"``), as architecture 16 explains.

Our own tasks (Q1-2: a level, a dungeon, a spell) become advancements in the core mod's data,
``data/realmforge/advancement/quest/<quest>/<key>.json``, without a display, so no toast and no advancement screen
entry. Each has one criterion of our triggers, and the quest's task is FTB Quests' advancement task on it, titled from
data ("Reach level 5", "Complete Test Dungeon", "Cast Firebolt"). FTB Quests reads a title or a description line that
is a JSON text component as one (``TextUtils#parseRawText``), so these titles and the description's generated "where,
with what" block (Q1-3) are components that translate through our own texts (``content/texts/quest.yaml``) and the
game's names of realms, items, biomes, dungeons and spells. A final boss quest's dependencies include its realm's
mandatory quests (``schema/quest.quest_dependencies``, RLM-367).

For the QA mod it writes ``GeneratedQuestBookExpectations`` (every chapter, quest, task and reward, which a GameTest
compares with the book FTB Quests loaded) and, from sample tasks, advancements in the QA mod's own data that a
GameTest completes through the triggers.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate import blocks, items
from realmforge_tools.generate.datapack import data_dir, translation_key
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.id_universe import IdUniverse, load_vanilla
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, QA_MOD_ID, RepoPaths
from realmforge_tools.schema.chapter import ChapterDef
from realmforge_tools.schema.quest import (
    CUSTOM_STATS,
    OWN_TASKS,
    AdvancementRewardDef,
    AdvancementTaskDef,
    CheckmarkTaskDef,
    DimensionTaskDef,
    DungeonTaskDef,
    ItemRewardDef,
    ItemTaskDef,
    KillTaskDef,
    LevelTaskDef,
    OwnTaskDef,
    QuestDef,
    RewardDef,
    SpellTaskDef,
    StatTaskDef,
    TaskDef,
    XpRewardDef,
    chapter_key,
    code_string,
    object_id,
    quest_dependencies,
    quest_key,
    quest_rewards,
    reward_key,
    task_advancement_path,
    task_key,
)
from realmforge_tools.schema.spell import SELF
from realmforge_tools.schema.text import text_key

SOURCES = "content/quests/ and content/chapters/"
LOCALE = "en_us"
# BaseQuestFile.VERSION in FTB Quests 26.1.2.9: a file of another version is marked changed and saved again on load.
FILE_VERSION = 13
# CommandReward#permissionLevel: 2 is LevelBasedPermissionSet.GAMEMASTER, the level /realmforge xp needs.
COMMAND_PERMISSION_LEVEL = 2
# FTB Quests' translation key of its own XP reward's name (assets/ftbquests/lang/en_us.json: "XP").
XP_NAME_KEY = "ftbquests.reward.ftbquests.xp"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
# Our criteria triggers (progression/ProgressionTriggers in the core mod, architecture 16).
LEVEL_REACHED = f"{MOD_ID}:level_reached"
DUNGEON_COMPLETED = f"{MOD_ID}:dungeon_completed"
SPELL_CAST = f"{MOD_ID}:spell_cast"
# The one criterion of a task's advancement.
CRITERION = "done"
# The group of content/texts/ whose texts the generated titles and description lines use.
TEXT_GROUP = "quest"
# Vanilla items whose id is also a block's but whose name is an item's (item.minecraft.<path>), not the block's: they
# register with Items#registerItem, not #registerBlock (checked in the 26.1.2 sources, net.minecraft.world.item.Items).
VANILLA_ITEMS_NAMED_AS_ITEMS = frozenset(
    {"minecraft:nether_wart", "minecraft:resin_clump", "minecraft:wheat"}
)
ITEM_REGISTRY = "minecraft:item"
BLOCK_REGISTRY = "minecraft:block"
# The objects of the quest book and how FTB Quests resolves their rewards (Reward#isTeamReward, #getAutoClaimType): no
# reward of ours is a team reward (data.json5's default_reward_team is false), an advancement reward claims itself and
# every other reward waits for the player (default_autoclaim_rewards: "disabled").
AUTO_CLAIM_ENABLED = "enabled"
AUTO_CLAIM_DISABLED = "disabled"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedQuestBookExpectations"): (
            _expectations(content)
        ),
    }
    qa_advancements = paths.qa_generated / "resources" / "data" / QA_MOD_ID / "advancement"
    for path, task in sample_tasks(content):
        files[qa_advancements / f"{path}.json"] = dump_json(task_advancement(task))
    if not content.quests:
        return files
    root = paths.pack_quests
    lang = root / "lang" / LOCALE
    chapters = {entry.model.id: entry.model for entry in content.chapters}
    every_quest = [entry.model for entry in content.quests]
    by_chapter: dict[str, list[QuestDef]] = {}
    for quest in every_quest:
        by_chapter.setdefault(quest.chapter, []).append(quest)
    names = _Names(content, paths)

    files |= {
        root / "data.json5": _json5(_data()),
        root / "chapter_groups.json5": _json5({"chapter_groups": []}),
    }
    advancements = data_dir(paths) / "advancement"
    chapter_titles: dict[str, Any] = {}
    reward_titles: dict[str, Any] = {}
    for index, chapter in enumerate(ordered_chapters(chapters, by_chapter)):
        quests = sorted(by_chapter[chapter.id], key=lambda quest: quest.id)
        files[root / "chapters" / f"{chapter.id}.json5"] = _json5(
            _chapter(chapter, index, quests, every_quest)
        )
        chapter_titles[_translation_key("chapter", chapter_key(chapter.id), "title")] = chapter.name.en_us
        texts: dict[str, Any] = {}
        for quest in quests:
            texts.update(_quest_texts(quest, names))
            reward_titles.update(_reward_titles(quest))
            for task in quest.tasks:
                if isinstance(task, OWN_TASKS):
                    path = task_advancement_path(quest.id, task)
                    files[advancements / f"{path}.json"] = dump_json(task_advancement(task))
        files[lang / "chapters" / f"{chapter.id}.json5"] = _json5(texts)
    files[lang / "chapter.json5"] = _json5(chapter_titles)
    if reward_titles:
        files[lang / "reward.json5"] = _json5(reward_titles)
    return files


def ordered_chapters(
    chapters: dict[str, ChapterDef], by_chapter: dict[str, list[QuestDef]]
) -> list[ChapterDef]:
    """The chapters with quests, in story order: the order of the written chapter files (``order_index``)."""
    return sorted((chapters[chapter_id] for chapter_id in by_chapter), key=lambda chapter: chapter.order)


def task_advancement(task: OwnTaskDef) -> dict[str, Any]:
    """The advancement that stands for one of our own tasks: no display, one criterion of our triggers."""
    match task:
        case LevelTaskDef():
            criterion: dict[str, Any] = {
                "trigger": LEVEL_REACHED,
                "conditions": {"level": {"min": task.level}},
            }
        case DungeonTaskDef():
            criterion = {"trigger": DUNGEON_COMPLETED}
            if task.dungeon is not None:
                criterion["conditions"] = {"dungeon": task.dungeon}
        case SpellTaskDef():
            criterion = {"trigger": SPELL_CAST}
            if task.spell is not None:
                criterion["conditions"] = {"spell": task.spell}
    return {"criteria": {CRITERION: criterion}, "requirements": [[CRITERION]]}


def sample_tasks(content: Content) -> list[tuple[str, OwnTaskDef]]:
    """One task of each of our kinds, for the QA mod's advancements (``realmforge_qa:quest_task/<name>``) that a
    GameTest completes: a level, any dungeon and the first dungeon type, any spell and the first spell a player casts on
    themselves, which a test casts without a target. Without a dungeon type or such a spell, its sample is left out."""
    samples: list[tuple[str, OwnTaskDef]] = [
        ("quest_task/level", LevelTaskDef(type="level", level=5)),
        ("quest_task/any_dungeon", DungeonTaskDef(type="dungeon")),
        ("quest_task/any_spell", SpellTaskDef(type="spell")),
    ]
    if content.dungeons:
        dungeon = f"{MOD_ID}:{content.dungeons[0].model.id}"
        samples.append(("quest_task/dungeon", DungeonTaskDef(type="dungeon", dungeon=dungeon)))
    castable = [
        entry.model.id
        for entry in content.spells
        if not entry.model.npc_only and entry.model.targeting.type == SELF
    ]
    if castable:
        samples.append(("quest_task/spell", SpellTaskDef(type="spell", spell=f"{MOD_ID}:{castable[0]}")))
    return samples


class _Names:
    """Text components that name things through the game's own translations."""

    def __init__(self, content: Content, paths: RepoPaths) -> None:
        self._items = {entry.model.id for entry in content.items}
        self._blocks = {entry.model.id for entry in content.blocks}
        self._paths = paths
        self._vanilla: IdUniverse | None = None

    def item(self, item: str) -> dict[str, Any]:
        """An item's name: ours from content, vanilla's under ``block.minecraft.<path>`` for a block item (its id is a
        block's too) and ``item.minecraft.<path>`` for the rest (Item.Properties#useBlockDescriptionPrefix)."""
        namespace, path = item.split(":", 1)
        if namespace == MOD_ID:
            return _translate(
                items.translation_key(path) if path in self._items else blocks.translation_key(path)
            )
        if self._vanilla is None:
            self._vanilla = load_vanilla(self._paths)
        block_item = self._vanilla.contains(BLOCK_REGISTRY, item) and item not in VANILLA_ITEMS_NAMED_AS_ITEMS
        return _translate(f"{'block' if block_item else 'item'}.{namespace}.{path}")


def _translate(key: str, *args: Any) -> dict[str, Any]:
    component: dict[str, Any] = {"translate": key}
    if args:
        component["with"] = list(args)
    return component


def _text(name: str, *args: Any) -> dict[str, Any]:
    """One of our quest texts (content/texts/quest.yaml)."""
    return _translate(text_key(TEXT_GROUP, name), *args)


def _component(component: dict[str, Any]) -> str:
    """A text component as FTB Quests reads it from a title or a description line: one line of JSON."""
    return json.dumps(component, ensure_ascii=False, separators=(",", ":"))


def _listed(components: list[dict[str, Any]]) -> list[Any]:
    """Names in a row, separated by commas: one component."""
    joined: list[Any] = [""]
    for i, component in enumerate(components):
        joined += [", ", component] if i else [component]
    return joined


def task_title(task: OwnTaskDef) -> dict[str, Any]:
    """The title of one of our own tasks, from data: "Reach level 5", "Complete Test Dungeon", "Cast a spell"."""
    match task:
        case LevelTaskDef():
            return _text("task_level", task.level)
        case DungeonTaskDef():
            if task.dungeon is None:
                return _text("task_any_dungeon")
            return _text("task_dungeon", _translate(_registry_name("dungeon_type", task.dungeon)))
        case SpellTaskDef():
            if task.spell is None:
                return _text("task_any_spell")
            return _text("task_spell", _translate(_registry_name("spell", task.spell)))


def _registry_name(registry: str, entry: str) -> str:
    """The name key of one of our datapack entries (``realm.realmforge.stone.name``)."""
    return translation_key(registry, entry.split(":", 1)[1], "name")


def where_with_what(quest: QuestDef, names: _Names) -> list[dict[str, Any]]:
    """The quest's generated "where, with what" lines (Q1-3): its realm and place, its recommended level, and the items
    to bring and to craft."""
    realm = _translate(_registry_name("realm", f"{MOD_ID}:{quest.realm}"))
    lines: list[dict[str, Any]] = []
    place = quest.place
    if place is None:
        lines.append(_text("where", realm))
    else:
        biome = None if place.biome is None else _translate(f"biome.{place.biome.replace(':', '.', 1)}")
        if place.name is None:
            spot: dict[str, Any] | None = biome
        else:
            structure = {"text": place.name.en_us}
            spot = structure if biome is None else _text("structure_in_biome", structure, biome)
        lines.append(_text("where_place", realm, spot))
    if quest.level is not None:
        lines.append(_text("level", quest.level))
    if quest.bring:
        lines.append(_text("bring", _listed([names.item(item) for item in quest.bring])))
    if quest.craft:
        lines.append(_text("craft", _listed([names.item(item) for item in quest.craft])))
    return lines


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
        "default_autoclaim_rewards": AUTO_CLAIM_DISABLED,
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


def _chapter(
    chapter: ChapterDef, index: int, quests: list[QuestDef], every_quest: list[QuestDef]
) -> dict[str, Any]:
    """A chapter; ``index`` is its place among the written chapters in story order, from 0, as FTB Quests saves
    ``order_index`` (its place in its group)."""
    return {
        "id": _code(chapter_key(chapter.id)),
        "group": "",
        "order_index": index,
        "filename": chapter.id,
        "default_quest_shape": "",
        "default_hide_dependency_lines": False,
        "quests": [_quest(quest, every_quest) for quest in quests],
        "quest_links": [],
        "images": [],
    }


def _item_stack(item: str) -> dict[str, Any]:
    return {"id": item, "count": 1}


def _quest(quest: QuestDef, every_quest: list[QuestDef]) -> dict[str, Any]:
    data: dict[str, Any] = {"id": _code(quest_key(quest.id)), "x": quest.position.x, "y": quest.position.y}
    if quest.icon is not None:
        data["icon"] = _item_stack(quest.icon)
    dependencies = quest_dependencies(quest, every_quest)
    if dependencies:
        data["dependencies"] = [_code(quest_key(dependency)) for dependency in dependencies]
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
        case LevelTaskDef() | DungeonTaskDef() | SpellTaskDef():
            data |= {
                "type": "advancement",
                "advancement": f"{MOD_ID}:{task_advancement_path(quest.id, task)}",
                "criterion": "",
            }
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
                "auto": AUTO_CLAIM_ENABLED,
                "advancement": reward.advancement,
                "criterion": "",
            }
    return data


def _quest_texts(quest: QuestDef, names: _Names) -> dict[str, Any]:
    key = quest_key(quest.id)
    texts: dict[str, Any] = {_translation_key("quest", key, "title"): quest.title.en_us}
    if quest.subtitle is not None:
        texts[_translation_key("quest", key, "quest_subtitle")] = quest.subtitle.en_us
    # One line per paragraph, an empty line between two paragraphs, then the generated block.
    lines: list[str] = []
    for paragraph in quest.description:
        lines += [*([""] if lines else []), paragraph.en_us]
    lines += [*([""] if lines else []), *(_component(line) for line in where_with_what(quest, names))]
    texts[_translation_key("quest", key, "quest_desc")] = lines
    for task in quest.tasks:
        title_key = _translation_key("task", task_key(quest.id, task.effective_key()), "title")
        if task.title is not None:
            texts[title_key] = task.title.en_us
        elif isinstance(task, OWN_TASKS):
            texts[title_key] = _component(task_title(task))
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


def _ftb_type(task: TaskDef) -> str:
    """The FTB Quests type a task becomes (``RegisteredQuestObjectType#getTypeForSerialization``)."""
    return "advancement" if isinstance(task, OWN_TASKS) else task.type


def _ftb_reward_type(reward: RewardDef) -> str:
    return "command" if isinstance(reward, XpRewardDef) else reward.type


def _expectations(content: Content) -> bytes:
    """The quest book as FTB Quests should load it, and the sample task advancements."""
    chapters = {entry.model.id: entry.model for entry in content.chapters}
    every_quest = [entry.model for entry in content.quests]
    by_chapter: dict[str, list[QuestDef]] = {}
    for quest in every_quest:
        by_chapter.setdefault(quest.chapter, []).append(quest)
    chapter_rows = []
    for chapter in ordered_chapters(chapters, by_chapter):
        quest_rows = []
        for quest in sorted(by_chapter[chapter.id], key=lambda q: q.id):
            tasks = [
                f"new TaskExpectation({string_literal(_code(task_key(quest.id, task.effective_key())))}, "
                f"{string_literal(_ftb_type(task))})"
                for task in quest.tasks
            ]
            rewards = []
            for reward in quest_rewards(quest):
                auto = AUTO_CLAIM_ENABLED if isinstance(reward, AdvancementRewardDef) else AUTO_CLAIM_DISABLED
                rewards.append(
                    f"new RewardExpectation({string_literal(_code(reward_key(quest.id, reward.effective_key())))}, "
                    f"{string_literal(_ftb_reward_type(reward))}, false, {string_literal(auto)})"
                )
            dependencies = [
                _code(quest_key(dependency)) for dependency in quest_dependencies(quest, every_quest)
            ]
            quest_rows.append(
                "                    new QuestExpectation(\n"
                f"                            {string_literal(f'{MOD_ID}:{quest.id}')}, "
                f"{string_literal(_code(quest_key(quest.id)))}, {'true' if quest.optional else 'false'},\n"
                f"                            {_strings(dependencies)},\n"
                f"                            {_inline(tasks)},\n"
                f"                            {_inline(rewards)})"
            )
        chapter_rows.append(
            "            new ChapterExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{chapter.id}')}, "
            f"{string_literal(_code(chapter_key(chapter.id)))},\n"
            f"                    {_rows(quest_rows)})"
        )
    samples = [
        f"            new TaskSample({string_literal(f'{QA_MOD_ID}:{path}')}, {string_literal(task.type)}, "
        f"{_target(task)})"
        for path, task in sample_tasks(content)
    ]
    stats = [f'"{MOD_ID}:{stat}"' for stat in CUSTOM_STATS]
    source = (
        header(Path(__file__), "content/quests/, content/chapters/, content/dungeons/ and content/spells/")
        + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/**\n"
        " * What {@code content/quests/} says the quest book holds, so a GameTest can compare it with the book FTB Quests\n"
        " * loaded, and our sample quest tasks (architecture 16). Ids are FTB Quests' 16 hex digits, types its own\n"
        " * ({@code RegisteredQuestObjectType#getTypeForSerialization}); a reward's team and auto-claim are as FTB Quests\n"
        " * resolves them ({@code Reward#isTeamReward}, {@code Reward#getAutoClaimType}).\n"
        " */\n"
        "public final class GeneratedQuestBookExpectations {\n"
        "    /** A chapter of ours, by its content id and its quest book id, with its quests in content id order. */\n"
        "    public record ChapterExpectation(String id, String code, List<QuestExpectation> quests) {\n"
        "    }\n"
        "\n"
        "    /** A quest, with the quest book ids of the quests it waits for. */\n"
        "    public record QuestExpectation(String id, String code, boolean optional, List<String> dependencies,\n"
        "            List<TaskExpectation> tasks, List<RewardExpectation> rewards) {\n"
        "    }\n"
        "\n"
        "    public record TaskExpectation(String code, String type) {\n"
        "    }\n"
        "\n"
        "    public record RewardExpectation(String code, String type, boolean team, String autoClaim) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * A sample of one of our own quest tasks: the QA mod's advancement the pipeline wrote for it, its kind\n"
        "     * ({@code level}, {@code dungeon}, {@code spell}) and its target: the level, or the dungeon type's or spell's id,\n"
        "     * empty for any.\n"
        "     */\n"
        "    public record TaskSample(String advancement, String kind, String target) {\n"
        "    }\n"
        "\n"
        "    /** The chapters of ours, in story order; empty while {@code content/quests/} is. */\n"
        f"    public static final List<ChapterExpectation> CHAPTERS = {_rows(chapter_rows)};\n"
        "\n"
        f"    public static final List<TaskSample> TASK_SAMPLES = {_rows(samples)};\n"
        "\n"
        "    /** Our custom statistics, which a stat task may name. */\n"
        f"    public static final List<String> CUSTOM_STATS = List.of({', '.join(stats)});\n"
        "\n"
        "    private GeneratedQuestBookExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _target(task: OwnTaskDef) -> str:
    match task:
        case LevelTaskDef():
            return string_literal(str(task.level))
        case DungeonTaskDef():
            return string_literal(task.dungeon or "")
        case SpellTaskDef():
            return string_literal(task.spell or "")


def _rows(rows: list[str]) -> str:
    """A ``List.of`` with one row per line."""
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"


def _inline(rows: list[str]) -> str:
    """A ``List.of`` on one line."""
    return "List.of(" + ", ".join(rows) + ")"


def _strings(values: list[str]) -> str:
    return f"List.of({', '.join(string_literal(value) for value in values)})"
