"""content/realms/: the datapack registry realmforge:realm, each realm's unlock advancement and the checks between a
realm, its dimension, its dimension type and its dimension rules (realm portal spec, "Data")."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedRealmExpectations.java"
)


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def edit_json(repo: RepoPaths, rel: str, change: dict[str, object]) -> None:
    path = repo.content / "worldgen" / rel
    data = json.loads(path.read_text(encoding="utf-8"))
    data.update(change)
    path.write_text(json.dumps(data), encoding="utf-8")


def test_repository_realm_is_undervault() -> None:
    content, universe = load_and_validate(RepoPaths())
    assert [(e.model.id, e.model.dimension, e.model.name.en_us) for e in content.realms] == [
        ("stone", "realmforge:stone", "Undervault")
    ]
    assert universe.contains("minecraft:advancement", "realmforge:realm/stone")
    assert universe.contains("realmforge:realm", "realmforge:stone")


def test_a_realm_becomes_its_entry_unlock_and_texts(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/realm/stone.json"]) == {
        "dimension": "realmforge:stone",
        "name": "realm.realmforge.stone.name",
        "subtitle": "realm.realmforge.stone.subtitle",
        "locked_hint": "realm.realmforge.stone.locked_hint",
        "flavour": "realm.realmforge.stone.flavour",
        "icon": "realmforge:vaultstone",
    }
    unlock = json.loads(files[f"{DATA}/advancement/realm/stone.json"])
    assert unlock["criteria"] == {"unlocked": {"trigger": "minecraft:impossible"}}
    assert unlock["requirements"] == [["unlocked"]]
    display = unlock["display"]
    assert (display["hidden"], display["show_toast"], display["announce_to_chat"]) == (True, True, False)
    assert display["title"] == {
        "translate": "advancement.realmforge.realm_unlocked",
        "with": [{"translate": "realm.realmforge.stone.name"}],
    }
    assert display["icon"] == {"id": "realmforge:vaultstone"}
    lang = json.loads(files[LANG])
    assert lang["realm.realmforge.stone.name"] == "Undervault"
    assert lang["realm.realmforge.stone.subtitle"] == "Stone Realm"
    assert lang["advancement.realmforge.realm_unlocked"] == "Realm unlocked: %1$s"
    assert '"realmforge:realm/stone")' in files[EXPECTATIONS].decode()


def test_a_realms_rules_must_require_its_unlock(repo: RepoPaths) -> None:
    rules = repo.content / "dimensions" / "stone.yaml"
    rules.write_text(rules.read_text(encoding="utf-8").split("access:")[0], encoding="utf-8")
    assert any(
        "dimensions/stone.yaml: access: the rules of realmforge:stone (content/dimensions/) need its unlock"
        in p
        for p in problems_for(repo)
    )


def test_a_realm_needs_rules(repo: RepoPaths) -> None:
    (repo.content / "dimensions" / "stone.yaml").unlink()
    assert any("realms/stone.yaml: access: the rules of realmforge:stone" in p for p in problems_for(repo))


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        ({"coordinate_scale": 1.0}, "a realm links to Overworld by a coordinate scale above 1"),
        (
            {
                "attributes": {
                    "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "never"}
                }
            },
            "a realm's beds set no spawn and do not explode",
        ),
        (
            {
                "attributes": {
                    "minecraft:gameplay/bed_rule": {
                        "can_set_spawn": "never",
                        "can_sleep": "never",
                        "explodes": True,
                    }
                }
            },
            "a realm's beds set no spawn and do not explode",
        ),
    ],
)
def test_a_realms_dimension_type_links_by_scale_and_has_safe_beds(
    repo: RepoPaths, change: dict[str, object], expected: str
) -> None:
    edit_json(repo, "dimension_type/stone.json", change)
    assert any("dimension_type/stone.json: " in p and expected in p for p in problems_for(repo)), (
        problems_for(repo)
    )


def test_a_realm_names_one_of_our_dimensions_once(repo: RepoPaths) -> None:
    source = (repo.content / "realms" / "stone.yaml").read_text(encoding="utf-8")
    write(repo.content / "realms" / "twin.yaml", source.replace("id: stone", "id: twin"))
    write(
        repo.content / "realms" / "surface.yaml",
        source.replace("id: stone", "id: surface").replace("realmforge:stone", "minecraft:overworld"),
    )
    problems = problems_for(repo)
    assert any("realms/twin.yaml: dimension: realmforge:stone is already the realm of" in p for p in problems)
    assert any(
        "realms/surface.yaml: dimension: minecraft:overworld is not one of our dimensions" in p
        for p in problems
    )


def test_a_realms_icon_is_an_item(repo: RepoPaths) -> None:
    path = repo.content / "realms" / "stone.yaml"
    path.write_text(path.read_text(encoding="utf-8").replace("realmforge:vaultstone", "realmforge:vaultstne"))
    assert any("realms/stone.yaml: icon: unknown id 'realmforge:vaultstne'" in p for p in problems_for(repo))
