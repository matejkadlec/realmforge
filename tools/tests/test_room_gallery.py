"""The room gallery (A4e, RLM-122): the page, the review decisions and the CLI. The renderer is replaced by a
stand-in, except in one test that renders for real where the client jar is already cached (not in CI)."""

from __future__ import annotations

from pathlib import Path

import pytest

from realmforge_tools import cli
from realmforge_tools.generate.gametest import SPIKE_ROOMS, room_structure
from realmforge_tools.project import MINECRAFT_VERSION, REPO_ROOT, RepoPaths
from realmforge_tools.rooms import gallery
from realmforge_tools.rooms.render import VIEWS, RoomRenderer, View
from realmforge_tools.rooms.structure import Structure, read_structure

from .conftest import write


class FakeRenderer:
    def __init__(self) -> None:
        self.calls: list[tuple[tuple[int, int, int], str, Path, int]] = []

    def render(self, structure: Structure, view: View, target: Path, cut: int = 5, outlines=()) -> None:
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
    # A valid gzip header over a damaged deflate stream (the stored block's length check flipped).
    corrupt = bytearray(room_structure(SPIKE_ROOMS["start"]))
    corrupt[13] ^= 0xFF
    (rooms / "corrupt.nbt").write_bytes(bytes(corrupt))
    renderer = FakeRenderer()
    out = tmp_path / "out"
    (out / "stale.png").parent.mkdir(parents=True)
    (out / "stale.png").write_bytes(b"old")
    (out / gallery.MARKER).write_text("", encoding="utf-8")  # an earlier gallery
    page, found = gallery.build(rooms, out, renderer, "Rooms", cut=4)  # type: ignore[arg-type]
    assert [room.key for room in found] == ["broken", "corrupt", "spike/corridor", "spike/start"]
    assert found[0].structure is None and found[0].error
    assert found[1].structure is None and "broken gzip data" in (found[1].error or "")
    assert {(call[1], call[2].relative_to(out).as_posix(), call[3]) for call in renderer.calls} == {
        (view.name, f"spike/{name}/{view.name}.png", 4) for view in VIEWS for name in ("corridor", "start")
    }
    assert not (out / "stale.png").exists()
    text = page.read_text(encoding="utf-8")
    assert 'src="spike/start/cutaway.png"' in text
    assert "Cannot read the template" in text
    assert "tags: entrance" in text
    assert "15 x 7 x 15" in text
    assert "cut 4 blocks above the bottom" in text  # the cut the views used, not the default


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
    decisions, changed = gallery.merge_review(
        rooms, "spike/start: keep\nspike/corridor: reject\n", RepoPaths(tmp_path)
    )
    assert changed == 2 and decisions == {"spike/start": "keep", "spike/corridor": "reject"}
    decisions, changed = gallery.merge_review(
        rooms, "spike/corridor: keep\nspike/start: keep\n", RepoPaths(tmp_path)
    )
    assert changed == 1
    text = (rooms / "review.yaml").read_text(encoding="utf-8")
    assert text.endswith('"spike/corridor": keep\n"spike/start": keep\n')
    assert text.startswith("# Room gallery decisions")


def test_review_refuses_a_generated_folder(tmp_path: Path) -> None:
    paths = RepoPaths(tmp_path)
    spike = spike_folder(paths.qa_generated / "resources" / "data" / "realmforge_qa" / "structure") / "spike"
    with pytest.raises(gallery.GalleryError, match=r"is generated: rf gen would delete a review\.yaml there"):
        gallery.merge_review(spike, "start: keep\n", paths)
    assert not (spike / "review.yaml").exists()
    other = spike_folder(tmp_path / "rooms") / "spike"
    assert gallery.merge_review(other, "start: keep\n", paths) == ({"start": "keep"}, 1)


def test_review_keys_with_yaml_syntax_survive(tmp_path: Path) -> None:
    # Any folder works, so room names are not always snake_case: these break as plain YAML keys.
    names = ["hall #2", "null", "123", "a: b"]
    for name in names:
        (tmp_path / f"{name}.nbt").write_bytes(room_structure(SPIKE_ROOMS["start"]))
    decisions = dict.fromkeys(names, "keep")
    pasted = gallery.review_text(decisions)  # the same form the page's copy button writes
    assert gallery.parse_decisions(pasted, "pasted") == decisions
    assert gallery.merge_review(tmp_path, pasted, RepoPaths(tmp_path))[0] == decisions
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
        gallery.merge_review(rooms, text, RepoPaths(tmp_path))
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
    write(target / "review.yaml", "hall: reject\n")
    (target / "hall.nbt").unlink()
    (target / "hall.yaml").unlink()
    assert cli.main(["rooms", "import", str(source), "--to", str(target)]) == 0
    out = capsys.readouterr().out
    assert f"SKIP {source}: " in out and "review.yaml rejects " in out
    assert "Imported 0 of 1 room(s), skipped 1 the review rejects." in out
    assert sorted(p.name for p in target.iterdir()) == ["review.yaml"]


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


# --- markers, always shown with their label (ADR 0035) ----------------------------------------------------------


def test_every_marker_has_its_own_style() -> None:
    from realmforge_tools.rooms.check import MARKERS
    from realmforge_tools.rooms.render import MARKER_STYLES, UNKNOWN_MARKER

    assert set(MARKER_STYLES) == set(MARKERS), "a new marker needs a style in rooms/render.py (ADR 0035)"
    styles = [*MARKER_STYLES.values(), UNKNOWN_MARKER]
    assert len({style.block for style in styles}) == len(styles)
    assert len({style.colour for style in styles}) == len(styles)


def test_views_draw_every_marker_in_its_colour_even_above_the_cut() -> None:
    from realmforge_tools.rooms.render import MARKER_STYLES, drawn_blocks
    from realmforge_tools.rooms.structure import BlockState, StructureBlock

    marker = BlockState("minecraft:structure_block", (("mode", "data"),))
    structure = Structure(
        (3, 9, 3),
        4790,
        (
            StructureBlock(
                (1, 1, 1), marker, {"id": "minecraft:structure_block", "mode": "DATA", "metadata": "mob"}
            ),
            StructureBlock(
                (1, 7, 1), marker, {"id": "minecraft:structure_block", "mode": "DATA", "metadata": "chest"}
            ),
            StructureBlock((0, 7, 0), BlockState("minecraft:stone")),
        ),
    )
    plan = next(view for view in VIEWS if view.name == "plan")
    drawn = dict(drawn_blocks(structure, plan, cut=4))
    assert drawn == {(1, 1, 1): MARKER_STYLES["mob"].block, (1, 7, 1): MARKER_STYLES["chest"].block}


def test_page_lists_each_rooms_markers_under_a_legend(tmp_path: Path) -> None:
    rooms = spike_folder(tmp_path / "rooms")
    page, _ = gallery.build(rooms, tmp_path / "out", FakeRenderer(), "Rooms")
    text = page.read_text(encoding="utf-8")
    assert '<section class="legend-markers">' in text
    for label in ("entrance", "mob", "chest", "exit"):
        assert f"<code>{label}</code>:" in text  # the legend
    assert "<code>entrance</code> &times;1: 7 1 7" in text  # the start room's card
    assert "<code>mob</code> &times;1: 12 1 3" in text  # the corridor's
