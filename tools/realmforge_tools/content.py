"""Loading ``content/`` into validated models (step 1 of validation: schema)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from pydantic import BaseModel, ValidationError

from realmforge_tools.problems import Problem, ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema import CONTENT_TYPES, ItemDef
from realmforge_tools.yamlio import YamlLoadError, load_yaml

# Files allowed in content/ besides YAML entries.
_ALLOWED_OTHER_FILES = {"README.md"}


@dataclass(frozen=True)
class Entry[M: BaseModel]:
    """A content entry and the repository-relative file it came from."""

    source: str
    model: M


@dataclass(frozen=True)
class Content:
    """All content, sorted by id within each type."""

    items: tuple[Entry[ItemDef], ...]


def load_content(paths: RepoPaths) -> Content:
    """Load and schema-validate every file in ``content/``. Raises ValidationFailed listing all problems."""
    problems: list[Problem] = []
    loaded: dict[str, list[Entry]] = {name: [] for name in CONTENT_TYPES}

    for file in sorted(p for p in paths.content.rglob("*") if p.is_file()):
        rel = paths.rel(file)
        type_dir = file.relative_to(paths.content).parts[0]
        if file.name in _ALLOWED_OTHER_FILES:
            continue
        if file.suffix == ".yml":
            problems.append(Problem(rel, "", "use the .yaml extension, not .yml"))
            continue
        if file.suffix != ".yaml":
            problems.append(Problem(rel, "", "unexpected file in content/ (only .yaml entries are allowed)"))
            continue
        if type_dir not in CONTENT_TYPES or file.parent.parent != paths.content:
            known = ", ".join(f"content/{name}/" for name in CONTENT_TYPES)
            problems.append(Problem(rel, "", f"unknown content location; entries go directly into {known}"))
            continue

        entry = _load_entry(file, rel, CONTENT_TYPES[type_dir], problems)
        if entry is not None:
            loaded[type_dir].append(entry)

    if problems:
        raise ValidationFailed(problems)

    def by_id(entries: list[Entry]) -> tuple[Entry, ...]:
        return tuple(sorted(entries, key=lambda e: (e.model.id, e.source)))

    return Content(items=by_id(loaded["items"]))


def _load_entry(file: Path, rel: str, model: type[BaseModel], problems: list[Problem]) -> Entry | None:
    try:
        data = load_yaml(file)
    except YamlLoadError as e:
        problems.append(Problem(rel, f"line {e.line}" if e.line else "", str(e)))
        return None
    if not isinstance(data, dict):
        problems.append(Problem(rel, "", "expected a mapping at the top level"))
        return None
    try:
        return Entry(rel, model.model_validate(data))
    except ValidationError as e:
        for err in e.errors():
            location = ".".join(str(part) for part in err["loc"])
            problems.append(Problem(rel, location, err["msg"]))
        return None
