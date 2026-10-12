# Hytale base game (Early Access, state as of 12 Oct 2026): world design and dungeon design

> Appendix to [the reference-game research](../reference-games.md) (RLM-94), written on 2026-10-12 by an AI research agent. Local citations name their source (the Craft to Exile 2 1.1.3 instance and the jars it ships, the Mine and Slash and Advent of Ascension source trees, the CTE2 GitHub changelogs); those files are not in this repository. Mechanics and numbers only: never reuse names, assets or code of the reference games.


Researched 2026-10-12. Hytale mods are out of scope. Primary sources: official hytale.com posts and patch notes, read in full. The community wiki hytalewiki.org (MediaWiki, read through its API) is used for in-game detail; many of its pages are tagged stub or outdated, so treat wiki figures as community-verified, not official. Press reviews cover reception. hytale.wiki.gg, Fandom and Reddit refused automated access (HTTP 403), so Reddit sentiment is missing (see Gaps).

**Labels used on every finding:**
- **[EA]**: in Early Access as of 2026-10-12, on the stable branch (Update 6, 0.6.x).
- **[EA-pre]**: in Early Access, but only on the opt-in pre-release branch for version 0.7.0 / Chapter 1, as of 2026-10-01. The stable release is scheduled for 2026-10-12 (see Q1).
- **[Shown YYYY-MM]**: shown or announced only (trailer, blog, concept art); not playable.

---

## Q1. Status and timeline: the 2025 cancellation, the reacquisition, the Early Access launch and updates through Oct 2026

### Takeaway
Riot cancelled Hytale on 23 Jun 2025. Co-founder Simon Collins-Laflamme bought it back on 17 Nov 2025 and launched Early Access on 13 Jan 2026 on the old "legacy" engine. Six stable updates followed, mostly foundations and tools. The first content drop, Chapter 1 "Out of the Breach" (v0.7.0: goblins, a handcrafted dungeon, Goblin Breach portals, runes, gadgets), has been on the pre-release branch since 3 Sep 2026 and is scheduled for release on 12 Oct 2026.

### Cited Findings
- Development started in 2015, the game was announced on 13 Dec 2018, and Riot fully acquired Hypixel Studios in Apr 2020. The game was originally meant to be playable in 2021 and slipped repeatedly ("unrealistic ambitions"). — [Wikipedia](https://en.wikipedia.org/wiki/Hytale)
- On 23 Jun 2025 Aaron "Noxy" Donaghey announced the cancellation and the studio's closure, citing overly ambitious scope. Collins-Laflamme said on 13 Aug 2025 that he was negotiating, and announced the reacquisition from Riot on 17 Nov 2025, with 30 developers rehired. — [Wikipedia](https://en.wikipedia.org/wiki/Hytale); [Hytale is saved (official, 2025-11-17)](https://hytale.com/news/2025/11/hytale-is-saved)
- The team went back to the "Legacy Engine" (C#/Java, PC only) seen in the trailer and most old blogs and dropped the C++ cross-platform engine, estimated at about 2 more years before Early Access. At reacquisition: "over 100 NPCs, thousands of items and blocks, but many aren't properly configured yet". — [Hytale is saved](https://hytale.com/news/2025/11/hytale-is-saved)
- The Early Access date was announced on 28 Nov 2025, with pre-purchase from 13 Dec 2025; preparing the build meant merging 300+ GitHub branches. — [Hytale Early Access: January 13, 2026](https://hytale.com/news/2025/11/hytale-early-access-january-13-2026)
- [EA] Early Access launched on 13 Jan 2026 for Windows, macOS and Linux; it is not on Steam. The Standard/Starter edition costs US$19.99; editions are Standard, Supporter and Cursebreaker (Founder's). The game passed 1M players and peaked at 420k+ Twitch viewers. — [Wikipedia](https://en.wikipedia.org/wiki/Hytale); [Hytale is finally here (2026-01-13)](https://hytale.com/news/2026/1/hytale-is-finally-here); [1st FAQ](https://hytale.com/news/2025/12/hytales-1st-faq)
- [EA] What Early Access contains: Exploration Mode, Creative Mode, modding and multiplayer. Not included: Adventure Mode ("rich story… dungeons with boss fights"), official minigames, social features, and "Default Spawn: World Gen V2 – the complete, restored world of Orbis". — [Hytale is finally here](https://hytale.com/news/2026/1/hytale-is-finally-here)
- [EA] Update 1 (17 Jan 2026) added "dinosaurs and other NPCs to populate the underground jungles" with placeholder behaviours, plus creature spawns in the Devastated Lands sewers. — [Update 1 notes](https://hytale.com/news/2026/1/hytale-patch-notes-update-1)
- [EA] Update 2 (24 Jan 2026) added a necromancy grimoire that can drop from the large Praetorian skeleton, refactored ore placement ("more ores, especially in Devastated Lands undergrounds") and made lower-tier pickaxes much weaker against higher-tier ore. — [Update 2 notes](https://hytale.com/news/2026/1/hytale-patch-notes-update-2); [PC Gamer on the necromancy backlash](https://www.pcgamer.com/games/survival-crafting/after-hytale-subreddit-goes-full-panic-mode-dev-assures-players-the-new-patch-is-not-the-magic-system-thats-coming-in-the-full-game/)
- [EA] Update 3 (17 Feb 2026) added early animal taming, a fire-spread system, a new Outlander village environment and "refactored encounters across all zones". It also set the release rhythm: weekly pre-release patches, bundled into a stable update every 2–6 weeks, plus "chapters, larger content drops that unfold the Cursebreaker arc in Exploration Mode". — [Update 3 notes](https://hytale.com/news/2026/2/hytale-patch-notes-update-3); [news index](https://hytale.com/news)
- [EA] Update 4 (26 Mar 2026) reworked potions, made wolves hunt in packs, made the temple golems drop gems, added new tree species, and made Zone 4 sewers generate only under cities. — [Update 4 notes](https://hytale.com/news/2026/3/hytale-patch-notes-update-4)
- [EA] Update 5 (26 May 2026) added the Trigger Volume Tool (scripted encounters without code), controller support, server discovery and `/locate`. In June 2026 the "Hytale Shared Source" programme was announced: server source, protocol and assets released on GitHub to licensed players. — [Update 5 notes](https://hytale.com/news/2026/5/update-5-patch-notes); [Wikipedia](https://en.wikipedia.org/wiki/Hytale)
- On 16 Jul 2026 Simon posted the Chapter 1 preview: "there aren't many reasons to come back and play Hytale right now", and new content had been "deliberately held back to focus on the game's foundations". — [First Look: Chapter 1 and More](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- [EA] Update 6 (27 Aug 2026) brought Spectator mode, Hardcore (permadeath), the Mod Browser and player–entity collision (moving platforms), plus "new mage towers… in the Sandstone, Shale, and Volcanic biomes", smooth colour blending across biome borders, and a close button on the Ancient Gateway. — [Update 6 notes](https://hytale.com/news/2026/8/update-6-patch-notes)
- [EA-pre] Update 7 pre-release Parts 1–5 (3 Sep – 1 Oct 2026) are the "first wave" and "second wave" of Chapter 1 content: the rune ability system, reworked goblins, goblin gadgets, Goblin breaches / The Guttering Hollows, and food and potion changes. — [Update 7 pre-release notes](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- Chapter 1 is "Out of the Breach" = 0.7.0, "set to release on October 12th, 2026". The wiki cites the official "Hytale Chapter I (Teaser)" of 24 Sep 2026. — [wiki: Chapter I](https://hytalewiki.org/w/Chapter_I%3A_Out_of_the_Breach); [teaser (YouTube, official channel)](https://www.youtube.com/watch?v=c_j4xg_Hi9U)
- When I fetched it (~11:10 CEST on 12 Oct 2026), the newest post in the official news feed was still the Update 7 pre-release notes. — [hytale.com/news](https://hytale.com/news)
- [Shown 2026-07] Planned next: a crafting rework for Chapter 2 ("reducing the number of benches… shifting more of that work into in-world activities like wood chopping and metalworking") and Companions who live in your home; Runeforging and volumetric clouds are TBD. — [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- Early Access Exploration runs on WorldGen V1 (a 2016–2020 prototype); this world is "The Echo of Orbis". V2 will replace it for 1.0: V1 worlds stay playable, but they can no longer be created once Early Access ends, and "something is brewing that will impact The Echo". — [The Future of World Generation (2026-01-05)](https://hytale.com/news/2026/1/the-future-of-world-generation); [Community Spotlight: WorldGen V2 (2026-07-29)](https://hytale.com/news/2026/7/community-spotlight-worldgen-v2)

### Inferences
- Almost all world and dungeon content in Early Access comes from the 2020–2022 legacy build. Since launch the team has mostly built tools (Trigger Volumes, Encounter Manager, WorldGen V2 node editor). The first new handcrafted dungeon and multi-phase boss arrive only with Chapter 1. The world today is wide and furnished, but light on bespoke encounters.

### Gaps
- I could not confirm that Chapter 1 actually went live on 12 Oct 2026: there were no official release notes at fetch time, and X and Reddit were not reachable.

---

## Q2. World structure: zones, regions, biomes, difficulty progression and gating

### Takeaway
The Early Access world, "the Echo of Orbis", is infinite and procedural around a fixed core: Zone 1 Emerald Wilds in the centre, Zone 2 Howling Sands to the south, Zone 3 Whisperfrost Frontiers to the north, and Zone 4 Devastated Lands on cliff-girt islands across the sea; further out the zones repeat at random. Each zone has its own elemental affinity, three regions of rising difficulty, several biomes per region (each with terrain or settlement variants), its own factions, ores, prefab sets and music. Gating is soft (gear and ore tiers, bench upgrades, an ocean crossing, Memory thresholds) rather than walls.

### Cited Findings
- [EA] Fixed layout: about 2×2 km in the middle is always Zone 1; a single Zone 2 lies immediately south and a single Zone 3 immediately north (each starting about 1 km from spawn). Zone 4 appears as islands (about 2×2 km or 1×5 km) a few km out in any direction, 1–2 km east of spawn. "Traveling far enough, all the zones are randomized." — [wiki: Orbis](https://hytalewiki.org/w/Orbis); [wiki: Zone](https://hytalewiki.org/w/Zone)
- [EA] Hierarchy: a zone is made of regions, and a region of biomes. Each zone "is centered around a different elemental magic": Z1 Earth, Z2 Wind, Z3 Water, Z4 Fire. Zones "are intended to be processed in order… but nothing prevents the player from exploring them in a different order". Two more zones appear in concept art only. — [wiki: Zone](https://hytalewiki.org/w/Zone)
- [Shown/speculation] Zone 5 is unannounced; the community guesses Lightning, based on Stormbark wood, Storm Hide, Wet Grass, a Thunder Golem and a Tuluk Fisherman found in the game files. Zone 6 is guessed to be Void. — [wiki: Zone 5](https://hytalewiki.org/w/Zone_5). A third-party guide says Zones 5 ("Skylands") and 6 ("Poisonlands") are "confirmed"; that contradicts the wiki's "unannounced" and is treated as unverified here. — [Switchblade Gaming all-zones guide](https://www.switchbladegaming.com/hytale/all-zones-guide/)
- [EA] Zone 1 Emerald Wilds has three regions with their biomes:
  - Drifting Plains: Birch Forest, Birch Plains, Flower Forest, Smooth Plains, Tier 1 Mountains.
  - Seedling Woods: Aspen Forest, Gorge Plains, Gully Forest, Tallgrass Plains, Tier 2 Mountains, Chalk Lake.
  - The Fens: Autumn Forest, Azure Forest, Moss Forest, Swamp Forest, Tier 3 Mountains, Lake.
  - Plus shore and shallow-ocean regions (beach, cliffs, river, trenches).
  - Factions: Kweebec, Trork, Goblin, Klops. — [wiki: Emerald Wilds](https://hytalewiki.org/w/Emerald_Wilds)
- [EA] Zone 2 Howling Sands:
  - Golden Steppes: Boab, Forest, Plains and Rocks Savannah, Bushland Scrub, Mudflats, River.
  - Badlands: Oasis Desert, Red Desert, Rock Desert, Springs Desert, Hotsprings Desert, Hidden Oasis Desert, Scrub Tar Pits.
  - Desolate Basin: Barren Desert, Mushroom Desert, Scrub Tar Pits, Hidden Oasis Desert.
  - Plus shore and shallow-ocean regions (palm beach, coral, islands).
  - Factions: Scarak, Feran. — [wiki: Howling Sands](https://hytalewiki.org/w/Howling_Sands)
- [EA] Zone 3 Whisperfrost Frontiers (formerly "Borea"):
  - Frostmarch Tundra: Fir, Redwood and Tundra Forest, Hotsprings Plains, Shire Plains, Tier 1 Mountains.
  - Boreal Reach: Cedar and Mixed Cedar Forest, Frozen Plains, Tundra Plains, Tier 2 Mountains; every biome has "- Lake" and "- Valley" sub-biomes, plus Glacier Valley and Glacier Lake.
  - The Everfrost: Frozen Forest, Light Frozen Forest, Frost Frozen Plains, Tier 3 Mountains.
  - Plus frozen, glacier and tundra cliffs and beaches, and cold/warm shores and trenches.
  - Factions: Outlander and Bramblekin (Bramblekin not implemented). — [wiki: Whisperfrost Frontiers](https://hytalewiki.org/w/Whisperfrost_Frontiers); [wiki: Boreal Reach](https://hytalewiki.org/w/Boreal_Reach); [wiki: Faction](https://hytalewiki.org/w/Faction)
- [EA] Zone 4 Devastated Lands has three surface regions and one underground region:
  - Basalt Peninsula.
  - Cinder Wastes: Dunes Desert, Ghost Forest, Swamp Forest, Geysers Wastes, Grasslands Wastes, with Caldera / Canyon / Town / Village / Mountain sub-biomes.
  - Charred Woodlands: Ash Desert, Burnt Forest, Roots Forest, Ash Wastes, Lava Wastes, with Canyon / Mountain / Town / Hills / "Volcano" sub-biomes.
  - The Tropical Jungle underground.
  - Factions: Slothian and Saurian, both unimplemented. — [wiki: Devastated Lands](https://hytalewiki.org/w/Devastated_Lands); [wiki: Cinder Wastes](https://hytalewiki.org/w/Cinder_Wastes); [wiki: Charred Woodlands](https://hytalewiki.org/w/Charred_Woodlands)
- [EA] Oceans are a pseudo-zone ("Zone 0" in code; region "Crystalline Depths"). An "ocean shelf" drops off into the deep ocean, which the 2019 blog called "a zone all by itself". — [wiki: Zone](https://hytalewiki.org/w/Zone); [wiki: Deep Ocean](https://hytalewiki.org/w/Deep_Ocean); [Worldgen Introduction (Shown 2019-01)](https://hytale.com/news/2019/1/worldgen-introduction)
- [EA] Difficulty tiers inside a zone: the regions carry tiered mountain biomes (Tier 1/2/3) in both Zone 1 and Zone 3. In Zone 4, ruins and abandoned villages are graded Tier 1 in Basalt Peninsula, Tier 2 in Cinder Wastes and Tier 3 in Charred Woodlands ("It doesn't always generate the tiers according to their region"). — [wiki: Mountains](https://hytalewiki.org/w/Mountains); [wiki: Devastated Ruins](https://hytalewiki.org/w/Devastated_Ruins); [wiki: Abandoned Village](https://hytalewiki.org/w/Abandoned_Village). This conflicts with a third-party guide that calls Charred Woodlands the "lowest mob density – best entry point". — [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/)
- [Shown 2019-01] Every zone has its own blocks, furniture sets, NPC races, creatures, plants, weather, geology, coastal biomes and underwater content, and its own rules for caves ("Exploring a cave in Borea will be very different from exploring a cave in the Emerald Grove!"). In Early Access each zone does have its own structure families and music (see Q4 and Q10). — [Worldgen Introduction](https://hytale.com/news/2019/1/worldgen-introduction)
- [Shown 2025-03] For the V2 world, "Regions have replaced Zones as the way we approach designing each part of Orbis. Each represents a collection of three to five biomes with a cohesive theme that ties gameplay, creative direction, and narrative together. Regions are smaller than the old Zones, but there are more of them". Named examples: Central Grasslands, Wild Savannah, Boreal Frontier, Earthglade. — [Spring 2025 Development Update](https://hytale.com/news/2025/3/spring-2025-development-update)
- Soft gates:
  - [EA] Since Update 2, lower-tier pickaxes do much less damage to higher-tier ore blocks. — [Update 2](https://hytale.com/news/2026/1/hytale-patch-notes-update-2)
  - [EA] Bench upgrades need materials from several zones. Workbench T2 needs 30 Copper, 20 Iron and 20 Linen; T3 needs 30 Thorium, 20 Cobalt, 30 Heavy Leather, 50 Shadoweave Scraps and 25 Essence of Fire. — [wiki: Workbench](https://hytalewiki.org/w/Workbench)
  - [EA] Zone 4 is "surrounded by a deep ocean… tall steep cliffs". Third-party guides say no portal links Zone 3 to Zone 4, so you swim or sail; [EA-pre/Shown 2026-07] boats arrive with Chapter 1. — [wiki: Devastated Lands](https://hytalewiki.org/w/Devastated_Lands); [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/); [Chapter 1 post](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- [EA] Community gear benchmarks (third-party): Iron before Zone 2, Thorium during Zone 2, Cobalt in Zone 3, Adamantite in Zone 4. The material order is Crude → Copper → Iron → Thorium → Cobalt → Adamantite. — [Kweebedia route guide](https://kweebedia.com/progression/adventure-route/)
- [EA] Onboarding: players always start at the "First Gate of the Echo" (a spawn cave) in Zone 1, and a Ruined Temple (the portal to the hub) "is present relatively near to the spawn area". — [wiki: Exploration Mode](https://hytalewiki.org/w/Exploration_Mode); [wiki: Ruined Temple](https://hytalewiki.org/w/Ruined_Temple)
- [Shown 2019-01] Prefab volume: "We've created thousands of prefabs – 3,785 so far", from trees and rock faces up to ruins, buildings and special encounters, "distributed throughout the world at random and sometimes appear only once". — [Worldgen Introduction](https://hytale.com/news/2019/1/worldgen-introduction)

### Inferences
- A Hytale "zone" is close to what the owner calls a "realm style". One theme stays varied because it is split into 3 regions × 3–7 biomes × terrain or settlement variants. The desert alone spans savanna, oasis badlands, tar pits and a mushroom desert.
- Difficulty is layered in three ways at once: which zone (element and ore tier), which region tier inside the zone, and which structure tier (T1 ruin → T3 castle).

### Gaps
- No reliable source on whether enemy strength also scales with distance from the world origin. The wiki's Mineshaft page claims "Hytale generates stronger enemies the further from the center of the map", but gives no source.
- I could not verify the in-game size of each zone instance beyond the central layout.

---

## Q3. The volcanic Devastated Lands, and Hytale's volcanoes

### Takeaway
Zone 4 is the endgame fire zone: islands behind sea cliffs, under ashen skies, with lava rivers, burnt or still-burning forests and geysers. Its showpieces are giant **Volcano** prefabs: hollow mountains with lava pouring down their sides, and a central shaft that drops almost to bedrock into a hidden four-level **Tropical Jungle** full of dinosaurs. At the bottom of the shaft is a treasure pile, the "Fire Dragon's Lair", but no dragon spawns: Hytale's dragons are props or unfinished. The zone's story is told by ruins of a burned civilization.

### Cited Findings
- [EA] Overview: "volcanic, consisting of ash-filled skies and magma-covered mountains". Animals are "almost all hostile and include undead variants of their normal (Emerald Wilds) counterparts. Most animals are dinosaur-like and in bright colors". The edge is "almost always a cliff and surrounded by ocean", and the cliffs have narrow holes that drop to the sea. — [wiki: Devastated Lands](https://hytalewiki.org/w/Devastated_Lands)
- [EA] **Volcano prefab**: lava flows from the top, and "a big very deep pit in the middle" sets it apart from a normal mountain. The bottom of the pit can hold a platform with a treasure pile (gold-brick variants, rubies, sapphires), visible as a yellow spot on the map, sometimes inside a very large cave. "The 'volcanic pit' is considered the 'Fire Dragons Lair', however the dragon does not currently spawn." The volcano also has high-roll chests. — [wiki: Volcano](https://hytalewiki.org/w/Volcano)
- [EA] Lava: volcanoes are "hollow mountain[s] with lava flowing along the sides. The middle has a deep vertical shaft that leads almost all the way to bedrock, as a dangerous means to access rare cave-jungle biomes".
  - Lava spreads at half the speed of water and slows the player by 50%. Lava Burn deals 10 HP/s for 5 s.
  - Water meeting lava makes cobblestone, stone or cooled magma. Lava can't be picked up in a bucket.
  - Fire-themed NPCs and Burnt Skeletons can walk through it. — [wiki: Lava](https://hytalewiki.org/w/Lava); [wiki: Entity Effect](https://hytalewiki.org/w/Entity_Effect)
- [EA] The Volcanic Mountain biome is a separate tall biome of Volcanic Rock peaking at about y=200, with "no NPCs spawning up there, and no lava pouring down", but rich in silver. — [wiki: Volcanic Mountain](https://hytalewiki.org/w/Volcanic_Mountain)
- [EA] The Tropical Jungle under Zone 4 is a cave system of four stacked levels:
  1. "Devastated caves", a fiery transition zone.
  2. Sparse vegetation and water, "possibly some NPC outposts".
  3. A lush jungle with large trees and Slothian Villages.
  4. The "Forgotten Jungle", the deepest, wettest and most dangerous level, with hostile dinosaurs, lakes and bioluminescent inhabitants.

  Update 1 populated it with Cave-Rex, Cave Raptor, Trillodon, Archaeopteryx and Pterodactyl. — [wiki: Tropical Jungle](https://hytalewiki.org/w/Tropical_Jungle); [wiki: Dinosaur](https://hytalewiki.org/w/Dinosaur); [Update 1](https://hytale.com/news/2026/1/hytale-patch-notes-update-1)
- [EA] Surface fauna and enemies: Emberwulf (bear-tier, spikes on fire, scale 0.8–1.2, drops Essence of Fire), Magma Slug, Magma Snail, Rhino Toad, Burnt Skeleton variants, undead cows, pigs and chickens, zombies, skeleton horses and wraiths. Deep caves: Cave-Rex, Cave Raptor, Trillodon, Firesteel Golem and Magma Rhino Toad. Planned: Dimetrodon, Macaque, Prehistoric Chicken, Magma Golem. — [wiki: Devastated Lands](https://hytalewiki.org/w/Devastated_Lands); [wiki: Emberwulf](https://hytalewiki.org/w/Emberwulf)
- [EA] Structures:
  - Devastated Villages: basalt houses with holes, a basalt wall, farmyards and a town hall with banners; a sewer grate in the centre leads down to the Sewers.
  - Sewers: wide tunnels with green water, Black Piranhas, zombies and Burnt Skeleton Alchemists; a basalt variant has tar pools.
  - Devastated Ruins and Abandoned Villages, each in Tiers 1–3 (a T3 Abandoned Village has a Praetorian).
  - Burnt Mage Tower, where Launch Platforms fling you between floors.
  - Launch Pad Towers, with a Skeleton Pirate Captain at the top.
  - Firelands Temple: a vandalized portal dungeon with decapitated Gaia statues and red crystals, guarded by an Ember Golem.
  - Two variants of the Firelands Challenge Gate (non-functional). — [wiki: Devastated Village](https://hytalewiki.org/w/Devastated_Village); [wiki: Sewer](https://hytalewiki.org/w/Sewer); [wiki: Burnt Mage Tower](https://hytalewiki.org/w/Burnt_Mage_Tower); [wiki: Launch Platform](https://hytalewiki.org/w/Launch_Platform); [wiki: Firelands Temple](https://hytalewiki.org/w/Firelands_Temple); [wiki: Challenge Gate](https://hytalewiki.org/w/Challenge_Gate)
- [EA] Factions: Slothian villages (bamboo furniture, docks, wooden totem lamp posts, training dummies) already generate in jungle level 3, but the Slothians themselves are unimplemented. Saurians (a reptilian military faction) exist only in Creative. — [wiki: Slothian Village](https://hytalewiki.org/w/Slothian_Village); [wiki: Slothian](https://hytalewiki.org/w/Slothian); [wiki: Saurian](https://hytalewiki.org/w/Saurian)
- [EA] Lore through item tooltips: the city of "Crucible" exists only in lore. The Adamantite Ingot was "once instrumental to the advancements made by the peoples of Crucible", and Cindercloth Scraps say "the cursed flame scoured Crucible". — [wiki: Devastated Lands](https://hytalewiki.org/w/Devastated_Lands)
- [EA] Materials: Adamantite (the Cinder Wastes surface, according to a third-party guide, plus underground layers), Cindercloth, Essence of Fire, silver on volcanic mountains, and gold and gems in volcano pits. A full Adamantite set needs 77 Adamantite Ingots, 24 Heavy Leather and 20 Cindercloth (third-party). The Dread Wade fragment key costs 10 Adamantite Ingots and 30 Essence of Fire. — [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/); [wiki: Fragment: Orbis – The Dread Wade](https://hytalewiki.org/w/Fragment%3A_Orbis_-_The_Dread_Wade)
- [EA] Dragons:
  - The Fire Dragon exists as an asset with no AI and does not spawn naturally.
  - The Frost Dragon (400 HP) only sleeps on a frost-hand peak in the Dragonspire Weald fragment and cannot be woken.
  - The Void Dragon is unimplemented; Simon has said a Void Dragon is under consideration as a future boss.
  - The wiki: "Currently the only NPCs considered bosses in-game are dragons. But they're only placeholders." — [wiki: Fire Dragon](https://hytalewiki.org/w/Fire_Dragon); [wiki: Frost Dragon](https://hytalewiki.org/w/Frost_Dragon); [wiki: Void Dragon](https://hytalewiki.org/w/Void_Dragon); [wiki: Boss](https://hytalewiki.org/w/Boss)
- [EA] Hazards: fire and lava (Burn 5 HP/s for 3 s; Lava Burn 10 HP/s for 5 s). A third-party guide says the only fire mitigation in Early Access is the full Adamantite set bonus (+15% fire resistance); the wiki lists a "Fire Immunity" effect, but no player source for it. — [wiki: Entity Effect](https://hytalewiki.org/w/Entity_Effect); [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/)
- [EA-pre] Audio: 24 new Zone 4 exploration tracks, and "Caves and sewers in Zone 4 now have their own music" (0.7.0-pre.4). — [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [Shown 2019-01 / 2020-03] Concept art showed "subterranean forests of zone 4" and "desolate human ruins… ashen wastelands", buildings deliberately "ruinified"; "Some ruins conceal hidden dangers, while others house lost lore". — [Worldgen Introduction](https://hytale.com/news/2019/1/worldgen-introduction); [Progress Update March 2020](https://hytale.com/news/2020/3/progress-update-march-26-2020)

### Inferences
- The owner's "one big volcano" inspiration most likely refers to this Volcano prefab and the Devastated Lands. The design pattern is vertical: the volcano is at once a landmark you can see from afar, a shaft down into a hidden lush underworld (fire above, jungle below), and a lair or treasure at the bottom. A grounded dragon lair at the bottom of a pit also fits Realmforge's rule of zero flying bosses (vision.md).
- "Undead or burnt variants of familiar animals" is a cheap way to populate a hellish zone without making it empty.

### Gaps
- Hytale has no fightable dragon and no volcano dungeon interior beyond the pit and its treasure. The "Volcanic Peaks" area named by third-party guides does not appear in the wiki's biome list.

---

## Q4. The desert and frozen zones (oases, ruins, frozen lakes, under-ice content, shipwrecks), forest-zone highlights, and oceans

### Takeaway
The desert stays varied through oases sunk into the dunes (with exclusive fauna), tar pits with insect nests, mushroom desert, savanna and giant fossil skeletons, plus fox-folk villages, bug hives, a buried-treasure labyrinth and ruined castles. The frozen zone has many lakes (with islands and glacier lakes), hot-spring plains, lake villages on stilts, ice camps on coastal ice, yeti caves marked by ice spikes, multi-level mines and tomb dungeons. There is **no specific under-ice content and there are no frozen shipwrecks**: shipwrecks belong to the deep ocean. The forest zone is the densest, with tree-folk villages, monster camps, a goblin lair, mage towers, crypts and swamps.

### Cited Findings
**Desert (Zone 2):**
- [EA] Oasis biome: "a depression in the middle of the sandy dunes", one of the few water sources in the zone. It has turquoise water with fish, purple grass at the lakeside with rare flowers (Poisoned Nettle, Purple Orchid), and calcite piles with some gold. "Flamingos are found exclusively in this biome"; sand lizards and geckos roam it; predators are saber-tooth tigers and crocodiles; Ferans walk through it. — [wiki: Oasis](https://hytalewiki.org/w/Oasis)
- [EA] The deserts hold "enormous incomplete skeletons of extinct at least whale-size creatures". Fauna: antelope, cactee, crocodile, meerkat, saber-tooth, snake, vulture. — [wiki: Howling Sands](https://hytalewiki.org/w/Howling_Sands)
- [EA] Desert structures:
  - Feran Villages, from tents up to sandstone houses, built around a campfire circle with watchtowers, furnaces and wells; larger ones have a raised "throne" stool.
  - Scarak Nests on the Scrub Tar Pits of the Badlands.
  - Labyrinth and Sandswept Catacombs (dungeons, see Q5).
  - Drylands Temple (the portal dungeon).
  - Howling Sands Ruins in Tiers 1–3 (T3 is a castle with a perimeter wall, a covered pit with a chest and a basement).
  - Trading Post (a rare ruined settlement with Sand Zombies).
  - Campsites and Sandswept mage towers. — [wiki: Feran](https://hytalewiki.org/w/Feran); [wiki: Scarak Nest](https://hytalewiki.org/w/Scarak_Nest); [wiki: Howling Sands Ruins](https://hytalewiki.org/w/Howling_Sands_Ruins); [wiki: Howling Sands Trading Post](https://hytalewiki.org/w/Howling_Sands_Trading_Post); [Update 6](https://hytale.com/news/2026/8/update-6-patch-notes)
- [Shown, concept art] The Sand Palace (Scarak fortress built to imprison the Sand Empress, with a portal to her), the Ziggurat (with a "crystal room" and a tunnel network that prevents digging under it), and a Prison Cave. None of these are in the game. — [wiki: Sand Palace](https://hytalewiki.org/w/Sand_Palace); [wiki: Scarak](https://hytalewiki.org/w/Scarak); [wiki: Prison Cave](https://hytalewiki.org/w/Prison_Cave)
- [EA, third-party claim] "Sandstorms reduce visibility to near-zero". This is not corroborated by the wiki or the patch notes. — [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/)

**Frozen (Zone 3):**
- [EA] Lakes generate in Seedling Woods and The Fens of Zone 1 and "the entirety of zone 3". Large lakes span several biomes, have rivers running into them, and can hold islands; Boreal Reach adds Glacier Lake and Glacier Valley sub-biomes. — [wiki: Lake](https://hytalewiki.org/w/Lake); [wiki: Boreal Reach](https://hytalewiki.org/w/Boreal_Reach)
- [EA] Yeti: lives in caves "usually in tall mountains… identified by ice spikes and a campfire". As a "Big Predator" it "will hunt any Prey or Big Prey NPCs" and throws chunks of terrain. The Tier 3 mountains (Everfrost) hold only the Yeti. — [wiki: Yeti](https://hytalewiki.org/w/Yeti); [wiki: Tier 3 Mountains Zone 3](https://hytalewiki.org/w/Tier_3_Mountains_Zone_3)
- [EA] Frozen-zone structures:
  - Outlander Villages in Tiers 1–3. "Tier 2 Lake Villages generate near large bodies of water… sit atop Wooden Platforms and typically generate with some small boats docked". T3 has a perimeter wall and a fort holding a Brute and a Priest.
  - Trork Ice Camps, "built on ice formations… on the coasts".
  - Outlander mines: a surface building with an elevator shaft and 2–3+ levels, more Outlanders and chests the deeper you go.
  - Ritual Site: poisoned ground, a palisade, braziers and Watcher statues.
  - Whisperfrost Ruins: ruined castles in 3 sizes.
  - Shale Dungeon and Outlander Temple (see Q5), and the Snowlands Temple (the portal dungeon). — [wiki: Outlander Village](https://hytalewiki.org/w/Outlander_Village); [wiki: Trork Ice Camp](https://hytalewiki.org/w/Trork_Ice_Camp); [wiki: Mineshaft](https://hytalewiki.org/w/Mineshaft); [wiki: Outlander Ritual Site](https://hytalewiki.org/w/Outlander_Ritual_Site); [wiki: Whisperfrost Ruins](https://hytalewiki.org/w/Whisperfrost_Ruins); [wiki: Snowlands Temple](https://hytalewiki.org/w/Snowlands_Temple)
- [Shown 2019-04] "Dig through the snow and ice and you'll find winding cave systems illuminated by bioluminescent mushrooms". Wildlife: woodpeckers, wolves, polar bears, rams. Mine-entrance prefabs give access to pre-existing underground structures. — [A visual tour of zone 3](https://hytale.com/news/2019/4/a-visual-tour-of-zone-3)
- [EA] Cold environment: neither the wiki's list of effects nor the patch notes mention any cold, heat or thirst mechanic. PC Gamer wished for "survival systems for things like heat, cold, thirst, and hunger, but it doesn't sound like the devs plan to add them". A third-party guide claims cold damage in Zone 3; this is unverified and likely wrong. — [wiki: Entity Effect](https://hytalewiki.org/w/Entity_Effect); [PC Gamer, 2026-01-14](https://www.pcgamer.com/games/survival-crafting/hytales-developer-warned-us-the-adventure-sandbox-isnt-good-yet-but-it-already-feels-pretty-good-to-me/); [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/)
- [EA] Shipwrecks are Deep Ocean prefabs: Darkwood and Redwood hulls covered in kelp and algae, with Ancient Barrels, broken crates and chests (seeds, linen, potions, hides). They have no set inhabitants. Cold oceans hold Salmon and Frostgill. — [wiki: Shipwreck](https://hytalewiki.org/w/Shipwreck); [wiki: Ocean](https://hytalewiki.org/w/Ocean)
- [EA] The **Dragonspire Weald** fragment (portals.boreal) is a frozen forest with a sleeping Frost Dragon, grizzlies, foxes, moose and birds; Silent Henges ("henges tower over a frozen land") can only be opened from Creative. — [wiki: Dragonspire Weald](https://hytalewiki.org/w/Dragonspire_Weald); [wiki: Silent Henges](https://hytalewiki.org/w/Silent_Henges)

**Forest (Zone 1):**
- [EA] Kweebec Villages come in three types: a few large oaks joined by stairs and bridges, a few giant maples, or "a single very enormous ash tree" with the whole settlement built onto it. Common features:
  - A ring of rock formations with damaging plants around the perimeter.
  - Tables and stools, a merchant shop, a fenced carrot field "that always spawns 2 rabbits", a well with a bucket, and a port with boats when the village sits on water.
  - Guard platforms with Razorleaf rangers, ponds, and a tree with a large animated face. — [wiki: Kweebec Village](https://hytalewiki.org/w/Kweebec_Village)
- [EA] Other forest structures:
  - Trork Camps: T1 a house and campfire; T2 the "Quarry" or "Lumber" variant; T3 the "Trork Castle".
  - Mage Tower (25 blocks across, 50 tall, ~400 bookshelves, 25+ skeletons).
  - Cursed Crypt (in a graveyard), Goblin Lair (under a fortified ancient library), Magic Ruins, Stone Dungeon.
  - Emerald Wilds Ruins (T1 rubble → T3 a 30%-damaged castle with hidden chests).
  - Skeleton Outposts, campsites, abandoned houses (some with Bear Traps), the "Hidey Hole" home of a Klops gentleman, mineshafts, Challenge Gates. — [wiki: Emerald Wilds](https://hytalewiki.org/w/Emerald_Wilds); [wiki: Trork Camp](https://hytalewiki.org/w/Trork_Camp); [wiki: Mage Tower](https://hytalewiki.org/w/Mage_Tower); [wiki: Emerald Wilds Ruins](https://hytalewiki.org/w/Emerald_Wilds_Ruins)
- [EA] Zone 1 cave biomes include "boomshroom caves, rat caves and various crystal caves of different colors"; ravines break through to the surface. — [wiki: Cave](https://hytalewiki.org/w/Cave)
- [EA, third-party] The Azure Forest (glowing blue trees, in The Fens) is said to be the only source of Azure Logs. The teleporter recipe does need Azure Log and Azure Kelp. — [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/); [wiki: Teleporter](https://hytalewiki.org/w/Teleporter)

**Oceans:**
- [EA] Ocean biomes include Deep Ocean, Ocean Shelf, Coral Reefs and Tropical Islands. "Legendary"-quality fauna: humpback whale, hammerhead shark, moray eel and frostgill. The wiki also lists a Kraken for the deep ocean, unconfirmed. — [wiki: Ocean](https://hytalewiki.org/w/Ocean); [wiki: Deep Ocean](https://hytalewiki.org/w/Deep_Ocean)
- [Shown 2026-01] A WorldGen V2 example pack shows "a complex of underwater caves [with] bracket coral and a shipwreck". It is not in the Early Access world. — [The Future of World Generation](https://hytale.com/news/2026/1/the-future-of-world-generation)

### Inferences
- Hytale makes a biome "style" feel like a place by giving each sub-biome its own water feature (oasis basin, tar pit, hot springs, glacier lake) and fauna found only there (flamingos only at oases, the yeti only on T3 peaks).
- The owner's "something beneath the frozen lakes" and "frozen shipwrecks" are not taken from Hytale; they would be Realmforge's own ideas.

### Gaps
- No source describes under-ice dungeons or caves under the frozen lakes in Zone 3.
- No first-party description of sandstorms or blizzards as gameplay hazards.

---

## Q5. Open-world dungeons: types, layouts (handcrafted vs procedural), bosses, loot, and how many per zone

### Takeaway
In Early Access, dungeons are open-world prefabs. Hand-built rooms are chained by rules (each prefab node ends in stairs or a ladder that the next one continues) down to a "final room". The wiki counts six named dungeons: 3 in Zone 1, 1 in Zone 2 (2 counting the Sandswept Catacombs), 2 in Zone 3, none in Zone 4. Each has an elite or miniboss. Every zone also has one portal temple guarded by a golem, and dozens of tiered ruins and camps. True multi-phase bosses and a fully handcrafted dungeon arrive only with Chapter 1.

### Cited Findings
- [Shown 2019-01 → EA] Definition: dungeons "comprise multiple chambers and encounters joined by connecting passageways, often culminating in a 'Final Room', providing challenges and special rewards tailored by our worldteam". Prefabs can carry their own triggers and events. — [Worldgen Introduction](https://hytale.com/news/2019/1/worldgen-introduction)
- [Shown 2019-04] Assembly technique: "multiple mine shaft prefabs can be dynamically combined… each prefab 'node' ends in a staircase or ladder. The next prefab will continue that staircase or ladder… This same process is used to generate dungeons." — [A visual tour of zone 3](https://hytale.com/news/2019/4/a-visual-tour-of-zone-3)
- [EA] The wiki's taxonomy has two kinds:
  - "Generic Dungeons": a small surface structure leads into tunnels and rooms and then a "final room"; the look, loot and enemies depend on the zone.
  - "Named Dungeons": a theme, larger rooms, more complex generation, and a miniboss or final boss.

  Named dungeons by zone: Zone 1 has the Cursed Crypt, Goblin Lair and Magic Ruins; Zone 2 the Labyrinth; Zone 3 the Outlander Temple and Shale Dungeon. "Gates" of Challenge Dungeons also spawn, but lead nowhere. — [wiki: Dungeon](https://hytalewiki.org/w/Dungeon)
- [EA] Notable dungeons:
  - **Cursed Crypt** (Z1): entered through a graveyard; tunnels and rooms with chests hidden behind walls of skulls; a lower floor with an arena where a Burnt Skeleton Praetorian waits "on a pedestal surrounded by skulls… towering walls of coffins in the higher spectator area". — [wiki: Cursed Crypt](https://hytalewiki.org/w/Cursed_Crypt)
  - **Goblin Lair** (Z1): reached from ancient libraries the goblins fortified. A spiked mound hides a tunnel down, with a spike pit at the bottom. The lair is a cave system of hovels, barricades and treasure hoards; the Goblin Duke sits on his gold in the boss room. — [wiki: Goblin Lair](https://hytalewiki.org/w/Goblin_Lair)
  - **Magic Ruins** (Z1): three entrance variants ("Pool", "Ruins", "Hidden"). Libraries and traps (spikes, lava pits) on the upper layer give way to "structured caves" with Root Spirits, and a boss room in three variants holding a Crystal Earth Golem. — [wiki: Magic Ruins](https://hytalewiki.org/w/Magic_Ruins)
  - **Labyrinth** (Z2): descend a platformed shaft into a start room with a bedroll and 3–4 hallways. Many high-roll chests are "buried underneath sand piles", up to three per pile ("bring a shovel"). A Stairs Room leads to the lower level, and a boss room with a Sandswept Golem and crocodiles opens onto a treasure room behind a Viper statue. Hyena packs, rattlesnakes, scorpions and the odd Scarak roam the halls. — [wiki: Labyrinth](https://hytalewiki.org/w/Labyrinth)
  - **Sandswept Catacombs** (Z2): a large dungeon whose boss room is a lava pool under a broken bridge, behind Earth Temple doors, with a Sandswept Golem at the bottom. — [wiki: Sandswept Catacombs](https://hytalewiki.org/w/Sandswept_Catacombs)
  - **Shale Dungeon / "Outlander Corridors"** (Z3): a gate guarded by Outlanders above an underground tomb maze. Optional unique rooms appear in some instances and not others: a Watcher shrine, a tomb with two pools, a preaching room with a Void Grimoire, a trapped tomb with spike trenches, a two-storey loot room. "They respawn if the player leaves far enough and returns." — [wiki: Shale Dungeon](https://hytalewiki.org/w/Shale_Dungeon)
  - **Outlander Temple** (Z3, rare in the Everfrost): a fortified wall with towers around a temple sitting on a massive underground facility, with a poison pool beneath it, plus Outlanders, spiders, piranhas and a Void Crawler. — [wiki: Outlander Temple](https://hytalewiki.org/w/Outlander_Temple)
  - **Stone Dungeon** (Z1, rare): three floors: goblins and spikes, then coffins with skeletons, then "a larger room filled with treasure". — [wiki: Stone Dungeon](https://hytalewiki.org/w/Stone_Dungeon)
  - **Scarak Nest** (Z2): sprawling hive caves with eggs; the Broodmother's chamber has a small chest room. — [wiki: Scarak Nest](https://hytalewiki.org/w/Scarak_Nest)
  - Mines: a tiny one-shaft mine; horizontal mines whose tunnel networks have about one chest per 30 blocks; staircase mines whose central rail shaft can reach lava at the bottom of the world, with side pits crossed by 1-block bridges to a single chest; and multi-level Outlander mines. — [wiki: Mineshaft](https://hytalewiki.org/w/Mineshaft)
- [EA] Portal temples, one per zone: the Ruined Temple (Z1), Drylands Temple (Z2), Snowlands Temple (Z3) and Firelands Temple (Z4). Each has a central dome with a Gaia statue outside, sometimes 3 broken bridges leading to towers, chests below, and a Forgotten Temple Gateway guarded by that zone's golem. — [wiki: Portal dungeon](https://hytalewiki.org/w/Portal_dungeon); [wiki: Forgotten Temple Gateway](https://hytalewiki.org/w/Forgotten_Temple_Gateway)
- [EA] Golem "bosses": they stand still until a player comes within 10 blocks, return to their post when the player retreats 10–15 blocks, and "will spawn again after one in-game day". Since Update 4 each temple golem drops its gem: Emerald, Zephyr, Sapphire or Ruby. A separate Firesteel Golem guards a ruin deep in Zone 1 at lava level. — [wiki: Golem](https://hytalewiki.org/w/Golem); [Update 4](https://hytale.com/news/2026/3/hytale-patch-notes-update-4)
- [EA] Other bosses and elites:
  - Goblin Duke: the files hint at a multi-phase fight; HP is listed for 3 phases (226/320/200), "but as of now… behaves more like a regular enemy".
  - Scarak Broodmother (145 HP): summons pairs of Fighters and 2–4 Lice; immune to poison.
  - Yeti.
  - Burnt Skeleton Praetorian: its utility AI picks between blocking, "summoning at low health" and charging. — [wiki: Goblin Duke](https://hytalewiki.org/w/Goblin_Duke); [wiki: Scarak Broodmother](https://hytalewiki.org/w/Scarak_Broodmother); [NPC Technical Rundown (2026-02-11)](https://hytale.com/news/2026/2/npc-technical-rundown)
- [EA] Open-world mini-arena: the **Skeleton Arena** in The Fens is a square of ruined corners with spike traps. One Burnt Skeleton Praetorian stands in the centre and "respawns daily. Fighting it causes it to spawn 3-4 lesser armed skeletons". — [wiki: Skeleton Arena](https://hytalewiki.org/w/Skeleton_Arena)
- [EA] Loot patterns:
  - Chests come in tiers (T1–T5), plus "High-roll" chests and faction-styled chests (Goblin, Kweebec, Feran, Lost Civilization, Wind Temple, Dark Temple…).
  - Chests are hidden behind skull walls, in wells and attics, and under iron grates; boss rooms hold treasure piles.
  - Rare placed decorations count as loot (Void Grimoires, Watcher statues, banners).
  - The tables also carry filler (sticks, plant fiber, crude arrows; "No Loot Generated" weights). — [wiki: Emerald Wilds Ruins](https://hytalewiki.org/w/Emerald_Wilds_Ruins); [wiki: Sewer](https://hytalewiki.org/w/Sewer); [wiki: Dungeon](https://hytalewiki.org/w/Dungeon)
- [EA] Repopulation: Trork camps respawn "if they have all been killed and the player returns later"; Mage Tower skeletons can respawn "by the time you're on your way down again". — [wiki: Trork Camp](https://hytalewiki.org/w/Trork_Camp); [wiki: Mage Tower](https://hytalewiki.org/w/Mage_Tower)
- Breakability:
  - [Shown 2019-03] "Some (planned) dungeons will not have breakable walls or floors… to prevent 'skipping through'".
  - [EA] Portal dungeons are fully destructible; only the Forgotten Temple hub forbids breaking blocks. — [wiki: Dungeon](https://hytalewiki.org/w/Dungeon); [wiki: Portal dungeon](https://hytalewiki.org/w/Portal_dungeon)
- [Shown 2018-12 / concept art] Planned dungeon types: "Story Dungeons", which spawn only once per world; "Challenge Dungeons", for skilled players; "Portal Dungeons", a nexus to other alterverses; and "World dungeons". — [wiki: Dungeon](https://hytalewiki.org/w/Dungeon); [UI sneak peek (2018-12)](https://hytale.com/news/2018/12/a-sneak-peek-at-hytale-s-user-interface)
- [Shown 2019-03] Elemental stone circles, one per zone, "act as an arena for a combat encounter that is triggered when players interact with the area in a particular way… players will come to recognize the type of encounter they're about to face whenever they see one". — [Creation of a new POI](https://hytale.com/news/2019/3/the-creation-of-a-new-point-of-interest-for-adventure-mode)
- [EA-pre / Shown 2026-07] Chapter 1 brings "our first handcrafted dungeon, featuring the first multi-phase boss fight", built on the **Encounter Manager**, which "coordinates the phases, wave spawns, and arena changes that make up encounters and boss fights. Encounters are authored as content instead of engine code". — [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- [EA] The Trigger Volume Tool (Update 5) places persistent 3D volumes with 20+ effects (spawn NPC, place prefab, teleport, weather, sound…) that can be saved into prefabs, "for ambushes, story beats, hidden discoveries, puzzles, and safe zones". Since Update 6, "Entity Count" combined with "On Entity Died" supports "encounters that require all enemies to be defeated to progress". — [Update 5](https://hytale.com/news/2026/5/update-5-patch-notes); [Update 6](https://hytale.com/news/2026/8/update-6-patch-notes)

### Inferences
- Dungeons per zone in Early Access (my count from the wiki pages):
  - Z1: Cursed Crypt, Goblin Lair, Magic Ruins and the rare Stone Dungeon, plus the Ruined Temple, Mage Tower, Skeleton Arena and the Firesteel golem ruin.
  - Z2: Labyrinth, Sandswept Catacombs and Scarak Nest, plus the Drylands Temple and mage towers.
  - Z3: Shale Dungeon, Outlander Temple and Outlander mines, plus the Snowlands Temple.
  - Z4: no named dungeon; instead the Firelands Temple, Burnt Mage Tower, Sewers, volcano pits and launch-pad towers.

  That is about 2–4 "real" dungeons plus one portal temple per zone, which matches the owner's 2–4 dungeons per realm.
- Bosses are mostly golem-style guardians: they stand still, have a leash radius, respawn daily and drop a crafting gem. This is cheap, repeatable, and ties combat to crafting.

### Gaps
- No first-party layout of the Chapter 1 handcrafted dungeon. A lore note mentions "the Deepworks. Beyond the great bridge", possibly a hint ([wiki: Goblin Commission](https://hytalewiki.org/w/Goblin_Commission%3A_Abandoned_Parts)); unconfirmed.

---

## Q6. Instances and portals: temporary portals opened with keys, timers, and wave or arena challenges

### Takeaway
Hytale's instancing is the **Fragments of Orbis** system. You craft an **Ancient Gateway**, place it at home, and *consume* a **Fragment Key** to open a temporary portal into a timed world that is generated on entry (5–12 min). If everyone dies, the fragment closes. Escalating "Void" pressure (Void Breach, or Void Surge) turns the end of a run into a survival phase. Chapter 1 adds **Goblin Breaches**, portals that appear in the overworld as world events, and a **Coffer Event** in which locked chests pay out by the wave you reached. This is the closest thing to a wave-survival-for-loot dungeon.

### Cited Findings
- [EA] Official framing (13 Jan 2026): the Ancient Gateway is "a device that you can craft and place down at your home, allowing you to venture into unstable, temporary worlds called Fragments of Orbis. Eventually, these will offer exciting timed adventures… from low-intensity exploration to difficult combat challenges and loot extraction gameplay… a sneak peek at what World Gen 2 is capable of". — [Hytale is finally here](https://hytale.com/news/2026/1/hytale-is-finally-here); [Future of World Generation](https://hytale.com/news/2026/1/the-future-of-world-generation)
- [EA] Ancient Gateway:
  - Crafted at the Arcanist's Workbench after **100 Memories**, from 30 Cobblestone, 10 Thorium Ingots, 10 Essence of Life and 10 Essence of the Void.
  - Interacting while holding a key opens a dialog with the fragment's info and an "open" button.
  - "Opening a portal… will consume one fragment key per use." An open portal "will vanish after one and half minutes if it is not entered".
  - Inside, a timer runs; when it expires "the gateway closes and the player is teleported back. If all players in a fragment die, the fragment will automatically close".
  - Since 0.6.0 the gateway has a close button, with "a failsafe that will prevent this button from working if someone is still on the other side". — [wiki: Ancient Gateway](https://hytalewiki.org/w/Ancient_Gateway); [Update 6](https://hytale.com/news/2026/8/update-6-patch-notes)
- [EA] Robustness rules:
  - "Portal devices will now refund fragments if the portal worlds fail to generate" (Update 4); keys are also refunded when there is no valid spawn (Update 6).
  - Destination worlds stay open and reconnect when you come back within range, and the time left can't exceed the starting time (Update 6).
  - [EA-pre] Portal worlds "are now removed from disk once the portal world's timer reaches 0" but "can now stay around between sessions when their asset asks for it", and "can now remain open without a time limit". A `fragment timer add` command and a HUD "gained time" tag exist. — [Update 4](https://hytale.com/news/2026/3/hytale-patch-notes-update-4); [Update 6](https://hytale.com/news/2026/8/update-6-patch-notes); [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [EA] The six fragments:

  | Fragment | Time | Key | How to get the key in Exploration | Content |
  |---|---|---|---|---|
  | Dragonspire Weald (`portals.boreal`) | 7 min | 10 Cobalt Ingots + 30 Essence of Ice | crafted | frozen forest; sleeping Frost Dragon; wildlife |
  | The Dread Wade (`portals.hederas_lair`) | 7 min (3 min during a Void Breach) | 10 Adamantite Ingots + 30 Essence of Fire | crafted | swamp island over the void |
  | Windrider Valley (`portals.oasis` / `windsurf_valley`) | 6 min | 10 Thorium Ingots + 30 Essence of Life | crafted | oasis crater; Feran town; 50% chance of a Tier 3 Sandswept Mage Tower (rarely 2–3) |
  | Silent Henges | 5 min | — | not obtainable | Creative only |
  | Tal'Kesi Refuge (`portals.jungles`) | 12 min | — | not obtainable | Creative only |
  | The Guttering Hollows (0.7.0) | 10 min (infobox: 10–14) | see below | crafted, or from loot | goblin fragment |

  — [wiki: Fragment](https://hytalewiki.org/w/Fragment); [wiki: Fragment Key](https://hytalewiki.org/w/Fragment_Key); [wiki: Windrider Valley](https://hytalewiki.org/w/Windrider_Valley)
- [EA] The Dread Wade is a swamp island "dropping off into the void on all sides". At its centre is a giant hollow stump holding "Hedera's Throne"; a Rhino Toad stands where the unimplemented Hedera boss was presumably meant to be. Small ruins hide chests under wooden planks. **Void Breach**: "After 7 minutes… the void breach begins… Void Invasion Portals begin appearing nearby to the player's position… As time continues, more void creatures spawn until the player dies or is ejected". The portals spawn a Void Eye and a Larva on creation, then keep spawning Crawlers and Fiends, until minute 10; the sky turns purple and a super moon rises. — [wiki: The Dread Wade](https://hytalewiki.org/w/The_Dread_Wade); [wiki: Void Breach](https://hytalewiki.org/w/Void_Breach); [wiki: Void Invasion Portal](https://hytalewiki.org/w/Void_Invasion_Portal)
  - The sources conflict. 0.7.0-pre.1 says "Reworked how the void event in Hedera's Lair builds up. It now escalates after fixed amounts of time", and 0.7.0-pre.4 says "Hedera's Lair will no longer end in a void breach", yet the wiki (edited 8 Oct 2026) still describes the breach after 7 minutes. Exactly how it behaves now is uncertain. — [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [EA-pre] **The Guttering Hollows** (0.7.0-pre.4, ID `portals.goblinBreach`) is a floating island with a central statue buried in a stone spire, forest and ruins, and an underground of Goblin hamlets. Its structures:
  - Cave Crypts: a Revenant Guardian at the bottom.
  - Cave Mines.
  - Cave Slums: a Goblin Feastmaster in the larger building below.
  - Enkindled Ruins.

  The **Void Surge**: "The longer the fragment stays open, the more the veil of the Void will close… Void NPCs will begin to spawn in higher and higher frequencies… before eventually cutting off the portal".

  The key is crafted at the Goblin Tinkerer's Workbench (15 Scraps + 10 Essence of the Void + 5 Iron Ingots), and every Tier 5 goblin chest inside contains one. — [wiki: The Guttering Hollows](https://hytalewiki.org/w/The_Guttering_Hollows); [wiki: Fragment: Orbis – The Guttering Hollows](https://hytalewiki.org/w/Fragment%3A_Orbis_-_The_Guttering_Hollows); [wiki: Cave Crypts](https://hytalewiki.org/w/Cave_Crypts)
- [EA-pre] **Coffer Event (waves for loot)**: "Locked Veilbound Coffers can be opened with the Veilbound Key during the Coffer Event… At the end of the Coffer Event, they will open, rewarding loot corresponding to the wave reached."
  - Veilbound Keys drop from "entombed Void Fiends", and "Veilbound items are lost when leaving the Fragment" (an extraction-style rule).
  - Payout by wave:

    | Wave | Scraps | Void Crystal Shards | Dubious Assembly | Voidheart | Other |
    |---|---|---|---|---|---|
    | 1 | 20 | 1 | — | — | — |
    | 2 | 40 | 2 | 1 | — | — |
    | 3 | 60 | 4 | 1 | 1 | — |
    | 4 | 85 | 6 | 2 | 2 | — |
    | 5 | 120 | 8 | 2 | 3 | a new Guttering Hollows key |

  — [wiki: The Guttering Hollows](https://hytalewiki.org/w/The_Guttering_Hollows); [wiki: Veilbound Key](https://hytalewiki.org/w/Veilbound_Key)
- [EA-pre / Shown 2026-07] The **Goblin Breach** world event: once you begin Chapter 1 at the Forgotten Temple, it "can trigger from time to time, creating a temporary portal somewhere in your world. Look around or open the world map to locate it… You'll appear in an unstable fragment of Orbis… This world gets regenerated every time you enter… the Void presses in and will eventually eject you". Activities inside: "predators you can track and trap, Void assassins that hunt you down, treasure chests that spawn enemy waves when you go for the loot, puzzles that lock off places you can't just dig into". — [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- [EA-pre] Goblin Breach tuning:
  - At least 30 minutes between breaches (up from 10).
  - A forming breach "will now vanish after 5 minutes if they're not approached".
  - A breach "will now launch players away from them as they emerge", with a protected no-build area around its spawn point.
  - "Hunts that take place on the other side of Goblin breaches now require a Voidheart", and "Hunt sites" have a world-map icon.
  - "Chests found on the other side of Goblin breaches will now be more rewarding… remain open once looted".
  - "Factions found on the other side… will tend to band together more on the surface".
  - World Events can be switched off in World Settings or with `/worldevent`.
  - A Goblin Breach Console "extends the time limit inside a Fragment. Each repair adds more time". — [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7); [wiki: Goblin Breach Console](https://hytalewiki.org/w/Goblin_Breach_Console)
- [EA] The **Forgotten Temple hub**: each zone's portal temple contains a Forgotten Temple Gateway, "always… marked on your map", that leads to the Forgotten Temple on a floating island "at the Edge of the Echo".
  - The hub is "programmed differently and the player can not break blocks there". Returning "will send the player back to the same portal they came from".
  - It is inhabited by Kweebecs and friendly animals, merchants, the Heart of Orbis, and domed buildings marked WIP that "will eventually lead to yet unknown game features, starting with Chapter 1".
  - "Game story chapters will each update the temple design." — [wiki: Portal dungeon](https://hytalewiki.org/w/Portal_dungeon); [wiki: Forgotten Temple](https://hytalewiki.org/w/Forgotten_Temple_%28Edge_of_the_Echo%29); [wiki: Ruined Temple](https://hytalewiki.org/w/Ruined_Temple)
- [EA] Challenge Gates: dark shale temple prefabs that spawn in every zone (Grasslands, Drylands, Snowlands, and two Firelands variants), containing braziers and an "Under Construction" sign; they "do not serve any functional purpose at this time". — [wiki: Challenge Gate](https://hytalewiki.org/w/Challenge_Gate)
- [Shown 2019-01] Original plan: portal dungeons as "a hand-designed encounter with its own rules and terrain. You'll be limited in what blocks you can break", with the Temple of Gaia and its Garden of Elements as the example; creators would be able to build "instanced portal dungeons". — [Worldgen Introduction](https://hytale.com/news/2019/1/worldgen-introduction)

### Inferences
- The Guttering Hollows key appears to be the Ancient Gateway route into the same world the Goblin Breach event opens (shared ID `portals.goblinBreach`); this is not stated by a first-party source.
- The owner's "Hytale dungeon where waves spawn and you survive for loot" most likely means the Void Breach in The Dread Wade (waves through invasion portals until you are ejected) or the Coffer Event (loot tier = wave reached). Both use escalating stages driven by data: pre-release notes mention a "Stages list" in `Portal.json` that "now drives the whole escalation". — [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- Hytale's fragments are timed extraction runs, not "kill the boss to complete". Realmforge's owner rule (completion = final boss or last wave) is a deliberate difference.

### Gaps
- I found no first-party write-up of exactly how many Coffer Event waves there are, or what each wave spawns.

---

## Q7. Creatures, factions and AI behaviours that create a sense of life

### Takeaway
Each zone pairs a friendly or neutral faction with a hostile one (Kweebec/Trork, Feran/Scarak, then Outlander), with undead and Void layered across zones. Each faction has its own blockset, furniture, chest style, archetypes and settlement tiers. The sense of life comes from many small data-driven behaviours: villagers wary of axes, sparring Trorks, bait and fear reactions, pack hunting, predators hunting prey, mimicry. The framework is strong, but pathfinding, flocking and combat positioning are still weak.

### Cited Findings
- [EA] Faction dispositions: friendly (Kweebecs), neutral (Ferans, Klops) and hostile (Trorks, Outlanders, Scaraks, Goblins). Unimplemented: Bramblekin, Humans (ruins only), Saurians, Slothians, Tuluk. — [wiki: Faction](https://hytalewiki.org/w/Faction)
- [Shown 2018-12] Design intent: "NPC factions form societies with their own culture, habits, blockset, and role to play in the story". — [Get to know Hytale's NPCs](https://hytale.com/news/2018/12/get-to-know-hytale-s-npcs)
- [EA] Kweebecs (tree-folk):
  - Holding a hatchet near their village makes them "paranoid and visibly less friendly". The hatchet tooltip explains: as long as you don't enter their woodlands with such a blade, "it's unlikely they'll ever bring it up".
  - Razorleaf guards attack anyone who harms villagers and forgive you after you leave for a while.
  - They sunbathe for nutrients, "dip their feet in water to drink", fight Void creatures, and ignore bears or skeletons that follow you into the village.
  - Life cycle: seedling → sproutling → sapling → rootling → elder → tree. — [wiki: Kweebec](https://hytalewiki.org/w/Kweebec)
- [EA] Trorks: archetypes Sentry, Warrior, Hunter, Elder/shaman, Brawler, Mauler, Chieftain. "Trork sentries dislike sparrows… may occasionally even throw their spear at a sparrow, disarming themselves momentarily." They randomly punch each other, and the loser naps until it recovers. They are partly scavengers who enjoy discarded meat. — [wiki: Trork](https://hytalewiki.org/w/Trork); [Building NPC behaviors (Shown 2019-04)](https://hytale.com/news/2019/4/an-introduction-to-building-npc-behaviors)
- [Shown 2019-04] Trork behaviour script: an "alerted" animation plus a "beacon" response that alerts other Trorks within a radius. A Trork "napping outside when it begins to rain… decides to seek out somewhere warm… moves to the nearby campfire". The team's goal is creatures "acting independently of the player". — [Building NPC behaviors](https://hytale.com/news/2019/4/an-introduction-to-building-npc-behaviors)
- [EA] Ferans (fox-folk): "will flee to maintain some distance, unless provoked", watch and follow the player, hit you if you get too close, and "attack any Scaraks who stray too far into their territory". — [wiki: Feran](https://hytalewiki.org/w/Feran); [wiki: Faction](https://hytalewiki.org/w/Faction)
- [EA] Scaraks (insect caste society):
  - Lice hatch from egg sacks and grow into Fighters, Seekers, Workers or Defenders.
  - Seekers guard the skies (and per a guide, alert the nest).
  - "Multiple Defenders can line up to make a large wall".
  - They communicate with light and gesture. — [wiki: Scarak](https://hytalewiki.org/w/Scarak); [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/)
- [EA] Outlanders: corrupted humans; archetypes Hunter, Berserker, Marauder, Initiate, Priest, Brute, Stalker, Unsworn. Settlement types: villages, mines, the Shale Dungeon, the ritual site and the temple. Lore comes through tooltips, e.g. the Watcher statue: "when they arrived, the whispers were already there". — [wiki: Outlander](https://hytalewiki.org/w/Outlander)
- [EA / EA-pre] Goblins:
  - Scrappers tease each other into fist fights and a Lobber steps in. Goblins steal loose items and throw bombs at cave walls when annoyed. Their tech is "Rejected" blueprints with jokes in the tooltips.
  - The elite Feastmaster picks up a Scrapper and throws it at you, summons Scrappers, and "will roar at them in order to break up the fight".
  - In 0.7.0-pre: "Goblins found in Crypts will now tend to keep their distance from any Skeletons inside", "Some lazy Goblins have found better sleeping spots", "Some Goblins have discovered fashion". — [wiki: Goblin](https://hytalewiki.org/w/Goblin); [wiki: Goblin Feastmaster](https://hytalewiki.org/w/Goblin_Feastmaster); [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [EA] Undead are re-skinned for each zone, giving one shared enemy "faction" a local flavour:
  - Zone 1: plain skeletons, including Knights, Mages, Archmages and Rangers; Tier 1 ruins have plain skeletons.
  - Zone 2: Sandswept Skeletons, with "about 10 different Sandswept Skeleton variants" in a T3 ruin, and Sand Zombies at the Trading Post.
  - Zone 3: Frost Skeletons in the Whisperfrost ruins.
  - Zone 4: Burnt Skeletons in the Devastated ruins, including Alchemists, Knights, Lancers and the Praetorian, plus undead cows, pigs and chickens.
  - Skeletons also burst out of coffins in tombs. — [wiki: Emerald Wilds Ruins](https://hytalewiki.org/w/Emerald_Wilds_Ruins); [wiki: Howling Sands Ruins](https://hytalewiki.org/w/Howling_Sands_Ruins); [wiki: Howling Sands Trading Post](https://hytalewiki.org/w/Howling_Sands_Trading_Post); [wiki: Whisperfrost Ruins](https://hytalewiki.org/w/Whisperfrost_Ruins); [wiki: Devastated Ruins](https://hytalewiki.org/w/Devastated_Ruins); [wiki: Abandoned Village](https://hytalewiki.org/w/Abandoned_Village); [wiki: Devastated Lands](https://hytalewiki.org/w/Devastated_Lands); [Update 5](https://hytale.com/news/2026/5/update-5-patch-notes)
- [EA] Wildlife ecology:
  - Zone 1 animal tables give spawn group sizes and biomes, e.g. horse "Medium (4–6)" on plains, boars "Small (3–5)" in forests, a single fox, mountain goats.
  - Fish are biome-specific: aggressive piranhas in swamps and the Azure Forest, catfish and trout in forests and mountains.
  - Predator, Prey and Big Prey roles exist; "As a Big Predator, the Yeti will hunt any Prey or Big Prey NPCs".
  - Update 4: "Black and white wolves will now fight as a pack, periodically attempting to flank their prey and attempting to retreat when injured."
  - Update 1: "Cautious wildlife predators, such as the Fox, should no longer engage players unless attacked." — [wiki: Emerald Wilds](https://hytalewiki.org/w/Emerald_Wilds); [wiki: Yeti](https://hytalewiki.org/w/Yeti); [Update 4](https://hytale.com/news/2026/3/hytale-patch-notes-update-4); [Update 1](https://hytale.com/news/2026/1/hytale-patch-notes-update-1)
- [EA] Fen Stalker (swamp elite): baited with raw fish ("completely docile until finished"), scared off by torch fire at night, "If a Fen Stalker sees a frog they try to mimic them by jumping… if it sees a frog getting attacked, it will jump to its aid"; it can't swim. — [wiki: Fen Stalker](https://hytalewiki.org/w/Fen_Stalker); [Progress Update March 2020 (Shown)](https://hytale.com/news/2020/3/progress-update-march-26-2020)
- [Shown 2020-03] Mice "tend to gather around barrels and chests in dungeons… They might lead you to some loot", and calm down while eating cheese. [Shown 2018-12] Saber-tooth tigers hunt in packs; "chicks will follow a mother hen… grown chickens will jealously guard their eggs". — [Progress Update March 2020](https://hytale.com/news/2020/3/progress-update-march-26-2020); [Get to know Hytale's NPCs](https://hytale.com/news/2018/12/get-to-know-hytale-s-npcs)
- [EA] Day/night:
  - Void creatures (Crawler, Eye, Larva, Spawn, Spectre) spawned at night in Zones 1–3 on the stable branch; [EA-pre] 0.7.0-pre.4: "Void-aligned creatures will no longer appear within the Echo of Orbis at night".
  - PC Gamer at launch found "No monsters, though, at least no more than there are during the daytime".
  - Fen Stalkers are most aggressive at night. — [wiki: Memories](https://hytalewiki.org/w/Memories); [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7); [PC Gamer review](https://www.pcgamer.com/games/survival-crafting/hytales-developer-warned-us-the-adventure-sandbox-isnt-good-yet-but-it-already-feels-pretty-good-to-me/)
- [EA] Reacting to the player: since Update 6, "opening chests or interacting with objects can once again cause nearby creatures to notice your presence". [EA-pre] Spiders "place cobwebs more effectively… more natural-looking patches in spider encounters". — [Update 6](https://hytale.com/news/2026/8/update-6-patch-notes); [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [EA] The NPC framework:
  - Every NPC has a "Role" made of JSON instruction lists: a sensor plus actions or motions, with fallback-selector semantics and 150+ element types.
  - A utility-based "Combat Action Evaluator" weighs attacks by conditions (low HP, distance, flanking).
  - Debug views show sensor cones, flock leader and members, and leash position, e.g. a sleeping bear has "only one sensor active". — [NPC Technical Rundown](https://hytale.com/news/2026/2/npc-technical-rundown)
- [EA] Limitations the team admits: "NPCs don't position themselves very well in combat; they don't use proper avoidance or flocking… swimming NPCs can't leave the water". Pathfinding is restricted for performance, so "cave raptors don't jump across gaps". GamingTrend: "enemies don't currently use any strategy besides running straight at you". — [NPC Technical Rundown](https://hytale.com/news/2026/2/npc-technical-rundown); [GamingTrend (2026-02-26)](https://gamingtrend.com/impressions/hytale-early-access-impressions/)
- [Shown 2026-07] Planned behaviour work:
  - Birds "sleep in nests, fly and land as a flock, take off when you get close, and grab dropped seeds. Owls and other birds of prey can even swoop down to hunt smaller creatures".
  - Foxes "follow you on adventures, stalk prey, sleep beside campfires".
  - Kweebec Seedlings follow you, and Saplings act as "dutiful guardians of their community that nurture the fauna and flora".
  - "All factions… will be reworked in future chapters". — [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)

### Inferences
- Hytale's sense of life comes from a large number of small, readable rules (wary of axes, sparring, mimicry, bait), each one data-driven and reusable. It does not come from deep simulation; this is achievable in Minecraft with goals and brains.

### Gaps
- No source quantifies spawn densities. The "Encounters" system (ruin prefabs named `Encounter_Zone1_Tier1_xxx`) is barely documented. — [wiki: Encounter](https://hytalewiki.org/w/Encounter); [wiki: Emerald Wilds Ruins](https://hytalewiki.org/w/Emerald_Wilds_Ruins)

---

## Q8. Progression: gear tiers and materials, crafting benches, Memories (bestiary), NPC interactions and quests

### Takeaway
Progression is driven by materials: ore tiers per zone feed tiered benches, and each bench upgrade pulls in materials from other zones, including faction drops and the gems dropped by temple golems. **Memories** are a bestiary. You record creatures you meet (some must now be killed), bring them back to the Heart of Orbis in the hub, and unlock permanent rewards at 10/25/50/100/200: recipes, teleporter capacity, the Ancient Gateway, backpack upgrades. NPC interaction is mostly trading; quests are only arriving now, as Chapter 1 objectives.

### Cited Findings
- [EA] Material order: Crude → Copper → Iron → Thorium → Cobalt → Adamantite (Mithril and Onyxium exist in the data and in Creative). Higher tiers need upgraded Armorer's and Blacksmith benches. — [Kweebedia](https://kweebedia.com/progression/adventure-route/); [wiki: Zone 5](https://hytalewiki.org/w/Zone_5)
- [EA] Benches:
  - Workbench, with tiers T1–T3 (costs in Q2), which "can interact with nearby storage". Since Update 1 benches pull materials from chests within 14 blocks horizontally and 6 vertically.
  - Specialised benches: Farmer's, Furniture, Builder's, Armorer's, Blacksmith's Anvil, Salvager's, Chef's Stove, Alchemist's, Arcanist's and [EA-pre] the Goblin Tinkerer's Workbench. — [Hytale is finally here](https://hytale.com/news/2026/1/hytale-is-finally-here); [Update 1](https://hytale.com/news/2026/1/hytale-patch-notes-update-1); [wiki: Workbench](https://hytalewiki.org/w/Workbench)
- [EA] Each tier of the Alchemist's Workbench is gated by a temple-golem gem: T2 Emerald (Zone 1), T3 Zephyr (Zone 2), T4 Sapphire (Zone 3), T5 Ruby (Zone 4), plus silver, gold, Venom Sacs, Sturdy Chitin, Shadoweave and Cindercloth. — [wiki: Alchemist's Workbench](https://hytalewiki.org/w/Alchemist%27s_Workbench)
- [EA] Arcanist's Workbench (teleporters, gateway, fragment keys): 10 Thorium Ingots + 30 Linen + 20 Essence of the Void at a T2 Workbench. — [wiki: Arcanist's Workbench](https://hytalewiki.org/w/Arcanist%27s_Workbench)
- [EA] **Memories**:
  - Unlocked by interacting with the Heart of Orbis in the Forgotten Temple. "You can collect memories by encountering creatures"; 225 of 245 are confirmed obtainable in Exploration.
  - You can carry at most 48 unrestored memories at a time.
  - The Heart's page shows each creature with the time and biome where you met it.
  - Categories: Abyssal, Avian, Critter, Elemental, Feran, Freshwater, Goblin, Kweebec, Livestock, Mythic, Outlander, Predator, Reptile, Scarak, Trork, Undead, Voidspawn, Voidtaken. — [wiki: Memories](https://hytalewiki.org/w/Memories)
- [EA] Memory rewards:
  - 10: decorative chests and Eternal Seeds.
  - 25: +1 max teleporter.
  - 50: Morph Potions, +1 teleporter.
  - 100: Ancient Gateway, portal fragments (keys), Backpack Upgrade II, +2 teleporters.
  - 200: Harvest Trophy, +2 teleporters.

  "Unlocking permanent rewards and upgrades for everyone in your world". — [wiki: Memories](https://hytalewiki.org/w/Memories); [Hytale is finally here](https://hytale.com/news/2026/1/hytale-is-finally-here)
- [EA-pre] "Some creatures must now be defeated in combat before you gain their Memories" (0.7.0-pre.3; config `MemoriesUnlockedBy: Proximity | Kill`). Guides say memories do not count before you activate the Heart. — [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7); [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/)
- [EA] Trading:
  - Merchants accept essences as currency.
  - The Kweebec Rootling Merchant takes Essence of Life, sells 9 items (food, Chef's Stove ingredients, high-end pie recipes), and restocks every 3 in-game days at 6:00.
  - The Klops Merchant sits at the hub.
  - [EA-pre] "one [friendly goblin] runs a shop". — [wiki: Merchant](https://hytalewiki.org/w/Merchant); [wiki: Kweebec](https://hytalewiki.org/w/Kweebec); [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- [EA] Quests: Exploration has no quest content. Lore is "uncovered almost exclusively through archaeology and discovery": structures, items and tooltips. Update 3 mentions "NPCReputation" and "NPCObjectives" modules. — [A look at Hytale's Lore and Philosophy (2026-01-08)](https://hytale.com/news/2026/1/a-look-at-hytale-s-lore-and-philosophy); [Update 3](https://hytale.com/news/2026/2/hytale-patch-notes-update-3)
- [Shown 2019-12] A "Kill 12 skeletons in the Emerald Grove ruins" quest that rewards a Fire Sword. — [wiki: Emerald Grove Ruins (Quest)](https://hytalewiki.org/w/Emerald_Grove_Ruins_%28Quest%29)
- [EA-pre / Shown 2026-07] Chapter 1 adds a chapter screen ("a roadmap for your progress through Early Access… finish objectives… unlock cosmetics"), lore notes (Goblin paintings, Enkindled journals), and the rune system: Ability and Modifier Runestones in a Runebinder's Lattice, with 2 ability slots and 2 modifiers each, on the E and R keys. — [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more); [wiki: Runebinder's Lattice](https://hytalewiki.org/w/Runebinder%27s_Lattice); [wiki: The Echo of Orbis](https://hytalewiki.org/w/The_Echo_of_Orbis)
- [Shown 2025-03] Adventure progression will centre on "fundamental lifeskills like cooking and smithing, along with a revamped and expanded approach to POIs and encounters". — [Spring 2025 Development Update](https://hytale.com/news/2025/3/spring-2025-development-update)

### Inferences
- Gating bench tiers on guardian gems turns every portal temple into a mandatory but repeatable progression step.
- Memories reward exploring every corner of a zone without forcing quests. Capping how many you can carry forces trips back to the hub, which keeps the hub alive.

### Gaps
- The teleporter cap is disputed:
  - The wiki says 2 by default, then 3 at 25 memories, 4 at 50, 6 at 100 and 8 at 200.
  - Kweebedia says 6 at the start, up to 12.
  - Switchblade says 5, up to 10.

  The wiki's per-milestone figures are internally consistent. — [wiki: Teleporter](https://hytalewiki.org/w/Teleporter); [Kweebedia](https://kweebedia.com/progression/adventure-route/); [Switchblade](https://www.switchbladegaming.com/hytale/all-zones-guide/)

---

## Q9. Food, cooking, potions and buffs; mounts, gliders, teleports and fast travel

### Takeaway
Food and potions give short, tiered buffs: food at most 6 minutes, potions instant or over a few seconds, with shared cooldowns. That is the opposite of the owner's "weak but long-lasting". Travel relies on crafted teleporter pairs (capped, gated by Memories), tamed mounts and, with Chapter 1, goblin gadgets: a stamina-limited glider, rocket boots, a hookshot and boats. Teleporting from the world map is reserved for operators.

### Cited Findings
- [EA] Food buffs (wiki):
  - Tier I (raw crops and fruit, Cooked Wildmeat, Grilled Fish): Health Regen I (1 HP per 2 s) or Health Boost I (+5% max HP), 45 s.
  - Tier II (skewers, salads): Health Regen II (1.5 HP per 2 s), Health Boost II (+10%), Stamina Boost II (+20% max stamina), 1 min 30 s.
  - Tier III (Meat/Apple/Pumpkin Pie, Caesar Salad): Health Regen III (2 HP per 2 s), Health Boost III (+15% max HP and +5% physical/projectile resistance), Stamina Boost III (+30%), 6 min.
  - Instant heals of 5/10/15% by food rarity. — [wiki: Entity Effect](https://hytalewiki.org/w/Entity_Effect); [wiki: Food](https://hytalewiki.org/w/Food)
- [EA-pre] 0.7.0-pre.4:
  - "Buffs granted from consuming cooked food will now last 6 minutes", and raw fruit or vegetables give a minor regen buff for 1 minute.
  - Food takes 2.5 s to eat; taking damage no longer interrupts eating or drinking.
  - Bread, Popcorn and Cheese now heal 10%. — [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [EA] Potions are brewed at the Alchemist's Workbench:
  - Health potion tiers (Update 4), from Lesser (15% instantly + 15% after 5 s) to Greater (25% + 50%). The Health Potion gives 25% + 35%, and the delayed part "is removed when taking damage".
  - Stamina potions restore 30–90% instantly. Energy potions give 30–90% signature energy over 30 s.
  - Antidote: 2 min of protection against spider poison.
  - Morph potions (dog, frog, pigeon, mouse, mosshorn) last 60 s and unlock at 50 Memories.
  - [EA-pre] Health potions share a 15 s cooldown and stamina potions a 30 s cooldown. — [Update 4](https://hytale.com/news/2026/3/hytale-patch-notes-update-4); [wiki: Health Potion](https://hytalewiki.org/w/Health_Potion); [wiki: Potion of Dog Morphing](https://hytalewiki.org/w/Potion_of_Dog_Morphing); [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [EA] Farming: crop growth depends on light (2× near artificial light), water (2.5×) and fertilizer (2×). Eternal Seeds regrow after harvest (a Memories reward). Crops convert to Essence of Life, the merchants' currency. — [wiki: Farming](https://hytalewiki.org/w/Farming)
- [EA] Survival: there is no hunger, thirst or temperature system. "you have to eat to regain health, you can sleep to pass the day"; you can sleep from 7:30 PM. — [PC Gamer review](https://www.pcgamer.com/games/survival-crafting/hytales-developer-warned-us-the-adventure-sandbox-isnt-good-yet-but-it-already-feels-pretty-good-to-me/); [Rambling Reviews](https://ramblingreviews.substack.com/p/hytale); [Update 1](https://hytale.com/news/2026/1/hytale-patch-notes-update-1)
- [EA] Teleporters:
  - Crafted at the Arcanist's Workbench: 8 Azure Logs + 2 Azure Kelp + 10 stone → **2** teleporters.
  - You name each one and choose its "Target Warp"; NPCs can be teleported too.
  - The cap grows with Memories (see the Q8 gap).
  - Since Update 3 you must "clear a short distance before re-use".
  - Teleporting from the world map needs permissions (anti-cheat). — [wiki: Teleporter](https://hytalewiki.org/w/Teleporter); [Update 3](https://hytale.com/news/2026/2/hytale-patch-notes-update-3); [Update 2](https://hytale.com/news/2026/1/hytale-patch-notes-update-2)
- [EA] Mounts: horse, camel and ram (one per zone theme) are tamed by holding their favourite food. A thought bubble shows that food, and a heart appears when you hold it. Tamed animals can be petted, fed, milked, sheared or ridden. — [wiki: Mount](https://hytalewiki.org/w/Mount); [wiki: Taming](https://hytalewiki.org/w/Taming); [Update 3](https://hytale.com/news/2026/2/hytale-patch-notes-update-3)
- [EA-pre] Goblin gadgets:
  - **Scrap Glider**: reduces gravity and negates fall damage. You dive and climb with Sprint and keep your velocity on deploy; it is stamina-limited, with a falling animation when stamina runs out. Recipe: 50 Scraps, 6 Dubious Assembly, 3 Spider Silk, 1 Voidtaken Grizzly Hide, at the Goblin Tinkerer's bench with a Boiler.
  - **Rocket Boots**: a double jump.
  - **Hookshot**: pulls you to Hook Anchors you have placed.
  - Scrap Drill, which mines 2×2, and Spyglass. — [wiki: Scrap Glider](https://hytalewiki.org/w/Scrap_Glider); [wiki: Rocket Boots](https://hytalewiki.org/w/Rocket_Boots); [wiki: Hookshot](https://hytalewiki.org/w/Hookshot); [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [Shown 2025-11] The original Glider was "a tool that was meant to come in handy for the new World Generation"; it was removed in 0.7.0-pre.1 and replaced by the Scrap Glider. — [wiki: Glider](https://hytalewiki.org/w/Glider)
- [EA-pre / Shown 2026-07] Basic boats, and carrying objects (chests, explosive barrels, and later "carry-only items… too big or too heavy" for the inventory). — [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- [EA] Movement: mantling and climbing ("who know a block game would benefit so much from a mantling mechanic"), launch platforms in towers, and minecarts in mines. — [GamingTrend](https://gamingtrend.com/impressions/hytale-early-access-impressions/); [wiki: Launch Platform](https://hytalewiki.org/w/Launch_Platform); [Update 3](https://hytale.com/news/2026/2/hytale-patch-notes-update-3)

### Inferences
- Hytale chains its convenience items to long gates: teleporters need the Arcanist bench (Thorium plus Void essence) and Azure wood from one biome; the glider needs a rare hide. Guides spend whole sections warning about this, which is a sign of friction.

### Gaps
- I found no complete official recipe list for foods; the effects tables come from the wiki.

---

## Q10. Techniques that make the world feel alive and full (synthesis of Q2–Q9)

### Takeaway
Hytale's fullness comes from stacking layers:
1. Zone-specific everything: blocks, furniture, chest styles, factions, fauna, music.
2. Tiered structure families at every scale, from a rubble pile to a castle, and from a tent to a fort.
3. Small behaviour quirks.
4. Environmental "tells" that signpost hidden content.
5. Lore told through ruins and tooltips.
6. A hub that changes over time.
7. Rotating instanced side worlds.

Its weak spots are placeholders and "WIP" signs, structures spawning too densely, shallow NPCs, and loot padded with filler.

### Cited Findings
- [Shown 2026-01, V2 design principle] **Heuristics** are environmental tells. "ash trees spawn above caves in the deeproot biome… players can learn that deeproot caves, which contain special resources, are found under dark ash trees on the map". Other examples: "A log bridge spawns across a river by searching for two shores"; "A yellow mushroom and some sticks spawn on the ground around the base of some cedar trees"; "Glowing mushrooms that only appear in sheltered areas"; "A meadow of flowers spawns in a dip surrounded by small trees and boulders". — [The Future of World Generation](https://hytale.com/news/2026/1/the-future-of-world-generation)
- [EA] Tells that already exist:
  - Ice spikes plus a campfire mark a Yeti cave. — [wiki: Yeti](https://hytalewiki.org/w/Yeti)
  - Graveyards mark the Cursed Crypt. — [wiki: Cursed Crypt](https://hytalewiki.org/w/Cursed_Crypt)
  - Fortified libraries mark Goblin Lairs. — [wiki: Goblin Lair](https://hytalewiki.org/w/Goblin_Lair)
  - Dangling ropes mark mine shafts. — [wiki: Mineshaft](https://hytalewiki.org/w/Mineshaft)
  - Volcano treasure shows as a yellow spot on the map. — [wiki: Volcano](https://hytalewiki.org/w/Volcano)
  - Challenge Gates are "the only type of black buildings" on the map. — [wiki: Emerald Wilds](https://hytalewiki.org/w/Emerald_Wilds)
  - Hub gateways are always marked. — [wiki: Forgotten Temple Gateway](https://hytalewiki.org/w/Forgotten_Temple_Gateway)
- [Shown 2019-01] "Happy little accidents" come from rule collisions: "rivers that carve their way through mountains… dungeons that have been split in half by a ravine… a lost mine deep within" a cave. — [Worldgen Introduction](https://hytale.com/news/2019/1/worldgen-introduction)
- [EA] Every structure family comes in tiers: Trork camps T1 house → T2 quarry or lumber camp → T3 castle; Outlander villages T1–T3 (plus lake villages); ruins T1 rubble → T3 castle; Zone 4 ruins tiered by region. One kind of POI scales with the region. — [wiki: Trork Camp](https://hytalewiki.org/w/Trork_Camp); [wiki: Outlander Village](https://hytalewiki.org/w/Outlander_Village); [wiki: Emerald Wilds Ruins](https://hytalewiki.org/w/Emerald_Wilds_Ruins); [wiki: Devastated Ruins](https://hytalewiki.org/w/Devastated_Ruins)
- [EA] Structures in the same family vary through optional rooms, alternative entrances and themed variants: the Shale Dungeon's unique rooms, the Magic Ruins' three entrances, three Kweebec village types, portal temples re-skinned per zone. — [wiki: Shale Dungeon](https://hytalewiki.org/w/Shale_Dungeon); [wiki: Magic Ruins](https://hytalewiki.org/w/Magic_Ruins); [wiki: Kweebec Village](https://hytalewiki.org/w/Kweebec_Village); [wiki: Portal dungeon](https://hytalewiki.org/w/Portal_dungeon)
- [EA] Atmosphere and audio:
  - Zone-specific soundtracks: 24 new Zone 4 tracks, plus 10 for Zone 3 and 5 for Zone 2.
  - Cave music crossfades between layers instead of swapping tracks.
  - "Traveling Band" plays in Zone 1 at night; fireflies appear more regularly in Zone 1 plains.
  - Mountain ambience changes with altitude.
  - Since Update 6, biome colours blend smoothly at borders.
  - [Shown 2026-07] Blocks can emit spatial music. — [Update 7 pre-release](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7); [Update 5](https://hytale.com/news/2026/5/update-5-patch-notes); [Update 4](https://hytale.com/news/2026/3/hytale-patch-notes-update-4); [Update 6](https://hytale.com/news/2026/8/update-6-patch-notes); [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- [EA] Lore through "archaeology": "lore… will be uncovered by exploring the world and uncovering hints left in the form of structures and the items or objects found therein". Examples are tooltips on ingots and statues, decapitated Gaia statues in the Firelands Temple, and goblin paintings that tell a story across six pieces. — [Lore and Philosophy](https://hytale.com/news/2026/1/a-look-at-hytale-s-lore-and-philosophy); [wiki: The Echo of Orbis](https://hytalewiki.org/w/The_Echo_of_Orbis)
- [EA] The hub changes over time: "Game story chapters will each update the temple design"; WIP domes stand ready for future chapters. — [wiki: Forgotten Temple](https://hytalewiki.org/w/Forgotten_Temple_%28Edge_of_the_Echo%29)
- [EA] Inhabitants come back: golems respawn after an in-game day, the arena Praetorian daily, cleared camps when you return. — [wiki: Golem](https://hytalewiki.org/w/Golem); [wiki: Skeleton Arena](https://hytalewiki.org/w/Skeleton_Arena); [wiki: Trork Camp](https://hytalewiki.org/w/Trork_Camp)
- [EA] One elite is reused across zones as a surprise: the Zone 4 Burnt Skeleton Praetorian is the boss of the Zone 1 Cursed Crypt, and appears as a rare daytime roaming group in Zone 1. — [wiki: Cursed Crypt](https://hytalewiki.org/w/Cursed_Crypt); [wiki: Encounter](https://hytalewiki.org/w/Encounter)
- [EA, criticism] Fullness can overshoot: "I think their generation rate is currently a tad over-tuned. Sometimes it's hard to find a blank stretch of land for building without being within arms reach of an infested tower or ancient ruin." — [GamingTrend](https://gamingtrend.com/impressions/hytale-early-access-impressions/)
- [EA] Placeholders: WIP signs in villages and ruins; a vendor "who can't vend anything"; Challenge Gates with no function; Slothian villages without Slothians. One reviewer liked the WIP signs as teasers ("great practice"); PC Gamer and others flagged them as unfinished. — [PC Gamer review](https://www.pcgamer.com/games/survival-crafting/hytales-developer-warned-us-the-adventure-sandbox-isnt-good-yet-but-it-already-feels-pretty-good-to-me/); [Rambling Reviews](https://ramblingreviews.substack.com/p/hytale); [wiki: Challenge Gate](https://hytalewiki.org/w/Challenge_Gate)

### Inferences
- In Minecraft terms, the techniques that carry over most cheaply:
  - Tiered prefab families per biome and region.
  - Placed features conditioned on what lies beneath (a tree above a cave).
  - Per-biome soundtracks with ambience layers.
  - Small mob AI goals (fear of torches, bait, mimicry, pack flanking).
  - Respawning guardians.
  - Tooltip lore.

### Gaps
- None beyond those noted in earlier sections.

---

## Q11. What players and reviewers praise or criticise about world fullness, dungeons and pacing

### Takeaway
Critics praised variety: biomes, creatures, structures, combat and movement. They criticised the lack of direction and vague progression, simple AI, placeholders, and grind for materials. The developer said in July 2026 that there were "not many reasons to come back". In short: an exciting world to explore once, with thin depth and replay value until chapters land.

### Cited Findings
- PC Gamer (14 Jan 2026): "it already feels pretty good"; praised gameplay, the variety of biomes and the NPCs. The reviewer met swamp creatures, a yeti, troll villages, "ruined forts filled with skellies" and a glacier "all in a single excursion", but also "a vendor who can't vend anything" and WIP signs. — [PC Gamer](https://www.pcgamer.com/games/survival-crafting/hytales-developer-warned-us-the-adventure-sandbox-isnt-good-yet-but-it-already-feels-pretty-good-to-me/); [Wikipedia](https://en.wikipedia.org/wiki/Hytale)
- Eurogamer called it "already enjoyable", highlighting its variety; IGN praised gameplay, controls and variety but "criticised its general lack of direction" (summarised by Wikipedia; I could not read the original reviews). — [Wikipedia](https://en.wikipedia.org/wiki/Hytale)
- PC Gamer (11 Feb 2026): "dungeons are stuffed with loot and unique monsters… its dungeons are exciting to poke around… the horizon in Hytale already conceals portals to faraway temples, magical grimoires, giant yetis", but "its NPCs don't say much just yet". — [PC Gamer](https://www.pcgamer.com/games/survival-crafting/poking-around-hytales-unfinished-sandbox-is-the-first-time-ive-felt-hope-for-a-voxel-rpg-since-cube-world-broke-my-heart-twice/)
- GamingTrend (26 Feb 2026): structure density is "over-tuned"; "Progression can sometimes be vague, requiring external research in forums and wikis"; enemies just run straight at you. — [GamingTrend](https://gamingtrend.com/impressions/hytale-early-access-impressions/)
- Rambling Reviews (blog): the world gives "a lot more stimulation than a Minecraft world that feels kind of empty in comparison"; Memories give "a proper incentive to explore and discover". — [Rambling Reviews](https://ramblingreviews.substack.com/p/hytale)
- Staley News (student opinion piece, 20 Feb 2026): "often having to search for hours just for one material"; the loop is "uninspired". — [Staley News](https://staleynews.com/opinion/2026/02/20/game-review-hytales-long-awaited-launch-misses-the-magic/)
- GGServers (a hosting company's blog, low authority): "around 20–30 hours of meaningful gameplay before hitting progression limits"; "Boss encounters are currently limited". — [GGServers](https://ggservers.com/blog/a-complete-hytale-review/)
- Simon Collins-Laflamme (16 Jul 2026): "there aren't many reasons to come back and play Hytale right now". — [First Look: Chapter 1](https://hytale.com/news/2026/7/first-look-chapter-1-and-more)
- Jan 2026 backlash: the necromancy grimoire, which is consumed on use and needs a boss drop, worried Reddit about the future magic system; Simon called the staves, totems and grimoires "all ass by design" for now. — [PC Gamer](https://www.pcgamer.com/games/survival-crafting/after-hytale-subreddit-goes-full-panic-mode-dev-assures-players-the-new-patch-is-not-the-magic-system-thats-coming-in-the-full-game/)
- Minecraft's Jens Bergensten called Hytale "very polished" but said he'd "rather just play Minecraft". — [Wikipedia](https://en.wikipedia.org/wiki/Hytale)

### Inferences
- The world itself is not called empty; reviewers call the structure density high. The complaints are about purpose and depth: no quests, weak bosses (golems and elites, no multi-phase bosses until Chapter 1), dumb AI, placeholders, and material grind.

### Gaps
- Reddit and other community-forum sentiment is missing: Reddit returned HTTP 403 to automated access.
- The IGN and Eurogamer originals could not be read: the IGN URL returned 404 and the Eurogamer search was rejected.

---

## Relevance to Realmforge

These points are tied to the owner decisions of 11–12 Oct 2026, docs/vision.md (no flying bosses, no pointless walks, no filler loot) and roadmap items Spike 3, W4–W6 and M4. Mechanics and structure only: Hytale is proprietary, and its EULA forbids redistributing game files even with "Shared Source" ([Wikipedia](https://en.wikipedia.org/wiki/Hytale)).

### Adopt
1. **Realm = zone model.** Give each realm three sub-regions of rising tier, each with 3–6 biomes, and give each biome terrain or settlement variants (lake, valley, canyon, caldera, village, volcano). Map the sub-regions to level sub-bands inside a chapter, e.g. desert realm 10–13 / 13–16 / 16–20. This is how Hytale keeps a single "style" from becoming monotonous (Q2).
2. **Per-realm faction pair and tiered settlements.** One friendly or neutral faction and one hostile faction per realm, each with its own blockset, furniture and chest type. Settlements in three tiers (T1 hamlet or ruin → T2 outpost → T3 fort or castle with an elite), plus campsites and outposts in between. This is the direct antidote to Advent of Ascension's empty dimensions (Q4, Q7, Q10).
3. **The instanced-dungeon portal matches Hytale's Ancient Gateway, and its robustness rules carry over.** Specifically:
   - The key is consumed when the portal opens.
   - The portal fades if nobody enters within about 90 s.
   - Refund the key if generation fails.
   - No "close" while a player is inside.
   - Delete the instance from disk when it closes.
   - Eject everyone if all players die.

   These feed straight into Spike 3 (plot cleanup) and W4 `InstanceManager` (Q6).
4. **Wave-survival instance modelled on the Coffer Event.** Locked coffers in the arena; elites in later waves drop keys that are "lost on leaving"; when the event ends, coffers pay out by the last wave cleared; the final wave can return a key. Completion = last wave cleared, as the owner decided (Q6).
5. **An encounter schema, like the Encounter Manager.** Make phases, wave spawns and arena changes data, so one schema covers both the miniboss → miniboss → boss chain and wave arenas. This fits the YAML content pipeline and W5 `BossPhaseMachine` (Q5, Q6).
6. **A guardian at each realm's portal temple.** It stands still, has a 10–15-block leash, respawns daily and drops a gem that gates a crafting tier, the way Hytale's golem gems gate Alchemist tiers. This could also be the source of the next realm's non-consumable realm item (Q5, Q8).
7. **Signposting with tells.** Surface hints for hidden content: special trees above cave pockets, ice spikes plus a campfire at a lair, a graveyard above a crypt, map markers for treasure. In vanilla worldgen these are placed features with conditions (Q10).
8. **For the M1 cave realm, borrow the Tropical Jungle's vertical layering.** A transition layer, then sparse, then lush with villages, then a deep and dangerous layer with bioluminescence; zone-specific cave biomes (boomshroom, crystal, rat caves); and a cave-dwelling faction with hovels, slums and mines, like Hytale's goblins. Each depth band becomes a level sub-band (Q3, Q4).
9. **For the fire realm, a Hytale-style volcano.** Lava down the flanks and a central shaft down to a hidden jungle or cavern layer. The dragon's lair sits at the bottom of the shaft as a **grounded** arena (vision: 0 flying bosses), with the treasure pile visible on the map. Burnt or undead variants of the realm's animals fill the surface (Q3).
10. **A hub that changes with chapters.** Like the Forgotten Temple, the shared portal structure or its plaza gains buildings, NPCs and services with each chapter (Q6, Q10).
11. **Small, readable behaviour quirks for every realm faction and animal.** Fear of torches, bait, mimicry, sparring, pack flanking with retreat at low HP, alert beacons, wariness of axes. Each is cheap as a Minecraft AI goal and does a lot for the sense of life (Q7).

### Adapt
1. **Timers.** Hytale ejects you after 5–12 minutes. For Realmforge boss dungeons, use no hard timer (completion is the boss). Keep escalating "pressure" only for wave or extraction instances, with soft stages rather than ejection, so a key is never wasted (Q6).
2. **The bestiary.** Adopt the record-and-turn-in loop (creature, biome, time; rewards at thresholds), but **do not** gate waystones or fast travel behind it, because the owner wants waystones easy. Use it for convenience and flavour unlocks: recipes, cosmetics, extra storage (Q8).
3. **Food and potion buffs.** Keep Hytale's three tiers (raw → simple → feast) and its stat families (max HP +5/10/15%, max stamina-like resource, regeneration), but stretch durations from 45 s–6 min to 20–60 min at low magnitudes, in the Craft to Exile style the owner chose. Keep shared short cooldowns (15 s / 30 s) only for instant combat potions (Q9).
4. **Traversal.** Hytale's glider (dive and climb on Sprint, limited by stamina) and its hookshot or rocket boots are good early traversal tools. Make them obtainable in chapter 1, not behind rare boss hides, and pair them with the early teleport or flight the owner wants (Q9).
5. **Respawning content.** Respawn guardians and camps (after a day, or when cleared and revisited) so persistent shared realms never stay "cleared and empty". Scale it by tier so that farming isn't trivial (Q5, Q10).
6. **World events.** Rifts or breaches that open inside a realm at most every 30 min, shown on the map and vanishing after 5 min if ignored, give persistent realms something happening over time. Make them optional in config (Q6).
7. **Night spawns.** Hytale first spread night-only Void mobs everywhere and later removed them from the overworld. Keep realm "corruption" enemies in dungeons and events, not as constant night spam; the vision hates mobs that one-shot you or teleport onto you (Q7).
8. **Fragments as rotating side worlds.** Hytale uses short timed fragments to show new biomes. Realmforge can use instanced "pocket" dungeons to try out themes before committing to a whole realm (Q6).

### Avoid
1. Shipping placeholders: WIP signs, Challenge Gates that do nothing, villages without inhabitants, unfightable "boss" dragons. Reviewers read them as unfinished; ship only populated structures (Q3, Q10).
2. Spawning structures so densely that there is no room left to build (GamingTrend). Tune spacing per realm (Q10).
3. Filler loot (sticks, plant fiber, crude arrows, weighted "no loot" in high-roll chests). This contradicts vision line 39 (Q5).
4. Long single-source dependency chains for travel or comfort, such as teleporters that need wood from one biome plus a mid-game bench, or a glider that needs a rare boss hide. The owner hates walking and grind (Q9).
5. Progression you can only work out from a wiki (GamingTrend). Use the chapter or quest screen for direction (Q11).
6. Melee AI that only rushes. Even a few flanking, retreat or leash behaviours change how fights feel (Q7).
7. Copying any Hytale names or assets, such as Kweebec, Trork, Orbis, Fragment of Orbis, Ancient Gateway, Heart of Orbis or Memories as a branded system. Use Realmforge-original names and art (CLAUDE.md rule 8; EULA).
