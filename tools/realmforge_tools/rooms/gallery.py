"""The room gallery (A4e, ADR 0028): every room in a folder rendered headless, on one page, to keep or reject.

``rf rooms gallery [DIR]`` renders each ``*.nbt`` below ``DIR`` (default ``content/rooms/``; also a folder of
generated seed candidates) from four views into ``.cache/rooms/gallery/<folder>/`` and writes ``index.html``
there. The page shows each room's size and sidecar, and a Keep and a Reject choice. Its "Copy decisions" button
copies them as YAML; ``rf rooms review DIR`` merges that text into ``DIR/review.yaml``, the record that the import
(A4c) and the generator (A4d) act on: rejected imports are dropped, a kept candidate's seed is frozen. The page
starts from the decisions already in ``review.yaml``. A generated folder (the spike rooms) can be viewed but not
reviewed: ``rf gen`` would delete its ``review.yaml``.
"""

from __future__ import annotations

import hashlib
import html
import json
import shutil
from dataclasses import dataclass
from pathlib import Path

from pydantic import ValidationError

from realmforge_tools.art.gallery import PAGE_CSS, PAGE_FONTS
from realmforge_tools.project import RepoPaths
from realmforge_tools.rooms.render import CUT_HEIGHT, VIEWS, RoomRenderer
from realmforge_tools.rooms.structure import AIR, Structure, StructureError, read_structure
from realmforge_tools.schema.room import REVIEW_FILE, ReviewFile, RoomDef
from realmforge_tools.yamlio import YamlLoadError, load_yaml, load_yaml_text

DECISIONS = ("keep", "reject")
# Written into every gallery folder; a later run replaces only a folder that has it (or an empty one).
MARKER = ".room-gallery"


class GalleryError(ValueError):
    """A review file or decision text that cannot be read."""


@dataclass(frozen=True)
class GalleryRoom:
    key: str
    """The room's path below the gallery folder, without the suffix."""
    structure: Structure | None
    sidecar: RoomDef | None
    error: str | None = None


def room_files(directory: Path) -> dict[str, Path]:
    """Every template below ``directory``, by key."""
    return {
        file.relative_to(directory).with_suffix("").as_posix(): file
        for file in sorted(directory.rglob("*.nbt"))
        if file.is_file()
    }


def collect(directory: Path) -> list[GalleryRoom]:
    rooms: list[GalleryRoom] = []
    for key, file in room_files(directory).items():
        sidecar = _sidecar(file.with_suffix(".yaml"))
        try:
            rooms.append(GalleryRoom(key, read_structure(file.read_bytes()), sidecar))
        except (StructureError, OSError) as e:
            rooms.append(GalleryRoom(key, None, sidecar, str(e)))
    return rooms


def build(
    directory: Path, out_dir: Path, renderer: RoomRenderer, title: str, cut: int = CUT_HEIGHT
) -> tuple[Path, list[GalleryRoom]]:
    """Render every room and write the page. ``out_dir`` is replaced: it holds only this gallery. It must not
    overlap the rooms' folder, and an existing ``out_dir`` must be an earlier gallery (or empty), so the
    replacement never deletes anything else."""
    _check_out_dir(directory, out_dir)
    rooms = collect(directory)
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)
    (out_dir / MARKER).write_text("rf rooms gallery output; replaced on every run\n", encoding="utf-8")
    for room in rooms:
        if room.structure is None:
            continue
        for view in VIEWS:
            renderer.render(room.structure, view, out_dir / room.key / f"{view.name}.png", cut)
    page = out_dir / "index.html"
    page.write_text(render_page(title, rooms, read_review(directory), cut), encoding="utf-8")
    return page, rooms


def _check_out_dir(directory: Path, out_dir: Path) -> None:
    rooms, out = directory.resolve(), out_dir.resolve()
    if out == rooms or out.is_relative_to(rooms) or rooms.is_relative_to(out):
        raise GalleryError(f"the output folder {out_dir} overlaps the rooms in {directory}; choose another")
    if out.exists() and (not out.is_dir() or (any(out.iterdir()) and not (out / MARKER).exists())):
        raise GalleryError(
            f"{out_dir} exists and is not an earlier room gallery (no {MARKER}); choose an empty or new folder"
        )


def read_review(directory: Path) -> dict[str, str]:
    path = directory / REVIEW_FILE
    if not path.exists():
        return {}
    try:
        return parse_decisions(path.read_text(encoding="utf-8"), str(path))
    except UnicodeDecodeError as e:
        raise GalleryError(f"{path}: not UTF-8 text") from e


def parse_decisions(text: str, where: str) -> dict[str, str]:
    try:
        data = load_yaml_text(text)
    except YamlLoadError as e:
        raise GalleryError(f"{where}{f':{e.line}' if e.line else ''}: {e}") from e
    try:
        return ReviewFile.model_validate(data or {}).root
    except ValidationError as e:
        problems = "; ".join(f"{'.'.join(map(str, err['loc']))}: {err['msg']}" for err in e.errors())
        raise GalleryError(f"{where}: {problems}") from e


def merge_review(directory: Path, text: str, paths: RepoPaths) -> tuple[dict[str, str], int]:
    """Merge decisions (the page's YAML) into ``directory/review.yaml``. Returns all decisions and how many
    changed. Every key must name a room in the folder, and the folder must not be generated."""
    folder = directory.resolve()
    if any(folder.is_relative_to(root.resolve()) for root in paths.generated_roots()):
        raise GalleryError(
            f"{paths.rel(directory)} is generated: rf gen would delete a review.yaml there. View its rooms with "
            "rf rooms gallery; review copies of them in another folder"
        )
    decisions = parse_decisions(text, "the decisions")
    known = room_files(directory)
    unknown = sorted(key for key in decisions if key not in known)
    if unknown:
        raise GalleryError(f"no such room in {directory}: {', '.join(unknown)}")
    merged = read_review(directory)
    changed = sum(1 for key, value in decisions.items() if merged.get(key) != value)
    merged.update(decisions)
    (directory / REVIEW_FILE).write_text(review_text(merged), encoding="utf-8")
    return merged, changed


def review_text(decisions: dict[str, str]) -> str:
    lines = [
        "# Room gallery decisions (rf rooms gallery, then rf rooms review): keep or reject, by room path below",
        "# this folder. The import (A4c) drops rejected rooms; the generator (A4d) freezes a kept seed.",
    ]
    # Keys are quoted (as JSON strings, which YAML reads): a room named "hall #2" or "null" stays a plain name.
    lines += [f"{json.dumps(key, ensure_ascii=False)}: {decisions[key]}" for key in sorted(decisions)]
    return "\n".join(lines) + "\n"


def batch_id(rooms: list[GalleryRoom]) -> str:
    """A digest of the rooms' keys and contents; the page keeps unsent choices under it."""
    digest = hashlib.sha256()
    for room in rooms:
        digest.update(f"{room.key}\0".encode())
        if room.structure is not None:
            digest.update(repr((room.structure.size, room.structure.blocks)).encode())
    return digest.hexdigest()[:16]


def render_page(
    title: str, rooms: list[GalleryRoom], decisions: dict[str, str], cut: int = CUT_HEIGHT
) -> str:
    """The page. Deterministic: the same rooms and decisions give the same text."""
    esc = html.escape
    out = [
        f"<title>{esc(title)}</title>",
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">',
        PAGE_FONTS,
        f"<style>{PAGE_CSS}{_CSS}</style>",
        f'<main class="page" data-batch="{batch_id(rooms)}">',
        "<header>",
        f"<h1>{esc(title)}</h1>",
        f'<p class="intro">{len(rooms)} room(s). Keep or reject each one, then copy your decisions into the '
        "session: <code>rf rooms review</code> records them. Views: outside, cut "
        f"{cut} blocks above the bottom (or below the top layer of a lower room) from two corners, and a plan of the "
        "cut.</p>",
        "</header>",
        '<div class="rooms">',
    ]
    for room in rooms:
        out.append(_card(room, decisions.get(room.key)))
    out += [
        "</div>",
        '<section class="picks"><h2>Your decisions</h2>',
        '<p class="muted">Paste them into the session; they merge into the folder\'s review.yaml.</p>',
        '<pre id="picks-text">No decisions yet.</pre>',
        '<button type="button" id="copy-picks">Copy decisions</button> <span id="copy-status"></span>',
        "</section></main>",
        f"<script>{_JS}</script>",
    ]
    return "\n".join(out) + "\n"


def _card(room: GalleryRoom, decision: str | None) -> str:
    esc = html.escape
    parts = [
        f'<article class="card room" data-key="{esc(room.key)}">',
        f"<h3><code>{esc(room.key)}</code></h3>",
    ]
    if room.structure is None:
        parts += [f'<p class="error">Cannot read the template: {esc(room.error or "")}</p>', "</article>"]
        return "".join(parts)
    structure = room.structure
    solid = sum(1 for block in structure.blocks if block.state.name != AIR)
    facts = [
        " x ".join(str(axis) for axis in structure.size),
        f"{solid} blocks",
        f"{len(structure.entities)} entities",
        f"data version {structure.data_version if structure.data_version is not None else 'unknown'}",
    ]
    if room.sidecar is not None:
        if room.sidecar.source is not None:
            facts.append(f"from {room.sidecar.source.file}")
        facts.append(f"pools: {', '.join(room.sidecar.pools) or 'none'}")
        facts.append(f"tags: {', '.join(room.sidecar.tags) or 'none'}")
    parts.append(f'<p class="stats">{esc(" · ".join(facts))}</p>')
    parts.append('<div class="views">')
    for view in VIEWS:
        src = f"{room.key}/{view.name}.png"
        parts.append(
            f'<figure><img src="{esc(src)}" width="256" height="256" loading="lazy" alt="{esc(view.label)}">'
            f"<figcaption>{esc(view.label)}</figcaption></figure>"
        )
    parts.append('</div><div class="decision">')
    for value in DECISIONS:
        checked = " checked" if decision == value else ""
        parts.append(
            f'<label class="pick"><input type="radio" name="review-{esc(room.key)}" value="{value}"{checked}> '
            f"{value.capitalize()}</label>"
        )
    parts.append("</div></article>")
    return "".join(parts)


def _sidecar(path: Path) -> RoomDef | None:
    if not path.exists():
        return None
    try:
        return RoomDef.model_validate(load_yaml(path) or {})
    except (YamlLoadError, ValidationError):
        return None  # rf validate reports it; the gallery still shows the room


_CSS = """
h1, h3 code { overflow-wrap: anywhere; }
.rooms { display: grid; grid-template-columns: minmax(0, 1fr); gap: 16px; }
.room .views figure { flex: 1 1 140px; max-width: 256px; min-width: 0; }
.room .views img { width: 100%; height: auto; background: var(--slot); border-radius: 4px; }
.room .decision { display: flex; gap: 16px; }
.room[data-decision="keep"] { border-color: #3c9a5f; box-shadow: inset 4px 0 0 #3c9a5f; }
.room[data-decision="reject"] { border-color: #c4473a; box-shadow: inset 4px 0 0 #c4473a; opacity: 0.8; }
.error { color: #c4473a; margin: 0; }
"""

# Starts from the decisions in review.yaml (checked radios), then any unsent choices kept in localStorage.
_JS = """
(() => {
  const KEY = "realmforge-room-review:" + document.querySelector("main").dataset.batch;
  const radios = [...document.querySelectorAll('.decision input[type="radio"]')];
  const text = document.getElementById("picks-text");
  const status = document.getElementById("copy-status");
  const keyOf = (r) => r.name.slice("review-".length);
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { saved = {}; }
  for (const r of radios) if (saved[keyOf(r)] === r.value) r.checked = true;
  const update = () => {
    const picks = {};
    for (const r of radios) if (r.checked) picks[keyOf(r)] = r.value;
    for (const card of document.querySelectorAll(".room")) {
      const value = picks[card.dataset.key];
      if (value) card.dataset.decision = value; else delete card.dataset.decision;
    }
    const lines = Object.keys(picks).sort().map((k) => JSON.stringify(k) + ": " + picks[k]);
    text.textContent = lines.length ? lines.join("\\n") : "No decisions yet.";
    try { localStorage.setItem(KEY, JSON.stringify(picks)); } catch (e) { /* the text still shows */ }
  };
  for (const r of radios) r.addEventListener("change", update);
  update();
  const selectText = () => {
    const range = document.createRange();
    range.selectNodeContents(text);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    status.textContent = "Selected; press Ctrl+C";
  };
  document.getElementById("copy-picks").addEventListener("click", () => {
    // navigator.clipboard is missing outside secure contexts (a file:// page); the call then throws.
    try {
      navigator.clipboard.writeText(text.textContent).then(() => { status.textContent = "Copied"; }, selectText);
    } catch (e) {
      selectText();
    }
  });
})();
"""
