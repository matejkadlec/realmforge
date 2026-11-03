# 0068. Elytra are bought in Skyhearth

Date: 2026-11-03

Status: Accepted (supersedes [0037](0037-elytra-from-chapter-3s-final-boss-amends-the-nether-and-end-removal.md);
amends [0025](0025-remove-the-nether-and-end.md)'s elytra row and the sources of End Stone, the Dragon Head and the
Dragon Egg)

## Context

[0025](0025-remove-the-nether-and-end.md) removed the Nether and End in M4 and gave the elytra a new source: "chapter 4,
when the fastest mount tier unlocks (RLM-101), or later". [0037](0037-elytra-from-chapter-3s-final-boss-amends-the-nether-and-end-removal.md)
moved it to chapter 3's final boss (Matěj, 2026-10-13, RLM-138). [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md)
then made chapter 3 the twin chapter with two guardians, which left open which one gives the elytra
([realm design, open question 15](../design/realms.md#open-questions)). Since 2026-10-30 the game's chapters are the
story's eight, with the twins as chapters 3 and 4 (RLM-466), so "chapter 3's final boss" no longer names one boss.

RLM-568, which renumbers the docs, asked Matěj which twin's final boss gives the elytra. He answered on 2026-11-03:
"Neither? Elytra will be purchasable in Skyhearth via NPC who will also sell Endstone and Dragon Head and Dragon
Egg... Elytra won't be a game drop." 0037 is accepted, so this ADR records the change instead
([0001](0001-record-architecture-decisions.md)).

## Decision

1. **Elytra are no drop.** No boss, chest or mob gives them.
2. **An NPC in Skyhearth sells them,** together with **End Stone, the Dragon Head and the Dragon Egg.** Those three
   were among the End items 0025 left to the M4 task to source; this NPC is their source.
3. **Open, for the Skyhearth design:** who the NPC is, the prices (in our coins,
   [0040](0040-coins-and-data-driven-merchant-trades.md)), and from when it sells the elytra
   ([realm design, open question 22](../design/realms.md#open-questions)).
4. **The "every item is obtainable" check lists the four items as known exceptions** until the NPC exists.

Everything else 0025 and [0066](0066-the-curio-rule-ends-artifacts-joins-and-immersive-armors-stays-out.md) say about
elytra stands: their own Curios slot, which never takes the chestplate slot and takes no other item, and the hook that
lets them fly from it.

## Consequences

- **0037 is superseded,** and 0025's elytra row is read with this ADR. [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md)'s
  note on which guardian gives them and [0044](0044-third-party-mods-by-milestone.md)'s "elytra already wait for
  chapter 3's final boss" are read the same way.
- **The source ships with Skyhearth, not with the End's removal.** The End is unreachable from M1 already (0044's
  strongholds), so elytra cannot be had until the NPC exists, whichever milestone builds Skyhearth.
- **Flight's place in the game** depends on when the NPC sells them: Skyhearth opens at about level 15, before the
  twins. How fast elytra are against the mount tiers stays as decided (RLM-115, 0025's consequence "Elytra and flying
  mounts").
- **Docs:** the realm design (travel, open questions 8, 15 and 22), the roadmap's M4 line (a plan change as Matěj
  decided it here), the Chococraft and Artifacts research.
