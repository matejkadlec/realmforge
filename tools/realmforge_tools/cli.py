"""``rf``: the Realmforge content pipeline command line.

uv run rf validate          validate content/ (schema, ids, references)
uv run rf gen               validate, then regenerate every generated file
uv run rf gen --check       exit 1 if any generated file is out of date (writes nothing)
uv run rf ids vanilla       re-extract the vanilla ID universe from the official server jar (needs Java 25)
uv run rf pack-boot         boot the whole pack plus the core mod jar on a server and a client (needs Java 25)
uv run rf gametest-log      exit 1 if the last GameTest run logged a data loading error (after runGameTestServer)
uv run rf art clean SRC DST snap a generated texture to the 16x16 grid and the project palette
uv run rf art gallery DIR   build the review gallery for the candidate textures in DIR
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from realmforge_tools import gamelog, packboot, vanilla
from realmforge_tools.art import gallery
from realmforge_tools.art.palette import DEFAULT_PALETTE, PaletteError, load_gpl
from realmforge_tools.art.texture import DEFAULT_SIZE, Image, TextureError, clean
from realmforge_tools.formats.png import PngError, decode_rgba
from realmforge_tools.generate import generate_all, sync
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import MINECRAFT_VERSION, RepoPaths
from realmforge_tools.validate import load_and_validate


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="rf", description="Realmforge content pipeline.")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate", help="validate content/ (schema, ids, references)")
    gen = commands.add_parser("gen", help="validate, then regenerate every generated file")
    gen.add_argument("--check", action="store_true", help="only report out-of-date files; exit 1 if any")
    ids = commands.add_parser("ids", help="manage the ID universe used for validation")
    ids_commands = ids.add_subparsers(dest="ids_command", required=True)
    ids_vanilla = ids_commands.add_parser("vanilla", help="extract vanilla ids from the official server jar")
    ids_vanilla.add_argument("--version", default=MINECRAFT_VERSION)
    pack_boot = commands.add_parser(
        "pack-boot",
        help="boot the whole pack plus the core mod jar on a server and a client; exit 1 on errors",
    )
    pack_boot.add_argument(
        "--side",
        choices=("both", *packboot.SIDES),
        default="both",
        help="boot a dedicated server, a client (under Xvfb when xvfb-run exists), or both (default)",
    )
    pack_boot.add_argument(
        "--mod-jar", type=Path, help="core mod jar (default: the one ./gradlew :mod:jar builds)"
    )
    pack_boot.add_argument("--pack-dir", type=Path, help="packwiz pack to install (default: pack/)")
    pack_boot.add_argument(
        "--optional",
        choices=("both", "with", "without"),
        default="both",
        help="boot with the optional mods, without them, or both (default)",
    )
    art = commands.add_parser("art", help="texture pipeline: palette cleanup and the review gallery")
    art_commands = art.add_subparsers(dest="art_command", required=True)
    art_clean = art_commands.add_parser(
        "clean", help="snap a generated PNG to the texture grid and the project palette"
    )
    art_clean.add_argument("source", type=Path, help="generated PNG")
    art_clean.add_argument("target", type=Path, help="where to write the cleaned PNG")
    art_gallery = art_commands.add_parser(
        "gallery", help="build an HTML review gallery from <dir>/<subject>/<candidate>.png"
    )
    art_gallery.add_argument(
        "directory", type=Path, help="directory of candidates, e.g. art/spike5/candidates"
    )
    art_gallery.add_argument(
        "--out", type=Path, help="HTML file to write (default: .cache/art/<directory name>-gallery.html)"
    )
    for sub in (art_clean, art_gallery):
        sub.add_argument("--palette", type=Path, help=f"GIMP palette (default: {DEFAULT_PALETTE.as_posix()})")
        sub.add_argument(
            "--size",
            type=int,
            default=DEFAULT_SIZE,
            help=f"texture width in pixels (default: {DEFAULT_SIZE})",
        )

    gametest_log = commands.add_parser(
        "gametest-log",
        help="check the log of the last ./gradlew runGameTestServer; exit 1 on data loading errors",
    )
    gametest_log.add_argument("--log", type=Path, help="log file (default: run/gametest/logs/latest.log)")

    args = parser.parse_args(argv)
    paths = RepoPaths()

    if args.command == "art":
        return _art(args, paths)

    if args.command == "ids":
        target = vanilla.extract(args.version, paths)
        print(f"Wrote {paths.rel(target)}")
        return 0

    if args.command == "pack-boot":
        return _pack_boot(args, paths)

    if args.command == "gametest-log":
        return _gametest_log(args.log or gamelog.gametest_log(paths), paths)

    try:
        content, _ = load_and_validate(paths)
    except ValidationFailed as e:
        print(f"Content validation failed ({len(e.problems)} problem(s)):", file=sys.stderr)
        for problem in e.problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    if args.command == "validate":
        print(
            f"Content OK: {len(content.items)} item(s), {len(content.curves)} curve(s), "
            f"{len(content.worldgen)} worldgen file(s)."
        )
        return 0

    result = sync(generate_all(content, paths), paths, check=args.check)
    if args.check:
        if result.clean:
            print("Generated files are up to date.")
            return 0
        print("Generated files are out of date; run `uv run rf gen`:", file=sys.stderr)
        for line in result.lines():
            print(f"  {line}", file=sys.stderr)
        return 1
    for line in result.lines():
        print(line)
    print("Generated files are up to date." if result.clean else f"Updated {len(result.lines())} file(s).")
    return 0


def _gametest_log(log: Path, paths: RepoPaths) -> int:
    if not log.is_file():
        print(f"FAIL {paths.rel(log)} does not exist; run ./gradlew runGameTestServer first", flush=True)
        return 1
    problems = gamelog.gametest_problems(log.read_text(encoding="utf-8", errors="replace"))
    if problems:
        print(f"FAIL {paths.rel(log)}: {len(problems)} problem(s)", flush=True)
        for problem in problems:
            print("  " + problem.replace("\n", "\n  "), flush=True)
        return 1
    print(f"OK   {paths.rel(log)}: no data loading errors", flush=True)
    return 0


def _pack_boot(args: argparse.Namespace, paths: RepoPaths) -> int:
    variants = {
        "both": packboot.VARIANTS,
        "with": ("with-optional",),
        "without": ("without-optional",),
    }[args.optional]
    sides = packboot.SIDES if args.side == "both" else (args.side,)
    failed = False
    for side in sides:
        try:
            reports = packboot.run(
                paths, side=side, mod_jar=args.mod_jar, pack_dir=args.pack_dir, variants=variants
            )
        except packboot.PackBootError as e:
            failed = True
            print(f"FAIL {side}: could not start it: {e}", flush=True)
            continue
        for report in reports:
            if report.problems:
                failed = True
                print(
                    f"FAIL {report.label} ({report.seconds:.0f} s): {len(report.problems)} problem(s)",
                    flush=True,
                )
                for problem in report.problems:
                    print("  " + problem.replace("\n", "\n  "), flush=True)
            else:
                print(f"OK   {report.label}: clean boot in {report.seconds:.0f} s", flush=True)
    print(f"Logs: {paths.rel(paths.cache / 'pack-boot' / 'reports')}/")
    return 1 if failed else 0


def _art(args: argparse.Namespace, paths: RepoPaths) -> int:
    try:
        palette = load_gpl(args.palette or paths.root / DEFAULT_PALETTE)
        if args.art_command == "clean":
            width, height, pixels = decode_rgba(args.source.read_bytes())
            cleaned, report = clean(Image(width, height, tuple(pixels)), palette, args.size)
            args.target.parent.mkdir(parents=True, exist_ok=True)
            args.target.write_bytes(cleaned.png())
            print(
                f"Wrote {paths.rel(args.target)} ({cleaned.width}x{cleaned.height}, "
                f"{report.colors_before} -> {report.colors_after} colors, {report.changed_pixels} pixels recolored)"
            )
            return 0
        info, subjects = gallery.collect(args.directory, palette, args.size)
        if not subjects:
            print(
                f"No candidates found: expected {args.directory}/<subject>/<candidate>.png", file=sys.stderr
            )
            return 1
        out = args.out or paths.cache / "art" / f"{args.directory.name}-gallery.html"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(gallery.render(info, subjects, palette), encoding="utf-8")
        count = sum(len(s.candidates) for s in subjects)
        print(f"Wrote {paths.rel(out)} ({len(subjects)} subject(s), {count} candidate(s))")
        return 0
    except (OSError, PaletteError, PngError, TextureError, gallery.GalleryError) as e:
        print(f"rf art {args.art_command}: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
