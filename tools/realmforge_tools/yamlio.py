"""YAML loading for content files.

Uses ruamel.yaml in pure safe mode: YAML 1.2 semantics (``no``/``on`` stay strings) and duplicate keys are errors.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, TextIO

from ruamel.yaml import YAML
from ruamel.yaml.error import MarkedYAMLError, YAMLError


class YamlLoadError(Exception):
    """A YAML file could not be parsed. ``line`` is 1-based, if known."""

    def __init__(self, message: str, line: int | None = None) -> None:
        self.line = line
        super().__init__(message)


def load_yaml(path: Path) -> Any:
    with path.open(encoding="utf-8") as f:
        return load_yaml_text(f)


def load_yaml_text(text: str | TextIO) -> Any:
    """Parse YAML from a string or an open text stream, with the same rules and errors as ``load_yaml``."""
    yaml = YAML(typ="safe", pure=True)
    try:
        return yaml.load(text)
    except MarkedYAMLError as e:
        mark = e.problem_mark or e.context_mark
        line = mark.line + 1 if mark is not None else None
        problem = e.problem or e.context or "invalid YAML"
        raise YamlLoadError(f"invalid YAML: {problem}", line) from e
    except YAMLError as e:
        raise YamlLoadError(f"invalid YAML: {e}") from e
