"""Realms: each realm's unlock advancement and the QA mod's expectations (the realm entries and their lang entries:
datapack.py). Overworld's entry is no realm, so it has no unlock.

- ``data/realmforge/advancement/realm/<id>.json``: the realm's unlock, with ``minecraft:impossible`` as its only
  criterion, so only the realm's unlock quest or a command grants it (realm portal spec, "Access and unlocks"). It is
  hidden until earned, shows the toast "Realm unlocked: <name>" and adds no chat line.
- ``GeneratedRealmExpectations``: every entry, its dimension, texts, unlock, sigil and portal colour.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir, translation_key
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.realm import RealmDef, unlock_advancement
from realmforge_tools.schema.text import text_key

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
REGISTRY = "realm"
SOURCES = "content/realms/"
# The toast's title, a text in content/texts/advancement.yaml, filled with the realm's name.
TITLE_TEXT = ("advancement", "realm_unlocked")
# A vanilla advancement background (a reference to the game's own texture, not a copy): every unlock is the root of
# its own tab until a realm map groups them (RE-7).
BACKGROUND = "minecraft:gui/advancements/backgrounds/stone"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    advancement_dir = data_dir(paths) / "advancement"
    for entry in content.realms:
        if entry.model.is_overworld:
            continue
        files[advancement_dir / f"{unlock_advancement(entry.model.id)}.json"] = dump_json(
            _advancement(entry.model)
        )
    qa_java = paths.qa_generated / "java"
    files[java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedRealmExpectations")] = _expectations(
        content
    )
    return files


def _advancement(realm: RealmDef) -> dict[str, object]:
    return {
        "criteria": {"unlocked": {"trigger": "minecraft:impossible"}},
        "display": {
            "announce_to_chat": False,
            "background": BACKGROUND,
            "description": {"translate": translation_key(REGISTRY, realm.id, "flavour")},
            "frame": "goal",
            "hidden": True,
            "icon": {"id": realm.icon},
            "show_toast": True,
            "title": {
                "translate": text_key(*TITLE_TEXT),
                "with": [{"translate": translation_key(REGISTRY, realm.id, "name")}],
            },
        },
        "requirements": [["unlocked"]],
    }


def _expectations(content: Content) -> bytes:
    rows = []
    for entry in content.realms:
        realm = entry.model
        hint = "" if realm.is_overworld else translation_key(REGISTRY, realm.id, "locked_hint")
        unlock = "" if realm.is_overworld else f"{MOD_ID}:{unlock_advancement(realm.id)}"
        rows.append(
            "            new RealmExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{realm.id}')},\n"
            f"                    {string_literal(realm.dimension)},\n"
            f"                    {string_literal(translation_key(REGISTRY, realm.id, 'name'))},\n"
            f"                    {string_literal(hint)},\n"
            f"                    {string_literal(unlock)},\n"
            f"                    {string_literal(realm.sigil or '')},\n"
            f"                    0x{realm.colour[1:].upper()})"
        )
    realms = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/realms/} says about every realm, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedRealmExpectations {\n"
        "    /**\n"
        "     * Expected state of one entry. Ids are full resource locations: the realm's, e.g. {@code realmforge:stone},\n"
        "     * its dimension's, its unlock advancement's and its sigil's; the colour is RGB. Overworld's entry, which is\n"
        "     * no realm, has an empty hint, unlock and sigil.\n"
        "     */\n"
        "    public record RealmExpectation(String id, String dimension, String nameKey, String lockedHintKey,\n"
        "            String unlockAdvancement, String sigil, int colour) {\n"
        "    }\n"
        "\n"
        f"    public static final List<RealmExpectation> REALMS = {realms};\n"
        "\n"
        "    private GeneratedRealmExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
