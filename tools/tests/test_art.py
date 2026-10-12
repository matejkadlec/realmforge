from __future__ import annotations

import re
import struct
import tracemalloc
import zlib
from pathlib import Path

import pytest

from realmforge_tools.art import gallery
from realmforge_tools.art.palette import DEFAULT_PALETTE, Palette, PaletteColor, PaletteError, load_gpl
from realmforge_tools.art.texture import TRANSPARENT, Image, TextureError, clean
from realmforge_tools.cli import main
from realmforge_tools.formats.png import PNG_SIGNATURE, PngError, decode_rgba, encode_rgba
from realmforge_tools.project import REPO_ROOT

RED = (200, 30, 30, 255)
BLUE = (30, 40, 200, 255)
PALETTE = Palette("test", (PaletteColor("red", (200, 30, 30)), PaletteColor("blue", (30, 40, 200))))


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def _png(width: int, height: int, depth: int, color_type: int, rows: list[bytes], *extra: bytes, interlace=0):
    """A PNG with real zlib compression and the given (already filtered) scanlines."""
    ihdr = struct.pack(">IIBBBBB", width, height, depth, color_type, 0, 0, interlace)
    return (
        PNG_SIGNATURE
        + _chunk(b"IHDR", ihdr)
        + b"".join(extra)
        + _chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
        + _chunk(b"IEND", b"")
    )


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c


def _filter(kind: int, line: bytes, previous: bytes, bpp: int) -> bytes:
    out = bytearray([kind])
    for i, value in enumerate(line):
        left = line[i - bpp] if i >= bpp else 0
        up = previous[i]
        up_left = previous[i - bpp] if i >= bpp else 0
        predictor = [0, left, up, (left + up) // 2, _paeth(left, up, up_left)][kind]
        out.append((value - predictor) & 0xFF)
    return bytes(out)


# --- PNG decoding -----------------------------------------------------------------------------------------


def test_decode_reads_back_what_the_encoder_writes() -> None:
    pixels = [(x * 16, y * 16, 128, 255 if (x + y) % 2 else 0) for y in range(16) for x in range(16)]
    assert decode_rgba(encode_rgba(16, 16, pixels)) == (16, 16, pixels)


def test_decode_undoes_every_filter_type() -> None:
    lines = [bytes((x * 37 + y * 11) % 256 for x in range(4 * 3)) for y in range(5)]
    previous = bytes(len(lines[0]))
    rows = []
    for y, line in enumerate(lines):
        rows.append(_filter(y % 5, line, previous, 3))
        previous = line
    width, height, pixels = decode_rgba(_png(4, 5, 8, 2, rows))
    assert (width, height) == (4, 5)
    expected = [(*line[i : i + 3], 255) for line in lines for i in range(0, 12, 3)]
    assert pixels == expected


def test_decode_palette_image_with_transparency_and_low_bit_depth() -> None:
    plte = _chunk(b"PLTE", bytes([255, 0, 0, 0, 255, 0, 0, 0, 255]))
    trns = _chunk(b"tRNS", bytes([0]))  # index 0 transparent, the rest opaque
    # 2-bit indices 0, 1, 2, 1 packed into one byte: 00 01 10 01
    _, _, pixels = decode_rgba(_png(4, 1, 2, 3, [b"\x00" + bytes([0b00011001])], plte, trns))
    assert pixels == [(255, 0, 0, 0), (0, 255, 0, 255), (0, 0, 255, 255), (0, 255, 0, 255)]


def test_decode_grayscale_with_color_key() -> None:
    trns = _chunk(b"tRNS", struct.pack(">H", 0))
    _, _, pixels = decode_rgba(_png(2, 1, 8, 0, [b"\x00\x00\x80"], trns))
    assert pixels == [(0, 0, 0, 0), (128, 128, 128, 255)]


@pytest.mark.parametrize(
    ("data", "message"),
    [
        (b"GIF89a", "not a PNG"),
        (_png(1, 1, 16, 6, [b"\x00" + bytes(8)]), "bit depth 16"),
        (_png(1, 1, 8, 6, [b"\x00" + bytes(4)], interlace=1), "interlaced"),
        (_png(1, 1, 8, 3, [b"\x00\x00"]), "without a PLTE"),
        (_png(2, 2, 8, 6, [b"\x00" + bytes(8)]), "shorter than the header"),
        (_png(1, 1, 8, 6, [b"\x00" + bytes(4)] * 2), "longer than the header"),
        (_png(1, 1, 8, 6, [b"\x07" + bytes(4)]), "filter type 7"),
        (PNG_SIGNATURE + _chunk(b"IHDR", bytes(12)), "IHDR chunk has 12 bytes"),
        (_png(0, 1, 8, 6, [b"\x00"]), "a 0x1 image has no pixels"),
        (
            _png(0xFFFFFFFF, 0xFFFFFFFF, 8, 6, [b"\x00" + bytes(4)]),
            "a 4294967295x4294967295 image is too large",
        ),
    ],
)
def test_decode_rejects_broken_or_unsupported_files(data: bytes, message: str) -> None:
    with pytest.raises(PngError, match=message):
        decode_rgba(data)


def _png_with_idat(width: int, height: int, idat: bytes) -> bytes:
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return PNG_SIGNATURE + _chunk(b"IHDR", ihdr) + _chunk(b"IDAT", idat) + _chunk(b"IEND", b"")


def test_decode_refuses_a_decompression_bomb_without_inflating_it() -> None:
    # A 1x1 image whose image data is 64 MiB of zeros, about 64 KiB compressed.
    bomb = _png_with_idat(1, 1, zlib.compress(bytes(64 * 1024 * 1024), 9))
    tracemalloc.start()
    try:
        with pytest.raises(PngError, match="longer than the header"):
            decode_rgba(bomb)
        _, peak = tracemalloc.get_traced_memory()
    finally:
        tracemalloc.stop()
    assert peak < 4 * 1024 * 1024


def test_decode_rejects_a_truncated_image_stream() -> None:
    stream = zlib.compress(b"\x00" + bytes(4))[:-4]  # the scanline is complete, the Adler-32 checksum is cut
    with pytest.raises(PngError, match="incomplete or truncated stream"):
        decode_rgba(_png_with_idat(1, 1, stream))


def test_decode_rejects_a_chunk_whose_crc_does_not_match() -> None:
    data = bytearray(encode_rgba(2, 1, [RED, BLUE]))
    width_offset = len(PNG_SIGNATURE) + 8  # first byte of IHDR's width
    data[width_offset + 3] = 3  # the image now claims to be 3 pixels wide, but the CRC still says 2
    with pytest.raises(PngError, match="corrupt IHDR chunk"):
        decode_rgba(bytes(data))


# --- Palette ----------------------------------------------------------------------------------------------


def test_project_palette_loads_with_unique_colors() -> None:
    palette = load_gpl(REPO_ROOT / DEFAULT_PALETTE)
    assert palette.name == "Realmforge"
    rgbs = [c.rgb for c in palette.colors]
    assert len(rgbs) == len(set(rgbs)) >= 64


def _write(tmp_path: Path, text: str) -> Path:
    path = tmp_path / "p.gpl"
    path.write_text(text, encoding="utf-8")
    return path


def test_load_gpl_reads_names_and_skips_comments(tmp_path: Path) -> None:
    path = _write(tmp_path, "GIMP Palette\nName: Mine\nColumns: 4\n# note\n\n 1 2 3\tdark red\n4 5 6\n")
    palette = load_gpl(path)
    assert palette.name == "Mine"
    assert palette.colors == (PaletteColor("dark red", (1, 2, 3)), PaletteColor("color 2", (4, 5, 6)))


@pytest.mark.parametrize(
    ("text", "message"),
    [
        ("JASC-PAL\n", "not a GIMP palette"),
        ("GIMP Palette\n1 2\n", "expected 'R G B name'"),
        ("GIMP Palette\n1 2 x a\n", "must be integers"),
        ("GIMP Palette\n1 2 300 a\n", "between 0 and 255"),
        ("GIMP Palette\n1 2 3 a\n4 5 6 a\n", "duplicate color name 'a'"),
        ("GIMP Palette\nName: Empty\n", "has no colors"),
    ],
)
def test_load_gpl_rejects_bad_files(tmp_path: Path, text: str, message: str) -> None:
    with pytest.raises(PaletteError, match=message):
        load_gpl(_write(tmp_path, text))


def test_load_gpl_rejects_a_file_that_is_not_utf8(tmp_path: Path) -> None:
    path = tmp_path / "p.gpl"
    path.write_bytes(b"GIMP Palette\n1 2 3 \xff\xfe\n")
    with pytest.raises(PaletteError, match="not UTF-8 text"):
        load_gpl(path)


def test_nearest_is_perceptual_and_prefers_the_first_on_ties() -> None:
    assert PALETTE.nearest((250, 0, 0)).name == "red"
    assert PALETTE.nearest((0, 0, 255)).name == "blue"
    twins = Palette("twins", (PaletteColor("first", (10, 10, 10)), PaletteColor("second", (10, 10, 10))))
    assert twins.nearest((12, 12, 12)).name == "first"


# --- Cleanup ----------------------------------------------------------------------------------------------


def test_clean_snaps_every_opaque_pixel_to_the_palette() -> None:
    pixels = tuple((220, 10, 20, 255) if x < 8 else (0, 60, 255, 255) for _y in range(16) for x in range(16))
    cleaned, report = clean(Image(16, 16, pixels), PALETTE)
    assert set(cleaned.pixels) == {RED, BLUE}
    assert (report.scale, report.colors_before, report.colors_after, report.changed_pixels) == (1, 2, 2, 256)


def test_clean_hardens_alpha_and_ignores_the_color_of_transparent_pixels() -> None:
    pixels = (RED, (200, 30, 30, 200), (99, 99, 99, 0), (200, 30, 30, 50))
    cleaned, report = clean(Image(2, 2, pixels), PALETTE, size=2)
    assert cleaned.pixels == (RED, RED, TRANSPARENT, TRANSPARENT)
    # 200 alpha became 255 and 50 alpha became transparent; the hidden grey pixel did not change.
    assert report.changed_pixels == 2


def test_clean_reduces_an_upscaled_image_by_its_grid() -> None:
    # A 4x4 image of 2x2 blocks; the top-left block has one stray pixel and the bottom-right is mostly empty.
    r, b, t = RED, BLUE, TRANSPARENT
    rows = [
        [r, r, b, b],
        [r, b, b, b],
        [b, b, r, t],
        [b, b, t, t],
    ]
    cleaned, report = clean(Image(4, 4, tuple(p for row in rows for p in row)), PALETTE, size=2)
    assert (cleaned.width, cleaned.height, report.scale) == (2, 2, 2)
    assert cleaned.pixels == (RED, BLUE, BLUE, TRANSPARENT)


def test_clean_keeps_the_aspect_ratio_of_a_strip() -> None:
    cleaned, _ = clean(Image(32, 64, (RED,) * 32 * 64), PALETTE)
    assert (cleaned.width, cleaned.height) == (16, 32)


def test_clean_refuses_an_empty_image() -> None:
    with pytest.raises(TextureError, match="no pixels"):
        clean(Image(0, 16, ()), PALETTE)


@pytest.mark.parametrize(("width", "height"), [(20, 20), (32, 33)])
def test_clean_refuses_sizes_that_are_not_a_whole_grid(width: int, height: int) -> None:
    with pytest.raises(TextureError, match="multiple"):
        clean(Image(width, height, (RED,) * width * height), PALETTE)


def test_clean_is_deterministic() -> None:
    pixels = tuple((x * 13 % 256, y * 7 % 256, (x * y) % 256, 255) for y in range(16) for x in range(16))
    first, _ = clean(Image(16, 16, pixels), load_gpl(REPO_ROOT / DEFAULT_PALETTE))
    second, _ = clean(Image(16, 16, pixels), load_gpl(REPO_ROOT / DEFAULT_PALETTE))
    assert first.png() == second.png()


def test_tiled_repeats_the_image() -> None:
    tiled = Image(2, 1, (RED, BLUE)).tiled(2)
    assert (tiled.width, tiled.height) == (4, 2)
    assert tiled.pixels == (RED, BLUE, RED, BLUE) * 2


# --- Gallery and CLI --------------------------------------------------------------------------------------


def _candidates(tmp_path: Path) -> Path:
    root = tmp_path / "candidates"
    for subject, name, pixel in [("b_item", "x", RED), ("a_block", "x", BLUE), ("a_block", "y", RED)]:
        (root / subject).mkdir(parents=True, exist_ok=True)
        alpha_corner = [TRANSPARENT] if subject == "b_item" else [pixel]
        (root / subject / f"{name}.png").write_bytes(encode_rgba(16, 16, alpha_corner + [pixel] * 255))
    return root


def test_gallery_follows_the_yaml_order_and_tiles_only_opaque_textures(tmp_path: Path) -> None:
    root = _candidates(tmp_path)
    (root / "gallery.yaml").write_text(
        "title: Test gallery\n"
        "candidates:\n  y: {label: Why}\n"
        "subjects:\n  b_item: {title: Item, prompt: a <red> thing}\n",
        encoding="utf-8",
    )
    info, subjects = gallery.collect(root, PALETTE, 16)
    assert [s.name for s in subjects] == ["b_item", "a_block"]  # listed first, then by name
    assert [c.name for c in subjects[1].candidates] == ["y", "x"]
    page = gallery.render(info, subjects, PALETTE)
    assert page.startswith("<title>Test gallery</title>")
    assert "a &lt;red&gt; thing" in page
    assert (
        page.count('class="px tiled"') == 2
    )  # both a_block candidates, not the item with a transparent corner
    assert page == gallery.render(info, subjects, PALETTE)


def test_gallery_keeps_picks_per_batch_of_candidates(tmp_path: Path) -> None:
    root = _candidates(tmp_path)
    info, subjects = gallery.collect(root, PALETTE, 16)
    first = gallery.batch_id(subjects)
    assert f'data-batch="{first}"' in gallery.render(info, subjects, PALETTE)
    assert gallery.batch_id(gallery.collect(root, PALETTE, 16)[1]) == first
    reds = Palette("reds", (PaletteColor("red", (200, 30, 30)),))
    assert gallery.batch_id(gallery.collect(root, reds, 16)[1]) != first  # same candidates, other cleanup
    (root / "a_block" / "y.png").write_bytes(encode_rgba(16, 16, [BLUE] * 256))  # same names, new pixels
    assert gallery.batch_id(gallery.collect(root, PALETTE, 16)[1]) != first


def test_gallery_previews_keep_the_aspect_ratio_of_a_strip(tmp_path: Path) -> None:
    (tmp_path / "strip").mkdir()
    (tmp_path / "strip" / "x.png").write_bytes(encode_rgba(16, 32, [RED] * 16 * 32))
    info, subjects = gallery.collect(tmp_path, PALETTE, 16)
    page = gallery.render(info, subjects, PALETTE)
    assert 'width="16" height="32"' in page  # the intrinsic ratio the browser scales by
    assert "height: auto" in page and "height: 128px" not in page  # CSS fixes only the width


def test_gallery_reports_a_broken_yaml_with_its_path(tmp_path: Path) -> None:
    root = _candidates(tmp_path)
    (root / "gallery.yaml").write_text("subjects:\n  b_item: {title: Item, colour: red}\n", encoding="utf-8")
    with pytest.raises(gallery.GalleryError, match=r"gallery\.yaml: subjects\.b_item\.colour"):
        gallery.collect(root, PALETTE, 16)


@pytest.mark.parametrize(
    ("data", "message"),
    [
        (_png(1, 1, 8, 6, [b"\x00" + bytes(4)], interlace=1), "interlaced PNGs are not supported"),
        (encode_rgba(20, 20, [RED] * 400), "a 20x20 image cannot be reduced to 16 pixels wide"),
    ],
    ids=["interlaced", "not-a-whole-grid"],
)
def test_gallery_names_the_candidate_it_cannot_read(
    tmp_path: Path, capsys: pytest.CaptureFixture[str], data: bytes, message: str
) -> None:
    root = _candidates(tmp_path)
    bad = root / "a_block" / "z.png"
    bad.write_bytes(data)
    with pytest.raises(gallery.GalleryError, match=f"^{re.escape(str(bad))}: {message}"):
        gallery.collect(root, PALETTE, 16)
    assert main(["art", "gallery", str(root), "--out", str(tmp_path / "g.html")]) == 1
    assert f"rf art gallery: {bad}: {message}" in capsys.readouterr().err


def test_gallery_reports_a_yaml_that_is_not_utf8(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    root = _candidates(tmp_path)
    (root / "gallery.yaml").write_bytes(b"title: \xff\xfe\n")
    assert main(["art", "gallery", str(root), "--out", str(tmp_path / "g.html")]) == 1
    assert "gallery.yaml: not UTF-8 text" in capsys.readouterr().err


def test_cli_cleans_a_texture_and_builds_a_gallery(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    root = _candidates(tmp_path)
    palette = _write(tmp_path, "GIMP Palette\n200 30 30 red\n30 40 200 blue\n")
    target = tmp_path / "out" / "clean.png"
    assert (
        main(["art", "clean", str(root / "a_block" / "y.png"), str(target), "--palette", str(palette)]) == 0
    )
    assert decode_rgba(target.read_bytes()) == (16, 16, [RED] * 256)
    page = tmp_path / "gallery.html"
    assert main(["art", "gallery", str(root), "--out", str(page), "--palette", str(palette)]) == 0
    assert "Your picks" in page.read_text(encoding="utf-8")
    assert "2 subject(s), 3 candidate(s)" in capsys.readouterr().out


def test_cli_reports_a_file_that_is_not_a_png(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    source = tmp_path / "x.png"
    source.write_bytes(b"not an image")
    assert main(["art", "clean", str(source), str(tmp_path / "y.png")]) == 1
    assert "not a PNG file" in capsys.readouterr().err
