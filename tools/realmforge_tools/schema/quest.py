"""Quests: ``content/quests/<id>.yaml``, the quest book that FTB Quests shows (architecture 16, ADR 0006).

A quest belongs to a chapter of ``content/chapters/``, stands at a position on the chapter's page, waits for the
quests it depends on, and is completed by its tasks: items to bring (detected or consumed), an advancement, a
statistic, kills, a dimension to enter, a checkmark the player clicks, or one of our own (Q1-2): a character level to
reach, a dungeon to complete or a spell to cast. FTB Quests has no task type of ours (ADR 0006), so each of our tasks
becomes an advancement without a display, completed by one of our criteria triggers, that FTB Quests' advancement task
reads. Its rewards are items, character XP (a ``CommandReward`` that runs our operator command ``/realmforge xp add``)
and our own advancements, such as a realm's unlock ``realmforge:realm/<id>``. A chapter's last quest says
``completes_chapter: true`` and grants the chapter's advancement ``realmforge:chapter/<id>``, which raises the level cap
(C2). Every advancement reward is per player and claims itself, so each member of an FTB Teams team gets it
(architecture 16, RLM-73).

Every quest says where, how and with what (Q1-3, research notes 3: "find X" quests with no location or recipe): where
is its ``realm`` and, more precisely, its ``place`` (a structure or a biome); how is its tasks; with what is its
recommended ``level``, the items to ``bring`` and the items to ``craft`` on the way. A quest that sends the player
somewhere or after something (an errand: any task but a checkmark, a level or a spell) needs its level, which lies in
its realm's level band. The quest's text names levels only through these fields, never typed by hand, and the
generator adds a "where, with what" block from them to the description; the how is the task list FTB Quests shows,
with titles from data for our own tasks. A realm's final boss quest says ``requires_mandatory: true``: it then also
waits for every mandatory (non-optional) quest of its realm in its chapter (RLM-367).

``generate/quests.py`` writes the quest book as FTB Quests 26.1.2's JSON5 files into ``pack/config/ftbquests/quests/``.
FTB Quests saves a player's progress under the ids of its chapters, quests, tasks and rewards: an id it cannot read, or
one that two objects share, is replaced by a random one, and the progress is lost. Every id is therefore derived from
a key (``object_id``): a chapter's and a quest's from their content ids, a task's and a reward's from the quest's id and
the task's or reward's key, which defaults to its type and target (``item:minecraft:diamond``). Reordering tasks or
changing a count, a position or a text keeps every id; changing a target changes that task's or reward's id, unless
its ``key`` keeps the old one.
"""

from __future__ import annotations

import hashlib
import re
from collections.abc import Iterable
from typing import Annotated, Literal

from pydantic import Field, StringConstraints, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.chapter import MAX_LEVEL, advancement_id
from realmforge_tools.schema.common import (
    ContentId,
    LocalizedText,
    ResourceId,
    StrictModel,
    UniqueContentIds,
    UniqueResourceIds,
)

# FTB Quests reads a quest object's id as 16 hex digits into a Java long and rejects 0 and 1 (BaseQuestFile#readID;
# 1 is the quest file itself), so ids are positive longs from 2 up.
_LONG_MAX = 0x7FFF_FFFF_FFFF_FFFF
# FTB Quests' limits (checked in the FTB Quests 26.1.2.9 jar): StatTask#value is a Java int, ItemReward#count is
# clamped to 1-8192, and a quest's position may be any double; a page of a few hundred units is plenty.
_INT_MAX = 2**31 - 1
_MAX_REWARD_COUNT = 8192
_MAX_POSITION = 1000.0

# A task's or reward's key: lowercase, digits and the separators of a default key (``item:minecraft:diamond``), so a
# key may pin an old default key after its target changes.
ObjectKey = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z0-9_.:/-]+$", max_length=128),
    Field(
        description=(
            "Keeps the task's or reward's id stable. Defaults to its type and target (item:minecraft:diamond); set "
            "it only to tell two tasks or rewards of one quest apart, or to keep the old id after the target changes."
        )
    ),
]


def object_id(path: str) -> int:
    """The FTB Quests id of the quest object with the key ``path``: the first 63 bits of its SHA-256, from 2 up."""
    value = int.from_bytes(hashlib.sha256(path.encode("utf-8")).digest()[:8], "big") & _LONG_MAX
    return value if value > 1 else value + 2


def code_string(object_id_: int) -> str:
    """The id as FTB Quests writes it: 16 uppercase hex digits (``QuestObjectBase#getCodeString``)."""
    return f"{object_id_:016X}"


def chapter_key(chapter_id: str) -> str:
    return f"realmforge:chapter/{chapter_id}"


def quest_key(quest_id: str) -> str:
    return f"realmforge:quest/{quest_id}"


def task_key(quest_id: str, key: str) -> str:
    return f"{quest_key(quest_id)}/task/{key}"


def reward_key(quest_id: str, key: str) -> str:
    return f"{quest_key(quest_id)}/reward/{key}"


# Our custom statistics, ``realmforge:<name>`` (progression/ProgressionStats in the core mod, Q1-2), which a stat task
# may name: dungeons completed, for repeatable quests.
CUSTOM_STATS = ("dungeons_completed",)

# A level typed into a quest's text: "level 12", "Lv. 12", "lvl 12". A quest names levels only through its data.
TYPED_LEVEL = re.compile(r"\b(?:level|lvl|lv)\.?\s*\d", re.IGNORECASE)


class PositionDef(StrictModel):
    """Where the quest stands on its chapter's page, in FTB Quests' grid units (one quest is 1 wide)."""

    x: float = Field(
        ge=-_MAX_POSITION, le=_MAX_POSITION, allow_inf_nan=False, description="Right is positive."
    )
    y: float = Field(
        ge=-_MAX_POSITION, le=_MAX_POSITION, allow_inf_nan=False, description="Down is positive."
    )


class _TaskDef(StrictModel):
    key: ObjectKey | None = None
    title: LocalizedText | None = Field(
        default=None, description="Replaces FTB Quests' own title for the task (e.g. 'Kill 10 Zombie')."
    )

    def default_key(self) -> str:
        raise NotImplementedError

    def effective_key(self) -> str:
        return self.key if self.key is not None else self.default_key()


class ItemTaskDef(_TaskDef):
    """Have items in the inventory: FTB Quests detects them, and takes them only with ``consume``."""

    type: Literal["item"]
    item: ResourceId
    count: int = Field(default=1, ge=1, le=_INT_MAX)
    consume: bool = Field(
        default=False,
        description="Take the items when the task completes; otherwise they stay with the player.",
    )

    def default_key(self) -> str:
        return f"item:{self.item}"


class AdvancementTaskDef(_TaskDef):
    """Have an advancement, or one of its criteria. Retroactive: an advancement earned before counts."""

    type: Literal["advancement"]
    advancement: ResourceId
    criterion: str = Field(
        default="",
        max_length=128,
        description="One criterion of the advancement; empty for the whole advancement.",
    )

    def default_key(self) -> str:
        return f"advancement:{self.advancement}" + (f"#{self.criterion}" if self.criterion else "")


class StatTaskDef(_TaskDef):
    """Reach a value of a custom statistic (``minecraft:custom``, e.g. minecraft:mob_kills). Retroactive."""

    type: Literal["stat"]
    stat: ResourceId
    value: int = Field(ge=1, le=_INT_MAX)

    def default_key(self) -> str:
        return f"stat:{self.stat}"


class KillTaskDef(_TaskDef):
    """Kill mobs of a type after the quest opens (not retroactive: prefer an advancement or a statistic where one
    exists, research notes 3)."""

    type: Literal["kill"]
    entity: ResourceId
    count: int = Field(default=1, ge=1, le=_INT_MAX)

    def default_key(self) -> str:
        return f"kill:{self.entity}"


class DimensionTaskDef(_TaskDef):
    """Enter a dimension."""

    type: Literal["dimension"]
    dimension: ResourceId

    def default_key(self) -> str:
        return f"dimension:{self.dimension}"


class CheckmarkTaskDef(_TaskDef):
    """A checkmark the player clicks, for a quest that only tells something."""

    type: Literal["checkmark"]

    def default_key(self) -> str:
        return "checkmark"


class LevelTaskDef(_TaskDef):
    """Reach a character level or a higher one (``realmforge:level_reached``, which fires at every level change and
    login, so a level reached before counts)."""

    type: Literal["level"]
    level: int = Field(ge=1, le=MAX_LEVEL)

    def default_key(self) -> str:
        return f"level:{self.level}"


class DungeonTaskDef(_TaskDef):
    """Complete a run of a dungeon type, or of any dungeon: be in its party when its goal is done
    (``realmforge:dungeon_completed``). A run completed before the quest opened counts, as for every advancement."""

    type: Literal["dungeon"]
    dungeon: ResourceId | None = Field(
        default=None,
        description="The dungeon type (content/dungeons/), e.g. realmforge:test_dungeon; any without it.",
    )

    def default_key(self) -> str:
        return "dungeon" if self.dungeon is None else f"dungeon:{self.dungeon}"


class SpellTaskDef(_TaskDef):
    """Cast a spell, or any spell: an instant spell when it is cast, a cast time or a channel when it completes
    (``realmforge:spell_cast``). A cast before the quest opened counts, as for every advancement."""

    type: Literal["spell"]
    spell: ResourceId | None = Field(
        default=None,
        description="The spell (content/spells/), e.g. realmforge:firebolt; any spell without it.",
    )

    def default_key(self) -> str:
        return "spell" if self.spell is None else f"spell:{self.spell}"


TaskDef = Annotated[
    ItemTaskDef
    | AdvancementTaskDef
    | StatTaskDef
    | KillTaskDef
    | DimensionTaskDef
    | CheckmarkTaskDef
    | LevelTaskDef
    | DungeonTaskDef
    | SpellTaskDef,
    Field(discriminator="type"),
]
# Our own tasks: FTB Quests has no task type for them, so each becomes an advancement its advancement task reads.
OwnTaskDef = LevelTaskDef | DungeonTaskDef | SpellTaskDef
OWN_TASKS = (LevelTaskDef, DungeonTaskDef, SpellTaskDef)
# Tasks that send the player somewhere or after something; a quest with one needs its recommended level. A checkmark,
# a level and a spell are done anywhere.
ERRAND_TASKS = (ItemTaskDef, AdvancementTaskDef, StatTaskDef, KillTaskDef, DimensionTaskDef, DungeonTaskDef)


def task_advancement_path(quest_id: str, task: OwnTaskDef) -> str:
    """The path of the advancement that stands for one of our own tasks: ``quest/<quest>/<key>``, the key's colons as
    slashes (``realmforge:quest/first_steps/level/5``), so it keeps its id as long as the task keeps its key."""
    return f"quest/{quest_id}/{task.effective_key().replace(':', '/')}"


class _RewardDef(StrictModel):
    key: ObjectKey | None = None

    def default_key(self) -> str:
        raise NotImplementedError

    def effective_key(self) -> str:
        return self.key if self.key is not None else self.default_key()


class ItemRewardDef(_RewardDef):
    """Items, given when the player claims the reward."""

    type: Literal["item"]
    item: ResourceId
    count: int = Field(default=1, ge=1, le=_MAX_REWARD_COUNT)

    def default_key(self) -> str:
        return f"item:{self.item}"


class XpRewardDef(_RewardDef):
    """Character XP: ``/realmforge xp add``, with the player's XP multiplier and level-ups (architecture 10.2)."""

    type: Literal["xp"]
    amount: int = Field(ge=1, le=_LONG_MAX)

    def default_key(self) -> str:
        return "xp"


class AdvancementRewardDef(_RewardDef):
    """One of our own advancements, such as a realm's unlock ``realmforge:realm/<id>``; per player and claimed by
    itself. A chapter's advancement comes from ``completes_chapter`` instead."""

    type: Literal["advancement"]
    advancement: ResourceId

    def default_key(self) -> str:
        return f"advancement:{self.advancement}"


RewardDef = Annotated[ItemRewardDef | XpRewardDef | AdvancementRewardDef, Field(discriminator="type")]


class PlaceDef(StrictModel):
    """Where in its realm the quest sends the player: a structure, a biome, or a structure in a biome."""

    structure: ResourceId | None = Field(
        default=None,
        description="A world structure (minecraft:worldgen/structure), e.g. minecraft:trial_chambers.",
    )
    name: LocalizedText | None = Field(
        default=None,
        description=(
            "How the text names the structure, which has no name of its own in the game: 'the Trial Chambers'. "
            "Required with a structure."
        ),
    )
    biome: ResourceId | None = Field(
        default=None, description="A biome (minecraft:worldgen/biome), e.g. minecraft:dripstone_caves."
    )

    @model_validator(mode="after")
    def _names_a_place(self) -> PlaceDef:
        if self.structure is None and self.biome is None:
            raise ValueError("a place names a structure, a biome or both")
        if (self.structure is None) != (self.name is None):
            raise ValueError("a structure needs its name, and a name belongs to a structure")
        return self


class QuestDef(StrictModel):
    """A quest in the quest book: where it stands, what opens it, what completes it and what it gives."""

    id: ContentId
    chapter: ContentId = Field(description="The chapter of content/chapters/ whose page shows the quest.")
    realm: ContentId = Field(
        description=(
            "Where: the realm of content/realms/ the quest takes place in (overworld for the Overworld). Its "
            "mandatory quests are its non-optional quests of the same chapter (requires_mandatory)."
        )
    )
    place: PlaceDef | None = Field(
        default=None,
        description="Where, more precisely: the structure or biome in the realm the quest sends to.",
    )
    level: int | None = Field(
        default=None,
        ge=1,
        le=MAX_LEVEL,
        description=(
            "With what: the recommended character level, within the realm's level band and at most the cap its "
            "chapter leaves. Required for an errand (any task but a checkmark, a level or a spell)."
        ),
    )
    bring: UniqueResourceIds = Field(
        default_factory=list, description="With what: items the player should have with them."
    )
    craft: UniqueResourceIds = Field(
        default_factory=list,
        description="With what: items the player crafts on the way; each needs a recipe (ours or vanilla).",
    )
    requires_mandatory: bool = Field(
        default=False,
        description=(
            "The realm's final boss quest (RLM-367): it also waits for every mandatory (non-optional) quest of its "
            "realm in its chapter, so killing the boss alone opens nothing."
        ),
    )
    title: LocalizedText
    subtitle: LocalizedText | None = Field(default=None, description="One line under the title.")
    description: list[LocalizedText] = Field(
        default_factory=list, description="The quest's text, one entry per paragraph."
    )
    icon: ResourceId | None = Field(
        default=None,
        description="An item shown as the quest's icon; FTB Quests picks one from the tasks without it.",
    )
    position: PositionDef
    dependencies: UniqueContentIds = Field(
        default_factory=list, description="Quests that must be completed before this one opens."
    )
    optional: bool = Field(
        default=False, description="Optional quests do not count towards the chapter's progress."
    )
    completes_chapter: bool = Field(
        default=False,
        description=(
            "The chapter's last quest: it grants the chapter's advancement realmforge:chapter/<chapter>, per player "
            "and claimed by itself, which raises the level cap."
        ),
    )
    tasks: list[TaskDef] = Field(min_length=1, description="Every task must be done to complete the quest.")
    rewards: list[RewardDef] = Field(default_factory=list)

    @model_validator(mode="after")
    def _consistent(self) -> QuestDef:
        if self.id in self.dependencies:
            raise ValueError(f"the quest {self.id!r} cannot depend on itself")
        if self.optional and self.completes_chapter:
            raise ValueError("the quest that completes its chapter cannot be optional")
        if self.optional and self.requires_mandatory:
            raise ValueError(
                "a quest that waits for its realm's mandatory quests (a final boss) cannot be optional"
            )
        errands = sorted({task.type for task in self.tasks if isinstance(task, ERRAND_TASKS)})
        if errands and self.level is None:
            raise ValueError(
                f"its {', '.join(errands)} task sends the player somewhere or after something: give the recommended "
                "level"
            )
        for field, text in self.texts():
            if TYPED_LEVEL.search(text.en_us):
                raise ValueError(
                    f"{field} types a level by hand ({text.en_us!r}): levels come from data (the quest's level, our "
                    "level tasks), which the generated text names"
                )
        paths = [task_advancement_path(self.id, task) for task in self.tasks if isinstance(task, OWN_TASKS)]
        for path in paths:
            if any(segment in ("", ".", "..") for segment in path.split("/")):
                raise ValueError(
                    f"the advancement path {path!r} of a task has an empty, '.' or '..' part: change its key"
                )
        shared = sorted({path for path in paths if paths.count(path) > 1})
        if shared:
            raise ValueError(
                f"two tasks would share the advancement {', '.join(f'{MOD_ID}:{path}' for path in shared)}: give one "
                "of them its own key"
            )
        for what, keys in (
            ("task", [task.effective_key() for task in self.tasks]),
            ("reward", [reward.effective_key() for reward in self.rewards]),
        ):
            duplicates = sorted({key for key in keys if keys.count(key) > 1})
            if duplicates:
                raise ValueError(
                    f"two {what}s share the key {', '.join(duplicates)}: give one of them its own key, so each keeps "
                    "its own id"
                )
        return self

    def texts(self) -> list[tuple[str, LocalizedText]]:
        """Every text written by hand, with its field: the title, subtitle, description, place name and task titles."""
        texts: list[tuple[str, LocalizedText]] = [("title", self.title)]
        if self.subtitle is not None:
            texts.append(("subtitle", self.subtitle))
        texts += [(f"description.{i}", paragraph) for i, paragraph in enumerate(self.description)]
        if self.place is not None and self.place.name is not None:
            texts.append(("place.name", self.place.name))
        texts += [
            (f"tasks.{i}.title", task.title) for i, task in enumerate(self.tasks) if task.title is not None
        ]
        return texts


def chapter_advancement_reward(chapter_id: str) -> AdvancementRewardDef:
    """The reward of a chapter's last quest: the chapter's advancement (``completes_chapter``)."""
    return AdvancementRewardDef(type="advancement", advancement=f"realmforge:{advancement_id(chapter_id)}")


def quest_rewards(quest: QuestDef) -> list[RewardDef]:
    """The quest's rewards as the quest book lists them: its own, then the chapter's advancement if it completes the
    chapter."""
    rewards: list[RewardDef] = list(quest.rewards)
    if quest.completes_chapter:
        rewards.append(chapter_advancement_reward(quest.chapter))
    return rewards


def quest_object_keys(quest: QuestDef) -> list[str]:
    """The keys of the quest and of every task and reward it puts into the quest book (``object_id``)."""
    return [
        quest_key(quest.id),
        *(task_key(quest.id, task.effective_key()) for task in quest.tasks),
        *(reward_key(quest.id, reward.effective_key()) for reward in quest_rewards(quest)),
    ]


def mandatory_quests(quest: QuestDef, quests: Iterable[QuestDef]) -> list[str]:
    """The quests a realm's final boss quest (``requires_mandatory``) also waits for (RLM-367): every mandatory
    (non-optional) quest of its realm in its chapter, but itself, the quests that come after it (which depend on it,
    directly or through others) and any other quest that waits for them. Empty for any other quest."""
    if not quest.requires_mandatory:
        return []
    candidates = list(quests)
    dependants: dict[str, list[str]] = {}
    for other in candidates:
        for dependency in other.dependencies:
            dependants.setdefault(dependency, []).append(other.id)
    after: set[str] = set()
    pending = [quest.id]
    while pending:
        for dependant in dependants.get(pending.pop(), []):
            if dependant not in after:
                after.add(dependant)
                pending.append(dependant)
    return sorted(
        other.id
        for other in candidates
        if other.id != quest.id
        and other.realm == quest.realm
        and other.chapter == quest.chapter
        and not other.optional
        and not other.requires_mandatory
        and other.id not in after
    )


def quest_dependencies(quest: QuestDef, quests: Iterable[QuestDef]) -> list[str]:
    """The quests it waits for, as the quest book writes them: its own dependencies, then the mandatory quests of its
    realm it does not name already (``mandatory_quests``)."""
    return list(quest.dependencies) + [
        other for other in mandatory_quests(quest, quests) if other not in quest.dependencies
    ]
