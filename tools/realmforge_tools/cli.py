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
uv run rf sound credits                credits left this ElevenLabs period, the reset date and the store's ledger
uv run rf sound gen SUBJECT EVENT ...  ElevenLabs candidates into the sound store (or --plan FILE), credits guarded
uv run rf sound list [FILTER]          the store's candidates with their measurements and scores, best first
uv run rf sound gallery                the store's review gallery (<store>/gallery/index.html)
uv run rf sound measure [FILTER]       measure the store's candidates again after the checks changed
uv run rf sound pick ID                convert a candidate into art/sounds/<subject>/<name>.ogg and record its source
uv run rf sound reject ID              mark a candidate rejected in its sidecar
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import time
from pathlib import Path

from realmforge_tools import apotheosis, gamelog, packboot, vanilla
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
from realmforge_tools.rooms.generator import paths as room_paths
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
from realmforge_tools.sound import art as sound_art
from realmforge_tools.sound import convert as sound_convert
from realmforge_tools.sound import elevenlabs
from realmforge_tools.sound import gallery as sound_gallery
from realmforge_tools.sound import rank as sound_rank
from realmforge_tools.sound import runner as sound_runner
from realmforge_tools.sound import store as sound_store
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
    ids_commands.add_parser(
        "apotheosis",
        help="snapshot the data of the Apotheosis jar pack/mods/apotheosis.pw.toml names (its affixes, gems, ...)",
    )
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
        "--game",
        action="store_true",
        help="count as rf gen does: also drop spots that would leave a lit cell dark or stand on a generated room's "
        "paths (the design's report had neither rule)",
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

    sound = commands.add_parser(
        "sound",
        help="our sounds: ElevenLabs candidates in the sound store, the gallery, picks into art/sounds/",
    )
    sound_commands = sound.add_subparsers(dest="sound_command", required=True)
    sound_commands.add_parser(
        "credits", help="credits left this period, the reset date and the ledger's total"
    )
    sound_gen = sound_commands.add_parser(
        "gen", help="generate candidates with ElevenLabs into the sound store (each request credit-guarded)"
    )
    sound_gen.add_argument("subject", nargs="?", help="content id the sound belongs to, e.g. frostbolt")
    sound_gen.add_argument("event", nargs="?", help="the event's name, e.g. impact")
    sound_gen.add_argument(
        "--plan", type=Path, help="a YAML plan of several events (resumable); see art/README.md"
    )
    sound_gen.add_argument(
        "--dry-run", action="store_true", help="with --plan: say what it would make and cost"
    )
    sound_gen.add_argument("--prompt", help="what to generate")
    sound_gen.add_argument("--ticket", help="the RLM ticket it is for, e.g. RLM-445")
    sound_gen.add_argument("--sound-event", help="the sound event id it is meant for")
    sound_gen.add_argument("--note", default="", help="the moment it is for, in a few words")
    sound_gen.add_argument("--music", action="store_true", help="Eleven Music instead of a sound effect")
    sound_gen.add_argument(
        "--duration", type=float, help="seconds (effect 0.5-30, music 3-600; effect default: auto)"
    )
    sound_gen.add_argument("--loop", action="store_true", help="a seamless loop (effects)")
    sound_gen.add_argument("--influence", type=float, help="prompt influence 0-1 (ElevenLabs default 0.3)")
    sound_gen.add_argument("--variants", type=int, default=4, help="how many candidates (default 4)")
    sound_gen.add_argument("--model", help="model id (default eleven_text_to_sound_v2, music_v1 for music)")
    sound_gen.add_argument(
        "--format", dest="output_format", help="output format (default wav_48000, music mp3)"
    )
    sound_gen.add_argument(
        "--reserve",
        type=int,
        default=1000,
        help="refuse a request that would leave fewer credits (default 1000)",
    )
    sound_list = sound_commands.add_parser("list", help="the store's candidates, best score first per event")
    sound_list.add_argument("filter", nargs="?", default="", help="part of an id or a ticket, e.g. RLM-445")
    sound_commands.add_parser("gallery", help="write the review gallery into the store")
    sound_measure = sound_commands.add_parser(
        "measure", help="measure the store's candidates again (after the checks changed)"
    )
    sound_measure.add_argument("filter", nargs="?", default="", help="part of an id or a ticket")
    sound_pick = sound_commands.add_parser(
        "pick", help="convert a candidate into art/sounds/ and record its source"
    )
    sound_pick.add_argument("id", help="the candidate, <subject>/<event>/<nn>")
    sound_pick.add_argument("--name", help="the file's name (default <event>_<n>, the next free)")
    sound_pick.add_argument("--replace", action="store_true", help="overwrite the file (a swapped pick)")
    sound_pick.add_argument("--loudness", type=float, help="loudness target in LUFS (default -18, loops -26)")
    sound_pick.add_argument(
        "--by", default="Matěj", help="who picked it; an agent writes its provisional note"
    )
    sound_reject = sound_commands.add_parser("reject", help="mark a candidate rejected")
    sound_reject.add_argument("id", help="the candidate, <subject>/<event>/<nn>")
    sound_reject.add_argument("--note", default="", help="why")

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

    if args.command == "sound":
        return _sound(args, paths)

    if args.command == "ids":
        if args.ids_command == "apotheosis":
            target = apotheosis.extract(paths)
        else:
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
            f"{len(content.chapters)} chapter(s), {len(content.quests)} quest(s), "
            f"{len(content.kill_xp_multipliers)} kill XP multiplier(s), "
            f"{len(content.effects)} effect(s), {len(content.spells)} spell(s), {len(content.mobs)} mob(s), "
            f"{len(content.bosses)} boss(es), {len(content.merchants)} merchant(s), "
            f"{len(content.container_loot)} container loot table(s), "
            f"{len(content.sounds)} sound group(s)."
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
    if any(path.startswith("pack/") for path in result.added + result.changed + result.removed):
        return _refresh_pack(paths)
    return 0


def _refresh_pack(paths: RepoPaths) -> int:
    """``packwiz refresh`` once ``rf gen`` changed files in pack/ (the quest book), so pack/index.toml lists them as
    CI expects (architecture 6.2)."""
    packwiz = shutil.which("packwiz")
    if packwiz is None:
        print("pack/ changed and packwiz is not on PATH: run `cd pack && packwiz refresh`", file=sys.stderr)
        return 1
    if subprocess.run([packwiz, "refresh"], cwd=paths.root / "pack", check=False).returncode != 0:
        print("packwiz refresh failed in pack/", file=sys.stderr)
        return 1
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

    def generated(key: str) -> tuple[dict[str, str] | None, frozenset[tuple[int, int]]]:
        if room_candidates.is_candidates_folder(directory):
            spec = specs.get(key.split("/")[0])
        elif room_candidates.is_candidates_folder(directory.parent):
            spec = specs.get(directory.name)
        else:
            spec = None
        if spec is None:
            return None, frozenset()
        theme = themes.get(spec.theme)
        return (theme.blocks.model_dump() if theme is not None else None), frozenset(room_paths(spec))

    def spots_of(
        room: room_gallery.GalleryRoom,
    ) -> tuple[tuple[room_gallery.SpotGroup, ...], Structure | None]:
        assert room.structure is not None
        role = room_role(room.sidecar.tags) if room.sidecar is not None else None
        fits = dungeons.room_fits(role, content.set_pieces)
        if not fits:
            return (), None
        mirrorable = [any(piece.model.variants.mirror for piece in group) for _, group in fits]
        view = RoomView(room.structure, *generated(room.key))
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
    generated = dungeons.generated_rooms(content)
    rooms = []
    for room in content.rooms:
        parts = room.id.split("/")
        source = (
            "generated" if parts[0] == "generated" else parts[1] if parts[0] == "third_party" else parts[0]
        )
        rooms.append((source, room, generated.get(room.id, (None, frozenset()))))
    split = {
        source
        for source in {s for s, _, _ in rooms}
        if len({bool(room.model.pools) for s, room, _ in rooms if s == source}) > 1
    }
    report_rooms = []
    for source, room, (theme, spec_paths) in rooms:
        group = source
        if source in split:
            group = (
                f"{source}, pool {', '.join(room.model.pools)}" if room.model.pools else f"{source}, no pool"
            )
        kept_clear = spec_paths if args.game else frozenset()
        report_rooms.append(ReportRoom(room.id, group, room.structure, theme, kept_clear))
    masks = [NAMED_MASKS[name] for name in args.masks or NAMED_MASKS]
    started = time.monotonic()
    rows = mask_report(report_rooms, masks, light=args.game)
    sizes: dict[str, int] = {}
    for room in report_rooms:
        sizes[room.group] = sizes.get(room.group, 0) + 1
    groups = sorted(sizes)
    print(
        f"Spots per mask in {len(report_rooms)} rooms, as rooms with at least one spot / spots{' (as rf gen counts them)' if args.game else ''}:"
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


def _credits_line(client: elevenlabs.Client, root: Path) -> str:
    subscription = client.subscription()
    spent = sound_store.spent(root, subscription.reset_unix)
    left = elevenlabs.credits_left(
        subscription, sound_store.used(root, subscription.reset_unix, subscription.count)
    )
    return (
        f"{left} of {subscription.limit} credits left ({subscription.tier}, {subscription.status}; the "
        f"subscription shows {subscription.count} used); resets {subscription.reset_date()}; the store's ledger "
        f"records {spent} charged this period"
    )


def _sound(args: argparse.Namespace, paths: RepoPaths) -> int:
    root = sound_store.store_root()
    try:
        if args.sound_command == "credits":
            print(_credits_line(elevenlabs.Client(elevenlabs.api_key()), root))
            return 0
        if args.sound_command == "gen":
            client = elevenlabs.Client(elevenlabs.api_key())
            if args.plan:
                specs = sound_runner.load_plan(args.plan)
                sound_runner.run_plan(specs, root, client, reserve=args.reserve, dry_run=args.dry_run)
            else:
                if not (args.subject and args.event and args.prompt and args.ticket):
                    print(
                        "rf sound gen: give SUBJECT EVENT --prompt --ticket, or --plan FILE", file=sys.stderr
                    )
                    return 2
                spec = sound_runner.Spec(
                    subject=args.subject,
                    event=args.event,
                    prompt=args.prompt,
                    ticket=args.ticket,
                    sound_event=args.sound_event,
                    note=args.note,
                    kind="music" if args.music else "sfx",
                    duration=args.duration,
                    loop=args.loop,
                    influence=args.influence,
                    variants=args.variants,
                    model=args.model,
                    output_format=args.output_format,
                )
                sound_runner.run(spec, root, client, reserve=args.reserve)
            print(f"Store: {root}")
            return 0
        if args.sound_command == "list":
            found = [
                c
                for c in sound_store.candidates(root)
                if args.filter in c.id or args.filter == c.ticket or args.filter in (c.sound_event or "")
            ]
            groups: dict[tuple[str, str], list[sound_store.Candidate]] = {}
            for c in found:
                groups.setdefault((c.subject, c.event), []).append(c)
            for (subject, event), group in sorted(groups.items()):
                print(f"{subject}/{event} ({group[0].ticket}):")
                for c, score in sound_rank.ranked(group):
                    m = c.measured
                    stats = (
                        f"{m.sound_seconds:5.2f} s {m.peak_dbfs:6.1f} dBFS {m.loudness_lufs:6.1f} LUFS"
                        if m
                        else ""
                    )
                    why = f"  ({', '.join(score.reasons)})" if score.reasons else ""
                    print(f"  {c.id:<40} {score.value:5.1f}  {stats}{why}")
            print(f"{len(found)} candidate(s) in {root}")
            return 0
        if args.sound_command == "gallery":
            try:
                credits = _credits_line(elevenlabs.Client(elevenlabs.api_key()), root)
            except elevenlabs.ElevenLabsError as e:
                credits = f"credits unknown ({e})"
            page = sound_gallery.write(root, sound_art.read_sources(paths), credits)
            print(f"Wrote {page}")
            windows = sound_gallery.windows_path(page)
            if windows:
                print(f"From Windows: {windows}")
            return 0
        if args.sound_command == "measure":
            count = 0
            for c in sound_store.candidates(root):
                if args.filter in c.id or args.filter == c.ticket:
                    measured, _ = sound_runner.measure(sound_store.audio_path(root, c).read_bytes())
                    sound_store.save(root, c.model_copy(update={"measured": measured}))
                    count += 1
            print(f"Measured {count} candidate(s) in {root}")
            return 0
        if args.sound_command == "pick":
            candidate = sound_store.load(root, args.id)
            name, report = sound_art.pick(
                paths,
                root,
                candidate,
                name=args.name,
                loudness=args.loudness,
                picked_by=args.by,
                replace=args.replace,
            )
            print(
                f"Wrote art/sounds/{name}.ogg from {candidate.id}: cut {report.cut_before:.2f} s before and "
                f"{report.cut_after:.2f} s after, {report.gain_db:+.1f} dB to {report.loudness_lufs:.1f} LUFS "
                f"(peak {report.peak_dbfs:.1f} dBFS{', limited by the peak' if report.limited_by_peak else ''})."
            )
            print(f"Play it from an event in content/sounds/ (file: {name}), then run uv run rf gen.")
            return 0
        candidate = sound_store.load(root, args.id)
        sound_store.save(root, candidate.model_copy(update={"review": "rejected", "review_note": args.note}))
        print(f"Rejected {candidate.id}")
        return 0
    except (
        OSError,
        ValueError,
        elevenlabs.ElevenLabsError,
        sound_convert.ConvertError,
    ) as e:
        print(f"rf sound {args.sound_command}: {e}", file=sys.stderr)
        return 1


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
