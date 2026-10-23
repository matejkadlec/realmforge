"""``rf``: the Realmforge content pipeline command line.

uv run rf validate          validate content/ (schema, ids, references)
uv run rf gen               validate, then regenerate every generated file
uv run rf gen --check       exit 1 if any generated file is out of date (writes nothing)
uv run rf ids vanilla       re-extract the vanilla ID universe from the official server jar (needs Java 25)
uv run rf pack-boot         boot the whole pack plus our mod jars on a server and a client (needs Java 25)
uv run rf gametest-log      exit 1 if the last GameTest run logged a data loading error (after runGameTestServer)
uv run rf art clean SRC DST snap a generated texture to the 16x16 grid and the project palette
uv run rf art gallery DIR   build the review gallery for the candidate textures in DIR
uv run rf rooms import SRC --to DIR    import .nbt, .schem and .litematic rooms into DIR with draft sidecars
uv run rf rooms gallery [DIR]          render the rooms in DIR (default content/rooms) for review (needs Vulkan)
uv run rf rooms review DIR             merge decisions copied from the gallery into DIR/review.yaml
uv run rf rooms candidates [SPEC...]   build seed candidates of the room specs into .cache/rooms/candidates
uv run rf set-pieces report            count the spots of the jig's masks in every room (the design's mask report)
uv run rf set-pieces import FILE --id ID   import a workshop jig save as content/set_pieces/ID (--from-world WORLD)
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

from realmforge_tools import gamelog, packboot, vanilla
from realmforge_tools.art import gallery
from realmforge_tools.art.palette import DEFAULT_PALETTE, PaletteError, load_gpl
from realmforge_tools.art.texture import DEFAULT_SIZE, Image, TextureError, clean
from realmforge_tools.content import load_content
from realmforge_tools.formats.png import PngError, decode_rgba
from realmforge_tools.generate import dungeons, generate_all, sync
from realmforge_tools.id_universe import IdUniverse, load_vanilla
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import MINECRAFT_VERSION, RepoPaths
from realmforge_tools.rooms import candidates as room_candidates
from realmforge_tools.rooms import gallery as room_gallery
from realmforge_tools.rooms import importer
from realmforge_tools.rooms import set_piece_import as piece_import
from realmforge_tools.rooms.render import CUT_HEIGHT, RenderError, RoomRenderer
from realmforge_tools.rooms.set_pieces import (
    NAMED_MASKS,
    ReportRoom,
    RoomView,
    mask_report,
    match,
    pattern_cells,
)
from realmforge_tools.rooms.set_pieces import dressed as set_piece_dressing
from realmforge_tools.rooms.structure import Structure
from realmforge_tools.schema.dungeon import room_role
from realmforge_tools.schema.room import REVIEW_FILE, ROOMS_DIR
from realmforge_tools.schema.set_piece import SET_PIECES_DIR
from realmforge_tools.validate import load_and_validate, universe_with_content


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
        help="boot the whole pack plus our mod jars on a server and a client; exit 1 on errors",
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

    rooms = commands.add_parser(
        "rooms", help="dungeon rooms: import, generated candidates, the review gallery, review decisions"
    )
    rooms_commands = rooms.add_subparsers(dest="rooms_command", required=True)
    rooms_import = rooms_commands.add_parser(
        "import", help="turn .nbt, .schem and .litematic files into rooms with draft sidecars"
    )
    rooms_import.add_argument(
        "sources", type=Path, nargs="+", help="room files, or folders whose room files are all imported"
    )
    rooms_import.add_argument(
        "--to",
        type=Path,
        required=True,
        dest="target",
        help="folder for the rooms, e.g. content/rooms/third_party/<source>",
    )
    rooms_import.add_argument(
        "--rules",
        type=Path,
        help="YAML import rules: blocks, block entities, jigsaws, loot tables, entities, the files to take",
    )
    rooms_import.add_argument(
        "--ours",
        action="store_true",
        help="the rooms are our own; without it, a folder in content/rooms/ must be third_party/<source>/",
    )
    rooms_gallery = rooms_commands.add_parser(
        "gallery", help="render every room below DIR headless and write a page to keep or reject them"
    )
    rooms_gallery.add_argument(
        "directory", type=Path, nargs="?", help=f"folder of .nbt rooms (default: content/{ROOMS_DIR})"
    )
    rooms_gallery.add_argument(
        "--out",
        type=Path,
        help="output folder, replaced on every run (default: .cache/rooms/gallery/<folder>)",
    )
    rooms_gallery.add_argument(
        "--cut",
        type=int,
        default=CUT_HEIGHT,
        help=f"height of the cutaway views above the room's bottom (default: {CUT_HEIGHT})",
    )
    rooms_gallery.add_argument("--size", type=int, default=512, help="render size in pixels (default: 512)")
    rooms_review = rooms_commands.add_parser(
        "review", help=f"merge decisions copied from the gallery into DIR/{REVIEW_FILE}"
    )
    rooms_review.add_argument("directory", type=Path, help="the folder the gallery showed")
    rooms_review.add_argument(
        "--from", dest="source", type=Path, help="file holding the copied decisions (default: standard input)"
    )

    rooms_candidates = rooms_commands.add_parser(
        "candidates", help="build the next seed candidates of the room specs for the gallery review"
    )
    rooms_candidates.add_argument("specs", nargs="*", help="room spec ids (default: every spec)")
    rooms_candidates.add_argument(
        "--count",
        type=int,
        default=room_candidates.DEFAULT_COUNT,
        help=f"candidates per spec (default: {room_candidates.DEFAULT_COUNT})",
    )
    rooms_candidates.add_argument(
        "--kept",
        action="store_true",
        help="also build the kept seeds, to review a generator change on them (with --count 0: only those)",
    )
    rooms_candidates.add_argument(
        "--out", type=Path, help="output folder, replaced on every run (default: .cache/rooms/candidates)"
    )

    set_pieces = commands.add_parser(
        "set-pieces",
        help="set pieces: the mask report over the rooms, and importing the workshop jig's saves",
    )
    piece_commands = set_pieces.add_subparsers(dest="pieces_command", required=True)
    pieces_report = piece_commands.add_parser(
        "report", help="spots per mask in every room, per source and pool, and the walls they stand against"
    )
    pieces_report.add_argument(
        "masks", nargs="*", help=f"jig masks to count (default: all of them: {', '.join(NAMED_MASKS)})"
    )
    pieces_report.add_argument(
        "--light",
        action="store_true",
        help="also drop spots that would leave a lit cell dark, as rf gen does",
    )
    pieces_import = piece_commands.add_parser(
        "import", help="turn a save of the workshop jig into a set piece with a drafted fit rule"
    )
    pieces_import.add_argument(
        "file",
        type=Path,
        nargs="?",
        help="the save: <world>/generated/realmforge_qa/structure/set_piece/<name>.nbt",
    )
    pieces_import.add_argument(
        "--id", dest="piece_id", help="the piece's id, its file name in content/set_pieces/"
    )
    pieces_import.add_argument(
        "--from-world",
        type=Path,
        dest="world",
        help="import every save of this world (a folder, or a world name in run/client/saves) not imported yet, "
        "each under its save's name",
    )
    pieces_import.add_argument(
        "--to", type=Path, dest="target", help=f"folder for the pieces (default: content/{SET_PIECES_DIR})"
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

    if args.command == "rooms":
        return _rooms(args, paths)

    if args.command == "set-pieces":
        return _set_pieces(args, paths)

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
            f"{len(content.stats)} stat(s), {len(content.stat_rules)} stat rule(s), "
            f"{len(content.dimensions)} dimension rule set(s), {len(content.races)} race(s), "
            f"{len(content.classes)} class(es), {len(content.worldgen)} worldgen file(s), "
            f"{len(content.rooms)} room(s), {len(content.room_specs)} room spec(s), "
            f"{len(content.room_themes)} room theme(s), {len(content.set_pieces)} set piece(s), "
            f"{len(content.dungeons)} dungeon type(s), "
            f"{len(content.chapters)} chapter(s), {len(content.kill_xp_multipliers)} kill XP multiplier(s), "
            f"{len(content.effects)} effect(s), {len(content.spells)} spell(s)."
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


def _rooms(args: argparse.Namespace, paths: RepoPaths) -> int:
    try:
        if args.rooms_command == "import":
            return _rooms_import(args, paths)
        if args.rooms_command == "review":
            text = args.source.read_text(encoding="utf-8") if args.source else sys.stdin.read()
            # A kept seed that this folder's candidates --kept run refused has no room in it; its reject still
            # freezes. Any other seed without a room is refused.
            refused_kept = (
                room_candidates.refused_kept_keys(args.directory)
                if room_candidates.is_candidates_folder(args.directory)
                else set()
            )
            decisions, changed = room_gallery.merge_review(args.directory, text, paths, refused_kept)
            kept = sum(1 for value in decisions.values() if value == "keep")
            print(
                f"Wrote {paths.rel(args.directory / REVIEW_FILE)}: {changed} decision(s) changed, "
                f"{kept} kept and {len(decisions) - kept} rejected in total"
            )
            if room_candidates.is_candidates_folder(args.directory):
                for frozen in room_candidates.freeze(decisions, paths):
                    print(
                        f"Froze content/room_specs/{frozen.spec}.yaml: kept {list(frozen.kept)}, "
                        f"rejected {list(frozen.rejected)}"
                    )
                print("Run uv run rf gen to write the kept rooms.")
            return 0
        if args.rooms_command == "candidates":
            return _rooms_candidates(args, paths)
        return _rooms_gallery(args, paths)
    except (
        OSError,
        importer.RoomImportError,
        room_gallery.GalleryError,
        room_candidates.CandidatesError,
        RenderError,
    ) as e:
        print(f"rf rooms {args.rooms_command}: {e}", file=sys.stderr)
        return 1


def _rooms_candidates(args: argparse.Namespace, paths: RepoPaths) -> int:
    if args.count < (0 if args.kept else 1):
        print("rf rooms candidates: --count must be at least 1 (0 with --kept)", file=sys.stderr)
        return 1
    try:
        # A kept seed that broke is refused below rather than stopping the run, so the review can reject it.
        broken: dict[tuple[str, int], list[str]] = {}
        content, universe = load_and_validate(paths, broken_kept_seeds=broken if args.kept else None)
    except ValidationFailed as e:
        print(
            f"rf rooms candidates: content/ does not validate ({len(e.problems)} problem(s)):",
            file=sys.stderr,
        )
        for problem in e.problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    out_dir = args.out or paths.cache / "rooms" / "candidates"
    results = room_candidates.write_candidates(
        content, universe, out_dir, args.count, args.specs or None, kept=args.kept, broken=broken
    )
    for result in results:
        print(f"{result.spec}: seeds {result.written or 'none'}")
        for seed, reason in result.refused:
            print(f"  REFUSED seed {seed}: {reason}")
    written = sum(len(result.written) for result in results)
    print(
        f"Wrote {written} candidate(s) to {paths.rel(out_dir)}. Next: uv run rf rooms gallery {paths.rel(out_dir)}, "
        f"then uv run rf rooms review {paths.rel(out_dir)} with the decisions."
    )
    return 0


def _rooms_import(args: argparse.Namespace, paths: RepoPaths) -> int:
    rules = importer.load_rules(args.rules)
    results = importer.import_rooms(
        args.sources, args.target, rules, _room_universe(paths), paths, ours=args.ours
    )
    if not results:
        print("No room files found (.nbt, .schem, .litematic).", file=sys.stderr)
        return 1
    for result in results:
        if result.excluded:
            continue
        if result.rejected_by is not None:
            print(
                f"SKIP {result.source}: {paths.rel(result.rejected_by)} rejects {paths.rel(result.template)}"
            )
            continue
        if result.written and result.structure is not None:
            size = " x ".join(map(str, result.structure.size))
            print(f"OK   {paths.rel(result.template)} ({size}, from {result.source.name})")
        else:
            print(f"FAIL {result.source}: not written, {len(result.problems)} problem(s)")
        for note in result.notes:
            print(f"     {note}")
        for problem in result.problems:
            print(f"     {problem}")
    excluded = sum(1 for r in results if r.excluded)
    skipped = sum(1 for r in results if r.rejected_by is not None)
    failed = sum(1 for r in results if not r.written and r.rejected_by is None and not r.excluded)
    imported = len(results) - excluded - skipped - failed
    print(
        f"Imported {imported} of {len(results) - excluded} room(s)"
        f"{f', skipped {skipped} the review rejects' if skipped else ''}"
        f"{f', left out {excluded} file(s) the rules exclude' if excluded else ''}."
    )
    return 1 if failed else 0


def _room_universe(paths: RepoPaths) -> IdUniverse:
    """Vanilla plus our content; vanilla alone while content/ does not load. The rooms add only their ids and pools,
    so their templates are not read."""
    vanilla = load_vanilla(paths)
    try:
        return universe_with_content(vanilla, load_content(paths, read_templates=False))
    except ValidationFailed:
        print(
            "Note: content/ does not validate, so rooms are checked against vanilla ids only.",
            file=sys.stderr,
        )
        return vanilla


def _rooms_gallery(args: argparse.Namespace, paths: RepoPaths) -> int:
    directory = args.directory or paths.content / ROOMS_DIR
    if not directory.is_dir() or not room_gallery.room_files(directory):
        print(f"No rooms (*.nbt) below {paths.rel(directory)}.", file=sys.stderr)
        return 1
    name = paths.rel(directory).strip("/").replace("/", "-") or directory.name
    out_dir = args.out or paths.cache / "rooms" / "gallery" / name
    started = time.monotonic()
    client_jar = vanilla.fetch_client_jar(MINECRAFT_VERSION, paths.cache / "vanilla" / MINECRAFT_VERSION)
    renderer = RoomRenderer(client_jar, args.size)
    page, rooms = room_gallery.build(
        directory,
        out_dir,
        renderer,
        f"Rooms: {paths.rel(directory)}",
        args.cut,
        _gallery_spots(paths, directory),
    )
    unreadable = [room for room in rooms if room.structure is None]
    print(
        f"Wrote {paths.rel(page)} ({len(rooms)} room(s) in {time.monotonic() - started:.1f} s"
        f"{f', {len(unreadable)} unreadable' if unreadable else ''})"
    )
    for room in unreadable:
        print(f"  {room.key}: {room.error}", file=sys.stderr)
    return 1 if unreadable else 0


def _gallery_spots(paths: RepoPaths, directory: Path) -> room_gallery.SpotsOf | None:
    """Each gallery room's set-piece spots, by its sidecar's role (any role without one), and the room with the
    heaviest piece of each pattern in it; None without set pieces. A candidate of a room spec reads its walls by the
    spec's theme; any other room is read as imported."""
    try:
        content = load_content(paths)
    except ValidationFailed:
        print("Note: content/ does not load, so the gallery shows no set-piece spots.", file=sys.stderr)
        return None
    if not content.set_pieces:
        return None
    themes = {f"realmforge:{entry.model.id}": entry.model for entry in content.room_themes}
    specs = {entry.model.id: entry.model for entry in content.room_specs}

    def theme_of(key: str) -> dict[str, str] | None:
        if room_candidates.is_candidates_folder(directory):
            spec_id = key.split("/")[0]
        elif room_candidates.is_candidates_folder(directory.parent):
            spec_id = directory.name
        else:
            return None
        spec = specs.get(spec_id)
        theme = themes.get(spec.theme) if spec is not None else None
        return theme.blocks.model_dump() if theme is not None else None

    def spots_of(
        room: room_gallery.GalleryRoom,
    ) -> tuple[tuple[room_gallery.SpotGroup, ...], Structure | None]:
        assert room.structure is not None
        role = room_role(room.sidecar.tags) if room.sidecar is not None else None
        fits = dungeons.room_fits(role, content.set_pieces)
        if not fits:
            return (), None
        mirrorable = [any(piece.model.variants.mirror for piece in group) for _, group in fits]
        view = RoomView(room.structure, theme_of(room.key))
        patterns = match(view, [fit for fit, _ in fits], mirrorable=mirrorable)
        groups = []
        picks = {}
        for pattern in patterns:
            pieces = dungeons.pattern_pieces(fits, pattern)
            if not pieces:
                continue
            fit = fits[pattern.fit][0]
            heaviest = min(pieces, key=lambda piece: (-piece.model.weight, piece.id))
            picks[id(pattern)] = (heaviest.structure, heaviest.model.variants.mirror)
            cells = tuple(
                sorted(
                    pos
                    for spot in pattern.spots
                    for pos in pattern_cells(fit, spot)[0]
                    if pos[1] == spot.pos[1]
                )
            )
            groups.append(
                room_gallery.SpotGroup(
                    ", ".join(piece.id for piece in pieces),
                    pattern.fit,
                    pattern.kind,
                    tuple((spot.pos, spot.facing) for spot in pattern.spots),
                    cells,
                )
            )
        dressed = set_piece_dressing(room.structure, patterns, lambda pattern: picks.get(id(pattern)))
        return tuple(groups), dressed

    return spots_of


def _set_pieces(args: argparse.Namespace, paths: RepoPaths) -> int:
    try:
        if args.pieces_command == "report":
            return _set_pieces_report(args, paths)
        return _set_pieces_import(args, paths)
    except (OSError, piece_import.SetPieceImportError) as e:
        print(f"rf set-pieces {args.pieces_command}: {e}", file=sys.stderr)
        return 1


def _set_pieces_report(args: argparse.Namespace, paths: RepoPaths) -> int:
    unknown = [name for name in args.masks if name not in NAMED_MASKS]
    if unknown:
        print(
            f"rf set-pieces report: unknown mask {', '.join(unknown)}; known: {', '.join(NAMED_MASKS)}",
            file=sys.stderr,
        )
        return 1
    try:
        content = load_content(paths)
    except ValidationFailed as e:
        print(f"rf set-pieces report: content/ does not load ({len(e.problems)} problem(s))", file=sys.stderr)
        return 1
    themes = {f"realmforge:{entry.model.id}": entry.model for entry in content.room_themes}
    specs = {entry.model.id: entry.model for entry in content.room_specs}
    rooms = []
    for room in content.rooms:
        parts = room.id.split("/")
        theme = None
        if parts[0] == "generated":
            source = "generated"
            spec_theme = themes.get(specs[parts[1]].theme)
            theme = spec_theme.blocks.model_dump() if spec_theme is not None else None
        else:
            source = parts[1] if parts[0] == "third_party" and len(parts) > 2 else parts[0]
        rooms.append((source, room, theme))
    split = {
        source
        for source in {s for s, _, _ in rooms}
        if len({bool(room.model.pools) for s, room, _ in rooms if s == source}) > 1
    }
    report_rooms = []
    for source, room, theme in rooms:
        group = source
        if source in split:
            group = (
                f"{source}, pool {', '.join(room.model.pools)}" if room.model.pools else f"{source}, no pool"
            )
        report_rooms.append(ReportRoom(room.id, group, room.structure, theme))
    masks = [NAMED_MASKS[name] for name in args.masks or NAMED_MASKS]
    started = time.monotonic()
    rows = mask_report(report_rooms, masks, light=args.light)
    sizes: dict[str, int] = {}
    for room in report_rooms:
        sizes[room.group] = sizes.get(room.group, 0) + 1
    groups = sorted(sizes)
    print(
        f"Spots per mask in {len(report_rooms)} rooms, as rooms with at least one spot / spots{' (light check on)' if args.light else ''}:"
    )
    print()
    print(
        "| Mask | "
        + " | ".join(f"{group} ({sizes[group]})" for group in groups)
        + f" | All ({len(report_rooms)}) |"
    )
    print("| --- |" + " --- |" * (len(groups) + 1))
    for row in rows:
        cells = [
            f"{row.groups.get(group, (0, 0))[0]} / {row.groups.get(group, (0, 0))[1]}" for group in groups
        ]
        print(
            f"| {row.label} (`{row.name}`) | "
            + " | ".join(cells)
            + f" | **{row.total[0]} / {row.total[1]:,}** |"
        )
    print()
    print("Walls the spots stand against (block families, most spots first):")
    print()
    for row in rows:
        if row.materials:
            top = ", ".join(f"{name} {count:,}" for name, count in row.materials.most_common(4))
            print(f"- {row.label}: {top}")
    print()
    print(f"Counted in {time.monotonic() - started:.1f} s.")
    return 0


def _set_pieces_import(args: argparse.Namespace, paths: RepoPaths) -> int:
    target = args.target or paths.content / SET_PIECES_DIR
    universe = _room_universe(paths)
    if args.world is not None:
        if args.file is not None or args.piece_id is not None:
            print("rf set-pieces import: give a FILE with --id, or --from-world alone", file=sys.stderr)
            return 1
        world = args.world if args.world.is_dir() else paths.root / "run" / "client" / "saves" / args.world
        done = piece_import.imported_sources(target)
        saves = [save for save in piece_import.world_saves(world) if piece_import.save_name(save) not in done]
        if not saves:
            print(f"No new saves of the jig in {world / piece_import.JIG_FOLDER}.")
            return 0
        jobs = [(save, save.stem) for save in saves]
    else:
        if args.file is None or args.piece_id is None:
            print("rf set-pieces import: give a FILE with --id, or --from-world alone", file=sys.stderr)
            return 1
        jobs = [(args.file, args.piece_id)]
    failed = 0
    for save, piece_id in jobs:
        result = piece_import.import_piece(save, piece_id, target, universe)
        if result.written:
            drafted = "drafted its fit rule" if result.drafted else "kept its fit rule"
            print(f"OK   {paths.rel(result.template)} (from {save.name}; {drafted})")
        else:
            failed += 1
            print(f"FAIL {save}: not written, {len(result.problems)} problem(s)")
        for problem in result.problems:
            print(f"     {problem}")
    print(
        f"Imported {len(jobs) - failed} of {len(jobs)} piece(s). Next: fill in the tags of a drafted fit rule, then "
        "uv run rf gen."
    )
    return 1 if failed else 0


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
