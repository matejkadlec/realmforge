"""Language files (``assets/realmforge/lang/<locale>.json``) from every translatable text in content."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate import datapack, spell_schools, stats, texts
from realmforge_tools.generate.items import translation_key
from realmforge_tools.project import MOD_ID, SUPPORTED_LOCALES, RepoPaths


def collect(content: Content) -> dict[str, dict[str, str]]:
    """locale -> {translation key -> text}, one entry per supported locale."""
    by_locale: dict[str, dict[str, str]] = {locale: {} for locale in SUPPORTED_LOCALES}
    for entry in content.items:
        for locale, text in entry.model.name.translations().items():
            by_locale[locale][translation_key(entry.model.id)] = text
    every = (
        datapack.translations(content)
        | stats.translations(content)
        | spell_schools.translations(content)
        | texts.translations(content)
    )
    for key, localized in every.items():
        for locale, text in localized.translations().items():
            by_locale[locale][key] = text
    return by_locale


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    lang_dir = paths.mod_generated / "resources" / "assets" / MOD_ID / "lang"
    return {
        lang_dir / f"{locale}.json": dump_json(keys, sort_keys=True)
        for locale, keys in collect(content).items()
    }
