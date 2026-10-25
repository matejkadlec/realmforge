# art/rooms/

The import rules of the third-party dungeon rooms in `content/rooms/third_party/<source>/`
([ADR 0028](../../docs/decisions/0028-dungeon-rooms-generated-or-imported.md), A4c, RLM-120): one folder per source,
each with the `rules.yaml` that `rf rooms import` reads ([tools/README](../../tools/README.md#rooms)). The rules
record which pieces of a source are rooms (`include`, `exclude`, `max_size`) and how they become ours: renamed and
custom blocks, jigsaws, loot tables, entities, and for the plainest sets the variety and decorations of `dress`.
Each source's `LICENSE` and `NOTICE` (and `COPYING` for LGPL-3.0) sit next to its rooms, not here.

## Sources

Every jar comes from Modrinth; the version id and the start of the file's sha512 identify it.

| Source folder | Project, version | License | Modrinth version | sha512 starts | Import from (in the jar) |
| --- | --- | --- | --- | --- | --- |
| `stonevaults` | Stonevaults 2.0.0+26.1-neoforge | MIT | `JFIYcTQq` | `1fdb9abd433e8fac` | `data/stonevaults/structure` |
| `dungeonz` | DungeonZ 1.3.0+1.21.1 | MIT | `BOTC37SN` | `3783a778bd38306c` | `data/dungeonz/structure` |
| `desert_dungeon` | Desert Dungeon (DungeonZ addon) 1.0.1 | MIT | `BHSd1fQe` | `08ac50635aac8036` | `data/dungeonz/structure` |
| `dungeon_now_loading` | Dungeon Now Loading 2.2 (Forge 1.20.1) | CC0 1.0 | `ejP7ljqP` | `ea46ccef1705594a` | `data/dungeonnowloading/structures` |
| `mo_structures` | Mo' Structures 1.6.0+1.21.8 (NeoForge) | LGPL-3.0 | `3ad5pb5R` | `bf6567162a7f1a2d` | `data/mostructures/structure` |

Not imported:

- **Moog's End Structures** 2.1.1 (LGPL-3.0), which the research listed too: its pieces are a ship's sections of up
  to 48 blocks per axis and surface buildings without connectors; none is a room.
- **Rejected in Matěj's gallery review** (2026-10-14, RLM-120): YUNG's Better Dungeons 26.1.2-NeoForge-6.1.1
  (LGPL-3.0; its zombie and skeleton dungeons are in nearly every modpack, so ours would look less unique, and the
  skeleton dungeon is stone bricks throughout) and Moog's Voyager Structures 5.1.3 (LGPL-3.0; the crypt under its
  cathedral did not look good). Their rules were removed; import them again only if Matěj changes his mind.
- Single rooms the review rejected stay listed as `reject` in `content/rooms/third_party/review.yaml`, so importing
  their source again skips them.

The same review accepted Stonevaults' dungeons and Dungeon Now Loading's labyrinth only with more block variety:
their rules `dress` them (`tools/realmforge_tools/rooms/dress.py`, [tools/README](../../tools/README.md#rooms)),
giving the plainer surfaces mossy, cracked and gravel variants and adding vines, cobwebs and barrels, most of it to
the corridors (armor stands were tried in the playtest and dropped: placed by rule, they looked out of place).
Stonevaults' dungeon entrances (`dungeon/entrances/*`), which the review accepted as they were, are left out; its
pillager dungeon's entrances are dressed like its other rooms.

Some sources build with marker blocks that their processors replace when the structure generates (Dungeon Now
Loading's red wool is the rock around a room). Our dungeons place the templates without
those processors, so the rules map each marker to what the processor always, or nearly always, makes of it; random
decay (mossy or cracked variants) is left to our themes.

Each license was checked at the version imported (2026-10-14); the `NOTICE` says where it is stated, and where
sources disagreed (Dungeon Now Loading: CC0 in the jar, MIT on Modrinth, repository gone) the import follows the
jar. None of the imported pieces
copies a vanilla template: each was compared with 26.1.2's templates of the same footprint. The closest copies,
Mo' Structures' abandoned village temples, are not imported.

## Importing a source again

Unpack the jar's folder outside the repository and import it with the rules; a re-import keeps the sidecars' pools,
tags, weight and room rules (`max_per_layout`, `near_start`, `explosion_breaks`), and skips the rooms the gallery's
review rejected:

```bash
unzip -q stonevaults-2.0.0+26.1-neoforge.jar 'data/stonevaults/structure/*' -d /tmp/stonevaults
uv run rf rooms import /tmp/stonevaults/data/stonevaults/structure --to content/rooms/third_party/stonevaults --rules art/rooms/stonevaults/rules.yaml
uv run rf gen
```

The dressing reads each room's slot tags from its sidecar (a `hallway` gets at most one barrel, other rooms a
pattern of two to four by floor area), so **retagging a dressed room needs its source imported again**, in the same
commit: the committed template otherwise keeps the dressing of its old tags, and the next import changes it where
nobody expects it. RLM-178 retagged Stonevaults' `lobby_01` without one, and its re-import in RLM-237 took a barrel out.
The rooms of Stonevaults' dungeons and pillager dungeon and of Dungeon Now Loading's labyrinth are dressed. Both
were imported again when the barrels became patterns (RLM-262): only the added barrels moved. Stonevaults' rules
also `place` the fall cushion into `lobby_01`, where every drop down its entrance shaft lands (RLM-276), and, after
Matěj's playtest of 2026-10-24 (RLM-363, RLM-364), mend the rooms that locked players in or kept loot out of reach
(buttons inside `zombie_three_way_temple`'s iron doors, no bars in `husk_temple`'s doorways, nor in what its
connectors leave behind, levers over `jail_2`'s cell doors, tinted glass for the door of `jail`'s garden), set a chest
and a barrel on its side at the end of `jail`'s passage, its only containers (Matěj, 2026-10-25), and set the exit
marker of `treasure/egg`, the room that ends the test dungeon, where its dressing adds no barrels.

A newer version of a source updates its `NOTICE` (version, file, what changed) and this table.
