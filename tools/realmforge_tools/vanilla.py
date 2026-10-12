"""Extract the vanilla ID universe from the official Minecraft server jar (``rf ids vanilla``).

1. Download the server jar for the pinned version from Mojang (sha1-verified, cached in ``.cache/``).
2. Run the vanilla data generator with ``--reports``; ``reports/registries.json`` lists every built-in registry.
3. Read the vanilla data pack embedded in the jar for data-driven registries (enchantments, worldgen, ...),
   other data (recipes, loot tables, advancements, structures) and tags.
4. Write ``tools/id_universe/vanilla-<version>.json`` (committed; validation reads only this file).
"""

from __future__ import annotations

import hashlib
import io
import json
import os
import shutil
import subprocess
import urllib.request
import zipfile
from pathlib import Path

from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.project import RepoPaths

VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

# Data pack folders (data/<namespace>/<folder>/...) whose files define ids, keyed by their registry-like name.
# Some exist only in mods or data packs (function, predicate, ...); listing them keeps classification complete.
DATA_FOLDERS = (
    "advancement",
    "banner_pattern",
    "cat_sound_variant",
    "cat_variant",
    "chat_type",
    "chicken_sound_variant",
    "chicken_variant",
    "cow_sound_variant",
    "cow_variant",
    "damage_type",
    "dialog",
    "dimension",
    "dimension_type",
    "enchantment",
    "enchantment_provider",
    "frog_variant",
    "function",
    "instrument",
    "item_modifier",
    "jukebox_song",
    "loot_table",
    "painting_variant",
    "pig_sound_variant",
    "pig_variant",
    "predicate",
    "recipe",
    "structure",
    "test_environment",
    "test_instance",
    "timeline",
    "trade_set",
    "trial_spawner",
    "trim_material",
    "trim_pattern",
    "villager_trade",
    "wolf_sound_variant",
    "wolf_variant",
    "world_clock",
    "zombie_nautilus_variant",
    "worldgen/biome",
    "worldgen/configured_carver",
    "worldgen/configured_feature",
    "worldgen/density_function",
    "worldgen/flat_level_generator_preset",
    "worldgen/multi_noise_biome_source_parameter_list",
    "worldgen/noise",
    "worldgen/noise_settings",
    "worldgen/placed_feature",
    "worldgen/processor_list",
    "worldgen/structure",
    "worldgen/structure_set",
    "worldgen/template_pool",
    "worldgen/world_preset",
)
_ID_FILE_SUFFIXES = (".json", ".nbt", ".mcfunction")
# Built-in optional feature packs (bundle, trade_rebalance) are not enabled by default; ignored for now.
_IGNORED_PREFIXES = ("datapacks/",)


def fetch_server_jar(version: str, cache_dir: Path) -> Path:
    """Download (or reuse) the official server jar for ``version``, verifying its sha1."""
    manifest = json.loads(_download(VERSION_MANIFEST_URL))
    entry = next((v for v in manifest["versions"] if v["id"] == version), None)
    if entry is None:
        raise ValueError(f"unknown Minecraft version {version}")
    server = json.loads(_download(entry["url"]))["downloads"]["server"]

    target = cache_dir / "server.jar"
    if target.exists() and _sha1(target.read_bytes()) == server["sha1"]:
        return target
    data = _download(server["url"])
    if _sha1(data) != server["sha1"]:
        raise RuntimeError("server jar sha1 mismatch")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return target


def run_reports(server_jar: Path, work_dir: Path) -> Path:
    """Run the vanilla data generator with ``--reports``. Returns the reports directory."""
    output = work_dir / "generated"
    shutil.rmtree(output, ignore_errors=True)
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / "java") if java_home else "java"
    command = [
        java,
        "-DbundlerMainClass=net.minecraft.data.Main",
        "-jar",
        str(server_jar.resolve()),
        "--reports",
        "--output",
        str(output.resolve()),
    ]
    # The bundler unpacks libraries into the working directory, so run inside the cache.
    subprocess.run(command, cwd=work_dir, check=True, stdout=subprocess.DEVNULL)
    return output / "reports"


def build_snapshot(version: str, server_jar: Path, reports_dir: Path) -> dict:
    report = json.loads((reports_dir / "registries.json").read_text(encoding="utf-8"))
    registries: dict[str, set[str]] = {name: set(data["entries"]) for name, data in report.items()}

    with zipfile.ZipFile(server_jar) as outer:
        inner_bytes = outer.read(f"META-INF/versions/{version}/server-{version}.jar")
    with zipfile.ZipFile(io.BytesIO(inner_bytes)) as inner:
        data_version = json.loads(inner.read("version.json"))["world_version"]
        names = inner.namelist()

    # Tags can target built-in and data-driven registries; match the longest registry path first.
    tag_registries = sorted(
        {name.removeprefix("minecraft:") for name in registries} | set(DATA_FOLDERS), key=len, reverse=True
    )
    data_folders = sorted(DATA_FOLDERS, key=len, reverse=True)
    tags: dict[str, set[str]] = {}
    unknown: set[str] = set()

    for name in names:
        if name.endswith("/") or not name.startswith("data/"):
            continue
        parts = name.split("/", 2)
        if len(parts) < 3:
            continue  # marker files such as data/.mcassetsroot
        _, namespace, rest = parts
        if rest.startswith(_IGNORED_PREFIXES) or not rest.endswith(_ID_FILE_SUFFIXES):
            continue
        stem = rest.rsplit(".", 1)[0]
        if stem.startswith("tags/"):
            folder = _match(stem.removeprefix("tags/"), tag_registries)
            if folder is None:
                unknown.add(stem)
                continue
            tag = f"{namespace}:{stem.removeprefix('tags/' + folder + '/')}"
            tags.setdefault(f"minecraft:{folder}", set()).add(tag)
        else:
            folder = _match(stem, data_folders)
            if folder is None:
                unknown.add(stem.split("/")[0])
                continue
            registries.setdefault(f"minecraft:{folder}", set()).add(
                f"{namespace}:{stem.removeprefix(folder + '/')}"
            )

    if unknown:
        raise RuntimeError(f"unclassified data paths (extend DATA_FOLDERS): {sorted(unknown)[:20]}")

    return {
        "minecraft_version": version,
        "data_version": data_version,
        "generated_by": "uv run rf ids vanilla",
        "registries": {name: sorted(ids) for name, ids in sorted(registries.items())},
        "tags": {name: sorted(ids) for name, ids in sorted(tags.items())},
    }


def extract(version: str, paths: RepoPaths) -> Path:
    """Full pipeline; returns the written snapshot path."""
    cache_dir = paths.cache / "vanilla" / version
    server_jar = fetch_server_jar(version, cache_dir)
    reports_dir = run_reports(server_jar, cache_dir)
    snapshot = build_snapshot(version, server_jar, reports_dir)
    target = paths.id_universe / f"vanilla-{version}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(dump_json(snapshot))
    return target


def _match(path: str, folders: list[str]) -> str | None:
    return next((folder for folder in folders if path.startswith(folder + "/")), None)


def _download(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read()


def _sha1(data: bytes) -> str:
    return hashlib.sha1(data).hexdigest()
