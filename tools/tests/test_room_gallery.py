"""The room gallery (A4e, RLM-122): the page, the review decisions and the CLI. The renderer is replaced by a
stand-in, except in one test that renders for real where the client jar is already cached (not in CI)."""

from __future__ import annotations

from pathlib import Path

import pytest

from realmforge_tools import cli
from realmforge_tools.generate.gametest import SPIKE_ROOMS, room_structure
from realmforge_tools.project import MINECRAFT_VERSION, REPO_ROOT
from realmforge_tools.rooms import gallery
from realmforge_tools.rooms.render import VIEWS, RoomRenderer, View
from realmforge_tools.rooms.structure import Structure, read_structure

from .conftest import write


class FakeRenderer:
    def __init__(self) -> None:
        self.calls: list[tuple[tuple[int, int, int], str, Path, int]] = []

    def render(self, structure: Structure, view: View, target: Path, cut: int = 5) -> None:
        self.calls.append((structure.size, view.name, target, cut))
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"png")


def spike_folder(root: Path) -> Path:
    for name in ("start", "corridor"):
        (root / "spike").mkdir(parents=True, exist_ok=True)
        (root / "spike" / f"{name}.nbt").write_bytes(room_structure(SPIKE_ROOMS[name]))
    return root


def test_build_renders_every_view_and_writes_the_page(tmp_path: Path) -> None:
    rooms = spike_folder(tmp_path / "rooms")
    write(rooms / "spike" / "start.yaml", "tags: [entrance]\n")
    (rooms / "broken.nbt").write_bytes(b"not a template")
    renderer = FakeRenderer()
    out = tmp_path / "out"
    (out / "stale.png").parent.mkdir(parents=True)
    (out / "stale.png").write_bytes(b"old")
    (out / gallery.MARKER).write_text("", encoding="utf-8")  # an earlier gallery
    page, found = gallery.build(rooms, out, renderer, "Rooms", cut=4)  # type: ignore[arg-type]
    assert [room.key for room in found] == ["broken", "spike/corridor", "spike/start"]
    assert found[0].structure is None and found[0].error
    assert {(call[1], call[2].relative_to(out).as_posix(), call[3]) for call in renderer.calls} == {
        (view.name, f"spike/{name}/{view.name}.png", 4) for view in VIEWS for name in ("corridor", "start")
    }
    assert not (out / "stale.png").exists()
    text = page.read_text(encoding="utf-8")
    assert 'src="spike/start/cutaway.png"' in text
    assert "Cannot read the template" in text
    assert "tags: entrance" in text
    assert "15 x 7 x 15" in text


@pytest.mark.parametrize("out", ["rooms", ".", "rooms/spike"])
def test_build_refuses_an_output_that_overlaps_the_rooms(tmp_path: Path, out: str) -> None:
    rooms = spike_folder(tmp_path / "rooms")
    with pytest.raises(gallery.GalleryError, match="overlaps the rooms"):
        gallery.build(rooms, tmp_path / out, FakeRenderer(), "Rooms")  # type: ignore[arg-type]
    assert sorted(p.name for p in (rooms / "spike").iterdir()) == ["corridor.nbt", "start.nbt"]


def test_build_replaces_only_an_earlier_gallery(tmp_path: Path) -> None:
    rooms = spike_folder(tmp_path / "rooms")
    other = tmp_path / "notes"
    write(other / "keep.txt", "mine\n")
    with pytest.raises(gallery.GalleryError, match="not an earlier room gallery"):
        gallery.build(rooms, other, FakeRenderer(), "Rooms")  # type: ignore[arg-type]
    assert (other / "keep.txt").exists()
    out = tmp_path / "out"
    gallery.build(rooms, out, FakeRenderer(), "Rooms")  # type: ignore[arg-type]
    gallery.build(rooms, out, FakeRenderer(), "Rooms")  # type: ignore[arg-type]
    assert (out / gallery.MARKER).exists() and (out / "index.html").exists()


def test_page_starts_from_the_review_file_and_is_deterministic(tmp_path: Path) -> None:
    rooms = spike_folder(tmp_path)
    write(rooms / "review.yaml", "spike/start: keep\n")
    found = gallery.collect(rooms)
    first = gallery.render_page("Rooms", found, gallery.read_review(rooms))
    assert first == gallery.render_page("Rooms", found, gallery.read_review(rooms))
    assert 'name="review-spike/start" value="keep" checked' in first
    assert 'name="review-spike/corridor" value="keep">' in first
    assert f'data-batch="{gallery.batch_id(found)}"' in first


def test_page_escapes_keys(tmp_path: Path) -> None:
    room = gallery.GalleryRoom('a"<b>', None, None, "broken")
    text = gallery.render_page("Rooms <test>", [room], {})
    assert "a&quot;&lt;b&gt;" in text and "<title>Rooms &lt;test&gt;</title>" in text


def test_review_merges_decisions(tmp_path: Path) -> None:
    rooms = spike_folder(tmp_path)
    decisions, changed = gallery.merge_review(rooms, "spike/start: keep\nspike/corridor: reject\n")
    assert changed == 2 and decisions == {"spike/start": "keep", "spike/corridor": "reject"}
    decisions, changed = gallery.merge_review(rooms, "spike/corridor: keep\nspike/start: keep\n")
    assert changed == 1
    text = (rooms / "review.yaml").read_text(encoding="utf-8")
    assert text.endswith('"spike/corridor": keep\n"spike/start": keep\n')
    assert text.startswith("# Room gallery decisions")


def test_review_keys_with_yaml_syntax_survive(tmp_path: Path) -> None:
    # Any folder works, so room names are not always snake_case: these break as plain YAML keys.
    names = ["hall #2", "null", "123", "a: b"]
    for name in names:
        (tmp_path / f"{name}.nbt").write_bytes(room_structure(SPIKE_ROOMS["start"]))
    decisions = dict.fromkeys(names, "keep")
    pasted = gallery.review_text(decisions)  # the same form the page's copy button writes
    assert gallery.parse_decisions(pasted, "pasted") == decisions
    assert gallery.merge_review(tmp_path, pasted)[0] == decisions
    assert gallery.read_review(tmp_path) == decisions
    page = gallery.render_page("Rooms", gallery.collect(tmp_path), {})
    assert 'JSON.stringify(k) + ": "' in page


@pytest.mark.parametrize(
    ("text", "message"),
    [
        ("spike/hall: keep\n", "no such room"),
        ("spike/start: maybe\n", "Input should be 'keep' or 'reject'"),
        ("spike/start: [keep\n", "invalid YAML"),
    ],
)
def test_review_refuses_bad_decisions(tmp_path: Path, text: str, message: str) -> None:
    rooms = spike_folder(tmp_path)
    with pytest.raises(gallery.GalleryError, match=message):
        gallery.merge_review(rooms, text)
    assert not (rooms / "review.yaml").exists()


def test_cli_review(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    rooms = spike_folder(tmp_path / "rooms")
    pasted = write(tmp_path / "pasted.txt", "spike/start: keep\nspike/corridor: reject\n")
    assert cli.main(["rooms", "review", str(rooms), "--from", str(pasted)]) == 0
    assert "2 decision(s) changed, 1 kept and 1 rejected in total" in capsys.readouterr().out
    assert cli.main(["rooms", "review", str(rooms), "--from", str(write(pasted, "nope: keep\n"))]) == 1
    assert "no such room" in capsys.readouterr().err


def test_cli_import(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    source = tmp_path / "Hall.nbt"
    source.write_bytes(room_structure(SPIKE_ROOMS["hall"]))
    target = tmp_path / "rooms"
    assert cli.main(["rooms", "import", str(source), "--to", str(target)]) == 0
    assert "Imported 1 of 1 room(s)." in capsys.readouterr().out
    assert sorted(p.name for p in target.iterdir()) == ["hall.nbt", "hall.yaml"]
    assert "yaml-language-server" not in (target / "hall.yaml").read_text(
        encoding="utf-8"
    )  # outside the repo


def test_cli_gallery_needs_rooms(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    assert cli.main(["rooms", "gallery", str(tmp_path)]) == 1
    assert "No rooms (*.nbt)" in capsys.readouterr().err


CLIENT_JAR = REPO_ROOT / ".cache" / "vanilla" / MINECRAFT_VERSION / "client.jar"


@pytest.mark.skipif(
    not CLIENT_JAR.exists(), reason="renders only where rf rooms gallery cached the client jar"
)
def test_real_render_of_a_spike_room(tmp_path: Path) -> None:
    renderer = RoomRenderer(CLIENT_JAR, size=64)
    target = tmp_path / "start.png"
    renderer.render(read_structure(room_structure(SPIKE_ROOMS["start"])), VIEWS[1], target)
    assert target.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
