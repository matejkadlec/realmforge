# 0032. The v1 realm layout amends the Nether and End removal

Date: 2026-10-16

Status: Accepted (amends [0025](0025-remove-the-nether-and-end.md))

## Context

[0025](0025-remove-the-nether-and-end.md) removed the Nether and End and placed their items in the realm progression
as it stood on 2026-10-12: Overworld for levels 1–5, the stone realm from level 5, the desert realm with ancient
debris in chapter 2, and a late fire realm that replaces the Nether.

Matěj then fixed the v1 realm layout (Jira RLM-188, his decisions of 2026-10-14 to 2026-10-16, recorded in the
ticket's comments): eight main-path realms with overlapping level bands, one chapter per realm with the twins
sharing one, each chapter's cap at its last level, and the realm names players read
([realm design](../design/realms.md)). Three of its decisions change what 0025 says, and 0025 is accepted, so this
ADR records them instead of rewriting it.

## Decision

- **Overworld is the starting area for levels 1–10** (chapter 1), not 1–5. The first sealed realm, Undervault
  (chapter 2, levels 6–18), opens at about level 6 through chapter 1's main questline, with no hard level lock. Once
  Overworld has its own final boss (a later milestone), that boss gates Undervault (Matěj, 2026-10-16). Overworld
  stays the hub for realm portals.
- **Ashfire, the fire realm, does not replace the Nether and is not inspired by it** (Matěj, 2026-10-16). It is
  chapter 6 (levels 40–52), much further into the game than the vanilla Nether, with no Nether structures and no
  Nether look; it may share some of the Nether's mobs. Its terrain is plains of dark black and red blocks, burnt
  trees, lava lakes and volcanoes, and its dragons still fight on the ground. Quartz and blaze rods are still found
  there, as 0025's item table says.
- **Netherite becomes Undervault's material** (Matěj, 2026-10-15), replacing 0025's "ancient debris in the desert
  realm" and the desert realm's netherite scrap:
  - ancient debris generates in Undervault, only below the walkable floor's Y, never in walls or ceilings;
  - the netherite upgrade smithing template is a fairly common item in every Undervault dungeon, which settles that
    template among the items 0025's consequences left to the M4 task;
  - netherite is Undervault's tool tier, so no tier sits between diamond and netherite (the realm tool tiers of the
    realm design, RLM-194);
  - what Sunwastes, the desert realm, gets instead is open (realm design, open question 16).
- **The realm names are no longer working names:** Matěj approved them (RLM-139, RLM-188), and they take no article:
  Overworld, Undervault, Sunwastes, Everwinter, Olympus, Prestoria, Quagmire, Ashfire, Unmade Isles.

Everything else in 0025 stands: both dimensions are removed in M4, every item stays obtainable, the elytra get their
own Curios slot, and Undervault offers most of what the Nether offered.

## Consequences

- **Chapter numbers moved under 0025's item table.** Its rows that name chapter 3 (elytra from chapter 3's final
  boss, RLM-138; a nether star as a chapter 3 boss reward) still hold, but chapter 3 is now the twin chapter
  (Sunwastes and Everwinter) with two guardians. Which guardian gives the elytra is open (realm design, open question
  15); chapter 3 still arrives in M4, so the elytra still ship with the removal.
- **Netherite is obtainable from M1 on:** W2a generates Undervault's ancient debris and W6 puts templates into its
  dungeons' loot, while the vanilla Nether still exists until M4. Nothing in M1 depends on netherite being rare.
- **The Nether's replacement is no one realm:** the items it gave come from Undervault (glowstone, quartz, nether
  wart, netherite), quests and trades, and Ashfire (quartz, blaze rods), as 0025's table and this ADR place them.
- **Docs:** the realm design, the lore outline, the realm portal spec, the guide design and the roadmap follow the
  v1 layout (RLM-188); the vision's status note points here.
