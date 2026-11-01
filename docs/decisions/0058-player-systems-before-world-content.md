# 0058. Player systems before world content: M1 is the character to level 18, M2 the world of chapters 1 and 2

Date: 2026-10-29

Status: Accepted (amends [0044](0044-third-party-mods-by-milestone.md),
[0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md) and
[0041](0041-dungeon-containers-hold-loot-through-lootr.md) in their milestones, and the roadmap's decision D7);
amended by [0066](0066-the-curio-rule-ends-artifacts-joins-and-immersive-armors-stays-out.md) (Artifacts placed item by item,
Immersive Armors rejected), 2026-10-31

## Context

The first plan's M1 was a vertical slice of the first two chapters: the character, Undervault, custom mobs, two
dungeon types, a boss, the quests and release v0.1.0. M2 was gear and loot: Apotheosis, curios, the gear mods, and
since RLM-264 the Overworld chapter's content and the first world-structure dungeons
([0044](0044-third-party-mods-by-milestone.md)).

On 2026-10-29 Matěj playtested Astone, the Unbroken (RLM-491), Undervault's boss. She had been built and handed to
him before the stats, the spells, the gear and Undervault's textures she depends on existed, so she could not be
balanced or judged: "I had to take her down with full netherite and Sharpness IX… we don't have character stats
done properly, any real spells, any Apotheosis affixes, any curios". His rule: **player systems come first**
(stats, then spells, then gear), and custom dungeons, mobs and bosses are built and balanced only once those work
up to level 18 (RLM-504). He rewrote the two milestone epics the same day: RLM-6 is "M1: The character to level 18
(stats, spells, gear)", RLM-7 "M2: The world of chapters 1 and 2". He moved 56 world, dungeon, boss and quest
tickets out of the sprint to M2's backlog and put the stat, character sheet and gear tickets into M1. M1 and M2
were drafts, and their scope will change again; M3 to M8 stay as they are.

## Decision

- **M1 is the character to level 18:** stats, the character sheet, the class kits and a full specialization spell
  tree to level 18 for the Warrior and the Mage, every spell's visuals and sounds, and gear: Apotheosis's
  rarities, affixes, gems and reforging fitted to our stats, weapons, wands and caster robes up to netherite, our
  shield tiers, curios, and Artifacts and Immersive Armors as Matěj picks them. M1 is tested in an Overworld test
  world, on the training dummy and vanilla mobs with levels (W1), with no custom mob, dungeon, boss or quest.
- **M2 is the world of chapters 1 and 2:** Undervault and the realm portal with their art, custom mobs with real
  models, the two instanced dungeon types, Astone finished and balanced against the finished character, the quests
  of chapters 1 and 2, coins and merchants, the old M2's world content (the world-structure dungeons, illagers and
  villages, the Overworld chapter's content and final boss, the Structure Compass, paintings, simple foods and
  potions, RNG item chests and quest choice rewards), and the release.
- **v0.1.0 comes at the end of M2** (the roadmap's decision D7, amended). M1 ships no release of its own; M3 to M8
  keep their numbers until they are planned.
- **No M2 task enters a sprint before M1's definition of done holds,** unless Matěj says so. Technical "after"
  links alone never make world work ready.
- **Third-party mods** ([0044](0044-third-party-mods-by-milestone.md)): the gear mods that 0044 put into M2
  (Artifacts and Immersive Armors after their catalogues, Legendary Tooltips, Item Borders, YDM's Weapon Master;
  RLM-312 to RLM-314) are M1's, and so is Apotheosis
  ([0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)). Lootr, which
  [0041](0041-dungeon-containers-hold-loot-through-lootr.md) moved into M1 for the dungeons, comes with the
  dungeons in M2. 0044's M1 QoL, UI and building mods and its strongholds-off rule stay in M1, and its world mods stay
  in M2.
- **Older documents' milestones:** in a document written before this decision, world content marked M1 is the
  first stage of M2 (the chapters' short versions, with the quest book as the only voice), and gear marked M2 is
  M1's. Such a document keeps its staging until its next revision; the roadmap, CLAUDE.md and the architecture
  follow this decision now.

## Consequences

- Every system lands where it can be tested: the character is played and tuned on its own before any boss is
  balanced against it, and Astone is finished once there is a character to balance her against.
- The first playable release moves later: v0.1.0 needs both milestones. M1 ends in a character Matěj plays in a test
  world, not in a release.
- World work that already exists (Undervault, the realm portal, the custom mobs, the dungeon framework, the boss
  framework, Astone, the quest pipeline) stays in the code and waits for M2; its follow-ups are M2's backlog.
- The release number v0.2.0 is free for now, since M3 to M8 keep theirs until they are planned.
- Gear comes before the world it drops in, so its loot tables and world tiers are first tuned against vanilla mobs
  and the training dummy, and again when M2's mobs and dungeons exist.
