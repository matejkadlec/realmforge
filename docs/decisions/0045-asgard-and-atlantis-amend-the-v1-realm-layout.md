# 0045. Asgard and Atlantis amend the v1 realm layout

Date: 2026-10-20

Status: Accepted (amends [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md)); the names amended by
[0054](0054-skyhearth-and-deepholm-amend-asgard-and-atlantis.md)

## Context

[0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md) recorded the v1 realm layout (RLM-188), including
the realm names players read: Overworld, Undervault, Sunwastes, Everwinter, Olympus, Prestoria, Quagmire, Ashfire and
Unmade Isles, each without an article. Matěj has since changed two of those realms and how sentences treat the
names:

- **RLM-245** (2026-10-17): the hub matters for progress, story and items, so players should reach it much earlier
  than level 30.
- **RLM-265** (2026-10-19): Prestoria, the prehistoric realm, was the one realm whose theme he was unsure of; a water
  realm is truly unique, and its band is short. This reverses RLM-188's "underwater realm dropped, an underwater
  instanced dungeon instead".

0032 is accepted, so this ADR records the changes instead of rewriting it.

## Decision

- **Olympus becomes Asgard,** whose king is Odin. It opens from about level 15 instead of 30: the Asgard Medallion
  that opens it is crafted around the Stone Heart, which Undervault's final boss drops, so in practice Asgard opens
  when that boss falls. It is the centre of the game, with quests into Sunwastes, Everwinter and every later realm.
  Everything else about the hub stays: small, bounded, passive, no block breaking or building, no flight, unique
  story NPCs only there, no chapter. (RLM-245 named the item a "Sigil of Asgard"; on 2026-10-20 Matěj made it the
  Asgard Medallion, with Asgard's own portal of Holy Stone bricks, RLM-302.)
- **Prestoria becomes Atlantis,** a water realm, in the same place: chapter 4, levels 26–40, the same world tier.
  It is mostly under water, with water up to about y 128, an empty surface above and no bedrock ceiling. The
  Ocean Monument and Dungeons Enhanced's Sunken Shrine, Elders Temple and Flying Dutchman move there, out of
  Overworld, so prismarine, and with it Apothic Enchanting's seashelves, comes only from Atlantis.
- **The article in sentences:** a realm's name still has no article as a name, label or title, but a sentence that
  refers to the realm takes "the" where it reads naturally, as with the Nether ("Enter the Nether"), and every
  realm's entry quest is titled "Enter the {realm}". This refines 0032's "they take no article" for running text.

So the realm names are now: Overworld, Undervault, Sunwastes, Everwinter, Asgard, Atlantis, Quagmire, Ashfire,
Unmade Isles. Everything else in 0032 stands.

## Consequences

- **Docs:** the [realm design](../design/realms.md) (its hub and water realm sections, structures, candidates and open
  questions 17–21), the [lore outline](../lore/outline.md), the [style guide](../lore/style.md), the
  [guide design](../design/guide.md), [leveling.md](../design/leveling.md), [apotheosis.md](../design/apotheosis.md),
  the [realm portal spec](../design/realm-portal.md) and [set-pieces.md](../design/set-pieces.md) use the new names.
  Olympus and Prestoria survive only in dated history notes.
- **The hub's quest share moves:** the leveling model assumed the hub gives about a fifth of the quests of realms
  4–7; it now gives a share of every realm's quests from chapter 3 on, set with Q2 and later chapters.
- **Open for Matěj:** the medallion's recipe (RLM-245's proposal) and how Holy Stone from Everwinter fits a level-15
  Asgard (RLM-302); Atlantis's arrival on the seabed, its mounts, mobs and vanilla shipwrecks (realm design, open
  questions 18–21).
- **No code changes:** neither realm exists in content yet. Undervault's final boss, the Stone Heart, the gems and
  the medallion come with the tasks that build them.
