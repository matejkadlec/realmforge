# 0054. Skyhearth and Deepholm amend Asgard and Atlantis

Date: 2026-10-27

Status: Accepted (amends [0045](0045-asgard-and-atlantis-amend-the-v1-realm-layout.md))

## Context

[0045](0045-asgard-and-atlantis-amend-the-v1-realm-layout.md) named the hub realm Asgard, with its king Odin and the
Asgard Medallion that opens it, and the water realm Atlantis. On 2026-10-22, while tuning the story outline, Matěj
renamed them to drop borrowed mythology (RLM-357): Asgard became Skyhearth, Odin became Taveth and Atlantis became
Deepholm, and the race of the Gatewrights and the Wayfarers became the Ithari. Chapter 2's text, which he approved on
2026-10-27 (RLM-351), names the hub's item the Skyhearth Medallion.

The approved chapters and the lore outline use the new names from RLM-464 on. 0045 is accepted, so this ADR records
the renames instead of rewriting it.

## Decision

- **Asgard becomes Skyhearth,** and its king **Odin becomes Taveth.** The item that opens it, crafted around the Stone
  Heart, is the **Skyhearth Medallion** instead of the Asgard Medallion.
- **Atlantis becomes Deepholm.**

So the realm names are now: Overworld, Undervault, Sunwastes, Everwinter, Skyhearth, Deepholm, Quagmire, Ashfire,
Unmade Isles. Everything else in 0045 stands: the hub opens from about level 15, the water realm keeps its place and
level band, and the article rule for sentences is unchanged.

## Consequences

- **Docs:** `docs/lore/` uses the new names from this ADR's pull request on. The [realm design](../design/realms.md),
  the [realm portal spec](../design/realm-portal.md), the [guide design](../design/guide.md),
  [leveling.md](../design/leveling.md), [apotheosis.md](../design/apotheosis.md),
  [set-pieces.md](../design/set-pieces.md), the [architecture](../architecture.md) and the roadmap's plain wording
  follow in RLM-465, which also checks the hub's way in (RLM-302) against chapter 2's text. Asgard, Odin and Atlantis
  then survive only in dated history notes and accepted ADRs.
- **No code changes:** neither realm exists in `content/`, code or the tools yet, so no id or translation key
  changes.
