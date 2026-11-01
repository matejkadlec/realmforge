# 0066. The curio rule ends, Artifacts joins the pack, and Immersive Armors stays out

Date: 2026-10-31

Status: Accepted (amends [0025](0025-remove-the-nether-and-end.md)'s curio rule,
[0044](0044-third-party-mods-by-milestone.md)'s hold on Artifacts and its Immersive Armors pick, and
[0058](0058-player-systems-before-world-content.md)'s "Artifacts and Immersive Armors as Matěj picks them")

## Context

[0044](0044-third-party-mods-by-milestone.md) took Artifacts and Immersive Armors into the pack's gear mods, each after
a catalogue for Matěj's pick, and held Artifacts because its items are curios:
[0025](0025-remove-the-nether-and-end.md) records Matěj's curio rule (RLM-7, 2026-10-12: Curios as a library, but no
rings, necklaces, gloves and the like from other mods, only the Sophisticated backpack).
[0058](0058-player-systems-before-world-content.md) moved both mods into M1 with the gear. The catalogues arrived on
2026-10-29 ([artifacts.md](../research/artifacts.md), RLM-312; [immersive-armors.md](../research/immersive-armors.md),
RLM-313), and Matěj decided on 2026-10-30:

- **Artifacts (RLM-512):** neither of the catalogue's options (A, out; B, six utility items in our own slots) but a
  third: the curio rule is cancelled, since Artifacts was picked precisely because it is a curios mod. Instead of a
  keep-list, an analysis gives each item its place (realm, dungeon or boss, level, loot, quest reward or boss drop);
  the more items find a place, the better, and an item that clashes with our systems gets a place late enough, a tweak,
  or is switched off, each with its reason.
- **Immersive Armors (RLM-513):** not taken. Its effects act in `LivingDamageEvent.Pre`, outside our damage pipeline and
  its caps, and can only be switched off all together; its fire and magic resistance would be a second magic defense on
  players, where Spell Resistance is a mob-only stat (RLM-233). With the effects off, its sets are only armor with
  other textures, and variety in armor comes from our loot-only uniques ([gear design](../design/gear.md), RLM-511).

## Decision

1. **The curio rule ends.** Other mods' curio items may join the pack; each mod that brings them is still a pick of
   Matěj's, like any third-party mod. [0025](0025-remove-the-nether-and-end.md)'s elytra slot stays a Realmforge slot on
   the Curios API, and "other mods' curio items are kept out of the slot" now means only that slot: no other item goes
   into the elytra slot.
2. **Artifacts joins the pack, item by item.** Its items get the places of
   [artifacts.md, Matěj's decision and where each item goes](../research/artifacts.md#matějs-decision-and-where-each-item-goes):
   a realm, a level and a source of ours, with a tweak (a lower number in its config) where an item clashes, or off
   with its reason. 0044's hold ends. The pack task (RLM-314) builds that placement, and Matěj's playtest of it decides
   what changes.
3. **Immersive Armors is rejected** and moves to 0044's rejected mods. RLM-314 no longer adds it.
4. **The milestone stays M1** for Artifacts, as [0058](0058-player-systems-before-world-content.md) set it; no other ADR
   is needed for that move.

## Consequences

- **More slots:** the placement uses Artifacts' own Curios slot types (head, belt, two hands, feet) next to our two
  rings and bracelet; the necklace is one slot that Artifacts' necklaces share with our stat necklaces
  ([artifacts.md](../research/artifacts.md#matějs-decision-and-where-each-item-goes)). Curios is not in the pack yet: it
  comes with the gear work.
- **Its items come from our sources:** the placement gives each one a quest reward, a boss drop or a dungeon's
  treasure of ours, so Artifacts' own world sources (chests, brushing, mob equipment, campsites and the Mimic) are
  off, and no item is only a chance roll.
- **Checks before it ships:** the Cross Necklace's mixin writes `invulnerableTime` on every hit, worn or not, so the
  damage pipeline's GameTests run with Artifacts loaded; quest rewards name its ids, which needs the full-pack registry
  dump ([0005](0005-content-pipeline-yaml-single-source-of-truth.md), rule 3).
- **Max health from gear:** the Crystal Heart adds max health, which the gear design keeps for third-party gear
  (RLM-511) once the login clamp is solved (RLM-565); it is placed after that.
- **Docs:** [third-party-mods.md](../research/third-party-mods.md), the [roadmap](../roadmap.md)'s gear and curios
  lines, and the two catalogues record the decisions.
