"""Texts: translation key constants for Java (the texts themselves go into the lang files: lang.py)."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.text import text_constant, text_key

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every text of every group, by translation key."""
    return {
        text_key(entry.model.id, name): text
        for entry in content.texts
        for name, text in sorted(entry.model.texts.items())
    }


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    fields = "".join(
        f"    /** {{@code {text_key(entry.model.id, name)}}} from {{@code {entry.source}}}: {_comment(text)} */\n"
        f"    public static final String {text_constant(entry.model.id, name)} = "
        f"{string_literal(text_key(entry.model.id, name))};\n"
        "\n"
        for entry in content.texts
        for name, text in sorted(entry.model.texts.items())
    )
    source = (
        header(Path(__file__), "content/texts/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        "/** Translation keys of the texts in {@code content/texts/}, so Java names a text without typing its key. */\n"
        "public final class GeneratedTexts {\n" + fields + "    private GeneratedTexts() {\n"
        "    }\n"
        "}\n"
    )
    path = java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedTexts")
    return {path: source.encode("utf-8")}


def _comment(text: LocalizedText) -> str:
    """The English text, safe inside a Javadoc comment."""
    english = text.en_us.replace("*/", "*&#47;").replace("@", "&#64;")
    return f'"{english}"'
