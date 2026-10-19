"""Seed candidates of the room specs for the gallery review, and freezing the review's decisions (A4d, ADR 0028).

``rf rooms candidates [SPEC...]`` builds the next ``count`` seeds of each spec that no review decided on yet
(``generator.candidate_seeds``) into ``.cache/rooms/candidates/<spec>/seed_<n>.nbt``, each with a sidecar, and
leaves out a candidate validation refuses (it reports why). ``rf rooms gallery .cache/rooms/candidates`` shows them,
and ``rf rooms review .cache/rooms/candidates`` records the decisions there and freezes them into the specs: a kept
seed joins the spec's ``seeds`` (``rf gen`` then ships it as ``realmforge:rooms/generated/<spec>/seed_<n>``), a
rejected one its ``rejected_seeds``, so the next candidates are new seeds.

Freezing rewrites only the spec's ``seeds:`` and ``rejected_seeds:`` lines (as flow lists, ``seeds: [3, 7]``) and
keeps the rest of the file as written.
"""

from __future__ import annotations

import re
import shutil
from dataclasses import dataclass, field
from pathlib import Path

from pydantic import ValidationError

from realmforge_tools.content import Content
from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.rooms.check import check_structure
from realmforge_tools.rooms.generator import (
    RoomGeneratorError,
    candidate_name,
    candidate_seeds,
    generate_room,
    spec_tags,
)
from realmforge_tools.rooms.structure import write_structure
from realmforge_tools.rooms.walk import walk_problems
from realmforge_tools.schema.room_spec import RoomSpecDef
from realmforge_tools.yamlio import YamlLoadError, load_yaml_text

# Written into every candidates folder: it marks the folder whose review freezes seeds, and lets a later run
# replace it.
MARKER = ".room-candidates"
DEFAULT_COUNT = 4
_KEY = re.compile(r"^(?P<spec>[a-z][a-z0-9_]*)/seed_(?P<seed>[1-9][0-9]*)$")


class CandidatesError(ValueError):
    """Candidates that cannot be written, or decisions that cannot be frozen."""


@dataclass
class SpecCandidates:
    spec: str
    written: list[int] = field(default_factory=list)
    refused: list[tuple[int, str]] = field(default_factory=list)


def write_candidates(
    content: Content,
    universe: IdUniverse,
    out_dir: Path,
    count: int,
    spec_ids: list[str] | None = None,
    *,
    kept: bool = False,
    broken: dict[tuple[str, int], list[str]] | None = None,
) -> list[SpecCandidates]:
    """Build the candidates into ``out_dir``, which is replaced: it must be new, empty or an earlier candidates
    folder. With ``kept`` the specs' kept seeds come too, so a change to the generator can be reviewed on the rooms
    it changes; a reject decision on one moves it to ``rejected_seeds``. A kept seed in ``broken`` (the validation
    problems ``load_and_validate`` set aside, by spec and seed) is refused with them."""
    specs = {entry.model.id: entry.model for entry in content.room_specs}
    unknown = sorted(set(spec_ids or ()) - specs.keys())
    if unknown:
        raise CandidatesError(f"no room spec {', '.join(unknown)} in content/room_specs/")
    if out_dir.exists() and (
        not out_dir.is_dir() or (any(out_dir.iterdir()) and not (out_dir / MARKER).exists())
    ):
        raise CandidatesError(f"{out_dir} exists and is not an earlier candidates folder (no {MARKER})")
    themes = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.room_themes}
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)
    (out_dir / MARKER).write_text(
        "rf rooms candidates output; replaced on every run. rf rooms review here freezes the decisions into "
        "content/room_specs/.\n",
        encoding="utf-8",
    )
    results = []
    for spec_id in spec_ids or sorted(specs):
        spec = specs[spec_id]
        result = SpecCandidates(spec_id)
        seeds = (sorted(spec.seeds) if kept else []) + candidate_seeds(spec, count)
        for seed in seeds:
            if (spec_id, seed) in (broken or {}):
                result.refused.append((seed, "; ".join((broken or {})[spec_id, seed])))
                continue
            try:
                structure = generate_room(spec, themes[spec.theme], seed)
            except RoomGeneratorError as e:
                result.refused.append((seed, str(e)))
                continue
            problems = check_structure(structure, universe) + walk_problems(structure)
            if problems:
                result.refused.append((seed, "; ".join(f"{where}: {message}" for where, message in problems)))
                continue
            folder = out_dir / spec_id
            folder.mkdir(exist_ok=True)
            (folder / f"{candidate_name(seed)}.nbt").write_bytes(write_structure(structure))
            (folder / f"{candidate_name(seed)}.yaml").write_text(_sidecar(spec), encoding="utf-8")
            result.written.append(seed)
        results.append(result)
    return results


def _sidecar(spec: RoomSpecDef) -> str:
    lines = [
        f"# A candidate of content/room_specs/{spec.id}.yaml (rf rooms candidates): keep or reject it in the gallery.",
        f"pools: [{', '.join(spec.pools)}]",
        f"tags: [{', '.join(spec_tags(spec))}]",
        f"weight: {spec.weight}",
    ]
    return "\n".join(lines) + "\n"


def is_candidates_folder(directory: Path) -> bool:
    return (directory / MARKER).is_file()


def kept_keys(paths: RepoPaths) -> set[str]:
    """``<spec>/seed_<n>`` of every kept seed in ``content/room_specs/``: a review of a candidates folder may decide on
    them even when ``write_candidates`` refused to build one (a kept seed a generator change broke)."""
    keys = set()
    for path in sorted((paths.content / "room_specs").glob("*.yaml")):
        spec = _parse(path.read_text(encoding="utf-8"), path)
        keys |= {f"{spec.id}/{candidate_name(seed)}" for seed in spec.seeds}
    return keys


@dataclass(frozen=True)
class Frozen:
    spec: str
    kept: tuple[int, ...]
    rejected: tuple[int, ...]


def freeze(decisions: dict[str, str], paths: RepoPaths) -> list[Frozen]:
    """Write the decisions on candidates (``<spec>/seed_<n>``: keep or reject) into their specs' ``seeds`` and
    ``rejected_seeds``. A later decision on a seed replaces an earlier one. Returns what changed per spec."""
    by_spec: dict[str, dict[int, str]] = {}
    for key, decision in decisions.items():
        match = _KEY.match(key)
        if match is None:
            raise CandidatesError(f"{key!r} names no candidate (<spec>/seed_<n>)")
        by_spec.setdefault(match["spec"], {})[int(match["seed"])] = decision
    folder = paths.content / "room_specs"
    missing = sorted(spec for spec in by_spec if not (folder / f"{spec}.yaml").is_file())
    if missing:
        raise CandidatesError(f"no room spec {', '.join(missing)} in content/room_specs/")
    updates: list[tuple[Path, str, Frozen]] = []
    for spec_id, chosen in sorted(by_spec.items()):
        path = folder / f"{spec_id}.yaml"
        text = path.read_text(encoding="utf-8")
        spec = _parse(text, path)
        seeds, rejected = set(spec.seeds), set(spec.rejected_seeds)
        for seed, decision in chosen.items():
            (seeds if decision == "keep" else rejected).add(seed)
            (rejected if decision == "keep" else seeds).discard(seed)
        new_text = _set_list(
            _set_list(text, "seeds", sorted(seeds), path), "rejected_seeds", sorted(rejected), path
        )
        _parse(new_text, path)
        if new_text != text:
            kept = tuple(sorted(seeds - set(spec.seeds)))
            dropped = tuple(sorted(rejected - set(spec.rejected_seeds)))
            updates.append((path, new_text, Frozen(spec_id, kept, dropped)))
    # Every spec is checked before any is written, so a bad decision leaves all specs as they were.
    for path, new_text, _ in updates:
        path.write_text(new_text, encoding="utf-8")
    return [frozen for _, _, frozen in updates]


def _parse(text: str, path: Path) -> RoomSpecDef:
    try:
        return RoomSpecDef.model_validate(load_yaml_text(text))
    except (YamlLoadError, ValidationError) as e:
        raise CandidatesError(f"{path}: {e}") from e


def _set_list(text: str, key: str, values: list[int], path: Path) -> str:
    """``text`` with its top-level ``key:`` line holding ``values`` as a flow list (appended when missing)."""
    line = f"{key}: [{', '.join(map(str, values))}]"
    pattern = re.compile(rf"^{key}:(?P<rest>.*)$", re.MULTILINE)
    match = pattern.search(text)
    if match is None:
        return text.rstrip("\n") + f"\n{line}\n"
    rest = match["rest"].split("#", 1)[0].strip()
    if not (rest.startswith("[") and rest.endswith("]")):
        raise CandidatesError(
            f"{path}: write {key} as a flow list on one line ({key}: [1, 2]) so it can be updated"
        )
    return text[: match.start()] + line + text[match.end() :]
