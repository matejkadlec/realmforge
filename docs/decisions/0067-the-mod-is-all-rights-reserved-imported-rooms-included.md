# 0067. The mod is All Rights Reserved, imported rooms included

Date: 2026-11-03

Status: Accepted (supersedes the mod's licenses in [0009](0009-licensing.md) and the room licensing of
[0028](0028-dungeon-rooms-generated-or-imported.md), points 4 and 8 and the license parts of points 3 and 5; amends
[0008](0008-distribution-github-and-curseforge.md))

## Context

Matěj, 2026-10-25 (RLM-414): the mod's code was MPL-2.0 and its assets CC BY 4.0, and each imported dungeon room kept
its source's license: MIT (Stonevaults, DungeonZ, Desert Dungeon), CC0 1.0 (Dungeon Now Loading) or LGPL-3.0 (Mo'
Structures, 24 rooms). He changes that: nobody may reuse the mod. The modpack was All Rights Reserved already.

## Decision

1. **Realmforge and Realmforge QA are All Rights Reserved** as a whole: code, assets, data, dungeon rooms and
   everything in their jars. The root `LICENSE` says so, and `mod_license` in `gradle.properties` is
   `All rights reserved. Use in a modpack prohibited.` Only the official Realmforge modpack may ship the mod.
   `LICENSE-ASSETS` is gone.
2. **The imported rooms become ours.** They are, and will be, heavily changed (remapped, dressed, rebuilt), so they
   fall under All Rights Reserved like the rest of the mod.
3. **The LGPL-3.0 rooms are a known grey zone.** LGPL-3.0 asks that changes to Mo' Structures' rooms stay under it, so
   this decision goes against that license. Matěj knows and accepts it: "if the owner contacts me ... we'll change it".
   Until an owner asks, no LGPL part: `COPYING`, `LICENSE` and `NOTICE` of Mo' Structures are gone. **If any source's
   owner objects, we change it.**
4. **Every source stays credited** by name and author, in the root `LICENSE` and the README.
5. **MIT's notices stay.** MIT asks only that its copyright and permission notice stay with the material, which fits
   All Rights Reserved, so Stonevaults, DungeonZ and Desert Dungeon keep `LICENSE` and `NOTICE` in
   `content/rooms/third_party/<source>/` and the jar's `META-INF/third_party/<source>/`. Their `NOTICE` says the rooms,
   as we changed them, fall under our `LICENSE`.
6. **Dungeon Now Loading (CC0) and Mo' Structures carry no license files**, in the repository or the jar.
   `SOURCES_WITHOUT_LICENSE_FILES` names the two; validation still requires `LICENSE` and `NOTICE` of every other
   source, and refuses them in these two folders. `art/rooms/` keeps where each source comes from.
7. **This decision covers these five sources.** The rules of [0028](0028-dungeon-rooms-generated-or-imported.md) stay
   for a new source, which keeps its `LICENSE` and `NOTICE`; a new LGPL-3.0 source also keeps `COPYING`, so an agent
   puts it to Matěj before importing it.
8. **Unchanged:** `pack/LICENSE` (All Rights Reserved; only its section on the mod now points at the root `LICENSE`).
   Better Key Binds keeps its own MPL-2.0 ([0048](0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md)).
   Apotheosis's data stays under its MIT license with its notice in the jar
   ([0063](0063-apotheosis-data-is-fitted-from-a-pinned-snapshot.md), RLM-524), and the Gradle wrapper under Gradle's
   Apache License 2.0: the root `LICENSE` names both as exceptions.

## Consequences

- **The change applies from this commit on.** The repository is public: everything published before it was under
  MPL-2.0 / CC BY 4.0 (and the rooms' own licenses), and copies people already got stay under those licenses.
- The repository stays public, which grants only what GitHub's Terms of Service describe (viewing and forking there).
- Nobody else may ship the mod in a modpack: a third-party pack needs Matěj's written permission.
- The risk of point 3 stays open: an owner of Mo' Structures' rooms could object, and then we change or remove those
  24 rooms. Mo' Structures' rooms keep their own folder, so they are easy to find.
- Purely AI-generated assets are probably not protected by copyright ([0009](0009-licensing.md)), so All Rights
  Reserved may not be enforceable for them; the same holds for the attribution CC BY 4.0 asked for.
