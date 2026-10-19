"""Apotheosis affixes (content/apotheosis_affixes/): validation and the generated affix files (RLM-271)."""

from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.apotheosis_affix import ApotheosisAffixDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

LUCKY_DATA = "mod/src/generated/resources/data/apotheosis/apotheosis/affixes/generic/attribute/lucky.json"

AFFIX = """\
affix: apotheosis:melee/attribute/test
type: apotheosis:attribute
attribute: minecraft:luck
operation: add_value
affix_type: stat
weight: 10
quality: 0.5
categories: [apotheosis:melee_weapon]
values:
  apotheosis:rare: {min: 1.0, max: 2.0, step: 0.5}
"""


def affix_model(**changes: object) -> ApotheosisAffixDef:
    data = {
        "id": "test",
        "affix": "apotheosis:melee/attribute/test",
        "type": "apotheosis:attribute",
        "attribute": "minecraft:luck",
        "operation": "add_value",
        "affix_type": "stat",
        "weight": 10,
        "quality": 0.5,
        "categories": ["apotheosis:melee_weapon"],
        "values": {"apotheosis:rare": {"min": 1.0, "max": 2.0, "step": 0.5}},
    }
    return ApotheosisAffixDef.model_validate({**data, **changes})


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def test_lucky_replaces_apotheosis_s_affix_without_the_curios_category() -> None:
    repo = RepoPaths()
    content, _ = load_and_validate(repo)
    files = {repo.rel(path): data for path, data in generate_all(content, repo).items()}
    lucky = json.loads(files[LUCKY_DATA])
    # Apotheosis 9.1.0's generic/attribute/lucky.json, without apotheosis:charm.
    assert lucky == {
        "type": "apotheosis:attribute",
        "attribute": "minecraft:luck",
        "categories": [
            "apotheosis:bow",
            "apotheosis:breaker",
            "apotheosis:helmet",
            "apotheosis:chestplate",
            "apotheosis:leggings",
            "apotheosis:boots",
            "apotheosis:shield",
            "apotheosis:trident",
            "apotheosis:melee_weapon",
            "apotheosis:shears",
        ],
        "definition": {"affix_type": "stat", "exclusive_set": [], "weights": {"quality": 0.1, "weight": 25}},
        "operation": "add_value",
        "values": {
            "apotheosis:uncommon": {"min": 1.0, "max": 1.5, "step": 0.25},
            "apotheosis:rare": {"min": 2.0, "max": 3.0, "step": 0.25},
            "apotheosis:epic": {"min": 2.5, "max": 4.0, "step": 0.25},
            "apotheosis:mythic": {"min": 3.0, "max": 6.0, "step": 0.25},
        },
    }


def test_rarities_are_written_in_apotheosis_s_order(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "test.yaml",
        "id: test\n"
        + AFFIX.replace(
            "  apotheosis:rare: {min: 1.0, max: 2.0, step: 0.5}\n",
            "  apotheosis:mythic: {min: 3.0, max: 4.0, step: 0.5}\n  apotheosis:common: {min: 0.5, max: 1.0, step: 0.5}\n",
        ),
    )
    content, _ = load_and_validate(repo)
    files = {repo.rel(path): data for path, data in generate_all(content, repo).items()}
    data = json.loads(
        files["mod/src/generated/resources/data/apotheosis/apotheosis/affixes/melee/attribute/test.json"]
    )
    assert list(data["values"]) == ["apotheosis:common", "apotheosis:mythic"]


def test_the_curios_charm_category_is_refused() -> None:
    with pytest.raises(ValidationError, match="exists only with Curios"):
        affix_model(categories=["apotheosis:melee_weapon", "apotheosis:charm"])


@pytest.mark.parametrize(
    ("changes", "message"),
    [
        ({"categories": ["apotheosis:wand"]}, "unknown loot category 'apotheosis:wand'"),
        ({"categories": []}, "at least 1 item"),
        ({"categories": ["apotheosis:bow", "apotheosis:bow"]}, "duplicate entries"),
        ({"values": {"apotheosis:legendary": {"min": 1.0, "max": 2.0, "step": 0.5}}}, "unknown rarities"),
        ({"values": {"apotheosis:rare": {"min": 2.0, "max": 1.0, "step": 0.5}}}, "max 1.0 is below min 2.0"),
        ({"values": {"apotheosis:rare": {"min": 1.0, "max": 2.0, "step": 0}}}, "greater than 0"),
        ({"affix": "realmforge:melee/test"}, "should match pattern"),
        ({"type": "apotheosis:mob_effect"}, "apotheosis:attribute"),
        ({"affix_type": "legendary"}, "'stat', 'basic_effect' or 'ability'"),
    ],
)
def test_invalid_affixes_are_refused(changes: dict, message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        affix_model(**changes)


def test_an_unknown_attribute_is_reported(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "test.yaml",
        "id: test\n" + AFFIX.replace("minecraft:luck", "minecraft:lucky"),
    )
    assert any(
        "unknown id 'minecraft:lucky' in registry minecraft:attribute" in p for p in problems_for(repo)
    )


def test_one_affix_is_replaced_once_and_never_excludes_itself(repo: RepoPaths) -> None:
    write(repo.content / "apotheosis_affixes" / "test.yaml", "id: test\n" + AFFIX)
    write(
        repo.content / "apotheosis_affixes" / "again.yaml",
        "id: again\n" + AFFIX + "exclusive_set: [apotheosis:melee/attribute/test]\n",
    )
    problems = problems_for(repo)
    assert any("apotheosis:melee/attribute/test is already replaced by" in p for p in problems), problems
    assert any("cannot exclude itself" in p for p in problems), problems
