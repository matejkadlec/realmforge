# art/rooms/

The import rules of the third-party dungeon rooms in `content/rooms/third_party/<source>/`
([ADR 0028](../../docs/decisions/0028-dungeon-rooms-generated-or-imported.md), A4c, RLM-120): one folder per source,
each with the `rules.yaml` that `rf rooms import` reads ([tools/README](../../tools/README.md#rooms)). The rules
record which pieces of a source are rooms (`include`, `exclude`, `max_size`) and how they become ours: renamed and
custom blocks, jigsaws, loot tables, entities. Each source's `LICENSE` and `NOTICE` (and `COPYING` for LGPL-3.0) sit
next to its rooms, not here.

## Sources

Every jar comes from Modrinth; the version id and the start of the file's sha512 identify it.

| Source folder | Project, version | License | Modrinth version | sha512 starts | Import from (in the jar) |
| --- | --- | --- | --- | --- | --- |
| `stonevaults` | Stonevaults 2.0.0+26.1-neoforge | MIT | `JFIYcTQq` | `1fdb9abd433e8fac` | `data/stonevaults/structure` |
| `dungeonz` | DungeonZ 1.3.0+1.21.1 | MIT | `BOTC37SN` | `3783a778bd38306c` | `data/dungeonz/structure` |
| `desert_dungeon` | Desert Dungeon (DungeonZ addon) 1.0.1 | MIT | `BHSd1fQe` | `08ac50635aac8036` | `data/dungeonz/structure` |
| `dungeon_now_loading` | Dungeon Now Loading 2.2 (Forge 1.20.1) | CC0 1.0 | `ejP7ljqP` | `ea46ccef1705594a` | `data/dungeonnowloading/structures` |
| `yungs_better_dungeons` | YUNG's Better Dungeons 26.1.2-NeoForge-6.1.1 | LGPL-3.0 | `e1qEvr8D` | `4e7a76ad46f8ca54` | `data/betterdungeons/structure` |
| `moogs_voyager_structures` | Moog's Voyager Structures 5.1.3 (universal-1.21) | LGPL-3.0 | `PiFoSPXI` | `80e9e4a6543e8fc7` | `data/mvs/structure/1_21_0/cathedral`, then `1_21_9/cathedral` copied over it, as one folder `cathedral` |
| `mo_structures` | Mo' Structures 1.6.0+1.21.8 (NeoForge) | LGPL-3.0 | `3ad5pb5R` | `bf6567162a7f1a2d` | `data/mostructures/structure` |

Not imported: Moog's End Structures 2.1.1 (LGPL-3.0), which the research listed too. Its pieces are a ship's
sections of up to 48 blocks per axis and surface buildings without connectors; none is a room.

Each license was checked at the version imported (2026-10-14); the `NOTICE` says where it is stated, and where
sources disagreed (Moog's Voyager Structures: LGPL-3.0 in the jar and repository, MIT on Modrinth; Dungeon Now
Loading: CC0 in the jar, MIT on Modrinth, repository gone) the import follows the jar. None of the imported pieces
copies a vanilla template: each was compared with 26.1.2's templates of the same footprint. The closest copies,
Mo' Structures' abandoned village temples, are not imported.

## Importing a source again

Unpack the jar's folder outside the repository and import it with the rules; a re-import keeps the sidecars' pools,
tags and weight, and skips the rooms the gallery's review rejected:

```bash
unzip -q stonevaults-2.0.0+26.1-neoforge.jar 'data/stonevaults/structure/*' -d /tmp/stonevaults
uv run rf rooms import /tmp/stonevaults/data/stonevaults/structure --to content/rooms/third_party/stonevaults --rules art/rooms/stonevaults/rules.yaml
uv run rf gen
```

A newer version of a source updates its `NOTICE` (version, file, what changed) and this table.
