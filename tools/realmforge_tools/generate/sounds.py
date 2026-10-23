"""Sounds: ``assets/realmforge/sounds.json``, the ``GeneratedSounds`` registrations and the subtitles (the lang files,
lang.py). Architecture 6.2, ADR 0007.

Each event of ``content/sounds/`` becomes a ``SoundEvent`` registered under its id and a ``sounds.json`` entry that
plays its vanilla sounds by reference (``"type": "event"``), with their own volume and pitch, which the game multiplies
with the vanilla event's own.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.sound import SoundRefDef, sound_path, subtitle_key

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
SOURCES = "content/sounds/"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    assets = paths.mod_generated / "resources" / "assets" / MOD_ID
    return {
        assets / "sounds.json": dump_json(sounds_json(content), sort_keys=True),
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedSounds"): _registrations(
            content
        ),
    }


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every event's subtitle, by its key ``subtitles.realmforge.<category>.<subject>.<name>``."""
    return {
        subtitle_key(entry.model, name): event.subtitle
        for entry in content.sounds
        for name, event in entry.model.events.items()
    }


def _sound(ref: SoundRefDef) -> dict[str, object]:
    sound: dict[str, object] = {"name": ref.event, "type": "event"}
    if ref.volume != 1:
        sound["volume"] = ref.volume
    if ref.pitch != 1:
        sound["pitch"] = ref.pitch
    return sound


def sounds_json(content: Content) -> dict[str, object]:
    """``sounds.json``: every event by its path, its subtitle key and the vanilla sounds it plays."""
    return {
        sound_path(entry.model, name): {
            "subtitle": subtitle_key(entry.model, name),
            "sounds": [_sound(ref) for ref in event.sounds],
        }
        for entry in content.sounds
        for name, event in entry.model.events.items()
    }


def _registrations(content: Content) -> bytes:
    events = sorted(
        ((entry, sound_path(entry.model, name)) for entry in content.sounds for name in entry.model.events),
        key=lambda event: event[1],
    )
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{path}}} from {{@code {entry.source}}}. */\n"
        f"    public static final DeferredHolder<SoundEvent, SoundEvent> {constant_name(path.replace('.', '_'))} =\n"
        f"            register({string_literal(path)});\n"
        "\n"
        for entry, path in events
    )
    every = ", ".join(constant_name(path.replace(".", "_")) for _, path in events)
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        "import java.util.List;\n"
        "import net.minecraft.core.registries.Registries;\n"
        "import net.minecraft.sounds.SoundEvent;\n"
        "import net.neoforged.bus.api.IEventBus;\n"
        "import net.neoforged.neoforge.registries.DeferredHolder;\n"
        "import net.neoforged.neoforge.registries.DeferredRegister;\n"
        "\n"
        "/**\n"
        " * Our sound events, generated from {@code content/sounds/} (architecture 6.2): each plays what\n"
        " * {@code assets/realmforge/sounds.json} names for it, with the range that its volume gives (vanilla's variable\n"
        " * range events).\n"
        " */\n"
        "public final class GeneratedSounds {\n"
        "    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =\n"
        "            DeferredRegister.create(Registries.SOUND_EVENT, Realmforge.MOD_ID);\n"
        "\n" + fields + "    /** Every event above, in id order. */\n"
        "    public static final List<DeferredHolder<SoundEvent, SoundEvent>> ALL =\n"
        f"            List.of({every});\n"
        "\n"
        "    private GeneratedSounds() {\n"
        "    }\n"
        "\n"
        "    /** Registers the sound events. Call once from the mod constructor. */\n"
        "    public static void register(IEventBus modEventBus) {\n"
        "        SOUND_EVENTS.register(modEventBus);\n"
        "    }\n"
        "\n"
        "    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {\n"
        "        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(Realmforge.id(path)));\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
