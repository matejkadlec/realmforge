"""Datapack registry types: curves (the first one), the generic conversion and the generated Java."""

from __future__ import annotations

import dataclasses
import json

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import curves, datapack, generate_all
from realmforge_tools.generate.curves import sample_levels
from realmforge_tools.generate.datapack import entry_data, game_field, percent_to_fraction, seconds_to_ticks
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.common import ContentId, LocalizedText, Percent, Seconds, StrictModel
from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

CURVE_DATA = "mod/src/generated/resources/data/realmforge/realmforge/curve"
CURVE_KEYS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedCurves.java"
CURVE_EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedCurveExpectations.java"
)
LANG = "mod/src/generated/resources/assets/realmforge/lang"


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def curve(points: str, extra: str = "") -> str:
    return f"id: c\n{extra}points:\n{points}"


# Validation


def test_repository_curves_are_valid() -> None:
    content, universe = load_and_validate(RepoPaths())
    assert [e.model.id for e in content.curves] == [
        "kill_xp",
        "kill_xp_above_player",
        "kill_xp_below_player",
        "mob_armor",
        "mob_damage",
        "mob_health",
        "stat_points_per_level",
        "test_curve",
        "xp_to_next_level",
    ]
    assert universe.contains("realmforge:curve", "realmforge:test_curve")


def test_curve_accepts_whole_numbers_as_values(repo: RepoPaths) -> None:
    write(repo.content / "curves" / "c.yaml", curve("  - {level: 0, value: 3}\n"))
    content, _ = load_and_validate(repo)
    (entry,) = (e for e in content.curves if e.model.id == "c")
    assert entry.model.points[0].value == 3.0


@pytest.mark.parametrize(
    ("points", "expected"),
    [
        (
            "  - {level: 2, value: 1}\n  - {level: 2, value: 3}\n",
            "levels must strictly increase, but 2 follows 2",
        ),
        (
            "  - {level: 5, value: 1}\n  - {level: 2, value: 3}\n",
            "levels must strictly increase, but 2 follows 5",
        ),
        ("  []\n", "points: List should have at least 1 item"),
        ("  - {level: -1, value: 1}\n", "points.0.level: Input should be greater than or equal to 0"),
        (
            "  - {level: 2147483648, value: 1}\n",
            "points.0.level: Input should be less than or equal to 2147483647",
        ),
        ("  - {level: 1, value: .nan}\n", "points.0.value: Input should be a finite number"),
        ("  - {level: 1.5, value: 1}\n", "points.0.level: Input should be a valid integer"),
        ('  - {level: 1, value: "1"}\n', "points.0.value: Input should be a valid number"),
    ],
)
def test_invalid_curves_are_rejected(repo: RepoPaths, points: str, expected: str) -> None:
    write(repo.content / "curves" / "c.yaml", curve(points))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


def test_curve_id_must_match_file_name(repo: RepoPaths) -> None:
    write(repo.content / "curves" / "d.yaml", curve("  - {level: 0, value: 1}\n"))
    assert any("curves/d.yaml: id: 'c' must equal the file name 'd'" in p for p in problems_for(repo))


# Interpolation: the GameTest compares the game's Curve#valueAt with these values.


def test_value_at_interpolates_and_stays_flat_beyond_the_ends() -> None:
    model = CurveDef.model_validate(
        {
            "id": "c",
            "points": [{"level": 1, "value": 0}, {"level": 10, "value": 450}, {"level": 20, "value": 1200.5}],
        }
    )
    assert model.value_at(-3) == 0
    assert model.value_at(1) == 0
    assert model.value_at(5.5) == 225
    assert model.value_at(10) == 450
    assert model.value_at(15) == 825.25
    assert model.value_at(20) == 1200.5
    assert model.value_at(99) == 1200.5


def test_samples_cover_both_ends_every_point_and_every_gap() -> None:
    model = CurveDef.model_validate(
        {"id": "c", "points": [{"level": 1, "value": 0}, {"level": 3, "value": 1}]}
    )
    assert sample_levels(model) == [0.0, 1.0, 2.0, 3.0, 4.0]


# Generation


def test_curve_outputs(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{CURVE_DATA}/test_curve.json"]) == {
        "name": "curve.realmforge.test_curve.name",
        "points": [{"level": 1, "value": 0.0}, {"level": 10, "value": 450.0}, {"level": 20, "value": 1200.5}],
    }
    assert json.loads(files[f"{LANG}/en_us.json"])["curve.realmforge.test_curve.name"] == "Test Curve"

    keys = files[CURVE_KEYS].decode()
    assert 'public static final ResourceKey<Curve> TEST_CURVE = key("test_curve");' in keys
    assert (
        "public static final List<ResourceKey<Curve>> ALL = List.of(KILL_XP, KILL_XP_ABOVE_PLAYER, "
        "KILL_XP_BELOW_PLAYER, MOB_ARMOR, MOB_DAMAGE, MOB_HEALTH, STAT_POINTS_PER_LEVEL, TEST_CURVE, "
        "XP_TO_NEXT_LEVEL);" in keys
    )

    expectations = files[CURVE_EXPECTATIONS].decode()
    assert '"realmforge:test_curve",' in expectations
    assert 'Optional.of("curve.realmforge.test_curve.name"),' in expectations
    assert "List.of(new Point(1, 0.0), new Point(10, 450.0), new Point(20, 1200.5))," in expectations
    assert "new Sample(15.0, 825.25)" in expectations


def test_curve_without_name_has_no_name_field_or_lang_entry(repo: RepoPaths) -> None:
    write(repo.content / "curves" / "c.yaml", curve("  - {level: 0, value: 1}\n"))
    files = generate(repo)
    assert json.loads(files[f"{CURVE_DATA}/c.json"]) == {"points": [{"level": 0, "value": 1.0}]}
    assert not any(key.startswith("curve.realmforge.c.") for key in json.loads(files[f"{LANG}/en_us.json"]))
    assert (
        'new CurveExpectation(\n                    "realmforge:c",\n                    Optional.empty(),'
        in (files[CURVE_EXPECTATIONS].decode())
    )


def test_no_curves_still_compile(repo: RepoPaths) -> None:
    # Our content needs leveling's curves (validation), so the curve generators get content without any directly.
    content, _ = load_and_validate(repo)
    content = dataclasses.replace(content, curves=())
    files = {
        repo.rel(path): data
        for path, data in (curves.generate(content, repo) | datapack.generate(content, repo)).items()
    }
    assert "ALL = List.of();" in files[CURVE_KEYS].decode()
    assert "CURVES = List.of();" in files[CURVE_EXPECTATIONS].decode()
    assert not any(path.startswith(CURVE_DATA) for path in files)


# The generic conversion (architecture 6.3), on a model shaped like a future type.


class _Effect(StrictModel):
    duration_seconds: Seconds
    label: LocalizedText


class _Spell(StrictModel):
    id: ContentId
    name: LocalizedText
    cooldown_seconds: Seconds
    crit_chance_percent: Percent
    max_level: int
    note: str | None = None
    effects: list[_Effect]


def test_entry_data_converts_units_and_texts_recursively() -> None:
    spell = _Spell.model_validate(
        {
            "id": "firebolt",
            "name": {"en_us": "Firebolt"},
            "cooldown_seconds": 1.5,
            "crit_chance_percent": 5,
            "max_level": 3,
            "effects": [{"duration_seconds": 4, "label": {"en_us": "Burning"}}],
        }
    )
    data, texts = entry_data(spell, "spell")
    assert data == {
        "name": "spell.realmforge.firebolt.name",
        "cooldown_ticks": 30,
        "crit_chance": 0.05,
        "max_level": 3,
        "effects": [{"duration_ticks": 80, "label": "spell.realmforge.firebolt.effects.0.label"}],
    }
    assert {key: text.translations() for key, text in texts.items()} == {
        "spell.realmforge.firebolt.name": {"en_us": "Firebolt"},
        "spell.realmforge.firebolt.effects.0.label": {"en_us": "Burning"},
    }


def test_unit_helpers() -> None:
    assert seconds_to_ticks(8) == 160
    assert seconds_to_ticks(0.05) == 1
    assert percent_to_fraction(50) == 0.5
    assert game_field("cooldown_seconds", 2) == ("cooldown_ticks", 40)
    assert game_field("chance_percent", 25) == ("chance", 0.25)
    assert game_field("level", 7) == ("level", 7)


def test_seconds_must_be_whole_ticks() -> None:
    class Timed(StrictModel):
        delay_seconds: Seconds

    assert Timed.model_validate({"delay_seconds": 0.15}).delay_seconds == 0.15
    with pytest.raises(ValidationError, match="not a whole number of game ticks"):
        Timed.model_validate({"delay_seconds": 0.01})
    with pytest.raises(ValidationError, match="greater than or equal to 0"):
        Timed.model_validate({"delay_seconds": -1})


def test_fields_that_convert_to_the_same_name_are_an_error() -> None:
    class Clash(StrictModel):
        id: ContentId
        chance: float
        chance_percent: Percent

    with pytest.raises(ValueError, match="both become 'chance'"):
        entry_data(Clash.model_validate({"id": "x", "chance": 0.5, "chance_percent": 50}), "clash")
