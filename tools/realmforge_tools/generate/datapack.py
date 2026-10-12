"""Datapack registry entries: ``content/<type>/<id>.yaml`` -> ``data/realmforge/realmforge/<registry>/<id>.json``.

One generic conversion for every type in ``DATAPACK_TYPES`` (architecture 6.3):

- the ``id`` becomes the file name, never a field (the entry is ``realmforge:<id>``);
- a ``LocalizedText`` becomes its translation key, ``<registry>.realmforge.<id>.<field path>``, and its texts go
  into the lang files (``lang.py``);
- human units in YAML become game units in data, by field name: ``<name>_seconds`` becomes ``<name>_ticks``
  (whole ticks) and ``<name>_percent`` becomes ``<name>`` (a fraction);
- unset optional fields are left out; everything else keeps its YAML name and value.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from pydantic import BaseModel

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema import DATAPACK_TYPES
from realmforge_tools.schema.common import TICKS_PER_SECOND, LocalizedText

_SECONDS = "_seconds"
_PERCENT = "_percent"


def translation_key(registry: str, entry_id: str, field_path: str) -> str:
    return f"{registry}.{MOD_ID}.{entry_id}.{field_path}"


def seconds_to_ticks(seconds: float) -> int:
    """Whole game ticks; content types validate seconds as multiples of a tick (``common.Seconds``)."""
    return round(seconds * TICKS_PER_SECOND)


def percent_to_fraction(percent: float) -> float:
    return percent / 100


def game_field(name: str, value: Any) -> tuple[str, Any]:
    """A YAML field name and value in game units: ``cooldown_seconds: 8`` -> ``cooldown_ticks: 160``."""
    if name.endswith(_SECONDS) and isinstance(value, int | float):
        return name.removesuffix(_SECONDS) + "_ticks", seconds_to_ticks(value)
    if name.endswith(_PERCENT) and isinstance(value, int | float):
        return name.removesuffix(_PERCENT), percent_to_fraction(value)
    return name, value


def entry_data(model: BaseModel, registry: str) -> tuple[dict[str, Any], dict[str, LocalizedText]]:
    """The JSON object of one entry, and its texts by translation key."""
    texts: dict[str, LocalizedText] = {}
    entry_id = model.id

    def convert(value: Any, path: str) -> Any:
        if isinstance(value, LocalizedText):
            key = translation_key(registry, entry_id, path)
            texts[key] = value
            return key
        if isinstance(value, BaseModel):
            return convert_fields(value, path)
        if isinstance(value, list | tuple):
            return [convert(item, f"{path}.{i}") for i, item in enumerate(value)]
        return value

    def convert_fields(value: BaseModel, path: str) -> dict[str, Any]:
        out: dict[str, Any] = {}
        for name in type(value).model_fields:
            field_value = getattr(value, name)
            if field_value is None or (value is model and name == "id"):
                continue
            out_name, out_value = game_field(name, convert(field_value, f"{path}.{name}" if path else name))
            if out_name in out:
                raise ValueError(
                    f"{type(value).__name__}: fields {name!r} and {out_name!r} both become {out_name!r}"
                )
            out[out_name] = out_value
        return out

    return convert_fields(model, ""), texts


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every text of every datapack entry, by translation key."""
    texts: dict[str, LocalizedText] = {}
    for data_type in DATAPACK_TYPES:
        for entry in content.entries(data_type.directory):
            texts.update(entry_data(entry.model, data_type.registry)[1])
    return texts


def data_dir(paths: RepoPaths) -> Path:
    return paths.mod_generated / "resources" / "data" / MOD_ID


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for data_type in DATAPACK_TYPES:
        registry_dir = data_dir(paths) / MOD_ID / data_type.registry
        for entry in content.entries(data_type.directory):
            data, _ = entry_data(entry.model, data_type.registry)
            files[registry_dir / f"{entry.model.id}.json"] = dump_json(data)
    return files
