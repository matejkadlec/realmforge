from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

GENERATED_TEXTS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedTexts.java"
LANG = "mod/src/generated/resources/assets/realmforge/lang"

HINTS = """\
id: hint
texts:
  first_steps:
    en_us: Press N
  open_the_map:
    en_us: Press M
"""


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def test_texts_become_lang_keys_and_constants(repo: RepoPaths) -> None:
    write(repo.content / "texts" / "hint.yaml", HINTS)
    files = generate(repo)

    en_us = json.loads(files[f"{LANG}/en_us.json"])
    assert en_us["hint.realmforge.first_steps"] == "Press N"
    assert en_us["hint.realmforge.open_the_map"] == "Press M"

    java = files[GENERATED_TEXTS].decode()
    assert 'public static final String HINT_FIRST_STEPS = "hint.realmforge.first_steps";' in java
    assert 'public static final String HINT_OPEN_THE_MAP = "hint.realmforge.open_the_map";' in java
    # Constants are sorted by name within a group, so the output does not depend on YAML key order.
    assert java.index("HINT_FIRST_STEPS") < java.index("HINT_OPEN_THE_MAP")


def test_repository_texts_cover_the_night_vision_key(repo: RepoPaths) -> None:
    files = generate(repo)
    java = files[GENERATED_TEXTS].decode()
    for constant in (
        "KEY_CATEGORY_MAIN",
        "KEY_NIGHT_VISION",
        "MESSAGE_NIGHT_VISION_ON",
        "MESSAGE_NIGHT_VISION_OFF",
    ):
        assert f"public static final String {constant} = " in java
    en_us = json.loads(files[f"{LANG}/en_us.json"])
    assert en_us["key.realmforge.night_vision"] == "Toggle Night Vision"
    # The key category realmforge:main reads its name from key.category.realmforge.main.
    assert en_us["key.category.realmforge.main"] == "Realmforge"


def test_dotted_group_prefixes_its_keys_and_joins_its_constants(repo: RepoPaths) -> None:
    write(repo.content / "texts" / "hint.extra.yaml", "id: hint.extra\ntexts:\n  tip:\n    en_us: Tip\n")
    files = generate(repo)
    assert json.loads(files[f"{LANG}/en_us.json"])["hint.extra.realmforge.tip"] == "Tip"
    java = files[GENERATED_TEXTS].decode()
    assert 'public static final String HINT_EXTRA_TIP = "hint.extra.realmforge.tip";' in java


def test_dotted_name_suffixes_its_key_and_joins_its_constant(repo: RepoPaths) -> None:
    # Apotheosis reads a loot category's plural from loot_category.<namespace>.<path>.plural (RLM-273).
    write(repo.content / "texts" / "hint.yaml", "id: hint\ntexts:\n  tip.plural:\n    en_us: Tips\n")
    files = generate(repo)
    assert json.loads(files[f"{LANG}/en_us.json"])["hint.realmforge.tip.plural"] == "Tips"
    java = files[GENERATED_TEXTS].decode()
    assert 'public static final String HINT_TIP_PLURAL = "hint.realmforge.tip.plural";' in java


def test_a_dotted_name_and_an_underscored_one_cannot_share_a_constant(repo: RepoPaths) -> None:
    write(
        repo.content / "texts" / "hint.yaml",
        "id: hint\ntexts:\n  tip.plural:\n    en_us: A\n  tip_plural:\n    en_us: B\n",
    )
    assert any("Java constant HINT_TIP_PLURAL is also generated for" in p for p in problems_for(repo))


def test_text_comment_cannot_close_the_javadoc(repo: RepoPaths) -> None:
    write(repo.content / "texts" / "hint.yaml", "id: hint\ntexts:\n  odd:\n    en_us: 'a */ b @c'\n")
    java = generate(repo)[GENERATED_TEXTS].decode()
    assert "a *&#47; b &#64;c" in java
    assert "a */ b" not in java


def test_a_text_with_a_line_break_keeps_its_comment_on_one_line(repo: RepoPaths) -> None:
    write(repo.content / "texts" / "hint.yaml", 'id: hint\ntexts:\n  two:\n    en_us: "On: a\\nOff: b"\n')
    files = generate(repo)
    assert json.loads(files[f"{LANG}/en_us.json"])["hint.realmforge.two"] == "On: a\nOff: b"
    java = files[GENERATED_TEXTS].decode()
    assert '"On: a\\nOff: b" */' in java
    assert "On: a\nOff: b" not in java


@pytest.mark.parametrize("group", ["item", "curve", "attribute", "stat_rule"])
def test_group_may_not_reuse_another_types_key_prefix(repo: RepoPaths, group: str) -> None:
    write(repo.content / "texts" / f"{group}.yaml", f"id: {group}\ntexts:\n  test_item:\n    en_us: Clash\n")
    assert any(
        f"texts/{group}.yaml: id: '{group}' is the translation key prefix of content/" in p
        for p in problems_for(repo)
    )


def test_java_constants_must_be_unique(repo: RepoPaths) -> None:
    write(repo.content / "texts" / "a_b.yaml", "id: a_b\ntexts:\n  c:\n    en_us: One\n")
    write(repo.content / "texts" / "a.yaml", "id: a\ntexts:\n  b_c:\n    en_us: Two\n")
    assert any("Java constant A_B_C is also generated for" in p for p in problems_for(repo))


@pytest.mark.parametrize(
    ("yaml", "expected"),
    [
        ("id: hint\ntexts: {}\n", "texts: Dictionary should have at least 1 item"),
        (
            "id: hint\ntexts:\n  Bad-Name:\n    en_us: X\n",
            "texts.Bad-Name.[key]: String should match pattern",
        ),
        ("id: hint\ntexts:\n  a: {}\n", "texts.a.en_us: Field required"),
        (
            "id: hint\ntexts:\n  a:\n    en_us: X\n    cs_cz: Y\n",
            "texts.a.cs_cz: Extra inputs are not permitted",
        ),
        ("id: hint\n", "texts: Field required"),
        ("id: hint.\ntexts:\n  a:\n    en_us: X\n", "id: String should match pattern"),
        ("id: hint..x\ntexts:\n  a:\n    en_us: X\n", "id: String should match pattern"),
        ("id: hint\ntexts:\n  a.:\n    en_us: X\n", "texts.a..[key]: String should match pattern"),
        ("id: hint\ntexts:\n  .a:\n    en_us: X\n", "texts..a.[key]: String should match pattern"),
    ],
)
def test_invalid_text_groups_are_rejected(repo: RepoPaths, yaml: str, expected: str) -> None:
    write(repo.content / "texts" / "hint.yaml", yaml)
    assert any(expected in p for p in problems_for(repo))
