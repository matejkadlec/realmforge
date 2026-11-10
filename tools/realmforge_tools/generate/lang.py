"""Language files (``assets/realmforge/lang/<locale>.json``) from every translatable text in content."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate import (
    apotheosis,
    blocks,
    datapack,
    dungeons,
    effects,
    enchantments,
    instance_biomes,
    mobs,
    sounds,
    spell_schools,
    stats,
    texts,
)
from realmforge_tools.generate.items import flavor_key, translation_key
from realmforge_tools.project import MOD_ID, SUPPORTED_LOCALES, RepoPaths
from realmforge_tools.schema.coin import plural_key
from realmforge_tools.schema.merchant import profession_key


def collect(content: Content) -> dict[str, dict[str, str]]:
    """locale -> {translation key -> text}, one entry per supported locale."""
    by_locale: dict[str, dict[str, str]] = {locale: {} for locale in SUPPORTED_LOCALES}
    for entry in content.items:
        for locale, text in entry.model.name.translations().items():
            by_locale[locale][translation_key(entry.model.id)] = text
        if entry.model.flavor is not None:
            for locale, text in entry.model.flavor.translations().items():
                by_locale[locale][flavor_key(entry.model.id)] = text
        if entry.model.coin is not None:
            for locale, text in entry.model.coin.plural.translations().items():
                by_locale[locale][plural_key(entry.model.id)] = text
    for entry in content.merchants:
        for locale, text in entry.model.name.translations().items():
            by_locale[locale][profession_key(entry.model.id)] = text
    for entry in content.blocks:
        for locale, text in entry.model.name.translations().items():
            by_locale[locale][blocks.translation_key(entry.model.id)] = text
    every = (
        datapack.translations(content)
        | stats.translations(content)
        | effects.translations(content)
        | enchantments.translations(content)
        | apotheosis.translations(content)
        | mobs.translations(content)
        | sounds.translations(content)
        | spell_schools.translations(content)
        | texts.translations(content)
        | dungeons.translations(content)
        | instance_biomes.translations(content)
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
