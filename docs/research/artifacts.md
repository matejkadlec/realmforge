# Artifacts: the item catalogue

> Research of 2026-10-29 (RLM-312): every item of the Artifacts mod with its effect, a proposed minimum realm and
> level, and whether it suits an early unique quest reward; what fights our stats and systems; and how config or data
> switch an item off or move its source. Facts come from the NeoForge 26.1.2 jar (Artifacts 15.1.3), read in a scratch
> directory: its `neoforge.mods.toml`, `pack.mcmeta` and its overlays, every data file (loot tables, loot modifiers,
> worldgen features, the biome modifier, Curios, Accessories and Trinkets slot data, tags, recipes), the `en_us` lang
> file, and its classes with `javap -c -p -constants` (`ItemConfigs` and its inner classes for every default number,
> `GeneralConfig`, `ConfigManager`, `ModItems`, `ArtifactProperties`, `MimicEntity`, the Curios integration and the
> mixins named below). Apotheosis's `charm` category was checked in the pack's Apotheosis 9.1.0 jar. The jar is not
> in the repository, and nothing of Artifacts' code or assets goes into Realmforge: mechanics only.
> **Decided by Matěj on 2026-10-30 (RLM-512): option C,** neither of the catalogue's options: the curio rule ends,
> Artifacts joins the pack, and each item gets its own place
> ([ADR 0066](../decisions/0066-the-curio-rule-ends-artifacts-joins-and-immersive-armors-stays-out.md),
> [Matěj's decision and where each item goes](#matějs-decision-and-where-each-item-goes)). The catalogue below is the
> research the placement reads.

**In short.** Artifacts adds 49 items: 45 worn accessories, one held item (the Umbrella), two foods that are never
used up, and the Mimic's spawn egg. The accessories have no slots of their own: they need Curios (or Accessories or
Trinkets), and Artifacts adds five Curios slot types for them (head, necklace, belt, two hands, feet). They come from
vanilla chests across Overworld, from suspicious sand and gravel, from rare mob equipment, from underground
campsites, and from the Mimic, a chest monster in those campsites. No item depends on the Nether or the End: every
one also rolls in Overworld structures, campsites and mimics. The catch is Matěj's curio rule (RLM-7, 2026-10-12: "the
library is fine, I just don't want rings, necklaces, gloves... from other mods; the exception is the backpack from
Sophisticated"), which [ADR 0025](../decisions/0025-remove-the-nether-and-end.md) records. Only 12 of the 45 fit our
four slots by kind (a necklace, a bracelet, two rings), about a dozen fight our stats or the combat pipeline
directly, and another dozen hand out early what later realms or systems are meant to give (flight, water breathing,
fire and lava safety, mount speed). The proposal: take Artifacts in narrowly, as a source of six hand-picked utility
items given as unique quest rewards, worn in our own slots, with every world source and every Artifacts slot off. An
ADR then amends the curio rule for those items alone. The alternative is to leave Artifacts out and build those six
effects as our own curios later ([Proposal](#proposal-for-matěj)).

## Matěj's decision and where each item goes

**Matěj, 2026-10-30 (RLM-512): option C.** The curio rule (RLM-7, 2026-10-12) is cancelled: Artifacts was picked
precisely because it is a curios mod. Artifacts is in. Instead of a keep-list, every item gets a place: which realm,
dungeon or boss gives it, at what level, as loot, a quest reward or a boss drop. The more items find a place, the
better; an item that clashes with our systems (stats, the damage pipeline, a later realm's progression) gets a place
late enough, a tweak, or is off, each with its reason. His notes: the Night Vision Goggles are useless here (every
player has Night Vision by default), and the Onion Ring, as far as he knows, does nothing. The catalogue's questions
2–9 fall away; this section answers them item by item, and the pack task RLM-314 builds it
([ADR 0066](../decisions/0066-the-curio-rule-ends-artifacts-joins-and-immersive-armors-stays-out.md)).

### The rules this placement follows

- **Slots:** Artifacts' Curios compatibility is on, so its slot types join ours: head, belt, two hands and feet next to
  our two rings and our bracelet. The necklace is one slot (Curios' `necklace`, which both use): an Artifacts necklace
  and one of our stat necklaces compete for it, as WoW's trinket slots make a player choose. A hand item is worn in
  Artifacts' hands slots, never in our ring or bracelet.
- **Sources are ours:** a quest reward (**quest**), a boss's drop at 100 % (**boss**, as every boss drop designed so
  far, [gear design](../design/gear.md#second-sources)), or one item per player from a dungeon's treasure through Lootr
  (**treasure**). Artifacts' own sources are off (`artifactRarity` 10000, `archaeologyChance` 0,
  `entityEquipmentChance` 0, `campsiteCount` 0, `generateAsLoot` false for every item, the beef's `dropRate` 0), so no
  item is only a chance roll, and an item's realm decides who meets it.
- **One of each:** a reward gives an item once per player; a player who loses one gets it again from the quest giver
  or the boss's dungeon (the second-source rule).
- **No affixes:** each item's effect is the point of it, so `apotheosis:loot_category_overrides` maps every Artifacts
  item to `none` and our curio categories never take one.
- **Tweaks are config numbers** (`items.toml` has every item's numbers, [Facts](#facts)); an item switched off stays
  registered, hidden from JEI and the creative tab.
- **Names:** Artifacts' names stay unless they clash with the [style guide](../lore/style.md)'s tone; the joke items
  are the ones off or renamed below. A rename is a lang entry of ours with the same key, if our resources load after
  Artifacts' (to verify with RLM-314).
- **Levels** are the realm bands of [realms.md](../design/realms.md#at-a-glance): Overworld 1–10, Undervault 6–18,
  Sunwastes 16–24, Everwinter 20–30, Skyhearth from about 15, Deepholm 26–40, Quagmire 36–44, Ashfire 40–52, Unmade
  Isles 48–60. A later realm's dungeon or quest that is not designed yet is named by its realm alone.

### The placement

| Item | Slot | Where | Level | Source | Tweak or reason |
| --- | --- | --- | --- | --- | --- |
| Novelty Drinking Hat | head | Overworld, a tavern quest (Village Taverns) | 5 | quest | renamed "Brewer's Hat" in our style; potions drink faster, which helps in a fight |
| Plastic Drinking Hat | | | | off | the Novelty Drinking Hat's numbers twice, and "plastic" against the lore's tone |
| Villager Hat | head | Skyhearth, its traders' questline | 20 | quest | cheaper villager trades: late, once coins and merchants ([ADR 0040](../decisions/0040-coins-and-data-driven-merchant-trades.md)) are an economy the discount does not break; reputation +75 lowered to +30 |
| Superstitious Hat | head | Sunwastes, the pyramid dungeon's boss | 20 | boss | +1 Looting on Apothic Enchanting's levels: a treasure-hunter's reward in the treasure realm |
| Cowboy Hat | head | the mounts' questline, with the mount tiers (M4) | 22 | quest | mount speed +40 % lowered to +15 %, so the mount tiers stay the main step ([Chococraft decision](chococraft.md#decision-rlm-115)) |
| Angler's Hat | head | the fishing profession (Aquaculture 2, M5) | its band | quest | as the catalogue: with the profession |
| Snorkel | head | Overworld, a coastal quest | 9 | quest | 30 s of breath per dive helps Overworld's shipwrecks and ruins; Deepholm's own Breath of Deepholm (infinite) supersedes it there |
| Night Vision Goggles | | | | off | useless here: every player has Night Vision by default (Matěj) |
| Lucky Scarf | necklace | Undervault, a Cairnfolk ore or gem quest | 12 | quest | +1 Fortune for the mine of the whole game |
| Panic Necklace | necklace | Overworld, a quest after the first dungeon | 6 | quest | Speed I when hurt: a mild escape for new players |
| Thorn Pendant | necklace | Overworld, the first dungeon's treasure | 4 | treasure | flat 2–6 damage is strong at level 4 and fades by Undervault: an early item that ages out, as WoW's early trinkets do |
| Shock Pendant | necklace | Everwinter, the ice castle's boss | 28 | boss | lightning on an attacker is vanilla damage outside our schools and lights fires, so it comes where frost covers the ground; chance 25 % lowered to 15 %, and a GameTest checks that it never hits a party member |
| Flame Pendant | necklace | Ashfire, a volcano dungeon's boss | 44 | boss | sets attackers on fire and then gives Fire Resistance: Ashfire is where fire safety belongs |
| Charm of Sinking | necklace | Deepholm, its seabed quests | 28 | quest | walking on the seabed is Deepholm's movement; given with or after the Breath of Deepholm, never before |
| Scarf of Invisibility | | | | off | permanent Invisibility breaks mob targeting, boss fights and multiplayer at any level |
| Cross Necklace | | | | off | adds to `invulnerableTime`, the field our damage pipeline keeps for its hurt-cooldown rules ([ADR 0049](../decisions/0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md), [0051](../decisions/0051-player-melee-passes-the-hurt-cooldown-only-at-full-strength.md)) |
| Charm of Shrinking | | | | off | half size through any one-block gap breaks dungeon layouts, hitboxes and boss areas |
| Universal Attractor | belt | Overworld, an early side quest | 4 | quest | pure convenience |
| Antidote Vessel | belt | Quagmire, the slime dungeon's boss | 42 | boss | caps vanilla's debuffs (Poison, Slowness, Weakness) at 5 s in the poison realm; our own effects are not in its tag, so our bosses' debuffs keep their length |
| Obsidian Skull | belt | Ashfire, an entry quest | 41 | quest | Fire Resistance after fire damage: Ashfire's hazard, at Ashfire |
| Crystal Heart | belt | Deepholm, the underwater city's boss | 36 | boss | +10 max health: after RLM-565 keeps gear's max health through a login, and late, where +10 is a few VIT points instead of a doubled pool |
| Cloud in a Bottle | belt | Skyhearth, the heavens' questline | 30 | quest | the double jump comes after Undervault's and the twins' jumping puzzles; later realms' puzzles and arenas are built knowing it |
| Helium Flamingo | belt | Deepholm, after elytra | 32 | quest | swimming in the air for 8 s comes after elytra (the twins' final boss, [ADR 0037](../decisions/0037-elytra-from-chapter-3s-final-boss-amends-the-nether-and-end-removal.md)), when flight is no longer new |
| Warp Drive | belt | Unmade Isles, the void's questline | 52 | quest | free ender pearls skip rooms: late, with ender pearls refused in instances (a rule RLM-314 checks for, or a follow-up) |
| Chorus Totem | | | | off | a random teleport on a fatal hit leaves a boss arena (which resets the boss) or an instance, beside vanilla's totem |
| Onion Ring | hands | Undervault, a Cairnfolk mining quest | 8 | quest | it does work, against Matěj's note: Haste II for 6 s per food point eaten (a steak gives 48 s); faster mining in the mining realm |
| Digging Claws | hands | Overworld, a miner's quest | 3 | quest | +30 % mining speed and hands-free mining: small since the tool speed rule is gone (RLM-450) |
| Pickaxe Heater | hands | Undervault, a smelting quest | 10 | quest | ores drop smelted: skips a furnace step, nothing else |
| Golden Hook | hands | Overworld, an enchanter's quest | 6 | quest | +50 % vanilla XP from kills feeds enchanting only, never our character XP |
| Pocket Piston | hands | Overworld, the illager questline | 8 | quest | +0.75 knockback: fun early; bosses' knockback resistance keeps them in place |
| Withered Bracelet | hands | Undervault, the catacombs' treasure | 14 | treasure | Wither II on a melee hit is about 8 damage over 8 s: modest beside our damage over time at that level |
| Fire Gauntlet | hands | Everwinter, the nordic fortress's boss | 24 | boss | melee hits set the target on fire: a frost realm's answer, outside our fire school, so its burn stays vanilla's |
| Feral Claws | hands | Everwinter, a dungeon's treasure | 26 | treasure | attack speed +30 % lowered to +10 %, next to DEX's attack speed and Better Combat's timing |
| Power Glove | hands | Ashfire, the fire realm's final boss | 50 | boss | +4 attack damage is half an iron sword at level 10 and little at 50, where it no longer bends the Warrior's spell scaling |
| Vampiric Glove | | | | off | 20 % melee lifesteal on every hit beside our lifesteal stat (Apotheosis's Vampiric, remapped), outside its rules |
| Kitty Slippers | feet | Overworld, a village cat quest | 6 | quest | creepers keep away: kind to new players; no dungeon depends on creepers |
| Rooted Boots | feet | Overworld, a farmer's quest | 7 | quest | a little hunger on grass; the vision keeps hunger, and this barely dents it |
| Running Shoes | feet | Sunwastes, a desert travel quest | 17 | quest | sprint speed +40 % lowered to +15 %, close to DEX's 20 % movement cap, before mounts |
| Snowshoes | feet | Everwinter, its entry quest | 20 | quest | as the catalogue: Everwinter's snow |
| Bunny Hoppers | feet | Everwinter, a frozen lake quest | 25 | quest | +40 % jump strength after the twins' first puzzles; the hurt sounds stay, a joke kept |
| Flippers | feet | Deepholm, its first quest | 26 | quest | +70 % swim speed joins Deepholm's swim plan (Depth Strider above III); they stack, so the plan is tuned with them |
| Strider Shoes | feet | Ashfire, a lava lake quest | 43 | quest | standing on lava is Ashfire's, at Ashfire |
| Aqua-Dashers | feet | Unmade Isles, an entry quest | 50 | quest | sprinting on fluids, lava included, comes after Ashfire's lava lakes and Deepholm's water |
| Steadfast Spikes | feet | Unmade Isles, the Void Cathedral's treasure | 56 | treasure | full knockback immunity lowered to +0.5 knockback resistance, so our knockback spells and bosses' pushes still move a player |
| Umbrella | off hand | Overworld, a rainy-day quest | 5 | quest | added to `#c:tools/shield` by our data, so the two-handed wands' shield lock (RLM-461) covers it; its slow fall is a small glider long before elytra; Better Combat has no preset, so it stays vanilla in the hand |
| Eternal Steak | any | Unmade Isles, the finale's last quest | 58 | quest | food that is never used up, at the end of the game, when every food buff (M2) and cooking (M5) has had its time |
| Everlasting Beef | | | | off | the raw form of the Eternal Steak, and its source (cows) is off; the steak is the reward |
| Whoopee Cushion | | | | off | a fart joke against the lore's tone, and it sits in the spawn bonus chest |
| Mimic Spawn Egg | | | | off | creative only; the Mimic is off with the campsites (below) |

**Count:** 49 items: 39 placed (6 of them with a lowered number) and 10 off, the Mimic's spawn egg among them. Placed
by realm: Overworld 11, Undervault 4, Sunwastes 2, Everwinter 5, Skyhearth 2, Deepholm 4, Quagmire 1, Ashfire 4,
Unmade Isles 4, and 2 with the mounts' and the fishing questlines (the Cowboy Hat and the Angler's Hat).

### Campsites and the Mimic

Off with every other Artifacts source (`campsiteCount` 0). The Mimic is a fair idea for a dungeon's treasure room (a
chest that bites), but it drops Artifacts' own table; a later dungeon ticket can bring it back with a loot table of
ours (an override of `artifacts:artifact`) and its spawns in our rooms.

### For RLM-314

- `general.toml`: Curios compatibility on (Accessories' and Trinkets' off); every world source off as above.
- `items.toml`: `generateAsLoot` false everywhere; the tweaks of the table (Villager Hat, Cowboy Hat, Shock Pendant,
  Feral Claws, Running Shoes, Steadfast Spikes); `enabled` false for the off items where they have the switch,
  their bonuses at 0 otherwise.
- Data: the Umbrella in `#c:tools/shield`; every item to `none` in `apotheosis:loot_category_overrides`; the off items
  hidden from JEI and the creative tab; the rename of the Novelty Drinking Hat.
- Sources: each item's quest, boss drop or treasure as its content lands (most are M2 and later); the ids need the
  full-pack registry dump.
- Checks: a pack boot; the damage pipeline's GameTests with Artifacts loaded (the Cross Necklace's mixin runs worn or
  not); ender pearls in instances for the Warp Drive; a playtest that the slots and a few items work.

## Facts

| What | Value |
| --- | --- |
| Project | Artifacts by Wouter Pauwels (ochotonida), Modrinth `artifacts`; source and issue tracker `ochotonida/artifacts` on GitHub (the jar's `issueTrackerURL`) |
| NeoForge 26.1.2 build | 15.1.3, published on Modrinth on 2026-09-05; 1,233,255 bytes, SHA-1 `efef666f3cde19c755b044873eb30d961748d7a9`; manifest `Built-On-Minecraft: 26.1.2` |
| Licence | **MIT** (`LICENSE_Artifacts`, copyright 2019 Wouter Pauwels; `license="MIT"` in `neoforge.mods.toml`). The jar ships no separate licence for its textures and models |
| Requires | Minecraft `[26.1, 26.2)`, NeoForge `[26.1.2.100,)` (the pack has 26.1.2.116) |
| Optional | Curios, Accessories or Trinkets Updated: one of them is needed to wear any accessory (without one, the tooltip reads "Install Curios, Trinkets Updated, or Accessories to use this item"). Cloth Config, client side, for its config screen (the pack has it) |
| Bundled | Expandability 14.0.2 (jar-in-jar, `be.florens`), the library behind walking on fluids and swimming in the air |
| Sides | both: items, entity, worldgen and loot on the server, models, key mappings and overlays on the client |
| Mixins | 48 (35 common, 10 client, 1 server, 2 NeoForge), into `LivingEntity`, `Player`, `EnchantmentHelper`, the Fortune and Looting loot functions, `FishingHook`, `ThrownEnderPearl`, `NaturalSpawner`, `ServerGamePacketListenerImpl` and others |
| Registers | 49 items, the `artifacts:mimic` entity, 13 attributes of its own (`artifacts:invincibility_ticks`, `sprinting_speed`, `mount_speed`, `villager_reputation`, `swim_speed` and the rest), the Magnetism effect, sounds, six key mappings (all unbound), three advancements in vanilla's Adventure tab, three recipes (Eternal Steak from Everlasting Beef) |
| Config | NightConfig TOML files that `ConfigManager` writes to `config/artifacts/`: `general.toml`, `items.toml`, `client.toml`. Watched and reloaded on change; server values are sent to clients; a few need a restart. A pack override goes into `pack/config/artifacts/` |
| Per-item config | every item: `generateAsLoot` (default true). Twelve items whose ability is one switch: `enabled`. Every number below. Eleven items: `cooldown` in seconds. A durability block per item whose `canBeDamaged` is false by default, so **no item wears out** unless that is switched on |

## Where the items come from

### Chests

Artifacts adds its own pool to vanilla chest tables through NeoForge loot modifiers (`neoforge:add_table`, one file per
table in `data/artifacts/loot_modifiers/inject/chests/`). Each pool rolls once with a chance (the condition
`artifacts:artifact_rarity_adjusted_chance`, scaled by `artifactRarity`, default 1.0) and then picks one item from a
short list for that structure (`data/artifacts/loot_table/inject/chests/`):

| Chance | Chests |
| --- | --- |
| 100 % | spawn bonus chest (always the Whoopee Cushion) |
| 65 % | bastion treasure (Nether) |
| 45 % | big underwater ruin |
| 40 % | woodland mansion |
| 35 % | jungle temple |
| 30 % | abandoned mineshaft, igloo, stronghold corridor, End city treasure (End) |
| 25 % | buried treasure, pillager outpost, bastion hoglin stable (Nether) |
| 20 % | desert pyramid, Ancient City ice box, village tannery and temple |
| 15 % | Ancient City, ruined portal, shipwreck treasure, simple dungeon, village toolsmith, Nether fortress (Nether) |
| 5–10 % | village armorer and weaponsmith 10 %, snowy and taiga houses 8 %, desert, plains and savanna houses and the butcher 5 % |

- **The Nether and End chests** (bastions, the Nether fortress, End cities) never generate in Realmforge, and the
  stronghold corridor is gone with the strongholds (ADR 0044). Every item those chests name still has another source:
  the Chorus Totem the woodland mansion, the Crystal Heart the pillager outpost, the Helium Flamingo the Ancient City.
  The ruined portal's table serves Overworld's ruined portals too.
- **The shared table** `artifacts:artifact` holds every item except the Eternal Steak, each at weight 8, except the
  Umbrella and the Whoopee Cushion (5), the Helium Flamingo (4) and the Everlasting Beef (2). The mimic, the campsite
  chest, the woodland mansion and the bastions roll it.

### Suspicious sand and gravel

With a chance of 6.25 % (`archaeologyChance`), a loot modifier of its own (`artifacts:replace_with_table`, priority
500) replaces vanilla's brushing loot in the desert pyramid, the desert well, the cold and warm ocean ruins and the
rare trail ruins table with one artifact from a list per site.

### Mobs

- **Worn by mobs:** a naturally spawned zombie, husk, drowned, skeleton, stray, bogged, parched, wither skeleton,
  piglin, piglin brute or zombified piglin wears one artifact from its own list with a chance of 0.15 %
  (`entityEquipmentChance` 0.0015; `NaturalSpawnerMixin`, `data/artifacts/loot_table/entity_equipment/`). It sits in the
  mob's Curios slot, so it needs Artifacts' Curios slots on those mobs, and it drops only when a player killed the mob
  (`DamageSourceHelper#shouldDestroyWornItemOnDeath`).
- **Cows and mooshrooms** killed by a player drop the Everlasting Beef with a chance of 0.2 % (`dropRate` 0.002),
  cooked when the animal burned.

### Campsites and the mimic

- **Campsites** are a worldgen feature (`artifacts:campsite`), added by the biome modifier `artifacts:add_campsite`
  to every biome in `#minecraft:is_overworld`, at the step `underground_structures`, from y −60 to 40: up to 40
  attempts per chunk (`campsiteCount`), each passing a 1-in-10 rarity filter and then needing a flat cave floor under a
  ceiling at most 6 blocks high. How often one actually generates was not measured. A campsite is a bed, a crafting
  station, a furnace, lights, barrels and a chest (12.5 % of them trapped). Undervault's biome `realmforge:undervault`
  is not in that tag, so campsites appear in Overworld caves only.
- **The campsite chest** (`artifacts:chests/campsite_chest`) gives 1–3 iron, gold, chainmail or diamond tools or
  armour, junk, ores (up to 4 diamonds), with 30 % an enchanted book (levels 15–30), a golden apple or an enchanted
  golden apple, and with 15 % an artifact.
- **The Mimic** takes the chest's place in 30 % of campsites (`mimicChance` 0.3). It is a hostile chest (`MimicEntity`,
  a `Mob` with vanilla's `Enemy` marker) with 60 health, 5 attack damage on contact at most once per 20 ticks,
  knockback resistance 0.8 and follow range 16; it hops, never despawns and never spawns naturally (mob category
  `MISC`). It always drops one roll of the shared table, so a mimic is a guaranteed artifact. As an `Enemy` it gets a
  mob level from our rules like any hostile mob ([architecture 14.1](../architecture.md#141-mob-levels)).

### Switching sources off or moving them

| Lever | Where | Effect |
| --- | --- | --- |
| `artifactRarity` 10000 | `general.toml` | no artifacts in chests (the config's own advice) |
| `archaeologyChance` 0 | `general.toml` | none from brushing |
| `entityEquipmentChance` 0 | `general.toml` | no mob wears one (also stops a log warning when a mob has no free slot) |
| `campsiteCount` 0, or `mimicChance` 0 | `general.toml` | no campsites, or campsites without mimics |
| `dropRate` 0 | `items.toml`, Everlasting Beef | no beef from cows |
| `generateAsLoot` false | `items.toml`, per item | the item leaves every Artifacts table, the mimic's too |
| `enabled` false, or a bonus of 0 | `items.toml`, per item | the item stays but does nothing |
| override a table or a loot modifier | data pack, same path (`data/artifacts/loot_table/...`, `data/artifacts/loot_modifiers/...`) | a structure's list changes, or its injection is gone |
| override `artifacts:artifact` | data pack | what mimics, campsite chests and the mansion give |
| override `add_campsite` | data pack, `data/artifacts/neoforge/biome_modifier/add_campsite.json` | campsites in other biomes, Undervault's included, or none (`neoforge:none`) |
| our own loot or quest reward | `content/` | any item from our tables or as an FTB Quests reward; the id check needs the full-pack registry dump (rule 3, [ADR 0005](../decisions/0005-content-pipeline-yaml-single-source-of-truth.md)) |

The items themselves stay registered whatever the config says: a disabled item can still be given by a command and
shows in JEI and the creative tab unless they are hidden.

## Catalogue

All numbers are the jar's defaults (`ItemConfigs`, `ModItems`, `ArtifactProperties`). "+30 %" is a modifier of
`ADD_MULTIPLIED_BASE`, "+4" one of `ADD_VALUE`; a cooldown is in seconds. **Our slot** is where the item would go if
kept: our curio slots are a necklace, a bracelet and two rings (Matěj, 2026-10-17); "none" means the item's kind has
no slot of ours. **Min.** is the proposed minimum realm and level, from the realm bands in
[realms.md](../design/realms.md#at-a-glance). **Early** is a candidate for an early unique quest reward (chapters 1
and 2). **Pick**: *keep* (the proposed keep-list), *later* (worth deciding when its realm or system is built), *off*.

### Head (Artifacts' `head` slot, face items included; our slot: none)

| Item | Effect | Fights | Min. | Early | Pick |
| --- | --- | --- | --- | --- | --- |
| Plastic Drinking Hat `plastic_drinking_hat` | drinking +150 %, eating +50 % (`artifacts:drinking_speed`, `eating_speed`) | nothing; a joke item ("plastic") against the lore's tone | Overworld 1 | no | off |
| Novelty Drinking Hat `novelty_drinking_hat` | the same numbers, plus a quoted lore line | as above | Overworld 1 | no | off |
| Villager Hat `villager_hat` | +75 villager reputation (`artifacts:villager_reputation`): cheaper villager trades | our coin trades and the Ancient merchants ([ADR 0040](../decisions/0040-coins-and-data-driven-merchant-trades.md)) | Skyhearth 15 | no | off |
| Superstitious Hat `superstitious_hat` | +1 Looting on kills | stacks on Apothic Enchanting's Looting; more drops from our tables that read Looting | Sunwastes 16 | no | off |
| Cowboy Hat `cowboy_hat` | ridden mounts +40 % speed (`artifacts:mount_speed`) | our mount tiers' ground speeds ([Chococraft research, decision](chococraft.md#decision-rlm-115)) | M4 mounts | no | off |
| Angler's Hat `anglers_hat` | +1 Luck of the Sea, +1 Lure | the fishing profession (Aquaculture 2, M5) | M5, a fishing reward | no | later |
| Snorkel `snorkel` (face) | Water Breathing while losing air, 30 s per dive (`isInfinite` false) | Deepholm's own Breath of Deepholm (infinite breath, its entry reward) | Deepholm 26 | no | off |
| Night Vision Goggles `night_vision_goggles` (face) | Night Vision at 15 % strength (`strength` 0.15), toggled by a key | a little against Undervault's dark and its light puzzles; Deepholm uses Night Vision to see | Undervault 6 | **yes** | keep |

### Necklace (Artifacts' `necklace`; our slot: necklace)

| Item | Effect | Fights | Min. | Early | Pick |
| --- | --- | --- | --- | --- | --- |
| Lucky Scarf `lucky_scarf` | +1 Fortune on mined blocks | stacks on Fortune; more of Undervault's ores and gems | Undervault 8 | **yes** | keep |
| Scarf of Invisibility `scarf_of_invisibility` | Invisibility while worn, toggled by a key | mob targeting, boss fights, multiplayer | none | no | off |
| Cross Necklace `cross_necklace` | +20 ticks of invulnerability after a hit (`artifacts:invincibility_ticks`), cooldown 2 | our hurt-cooldown handling: its mixin writes `invulnerableTime` in `LivingEntity#hurtServer`, the field `DamagePipeline` sets ([ADR 0049](../decisions/0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md), [0051](../decisions/0051-player-melee-passes-the-hurt-cooldown-only-at-full-strength.md)) | none | no | off |
| Panic Necklace `panic_necklace` | Speed I for 8 s when hurt, cooldown 2 | vanilla Speed next to DEX's capped movement speed; mild | Overworld 5 | **yes** | keep |
| Shock Pendant `shock_pendant` | 25 % chance to strike an attacker with lightning, cooldown 2; immune to lightning | lightning is vanilla damage outside our schools, starts fires and hits whoever stands near | none | no | off |
| Flame Pendant `flame_pendant` | 40 % chance to set an attacker on fire for 10 s, cooldown 2, then Fire Resistance | Fire Resistance on demand, ahead of Ashfire; vanilla fire, not our fire school | Ashfire 40 | no | off |
| Thorn Pendant `thorn_pendant` | 50 % chance to deal 2–6 damage to an attacker, cooldown 2 | flat damage outside our schools and crits: strong at level 3, nothing at level 30 | Overworld 3 | no | off |
| Charm of Sinking `charm_of_sinking` | no water collision (walk on the seabed), +1.5 oxygen (`minecraft:oxygen_bonus`), toggled by a key | Deepholm's movement and its Breath of Deepholm | Deepholm 26 | no | later |
| Charm of Shrinking `charm_of_shrinking` | scale −50 % (`minecraft:scale`, multiplied total), toggled by a key | dungeon layouts (one-block gaps), hitboxes, boss areas, reach | none | no | off |

### Belt (Artifacts' `belt`; our slot: none)

| Item | Effect | Fights | Min. | Early | Pick |
| --- | --- | --- | --- | --- | --- |
| Universal Attractor `universal_attractor` | Magnetism V: pulls nearby items to the wearer, toggled by a key | nothing; pure convenience | Overworld 1 | **yes** | keep |
| Cloud in a Bottle `cloud_in_a_bottle` | a double jump (+0.25 up and forward when sprinting), +3 safe fall distance, its fall damage factor 0 | jumping puzzles, arenas, vertical exploration | none | no | off |
| Obsidian Skull `obsidian_skull` | Fire Resistance for 30 s after fire damage, cooldown 60 | Ashfire's fire and lava hazards | Ashfire 40 | no | later |
| Antidote Vessel `antidote_vessel` | caps nine vanilla negative effects (Blindness, Nausea, Mining Fatigue, Hunger, Levitation, Slowness, Poison, Weakness, Wither) at 5 s | boss debuffs and the planned tenacity; our own `realmforge:` effects are not in its tag | Quagmire 36 | no | off |
| Crystal Heart `crystal_heart` | +10 max health (`minecraft:max_health`, add) | our rule that gear never adds max health directly (the login clamp, [architecture 8.2](../architecture.md#82-where-modifiers-come-from)); VIT | none | no | off |
| Helium Flamingo `helium_flamingo` | "swims" in the air for 8 s, recharging in 15 s, cooldown 3, on a key | flight: mounts fly from chapter 2 (M4), elytra come from chapter 3's final boss ([ADR 0037](../decisions/0037-elytra-from-chapter-3s-final-boss-amends-the-nether-and-end-removal.md)) | none | no | off |
| Chorus Totem `chorus_totem` | on a fatal hit: used up, clears every effect, teleports at random (diameter 32), heals 9 | boss arenas (a teleport out resets the boss), dungeon instances, a second totem beside vanilla's | none | no | off |
| Warp Drive `warp_drive` | ender pearls are not used up and cost 2 hunger instead, deal no damage, cooldown 0 | travel design next to waystones; skipping rooms in dungeons | none | no | off |

### Hands (Artifacts' two `hands` slots; our slot: ring or bracelet for three of them)

| Item | Effect | Fights | Min. | Early | Pick |
| --- | --- | --- | --- | --- | --- |
| Onion Ring `onion_ring` (our ring) | Haste II for 6 s per food point eaten | nothing; mining speed only | Undervault 6 | **yes** | keep |
| Withered Bracelet `withered_bracelet` (our bracelet) | 30 % chance on a melee hit to inflict Wither II for 8 s, cooldown 2 | vanilla Wither damage beside our damage over time; a combat item in the utility slot | Undervault 10 | no | off |
| Pickaxe Heater `pickaxe_heater` (our bracelet) | mined ores drop smelted | the furnace step of mining; minor | Undervault 8 | **yes** | keep |
| Digging Claws `digging_claws` | mining speed +30 % (`minecraft:block_break_speed`), harvests pickaxe, shovel, axe and hoe blocks as a stone tool would | little since the tool speed rule is gone (RLM-450); hands-free mining | Overworld 3 | yes | off |
| Feral Claws `feral_claws` | attack speed +30 % (`minecraft:attack_speed`) | DEX's attack speed and Better Combat's timing | none | no | off |
| Power Glove `power_glove` | +4 attack damage (`minecraft:attack_damage`, add) | STR and the Warrior's spell scaling: an iron sword deals 6 | none | no | off |
| Fire Gauntlet `fire_gauntlet` | melee hits set the target on fire for 8 s (`artifacts:attack_burning_duration`) | vanilla fire beside our fire school and damage over time | none | no | off |
| Pocket Piston `pocket_piston` | +0.75 attack knockback (`minecraft:attack_knockback`) | melee positioning and Better Combat | none | no | off |
| Vampiric Glove `vampiric_glove` | heals 20 % of melee damage dealt, at most 6 per hit, on every hit | our lifesteal stat | none | no | off |
| Golden Hook `golden_hook` | +50 % vanilla experience from kills (`artifacts:entity_experience`); piglins love it | vanilla XP only (enchanting), not our character XP | Overworld 1 | no | off |

### Feet (Artifacts' `feet`; our slot: none)

| Item | Effect | Fights | Min. | Early | Pick |
| --- | --- | --- | --- | --- | --- |
| Aqua-Dashers `aqua_dashers` | sprints on fluids (its tooltip: "walk on fluids while sprinting") | Deepholm, lava | none | no | off |
| Bunny Hoppers `bunny_hoppers` | jump strength +40 % (`minecraft:jump_strength`), +10 safe fall distance, rabbit hurt sounds | jumping puzzles and arenas | none | no | off |
| Kitty Slippers `kitty_slippers` | creepers avoid the wearer, phantoms hiss and leave, cat hurt sounds | creeper fights in dungeons | Overworld 1 | maybe | off |
| Running Shoes `running_shoes` | sprint speed +40 % (`artifacts:sprinting_speed`), +0.5 step height while sprinting | DEX's movement speed, capped at 20 % on purpose; ground mounts | none | no | off |
| Snowshoes `snowshoes` | walks on powder snow, +30 % speed on snow | Everwinter's terrain | Everwinter 20 | no | later |
| Steadfast Spikes `steadfast_spikes` | +1 knockback resistance (full immunity), ice no longer slippery | boss knockback and the bracelet's knockback resistance affix | none | no | off |
| Flippers `flippers` | swim speed +70 % (`artifacts:swim_speed`) | Deepholm's swim speed plan (Depth Strider above III) | Deepholm 26 | no | off |
| Rooted Boots `rooted_boots` | restores 1 hunger every 10 s on grass; bone meal around the wearer after eating | hunger, which the vision keeps | Overworld 1 | no | off |
| Strider Shoes `strider_shoes` | stands on lava while sneaking; immune to hot floors (magma) | Ashfire's lava | Ashfire 40 | no | later |

### Any slot, held, food

| Item | Effect | Fights | Min. | Early | Pick |
| --- | --- | --- | --- | --- | --- |
| Whoopee Cushion `whoopee_cushion` (any slot) | 12 % chance of a fart sound on sneaking or double jumping; always in the spawn bonus chest | the lore's tone | none | no | off |
| Umbrella `umbrella` (off hand) | blocks attacks like a shield, slows a fall while held, and stabs as a spear (26.1's spear components; in `#minecraft:spears`) | our two-handed wands lock items in `#c:tools/shield`, and the Umbrella is not in it, so a caster could block with it; no Better Combat preset; a glider before elytra | none | no | off |
| Everlasting Beef `everlasting_beef` | raw beef that is never used up, 15 s cooldown | food and the coming foods with buffs (M2) and cooking (M5); the vision keeps hunger | none | no | off |
| Eternal Steak `eternal_steak` | the same as cooked beef (smelted, smoked or campfire-cooked from the beef) | as above | none | no | off |
| Mimic Spawn Egg `mimic_spawn_egg` | spawns a mimic (creative) | none | n/a | no | n/a |

Count: 49 items, of which 6 are proposed to keep, 5 for later, and 37 off (the spawn egg aside).

## How it meets our systems

### Curio slots

- **Artifacts' Curios data** adds five slot types to players (`data/artifacts/curios/entities/slots.json`): `head`,
  `necklace`, `belt`, `hands` (size 2) and `feet`, each with a cosmetic slot, and the same slots to the eleven mobs that
  can wear its items. It fills them through Curios' item tags (`curios:head`, `necklace`, `belt`, `hands`, `feet`) from
  its own slot tags (`artifacts:slot/*`). Our design has four slots (Matěj, 2026-10-17), plus the elytra slot (ADR 0025)
  and the Sophisticated backpack. Taken as it ships, Artifacts would add four slot types we never planned (head, belt,
  two hands, feet), and its `necklace.json` would give our necklace slot a cosmetic slot too.
- **`enableCuriosCompat` false** (`general.toml`, `general.slots`) applies the overlay `overlay_disable_curios`, which
  empties Artifacts' Curios slot files, its entity slot list and its `curios` item tags: it then adds no slot and no
  item of it fits any slot. `removeSlotRestrictions` does the opposite (every artifact into any slot, through
  `curios:curio`). `addFaceSlot` is for Trinkets only.
- **Our own slots can take chosen items:** Artifacts' Java registers with Curios whenever Curios is loaded
  (`ArtifactsNeoForge` calls `CuriosCompat.setup`), not by that config, and its `CuriosSlotProvider` reads every Curios
  slot of an entity. So with Artifacts' slots off, a data file of ours that adds a kept item to `curios:necklace`,
  `curios:ring` or `curios:bracelet` should make it wearable and working in our slot. To verify in play, together with
  where its model renders (it renders by item, so goggles in the bracelet slot may still show on the head).
- **Apotheosis adds a slot too:** with Curios installed, Apotheosis gives every player-like entity a `charm` slot
  (`data/apotheosis/curios/entities/player_charm.json` in its 9.1.0 jar), for its potion charms. That is outside this
  ticket, but our slot list will meet it when Curios joins the pack; the Breath of Deepholm is already planned as a
  "charm-slot curio" ([realms.md](../design/realms.md#the-water-realm)).
- Curios is not in the pack yet: our slots come with the gear work, which moved from M2 to M1 on 2026-10-29.

### Our stats

[Architecture 8](../architecture.md#8-stats-and-attributes) keeps one attribute model: vanilla's attributes plus our
primary and secondary stats. What the items change:

- **Duplicates our stats:** attack damage (Power Glove; STR), attack speed (Feral Claws; DEX), max health (Crystal
  Heart; VIT, and against the max-health rule), lifesteal (Vampiric Glove, as its own heal rather than our stat),
  knockback resistance (Steadfast Spikes; the bracelet's affix), movement speed (Running Shoes' own sprint speed
  attribute, outside DEX's 20 % cap).
- **Vanilla attributes we keep but do not tune:** jump strength, safe fall distance, scale, oxygen bonus, block break
  speed, attack knockback.
- **Artifacts' own attributes** (13, `ModAttributes`) sit on every living entity beside ours and Apothic Attributes',
  and the character sheet does not show them.
- **Enchantment levels:** Fortune, Looting, Lure and Luck of the Sea bonuses are applied through mixins into vanilla's
  loot functions (`ApplyBonusCountMixin`, `EnchantedCountIncreaseFunctionMixin`) and `EnchantmentHelper`, on top of
  Apothic Enchanting's higher levels.

### Apotheosis

- Artifacts' accessories have no `EQUIPPABLE` component, so none of Apotheosis's armor categories takes them, and
  Apotheosis's own curio category, `apotheosis:charm`, takes only items in `#curios:charm` (`CuriosCompat` in its jar),
  where Artifacts puts nothing.
- **Our planned curio categories** (`realmforge:ring`, `necklace`, `bracelet`, from `#curios:ring` and so on, RLM-273)
  would take any Artifacts item in those tags. With Artifacts' Curios compat on, its nine necklace items would roll our
  necklace affixes and take sockets when reforged. A kept item can be kept out with `apotheosis:loot_category_overrides`
  (item to `none`), as Apotheosis does for the shulker shell.
- Apotheosis injects its own affix items and gems into the same vanilla chests; Artifacts' pools come on top.

### Combat and the damage pipeline

- **Hurt cooldown:** the Cross Necklace's mixin (`mixin/attribute/invincibilityticks/LivingEntityMixin`) adds its
  attribute to `invulnerableTime` in `LivingEntity#hurtServer` and `handleDamageEvent`, the field our
  `DamagePipeline` keeps for its window rules. The mixin runs on every hit, worn or not; with the attribute at 0 it
  should add nothing, which a pack boot with Artifacts would have to confirm against the pipeline's GameTests.
- **Damage outside our schools:** the Thorn, Flame and Shock Pendants, the Fire Gauntlet and the Withered Bracelet
  deal vanilla thorns, fire, lightning and Wither damage, without crits, spell power or our resistances.
- **Melee** for Artifacts is its tag `artifacts:is_melee` (mob attacks, player attacks, spears, the mace): our spell
  schools are not in it, so spells never trigger the Vampiric Glove or the gauntlets.
- **The Umbrella** is a shield outside `#c:tools/shield`, so the shield lock of our two-handed wands (RLM-461) misses it.

### Mobility and progression

The items that move a player in new ways are the ones that undercut later content most: the Helium Flamingo (air
movement before mounts and elytra), the Cloud in a Bottle and Bunny Hoppers (jumps that skip puzzles), the Charm of
Shrinking (half-size through any gap), the Warp Drive and the Chorus Totem (teleports inside arenas and instances; no
rule on ender pearls in instances was found), and the water, ice, fire and lava items that answer the hazards of
Deepholm, Everwinter and Ashfire long before a player gets there. Nothing in Artifacts is a flying boss, so the
vision's hard no is not touched; its "no RNG grind" is, since every Artifacts source is a chance roll.

### Other

- **Key mappings:** six, all unbound by default (toggles for the goggles, the scarf of invisibility, the attractor and
  the two charms, and the flamingo's activation), so nothing clashes with our spell bar keys
  ([ADR 0046](../decisions/0046-spell-bar-default-keys-amend-the-pack-defaults.md)).
- **Advancements:** three of its own (find an artifact, eat one, kill a mimic) in vanilla's Adventure tab.
- **Names:** the items keep Artifacts' names (Whoopee Cushion, Cowboy Hat, Plastic Drinking Hat), several of them
  jokes against the [lore style guide](../lore/style.md)'s tone. A lang entry of ours with the same key could rename
  an item, if our resources load after Artifacts' (to verify). As with Chococraft, the decision settles whether quests
  may name them.
- **Mob levels:** the Mimic is an `Enemy`, so it is leveled and scaled; its drop is not gated by level.
- **Licence:** MIT allows adapting its code into ours with its copyright notice kept, as for Chococraft's
  ([Chococraft research](chococraft.md#what-our-own-mounts-would-reuse)); its textures and models are not reused.

## Proposal for Matěj

### Options

| | A: leave Artifacts out | B: a short keep-list in our slots | C: Artifacts as it ships |
| --- | --- | --- | --- |
| What ships | nothing; the six utility effects below become effects of our own curios later | Artifacts with its slots and world sources off; six items, each a unique quest reward, worn in our necklace, ring and bracelet slots | 45 accessories in five new slot types, from chests, brushing, mobs, campsites and mimics |
| Curio rule | unchanged | an ADR amends it for the kept items alone | an ADR drops it for Artifacts |
| Our slots | four, as planned | four, as planned; a utility item costs a stat slot, like WoW Classic's utility trinkets | nine more slot positions (head, belt, two hands, feet), mostly utility |
| Risk | none | 48 mixins and 13 attributes in the pack for six items; the hurt-cooldown mixin to check; the id check needs the registry dump | all of the above, plus every conflict in the catalogue |
| Cost | our own versions later: each is a status effect or one ability, plus art | config, a data pack (slot tags, Apotheosis overrides, hidden items), quest rewards, a pack-boot check | the pack entry and a pack boot |

**Recommendation: B.** Matěj marked Artifacts Yes on 2026-10-18, and the ticket looks for early rewards. B delivers
those rewards without breaking the reason behind his rule: no new slots, no other mod's curios dropping from loot, and
only items he picked, each with a fixed place in the story. A stays the clean choice if he prefers the rule as it is;
the keep-list then becomes a shortlist of effects for our own unique curios
([gear design](../design/gear.md#loot-only-gear), RLM-198). C is not recommended.

### The keep-list (for B)

| Item | Our slot | Source (proposal) | Level |
| --- | --- | --- | --- |
| Universal Attractor | bracelet | an Overworld chapter 1 side quest | about 4 |
| Panic Necklace | necklace | an Overworld chapter 1 quest after the first dungeon | about 6 |
| Night Vision Goggles | bracelet | "Enter the Undervault", or the first Undervault quest | 6 |
| Onion Ring | ring | an Undervault mining quest (the Cairnfolk) | about 8 |
| Pickaxe Heater | bracelet | an Undervault mining or smelting quest | about 10 |
| Lucky Scarf | necklace | an Undervault quest for ores or gems, late in its band | about 12 |

For later, each decided when its realm or system is built: the Angler's Hat (the fishing profession, M5), the
Snowshoes (Everwinter), the Charm of Sinking (Deepholm, unless the Breath of Deepholm covers it), the Obsidian Skull
and the Strider Shoes (Ashfire). Each would need a slot of ours or a reason to take a stat slot.

### How B is set up

- **`general.toml`:** `enableCuriosCompat`, `enableAccessoriesCompat` and `enableTrinketsCompat` false;
  `artifactRarity` 10000, `archaeologyChance` 0, `entityEquipmentChance` 0, `campsiteCount` 0 (or `mimicChance` 0,
  question 5).
- **`items.toml`:** `generateAsLoot` false for every item and `dropRate` 0 for the beef; the defaults of the six kept
  items stay; durability stays off.
- **Our data:** the six items in `curios:necklace`, `curios:ring` and `curios:bracelet`; the six in
  `apotheosis:loot_category_overrides` as `none` (question 6); the other 43 hidden from JEI and the creative tab.
- **Content:** each kept item as one unique quest reward, which needs the full-pack registry dump for the id check
  (rule 3).
- **Checks:** a pack boot; the damage pipeline's GameTests with Artifacts loaded (the Cross Necklace's mixin); a
  playtest that each kept item works and renders in our slot.

### What the ADR would say (for B, not taken)

Matěj chose option C instead; [ADR 0066](../decisions/0066-the-curio-rule-ends-artifacts-joins-and-immersive-armors-stays-out.md)
records it. The draft for B stays as written:

"Amends [0025](../decisions/0025-remove-the-nether-and-end.md)'s curio rule (RLM-7) and lifts
[0044](../decisions/0044-third-party-mods-by-milestone.md)'s hold on Artifacts. Artifacts joins the pack. The items on
Matěj's keep-list (RLM-312) may be worn in our curio slots; every other item of Artifacts, and every other mod's curio
item except the Sophisticated backpack, stays out. Artifacts adds no slot of its own (its Curios, Accessories and
Trinkets compatibility off); our data tags each kept item into one of our slots. Every world source of Artifacts is
off (chests, brushing, mob equipment, campsites and mimics, the beef); a kept item comes only from our own sources,
first as a unique quest reward. The items not kept stay registered but have no source and are hidden. A new kept item
is a decision of Matěj's, recorded in this ADR's list." Under A, the curio rule stands, and a short new ADR amends
0044 and [0058](../decisions/0058-player-systems-before-world-content.md) to move Artifacts from held to Rejected:
CLAUDE.md rule 10 never rewrites an accepted ADR in place (the precedent:
[ADR 0049](../decisions/0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md) amended 0044 for Iron's RPG
Tweaks). Its milestone needs no ADR in either case: ADR 0058 already moved Artifacts into M1 with the gear (RLM-552).

## Questions for Matěj

Answered on 2026-10-30 (RLM-512): option C, [above](#matějs-decision-and-where-each-item-goes); questions 2–9 fall away
with it. The questions as they were asked:

1. **Artifacts in or out?** Proposed: in, narrowly (option B), with an ADR that amends the curio rule for the kept
   items alone. Otherwise out (option A), and the six effects become our own curios later.
2. **The keep-list:** proposed the six of [the keep-list](#the-keep-list-for-b) (Universal Attractor, Panic Necklace,
   Night Vision Goggles, Onion Ring, Pickaxe Heater, Lucky Scarf), at the levels shown. Which would you add or drop?
3. **Slots:** proposed no new slot; the kept items go into our necklace, ring and bracelet, so a utility item costs a
   stat slot. Or should a head slot be added for the goggles and later hats?
4. **Sources:** proposed only our unique quest rewards, one of each item, with every Artifacts world source off. Or
   should some stay, such as village or mineshaft chests?
5. **Campsites and the Mimic:** proposed off with the rest. The alternative keeps them in Overworld caves (or adds them
   to Undervault through the biome modifier), with the Mimic dropping one of our own tables instead of an artifact.
6. **Affixes on kept items:** proposed none (they are chosen for their effect, and an override keeps them out of our
   curio categories). Or should they roll our curio affixes and sockets like our own curios?
7. **Names:** proposed keeping Artifacts' names for the six (none of them is a joke name). Or rename them in our style
   through lang entries?
8. **The later items** (Angler's Hat, Snowshoes, Charm of Sinking, Obsidian Skull, Strider Shoes): proposed to decide
   each when its realm or the fishing profession is built.
9. **Milestone:** gear, Artifacts included, moved from M2 to M1 on 2026-10-29 (RLM-7; the roadmap follows with
   RLM-504). Proposed: Artifacts joins with M1's gear and Curios, and the six rewards come with the chapter 1 and 2
   quests, which need the full-pack registry dump for their ids; any reward whose quest is M2's waits for it.
