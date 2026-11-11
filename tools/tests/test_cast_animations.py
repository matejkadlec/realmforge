"""The player animations of casts (RLM-641, ADR 0073 decision 8): ``cast.player_animation`` in a spell, the
animation files of the core mod it may name, and the lengths the server and the client count on."""

from __future__ import annotations

import json
import re
import shutil

import pytest

from realmforge_tools.project import MOD_ID, REPO_ROOT, RepoPaths
from realmforge_tools.schema.spell import INSTANT_ANIMATION_TICKS
from realmforge_tools.validate import PLAYER_ANIMATIONS_DIR, load_and_validate, player_animations
from tests.conftest import write
from tests.test_spell_visuals import (
    CUBE,
    DATA,
    cube_model,
    generate,
    geo,
    problems_for,
    spell,
)

ANIMATIONS = f"mod/src/main/resources/assets/{MOD_ID}/{PLAYER_ANIMATIONS_DIR}"
JAVA = "mod/src/main/java/io/github/matejkadlec/realmforge"
SPELL_EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedSpellExpectations.java"
)
# A bolt without a model: its look's trail would draw it, and a spell without visuals needs neither.
EFFECTS = (
    "effects:\n"
    "  - type: realmforge:projectile\n"
    "    speed: 20\n"
    "    effects:\n"
    "      - {type: realmforge:damage, amount: 3}\n"
)
INSTANT = "cast: {type: instant, player_animation: realmforge:%s}\n" + EFFECTS
CAST_TIME = "cast: {type: cast_time, duration_seconds: 1.5, player_animation: realmforge:%s}\n" + EFFECTS
CHANNEL = (
    "cast: {type: channel, duration_seconds: 2, interval_seconds: 0.5, player_animation: realmforge:%s}\n"
    + EFFECTS
)


@pytest.fixture
def animated(repo: RepoPaths) -> RepoPaths:
    """The content copy with the core mod's player animations beside it, which a cast may name."""
    shutil.copytree(REPO_ROOT / ANIMATIONS, repo.root / ANIMATIONS)
    return repo


def animation(repo: RepoPaths, name: str, **emote: object) -> None:
    write(
        repo.root / ANIMATIONS / f"{name}.json",
        json.dumps({"name": name, "emote": {"beginTick": 0, "degrees": False, "moves": [], **emote}}),
    )


# The repository's animations


def test_the_core_mod_has_the_four_cast_animations() -> None:
    animations = player_animations(RepoPaths())
    assert sorted(animations) == [f"{MOD_ID}:{name}" for name in ("cast", "channel", "instant", "release")]
    assert animations[f"{MOD_ID}:cast"].loops, "a cast time's arms stay up for as long as it runs"
    assert animations[f"{MOD_ID}:channel"].loops, "and a channel's"
    instant = animations[f"{MOD_ID}:instant"]
    assert not instant.loops, "an instant spell's push plays once"
    assert instant.ticks == INSTANT_ANIMATION_TICKS, "and lasts exactly as long as its melee block"
    assert not animations[f"{MOD_ID}:release"].loops


def test_the_lengths_the_code_counts_on_are_the_animations_own() -> None:
    """The server blocks melee for ``SpellCast.PUSH_TICKS`` and the client lowers the arms after
    ``CastAnimation.RELEASE_TICKS``: both are numbers in Java that the animation files have to agree with."""
    animations = player_animations(RepoPaths())
    spell_cast = (REPO_ROOT / JAVA / "spell/SpellCast.java").read_text("utf-8")
    push = re.search(r"int PUSH_TICKS = (\d+);", spell_cast)
    assert push is not None
    assert int(push[1]) == INSTANT_ANIMATION_TICKS == animations[f"{MOD_ID}:instant"].ticks
    cast_animation = (REPO_ROOT / JAVA / "client/animation/CastAnimation.java").read_text("utf-8")
    release = re.search(r"int RELEASE_TICKS = (\d+);", cast_animation)
    assert release is not None
    assert int(release[1]) == animations[f"{MOD_ID}:release"].ticks
    for name in ("CAST", "CHANNEL", "INSTANT", "RELEASE"):
        assert f'Identifier {name} = Realmforge.id("{name.lower()}");' in cast_animation


def test_no_spell_of_the_repository_names_an_animation_of_its_own_yet() -> None:
    content, _ = load_and_validate(RepoPaths())
    assert [entry.model.id for entry in content.spells if entry.model.cast.player_animation is not None] == []


# A spell's own animation


@pytest.mark.parametrize(("body", "name"), [(INSTANT, "instant"), (CAST_TIME, "channel"), (CHANNEL, "cast")])
def test_a_casts_own_animation_reaches_its_data_and_its_expectations(
    animated: RepoPaths, body: str, name: str
) -> None:
    spell(animated, body % name)
    files = generate(animated)
    cast = json.loads(files[f"{DATA}/spell/test_shot.json"])["cast"]
    assert cast["player_animation"] == f"realmforge:{name}"
    assert f'Optional.of("realmforge:{name}")' in files[SPELL_EXPECTATIONS].decode()
    # A spell that names none has none in its data: its cast type's own plays.
    assert "player_animation" not in json.loads(files[f"{DATA}/spell/frostbolt.json"])["cast"]


@pytest.mark.parametrize(
    ("body", "expected"),
    [
        (
            INSTANT % "wave",
            "content/spells/test_shot.yaml: cast.player_animation: no player animation realmforge:wave: "
            "assets/realmforge/player_animations/ of the core mod has realmforge:cast, realmforge:channel, "
            "realmforge:half_loop, realmforge:instant, realmforge:long, realmforge:release, realmforge:tail",
        ),
        (
            INSTANT % "cast",
            "cast.player_animation: an instant spell's animation plays once and is over within 10 ticks, the time "
            "its caster's melee is blocked; realmforge:cast loops",
        ),
        (
            INSTANT % "long",
            "an instant spell's animation plays once and is over within 10 ticks, the time its caster's melee is "
            "blocked; realmforge:long lasts 14 ticks",
        ),
        (
            # PlayerAnimatorLoader: without a stopTick past its endTick, it lasts 3 ticks more than its endTick.
            INSTANT % "tail",
            "realmforge:tail lasts 11 ticks",
        ),
        (
            CAST_TIME % "instant",
            "cast.player_animation: a cast_time animation loops for as long as the cast runs; realmforge:instant "
            "plays once",
        ),
        (
            CHANNEL % "release",
            "a channel animation loops for as long as the cast runs; realmforge:release plays once",
        ),
        (
            # isLoop without a returnTick plays once in Player Animation Library.
            CHANNEL % "half_loop",
            "realmforge:half_loop plays once",
        ),
    ],
)
def test_an_animation_that_is_missing_or_does_not_fit_the_cast_is_refused(
    animated: RepoPaths, body: str, expected: str
) -> None:
    animation(animated, "long", isLoop=False, endTick=10, stopTick=14)
    animation(animated, "tail", isLoop=False, endTick=8)
    animation(animated, "half_loop", isLoop=True, endTick=20, stopTick=20)
    spell(animated, body)
    problems = problems_for(animated)
    assert any(expected in problem for problem in problems), problems


def test_an_animation_that_fits_is_accepted(animated: RepoPaths) -> None:
    animation(animated, "flick", isLoop=False, endTick=5, stopTick=10)
    animation(animated, "hold", isLoop=True, returnTick=4, endTick=20, stopTick=20)
    spell(animated, INSTANT % "flick", name="test_flick")
    spell(animated, CAST_TIME % "hold", name="test_hold")
    load_and_validate(animated)


def test_a_file_that_cannot_be_read_names_no_animation(animated: RepoPaths) -> None:
    write(animated.root / ANIMATIONS / "broken.json", "{not json")
    write(animated.root / ANIMATIONS / "nameless.json", '{"emote": {}}')
    assert sorted(player_animations(animated)) == [
        f"{MOD_ID}:{name}" for name in ("cast", "channel", "instant", "release")
    ]
    spell(animated, INSTANT % "broken")
    assert any("no player animation realmforge:broken" in problem for problem in problems_for(animated))


def test_a_checkout_without_the_core_mods_animations_refuses_a_named_one(repo: RepoPaths) -> None:
    assert player_animations(repo) == {}
    spell(repo, INSTANT % "instant")
    assert any(
        "no player animation realmforge:instant: assets/realmforge/player_animations/ of the core mod has none"
        in problem
        for problem in problems_for(repo)
    )


def test_a_swing_names_no_player_animation(animated: RepoPaths) -> None:
    spell(
        animated,
        "cast: {type: instant, animation_seconds: 0.5, player_animation: realmforge:instant}\n" + EFFECTS,
    )
    assert any(
        "player_animation: a spell that plays out over animation_seconds is a swing, whose weapon move is its own"
        in problem
        for problem in problems_for(animated)
    )


# The review notes this part took over (RLM-640's pull request)


def test_nothing_says_forming_still_waits_for_its_renderer() -> None:
    for path in (
        "tools/realmforge_tools/schema/spell.py",
        "tools/schemas/spells.schema.json",
        f"{JAVA}/spell/Visuals.java",
    ):
        text = (REPO_ROOT / path).read_text("utf-8")
        assert "Until the forming renderer" not in text, path
        assert "hands shows nothing" not in text, path


def test_a_cube_without_inflate_or_with_none_is_accepted(repo: RepoPaths) -> None:
    cube_model(repo, geo([CUBE, {**CUBE, "inflate": 0}], inflate=0.0))
    load_and_validate(repo)
