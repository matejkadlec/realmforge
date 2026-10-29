# 0064. NeoForge's attributes join the ID universe

Date: 2026-10-29

Status: Accepted (amends [0005](0005-content-pipeline-yaml-single-source-of-truth.md))

## Context

[0005](0005-content-pipeline-yaml-single-source-of-truth.md) validates every id content references against the ID
universe, which holds vanilla's ids and our own until a registry dump of the whole pack exists (its TODO). Matěj's
numbers for Dexterity (RLM-170) give +20 % Swimming Speed at 10 points. The character sheet's Swimming Speed is
NeoForge's `neoforge:swim_speed` (`NeoForgeMod.SWIM_SPEED`), the attribute vanilla's swimming reads through NeoForge;
vanilla's own `minecraft:water_movement_efficiency` is Depth Strider's, a different thing. So a stat rule must name
`neoforge:swim_speed`, which the universe lacks.

NeoForge is no third-party mod: it is the loader, pinned with the game
([0026](0026-platform-minecraft-26-1-2-neoforge.md)), so its attributes exist in every run, the GameTest server
included, and change only with its pinned version.

## Decision

- The ID universe holds NeoForge's attributes too: `neoforge:creative_flight`, `neoforge:nametag_distance` and
  `neoforge:swim_speed`, every attribute `NeoForgeMod` registers in the pinned NeoForge 26.1.2.116, read from its
  sources. They are a hand-kept list (`NEOFORGE_ATTRIBUTES` in `tools/realmforge_tools/id_universe.py`), checked
  whenever NeoForge's pinned version changes.
- Every other NeoForge id and every third-party mod's id stays as 0005 has it, until the full-pack registry dump.

## Consequences

- Content may name NeoForge's attributes like vanilla's, with the same reference validation; a typo
  (`neoforge:swim_sped`) is refused with close matches.
- A NeoForge update that adds, renames or removes an attribute needs the list changed by hand, as the vanilla snapshot
  needs `rf ids vanilla`.
- 0005 keeps its accepted text; its ID universe is read with this ADR.
