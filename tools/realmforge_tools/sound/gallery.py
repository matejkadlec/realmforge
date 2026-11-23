"""``rf sound gallery``: every candidate in the store on one page with a player (RLM-126, ADR 0079).

The page lives in the store (``gallery/index.html``) and plays the candidates from there, so it works without a
checkout and survives every worktree. It groups the candidates by ticket and sound event, best score first
(``rank.py``), with each one's measurements, prompt, parameters and cost, and marks the one a checkout's
``art/sounds/sources.yaml`` installed: "in the game", "provisional" when an agent picked it. Buttons copy the command
that picks, swaps or rejects a candidate; Matěj runs it in a checkout (``art/README.md``, Sound pipeline).
"""

from __future__ import annotations

import html
import os
from collections import defaultdict
from pathlib import Path

from realmforge_tools.sound import rank, store
from realmforge_tools.sound.art import SourceDef
from realmforge_tools.sound.store import Candidate

PAGE = "gallery/index.html"


def _picks(sources: dict[str, SourceDef]) -> dict[str, tuple[str, SourceDef]]:
    """Candidate id -> (file name, its source) for every pick a checkout installed."""
    return {s.candidate: (name, s) for name, s in sources.items() if s.candidate}


def _row(
    candidate: Candidate, score: rank.Score, picked: tuple[str, SourceDef] | None, event_pick: str | None
) -> str:
    m = candidate.measured
    e = html.escape
    audio = f"../{store.CANDIDATES}/{candidate.subject}/{candidate.event}/{candidate.file}"
    badges = []
    if picked:
        provisional = "provisional" in picked[1].picked_by.lower()
        badges.append(
            f'<span class="badge {"prov" if provisional else "pick"}">'
            f"{'provisional pick' if provisional else 'in the game'}: art/sounds/{e(picked[0])}.ogg</span>"
        )
    if candidate.review == "rejected":
        badges.append('<span class="badge rej">rejected</span>')
    stats = (
        (
            f"{m.sound_seconds:.2f} s of sound ({m.seconds:.2f} s file), peak {m.peak_dbfs:.1f} dBFS, "
            f"{m.loudness_lufs:.1f} LUFS, {m.clipped_runs} clipped runs, {m.rate} Hz, {m.channels} channel(s)"
            + (f", seam {m.loop_seam:.3f}" if candidate.loop else "")
        )
        if m
        else "not measured"
    )
    params = [f"model {candidate.model}"]
    if candidate.duration_seconds is not None:
        params.append(f"{candidate.duration_seconds:g} s asked")
    if candidate.music_length_ms is not None:
        params.append(f"{candidate.music_length_ms / 1000:g} s asked")
    if candidate.loop:
        params.append("loop")
    if candidate.prompt_influence is not None:
        params.append(f"influence {candidate.prompt_influence:g}")
    params.append(f"{candidate.cost.charged if candidate.cost.charged is not None else '?'} credits")
    params.append(candidate.created[:10])
    pick_cmd = f"uv run rf sound pick {candidate.id} --by Matěj"
    if event_pick:
        name = event_pick.split("/", 1)[1]
        swap = f"uv run rf sound pick {candidate.id} --name {name} --replace --by Matěj"
        commands = [("swap in", swap), ("add as another", pick_cmd)]
    else:
        commands = [("pick", pick_cmd)]
    commands.append(("reject", f"uv run rf sound reject {candidate.id}"))
    buttons = "".join(
        f'<button data-cmd="{e(cmd)}" title="{e(cmd)}">{e(label)}</button>' for label, cmd in commands
    )
    reasons = f" · {e(', '.join(score.reasons))}" if score.reasons else ""
    return (
        f'<div class="cand{" picked" if picked else ""}{" rejected" if candidate.review == "rejected" else ""}">'
        f'<div class="head"><b>{e(candidate.id)}</b> <span class="score">score {score.value:g}</span>{reasons}'
        f" {''.join(badges)}</div>"
        f'<audio controls preload="none" src="{e(audio)}"></audio>'
        f'<div class="prompt">“{e(candidate.prompt)}”</div>'
        f'<div class="meta">{e(stats)}<br>{e(" · ".join(params))}</div>'
        f'<div class="cmds">{buttons}</div></div>'
    )


def render(root: Path, found: list[Candidate], sources: dict[str, SourceDef], credits: str) -> str:
    picks = _picks(sources)
    by_event: dict[tuple[str, str, str], list[Candidate]] = defaultdict(list)
    for c in found:
        by_event[(c.ticket, c.subject, c.event)].append(c)
    tickets: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for ticket, subject, event in sorted(by_event):
        tickets[ticket].append((subject, event))
    e = html.escape
    sections = []
    for ticket in sorted(tickets, key=lambda t: int(t.split("-")[1])):
        blocks = []
        for subject, event in tickets[ticket]:
            group = by_event[(ticket, subject, event)]
            event_pick = next((picks[c.id][0] for c in group if c.id in picks), None)
            target = next((c.sound_event for c in group if c.sound_event), None)
            note = next((c.note for c in group if c.note), "")
            title = f"{subject} · {event}" + (f" → {target}" if target else "")
            rows = "".join(_row(c, s, picks.get(c.id), event_pick) for c, s in rank.ranked(group))
            blocks.append(
                f'<section class="event" data-key="{e(ticket)} {e(subject)} {e(event)} {e(target or "")}">'
                f"<h3>{e(title)}</h3>"
                + (f'<p class="note">{e(note)}</p>' if note else "")
                + rows
                + "</section>"
            )
        sections.append(
            f'<section class="ticket"><h2><a href="https://envelopment.atlassian.net/browse/{e(ticket)}">'
            f"{e(ticket)}</a> ({sum(len(by_event[(ticket, s, ev)]) for s, ev in tickets[ticket])} candidates)"
            f"</h2>{''.join(blocks)}</section>"
        )
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Realmforge sounds</title>
<style>
:root {{ color-scheme: light dark; --bg:#fafafa; --fg:#1d1d1f; --muted:#666; --card:#fff; --line:#ddd;
  --pick:#1b7f3b; --prov:#b26a00; --rej:#b00020; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#17181a; --fg:#e8e8ea; --muted:#a0a0a8; --card:#222326;
  --line:#3a3b40; --pick:#4cc373; --prov:#f0a640; --rej:#ff6b81; }} }}
body {{ background:var(--bg); color:var(--fg); font:14px/1.45 system-ui,sans-serif; margin:0 auto; max-width:1100px;
  padding:16px; }}
h1 {{ margin:0 0 4px; }} h2 {{ margin:28px 0 8px; border-bottom:1px solid var(--line); }} h3 {{ margin:16px 0 6px; }}
a {{ color:inherit; }} .note,.meta,.intro {{ color:var(--muted); }}
.cand {{ background:var(--card); border:1px solid var(--line); border-radius:8px; padding:10px; margin:8px 0; }}
.cand.picked {{ border-color:var(--pick); }} .cand.rejected {{ opacity:.55; }}
.cand audio {{ width:100%; margin:6px 0; }} .prompt {{ font-style:italic; }}
.badge {{ border-radius:4px; padding:1px 6px; font-size:12px; color:#fff; }}
.badge.pick {{ background:var(--pick); }} .badge.prov {{ background:var(--prov); }} .badge.rej {{ background:var(--rej); }}
.score {{ font-variant-numeric:tabular-nums; }}
.cmds button {{ margin:6px 6px 0 0; padding:4px 10px; border:1px solid var(--line); border-radius:6px;
  background:transparent; color:inherit; cursor:pointer; }}
input {{ width:100%; padding:8px; box-sizing:border-box; border:1px solid var(--line); border-radius:6px;
  background:var(--card); color:inherit; margin:8px 0; }}
</style></head><body>
<h1>Realmforge sounds</h1>
<p class="intro">{len(found)} candidates in <code>{e(str(root))}</code> · {e(credits)}<br>
Best score first: no clipping, the length asked for, little silence before the sound, a level above the noise
(art/README.md, Sound pipeline). A button copies its command: run it in a checkout, then wire or commit as the
README says.</p>
<input id="filter" placeholder="Filter: a ticket, subject, event or sound event">
{"".join(sections)}
<script>
document.addEventListener('click', ev => {{
  const cmd = ev.target.dataset && ev.target.dataset.cmd; if (!cmd) return;
  const done = () => {{ ev.target.textContent = 'copied'; }};
  (navigator.clipboard ? navigator.clipboard.writeText(cmd).then(done, () => prompt('Copy:', cmd))
    : (prompt('Copy:', cmd), null));
}});
document.getElementById('filter').addEventListener('input', ev => {{
  const q = ev.target.value.toLowerCase();
  document.querySelectorAll('section.event').forEach(s => {{
    s.style.display = s.dataset.key.toLowerCase().includes(q) ? '' : 'none'; }});
}});
</script>
</body></html>
"""


def write(root: Path, sources: dict[str, SourceDef], credits: str) -> Path:
    found = store.candidates(root)
    page = root / PAGE
    page.parent.mkdir(parents=True, exist_ok=True)
    page.write_text(render(root, found, sources, credits), encoding="utf-8")
    return page


def windows_path(page: Path) -> str | None:
    """The page's path as Windows Explorer opens it from WSL, when this runs in WSL."""
    distro = os.environ.get("WSL_DISTRO_NAME")
    if not distro:
        return None
    return "\\\\wsl.localhost\\" + distro + str(page).replace("/", "\\")
