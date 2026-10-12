"""Review gallery (ADR 0007, texture pipeline step 4): every candidate texture before and after cleanup.

Input is a directory of candidates, ``<dir>/<subject>/<candidate>.png`` (``candidates/sword/rd-fast.png``), plus
an optional ``<dir>/gallery.yaml`` with a title, labels for the candidates and a prompt per subject. Output is
one self-contained HTML page: the images are embedded, so it opens offline and can be shared as is.
"""

from __future__ import annotations

import base64
import hashlib
import html
from dataclasses import dataclass
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from realmforge_tools.art.palette import Palette
from realmforge_tools.art.texture import CleanReport, Image, clean
from realmforge_tools.formats.png import decode_rgba
from realmforge_tools.yamlio import YamlLoadError, load_yaml

GALLERY_FILE = "gallery.yaml"


class GalleryError(ValueError):
    """A ``gallery.yaml`` that cannot be read."""


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class CandidateInfo(_Strict):
    label: str = Field(description="Shown instead of the file name, e.g. 'Retro Diffusion Fast'.")
    note: str = Field(default="", description="Model, settings and cost.")


class SubjectInfo(_Strict):
    title: str
    prompt: str = ""


class GalleryInfo(_Strict):
    title: str = "Texture candidates"
    intro: str = ""
    candidates: dict[str, CandidateInfo] = Field(default_factory=dict)
    subjects: dict[str, SubjectInfo] = Field(default_factory=dict)


@dataclass(frozen=True)
class Candidate:
    name: str
    raw: Image
    cleaned: Image
    report: CleanReport


@dataclass(frozen=True)
class Subject:
    name: str
    candidates: tuple[Candidate, ...]


def collect(directory: Path, palette: Palette, size: int) -> tuple[GalleryInfo, tuple[Subject, ...]]:
    """Read and clean every candidate under ``directory``.

    Subjects and candidates come in the order ``gallery.yaml`` lists them, then the unlisted ones by name.
    """
    info = _load_info(directory / GALLERY_FILE)
    subjects: list[Subject] = []
    subject_dirs = [p for p in directory.iterdir() if p.is_dir()]
    for subject_dir in sorted(subject_dirs, key=lambda p: _rank(p.name, info.subjects)):
        candidates: list[Candidate] = []
        for png in sorted(subject_dir.glob("*.png"), key=lambda p: _rank(p.stem, info.candidates)):
            width, height, pixels = decode_rgba(png.read_bytes())
            raw = Image(width, height, tuple(pixels))
            cleaned, report = clean(raw, palette, size)
            candidates.append(Candidate(png.stem, raw, cleaned, report))
        if candidates:
            subjects.append(Subject(subject_dir.name, tuple(candidates)))
    return info, tuple(subjects)


def _rank(name: str, listed: dict[str, object]) -> tuple[int, str]:
    order = list(listed)
    return (order.index(name), "") if name in listed else (len(order), name)


def _load_info(path: Path) -> GalleryInfo:
    if not path.exists():
        return GalleryInfo()
    try:
        return GalleryInfo.model_validate(load_yaml(path) or {})
    except YamlLoadError as e:
        where = f":{e.line}" if e.line else ""
        raise GalleryError(f"{path}{where}: {e}") from e
    except ValidationError as e:
        problems = "; ".join(f"{'.'.join(map(str, err['loc']))}: {err['msg']}" for err in e.errors())
        raise GalleryError(f"{path}: {problems}") from e


def render(info: GalleryInfo, subjects: tuple[Subject, ...], palette: Palette) -> str:
    """The gallery page. Deterministic: the same inputs give the same text."""
    esc = html.escape
    out: list[str] = [
        f"<title>{esc(info.title)}</title>",
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">',
        _FONTS,
        f"<style>{_CSS}</style>",
        f'<main class="page" data-batch="{batch_id(subjects)}">',
        "<header>",
        f"<h1>{esc(info.title)}</h1>",
    ]
    if info.intro:
        out.append(f'<p class="intro">{esc(info.intro)}</p>')
    out.append('<div class="legend">')
    names = sorted({c.name for s in subjects for c in s.candidates}, key=lambda n: _rank(n, info.candidates))
    for name in names:
        meta = info.candidates.get(name)
        label = meta.label if meta else name
        note = f' <span class="muted">{esc(meta.note)}</span>' if meta and meta.note else ""
        out.append(f"<p><b>{esc(label)}</b> <code>{esc(name)}</code>{note}</p>")
    out.append("</div>")
    out.append(f'<h2 class="small">Palette: {esc(palette.name)}, {len(palette.colors)} colors</h2>')
    out.append('<div class="palette">')
    for color in palette.colors:
        out.append(
            f'<span class="swatch" style="background:{color.hex}" title="{esc(color.name)} {color.hex}"></span>'
        )
    out.append("</div></header>")

    for subject in subjects:
        meta = info.subjects.get(subject.name)
        title = meta.title if meta else subject.name
        out.append(f'<section id="{esc(subject.name)}">')
        out.append(f"<h2>{esc(title)} <code>{esc(subject.name)}</code></h2>")
        if meta and meta.prompt:
            out.append(f'<p class="prompt">{esc(meta.prompt)}</p>')
        out.append('<div class="cards">')
        for candidate in subject.candidates:
            out.append(_card(subject.name, candidate, info))
        out.append("</div></section>")

    out.append('<section class="picks"><h2>Your picks</h2>')
    out.append('<p class="muted">Pick a favorite per texture above, then copy the list into the session.</p>')
    out.append('<pre id="picks-text">No picks yet.</pre>')
    out.append('<button type="button" id="copy-picks">Copy picks</button> <span id="copy-status"></span>')
    out.append("</section></main>")
    out.append(f"<script>{_JS}</script>")
    return "\n".join(out) + "\n"


def batch_id(subjects: tuple[Subject, ...]) -> str:
    """A short digest of the candidates' names and pixels. The page keeps a reviewer's picks under it, so a
    gallery of other candidates never shows picks made for these."""
    digest = hashlib.sha256()
    for subject in subjects:
        for candidate in subject.candidates:
            digest.update(f"{subject.name}/{candidate.name}\0".encode())
            digest.update(candidate.raw.png())
    return digest.hexdigest()[:16]


def _card(subject: str, candidate: Candidate, info: GalleryInfo) -> str:
    esc = html.escape
    meta = info.candidates.get(candidate.name)
    label = meta.label if meta else candidate.name
    report = candidate.report
    scale = f", {report.scale}x grid" if report.scale > 1 else ""
    stats = (
        f"{report.colors_before} → {report.colors_after} colors, "
        f"{report.changed_pixels} of {len(candidate.cleaned.pixels)} pixels recolored{scale}"
    )
    parts = [
        '<article class="card">',
        f"<h3>{esc(label)}</h3>",
        '<div class="views">',
        _figure("Generated", candidate.raw),
        _figure("Cleaned", candidate.cleaned),
    ]
    if candidate.cleaned.is_opaque():
        parts.append(_figure("Tiled 3x3", candidate.cleaned.tiled(3), tiled=True))
    parts += [
        "</div>",
        f'<p class="stats">{esc(stats)}</p>',
        f'<label class="pick"><input type="radio" name="pick-{esc(subject)}" '
        f'id="pick-{esc(subject)}-{esc(candidate.name)}" value="{esc(candidate.name)}"> Pick</label>',
        "</article>",
    ]
    return "".join(parts)


def _figure(caption: str, image: Image, tiled: bool = False) -> str:
    data = base64.b64encode(image.png()).decode("ascii")
    cls = "px tiled" if tiled else "px"
    return (
        f'<figure><img class="{cls}" src="data:image/png;base64,{data}" '
        f'width="{image.width}" height="{image.height}" alt="{html.escape(caption)}">'
        f"<figcaption>{html.escape(caption)}</figcaption></figure>"
    )


_FONTS = (
    '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@500'
    '&family=Source+Sans+3:wght@400;600&family=JetBrains+Mono:wght@400&display=swap">'
)

# Layout: a header with the legend and palette, then one section per texture with a wrapping row of cards.
_CSS = """
:root {
  --bg: #eef1f4; --surface: #ffffff; --fg: #1c2330; --muted: #5b6676; --line: #d5dbe3;
  --accent: #2f6fb5; --slot: #8e939b; --slot-edge: #5d6168;
  --display: "Pixelify Sans", "Trebuchet MS", sans-serif;
  --body: "Source Sans 3", "Segoe UI", system-ui, sans-serif;
  --mono: "JetBrains Mono", ui-monospace, "Cascadia Mono", monospace;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #12161d; --surface: #1b212b; --fg: #e3e8ef; --muted: #98a3b3; --line: #2c3542;
    --accent: #7db4ec; --slot: #4a4f57; --slot-edge: #2a2d33; color-scheme: dark;
  }
}
:root[data-theme="dark"] {
  --bg: #12161d; --surface: #1b212b; --fg: #e3e8ef; --muted: #98a3b3; --line: #2c3542;
  --accent: #7db4ec; --slot: #4a4f57; --slot-edge: #2a2d33; color-scheme: dark;
}
body { background: var(--bg); color: var(--fg); font: 16px/1.5 var(--body); margin: 0; }
.page { max-width: 72rem; margin: 0 auto; padding-inline: 16px; padding-block: 24px 48px;
  display: grid; gap: 32px; }
header { display: grid; gap: 12px; }
h1 { font: 500 2rem/1.1 var(--display); margin: 0; letter-spacing: 0.02em; text-wrap: balance; }
h2 { font-size: 1.25rem; margin: 0; text-wrap: balance; }
h2.small { font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
h3 { font-size: 1rem; margin: 0; }
code, .stats, pre { font-family: var(--mono); font-size: 0.8rem; }
h2 code { color: var(--muted); font-weight: 400; }
.intro, .prompt { max-width: 65ch; margin: 0; }
.prompt { color: var(--muted); font-style: italic; }
.muted { color: var(--muted); }
.legend { display: grid; gap: 2px; }
.legend p { margin: 0; }
.palette { display: flex; flex-wrap: wrap; gap: 2px; }
.swatch { width: 18px; height: 18px; border-radius: 2px; outline: 1px solid var(--line); }
section { display: grid; gap: 10px; }
.cards { display: flex; flex-wrap: wrap; gap: 12px; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: 6px; padding: 12px;
  display: grid; gap: 8px; min-width: 0; }
.views { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-start; }
figure { margin: 0; display: grid; gap: 4px; justify-items: center; }
figcaption { font-size: 0.75rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.06em; }
img.px { width: 128px; height: auto; image-rendering: pixelated; background: var(--slot);
  box-shadow: inset 0 0 0 2px var(--slot-edge); max-width: 100%; }
img.px.tiled { width: 192px; }
.stats { margin: 0; color: var(--muted); }
.pick { display: flex; gap: 6px; align-items: center; font-weight: 600; cursor: pointer; }
.pick input { accent-color: var(--accent); width: 18px; height: 18px; }
.picks pre { background: var(--surface); border: 1px solid var(--line); border-radius: 6px; padding: 12px;
  margin: 0; overflow-x: auto; white-space: pre-wrap; }
button { font: 600 0.95rem var(--body); color: var(--bg); background: var(--accent); border: 0;
  border-radius: 4px; padding: 8px 14px; cursor: pointer; justify-self: start; }
button:focus-visible, .pick input:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
"""

_JS = """
(() => {
  const KEY = "realmforge-gallery-picks:" + document.querySelector("main").dataset.batch;
  const radios = [...document.querySelectorAll('.pick input[type="radio"]')];
  const text = document.getElementById("picks-text");
  const status = document.getElementById("copy-status");
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { saved = {}; }
  for (const r of radios) {
    const subject = r.name.slice("pick-".length);
    if (saved[subject] === r.value) r.checked = true;
  }
  const update = () => {
    const picks = {};
    for (const r of radios) if (r.checked) picks[r.name.slice("pick-".length)] = r.value;
    const lines = Object.keys(picks).sort().map((s) => s + ": " + picks[s]);
    text.textContent = lines.length ? lines.join("\\n") : "No picks yet.";
    try { localStorage.setItem(KEY, JSON.stringify(picks)); } catch (e) { /* picks still show */ }
  };
  for (const r of radios) r.addEventListener("change", update);
  update();
  const selectPicks = () => {
    const range = document.createRange();
    range.selectNodeContents(text);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    status.textContent = "Selected; press Ctrl+C";
  };
  document.getElementById("copy-picks").addEventListener("click", () => {
    // navigator.clipboard is missing outside secure contexts; the call then throws instead of rejecting.
    try {
      navigator.clipboard.writeText(text.textContent).then(
        () => { status.textContent = "Copied"; },
        selectPicks
      );
    } catch (e) {
      selectPicks();
    }
  });
})();
"""
