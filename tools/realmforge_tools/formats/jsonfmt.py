"""Canonical JSON output: 2-space indent, UTF-8 (no ASCII escaping), trailing newline."""

from __future__ import annotations

import json
from typing import Any


def dump_json(data: Any, *, sort_keys: bool = False) -> bytes:
    return (json.dumps(data, indent=2, ensure_ascii=False, sort_keys=sort_keys) + "\n").encode("utf-8")
