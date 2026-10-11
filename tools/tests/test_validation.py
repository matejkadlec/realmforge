from __future__ import annotations

import pytest

from realmforge_tools.content import load_content
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

VALID_ITEM = """\
id: {id}
name:
  en_us: Thing
max_stack_size: 8
creative_tabs:
  - minecraft:ingredients
"""


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def test_repository_content_is_valid() -> None:
    content, universe = load_and_validate(RepoPaths())
    assert [e.model.id for e in content.items] == ["test_item"]
    assert universe.contains("minecraft:item", "realmforge:test_item")


def test_valid_item_loads_with_defaults(repo: RepoPaths) -> None:
    write(repo.content / "items" / "plain.yaml", "id: plain\nname:\n  en_us: Plain\n")
    content, _ = load_and_validate(repo)
    plain = next(e.model for e in content.items if e.model.id == "plain")
    assert plain.max_stack_size == 64
    assert plain.creative_tabs == []
    assert plain.name.translations() == {"en_us": "Plain"}


def test_unknown_key_is_rejected(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", VALID_ITEM.format(id="a") + "colour: red\n")
    assert any("items/a.yaml: colour: Extra inputs are not permitted" in p for p in problems_for(repo))


def test_wrong_type_is_rejected_without_coercion(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", VALID_ITEM.format(id="a").replace("8", '"8"'))
    assert any("items/a.yaml: max_stack_size:" in p for p in problems_for(repo))


def test_stack_size_range(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", VALID_ITEM.format(id="a").replace("8", "100"))
    assert any("max_stack_size: Input should be less than or equal to 99" in p for p in problems_for(repo))


def test_english_name_is_required(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", "id: a\nname:\n  cs_cz: Věc\n")
    assert any("items/a.yaml: name.en_us: Field required" in p for p in problems_for(repo))


def test_id_must_match_file_name(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", VALID_ITEM.format(id="b"))
    assert any("items/a.yaml: id: 'b' must equal the file name 'a'" in p for p in problems_for(repo))


def test_invalid_id_format(repo: RepoPaths) -> None:
    write(repo.content / "items" / "Bad-Id.yaml", VALID_ITEM.format(id="Bad-Id"))
    assert any("items/Bad-Id.yaml: id: String should match pattern" in p for p in problems_for(repo))


def test_unknown_reference_is_reported_with_suggestion(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", VALID_ITEM.format(id="a").replace("ingredients", "ingredient"))
    (problem,) = problems_for(repo)
    assert "items/a.yaml: creative_tabs.0: unknown id 'minecraft:ingredient'" in problem
    assert "did you mean: minecraft:ingredients" in problem


def test_reference_needs_namespace(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "a.yaml",
        VALID_ITEM.format(id="a").replace("minecraft:ingredients", "ingredients"),
    )
    assert any("creative_tabs.0: String should match pattern" in p for p in problems_for(repo))


def test_duplicate_creative_tabs_are_rejected(repo: RepoPaths) -> None:
    # NeoForge throws when the same item is added to one creative tab twice, so this must fail validation.
    write(repo.content / "items" / "a.yaml", VALID_ITEM.format(id="a") + "  - minecraft:ingredients\n")
    (problem,) = problems_for(repo)
    assert "items/a.yaml: creative_tabs: Value error, duplicate entries: minecraft:ingredients" in problem


def test_duplicate_yaml_keys_are_errors(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", VALID_ITEM.format(id="a") + "max_stack_size: 4\n")
    assert any("items/a.yaml: line" in p and "duplicate" in p.lower() for p in problems_for(repo))


def test_yaml_1_2_semantics_keep_no_as_a_string(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", "id: a\nname:\n  en_us: no\n")
    content = load_content(repo)
    assert next(e.model for e in content.items if e.model.id == "a").name.en_us == "no"


@pytest.mark.parametrize(
    ("rel", "expected"),
    [
        ("items/a.yml", "use the .yaml extension"),
        ("item/a.yaml", "unknown content location"),
        ("items/nested/a.yaml", "unknown content location"),
        ("items/notes.txt", "unexpected file in content/"),
    ],
)
def test_misplaced_files_are_reported(repo: RepoPaths, rel: str, expected: str) -> None:
    write(repo.content / rel, VALID_ITEM.format(id="a"))
    assert any(expected in p for p in problems_for(repo))


def test_all_problems_are_reported_at_once(repo: RepoPaths) -> None:
    write(repo.content / "items" / "a.yaml", "id: a\nname: {}\n")
    write(repo.content / "items" / "b.yaml", "id: b\nname:\n  en_us: B\nmax_stack_size: 0\n")
    problems = problems_for(repo)
    assert any("items/a.yaml" in p for p in problems)
    assert any("items/b.yaml" in p for p in problems)
