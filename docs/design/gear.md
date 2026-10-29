# Gear: rarity, affixes and loot-only gear

> How gear grows stronger in Realmforge (RLM-198): what a rarity changes and where a rarity comes from, which affixes
> roll on each gear archetype (melee, caster, ranged and rogue, tools, curios), how much stronger loot-only gear is
> than crafted gear, and how the netherite upgrades and the gems fit in. Written on 2026-10-29 from Matěj's decisions
> on RLM-198 (2026-10-15 to 2026-10-17), RLM-201, RLM-205 and RLM-232, and read in Apotheosis 9.1.0's 26.1.2 jar
> (`Apotheosis-26.1.2-9.1.0.jar`, the pack's build: its data files and its `LootCategory` and loot function classes
> with `javap`). Rarity, affixes, gems and reforging come from Apotheosis
> ([ADR 0038](../decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)); its world tiers,
> loot and reforging loop are in [apotheosis.md](apotheosis.md). Every number here is a proposal for the data
> tickets until Matěj approves it ([Open questions](#open-questions)), and a playtest tunes it after that.

## At a glance

- **Rarity is Apotheosis's** (Common to Legendary, mythic shown as Legendary): it sets how many affixes and sockets
  an item has, never its base stats. No rarity component of ours, no item level.
- **Two ways to a rarity:** Apotheosis's own drops roll it at the player's world tier; our loot (dungeon chests,
  bosses, quest rewards) names a rarity band per realm, Matěj's table of 2026-10-15, through Apotheosis's
  `apotheosis:reforge_item` loot function.
- **Every archetype rolls its own affixes:** melee armor and weapons keep Apotheosis's (RLM-232's set); caster robes
  and wands get loot categories and affixes of our own; curios get stat affixes only; ranged and rogue armor gets its
  categories with the first ranged set (M3).
- **Loot-only gear is one step above crafted gear of its source,** at most: the best set a player can craft at that
  source's world tier, plus a share by the kind of source, plus a fixed effect of its own. Armor and toughness climb
  slowest; better gear gets effects instead (Matěj, 2026-10-16).
- **Ruby, Sapphire and Topaz are netherite upgrades, not sockets.** Apotheosis's gems stay the socket gems, and
  some of them get bonuses for our categories.

## Rarity

### What a rarity changes

Apotheosis's rarity files (`data/apotheosis/apotheosis/rarities/`) give each rarity its rules, kept as they ship
(RLM-204):

| Rarity | Affixes | Sockets | Durability bonus |
| --- | --- | --- | --- |
| Common | 1 stat, 25 % chance of a 2nd | none | none |
| Uncommon | 2 stats, 50 % chance of an effect | 0–1 | none |
| Rare | 2 stats, 1 effect, 35 % chance of a 2nd | 0–2 | +10–25 % |
| Epic | 3 stats, 1 effect, 25 % chance of a 2nd, 1 ability | 1–3 | +25–55 % |
| Legendary | 4 stats, 2 effects, 1 ability | 1–3 (5 % chance of 4) | +45–75 % (1 % chance of unbreakable) |

- **The kind gates the rarity:** stats roll from Common, effects from Uncommon, abilities on Epic and Legendary only
  (the review page of RLM-198, 2026-10-16). Each affix's value range widens and rises with the rarity: a rarity is
  both more affixes and stronger ones, which answers MnS's "affix rarity is the roll band" (LE-4) without bands of
  our own.
- **Base stats never change with the rarity.** A Legendary iron sword has an iron sword's 6 attack damage; its power
  above that is affixes and gems. So the crafted sets stay the base types that rarity builds on (task 4), and a
  material tier and a rarity are two separate ladders, as in WoW, where an item's quality and its level are apart.
- **Category overrides:** a `rarity_override` file can change the rules per loot category (Apotheosis ships one for
  shears). Curios use one for their sockets (RLM-273).

### Where an item's rarity comes from

| Source | Rarity | Mechanism |
| --- | --- | --- |
| Apotheosis's own drops: 12 % of natural hostile spawns wear an affix item, the affix items injected into chests | rolled at the looting player's world tier ([weights](apotheosis.md#what-the-tier-changes)) | Apotheosis, unchanged |
| Our loot: dungeon containers, bosses, mobs' extra drops, quest rewards | a band per realm (below), weighted inside the band by the player's tier | `apotheosis:reforge_item` with `rarities` on the stack in our loot table (RLM-289 for containers) |
| Crafted gear | none until reforged; reforging rolls at the player's highest world tier | the reforging tables (RLM-199, [apotheosis.md](apotheosis.md#reforging)) |
| Quest choice rewards | fixed when the quest file is written (pre-rolled, ADR 0006) | the quest pipeline writes the stack with its rarity |

`apotheosis:reforge_item` (class `ReforgeItemFunction`, one optional field `rarities`) turns a stack into an affix
item of one of the listed rarities, which is how our tables reach Matěj's bands without a loot entry type of our
own. A source with no band (vanilla mobs, vanilla chests) follows the tier alone.

### The realm bands

Matěj's table of 2026-10-15 (RLM-198) caps nothing for Apotheosis's own drops since RLM-204 ([apotheosis.md](apotheosis.md#what-the-tier-changes)):
the tier weights already give a player mostly these rarities. For **our own loot** it is the band the source names,
by the realm the source stands in:

| Realm | World tier | Band of our loot | The tier's weights give |
| --- | --- | --- | --- |
| Overworld | Haven | Common–Uncommon | 60 % Common, 36 % Uncommon |
| Undervault | Frontier | Common–Uncommon | 29 % Common, 60 % Uncommon |
| Sunwastes | Frontier or Ascent | Uncommon–Rare | Ascent: 30 % Uncommon, 50 % Rare |
| Everwinter | Ascent | Uncommon–Rare | as above |
| Deepholm | Ascent | Rare–Epic | 50 % Rare, 10 % Epic |
| Quagmire | Summit | Rare–Epic | 29 % Rare, 54 % Epic |
| Ashfire | Summit | Epic–Legendary | 54 % Epic, 5 % Legendary |
| Unmade Isles | Pinnacle | Epic–Legendary | 65 % Epic, 25 % Legendary |

- **Bosses take the band's top:** a dungeon boss's gear drop is the band's upper rarity (Undervault's Astone: an
  Uncommon), a realm's final boss one above the band for its unique piece (Astone's unique: Rare). Chests and trash
  mobs roll the whole band.
- **Deepholm** (formerly Prestoria, then Atlantis) keeps the chapter 4 band; Skyhearth has no loot of its own.
- **Repeat runs** roll the same band: no diminishing loot from one spot (Matěj, 2026-10-15), and no item drops
  only by luck without another way to it (LE-15, LE-17): every unique has a second source, a quest-shop item or a
  repeatable quest ([Loot-only gear](#loot-only-gear)).

## Affixes by archetype

### Loot categories

Apotheosis picks an item's category in `LootCategory.forItem`: first the data map
`apotheosis:loot_category_overrides` (item id to category; Apotheosis maps `minecraft:shulker_shell` to `none`), then
the first registered category, by priority, whose test the stack passes. Read in the jar:

- **Armor** (`helmet`, `chestplate`, `leggings`, `boots`) is any item whose `EQUIPPABLE` component names that slot,
  except a carved pumpkin or a skull. **So our robes are armor to Apotheosis today:** they would roll Ironforged
  (+armor), Steel Touched (+toughness) and the rest, against the caster rule of no armor (realm design, gear
  archetypes).
- **`melee_weapon`** is a sword, or anything whose default modifiers give more than 1 attack damage in the main
  hand: our wands (2 to 5), as RLM-205 found.
- The others: `bow`, `breaker` (pickaxes, axes, shovels), `shield`, `trident`, `shears`, and `none`.

So the proposal, for RLM-273's Java (a category is a registry entry) and RLM-272's data:

| Category | Items | Slot | How |
| --- | --- | --- | --- |
| `realmforge:wand` | `#realmforge:wands` | main hand | RLM-273, as planned |
| `realmforge:robe_head`, `robe_chest`, `robe_legs`, `robe_feet` | the caster sets (piece names by archetype: Cap, Robes, Pants, Sandals) | the armor slot | new: four categories, each robe mapped in `apotheosis:loot_category_overrides` (generated from the item's `gear`), so the order of the predicates never matters |
| `realmforge:ring`, `necklace`, `bracelet` | `#curios:ring` and so on | the curio slot | RLM-273, as planned |
| ranged and rogue armor | the first ranged set (Hood, Tunic, Greaves, Tabi) | the armor slot | when that set exists (M3), the same way as robes |

Melee armor, melee weapons, bows, tools, shields and tridents keep Apotheosis's categories.

### Melee: armor and weapons

Apotheosis's affixes as RLM-232 sets them (70 kept, 30 off, Executing among them), remapped to our stats by RLM-272:
crit chance (Intricate), crit damage (Lacerating), armor pierce (Piercing), lifesteal (Vampiric), overheal
(Berserking), cooldown reduction (Timekeeping), and vanilla's attack damage, attack speed, armor and toughness as
they are. Warrior spells scale with attack damage, so Violent and Murderous are the Warrior's spell power too.

**One clash to fix in RLM-272: max health on gear.** Architecture 8.2 forbids gear that adds `minecraft:max_health`
directly, because equipment modifiers apply one tick after a login and the saved health is cut to the lower maximum
first (the item schema refuses it for our gear). Apotheosis's **Blessed** affix (armor, 2–8 health) and the
**Brawler's** and **Warlord's** gems add exactly that. Proposal: remap them to Vitality at 1 point per health point
(VIT gives 1 health per point through the saved derived modifiers, `content/stat_rules/vitality.yaml`), so a
Legendary Blessed gives 5–8 VIT and the health survives a login.

### Caster: robes and wands

New affixes of our own, through the `apotheosis_affixes` content type (`type: apotheosis:attribute`, like
`lucky.yaml`), all `stat` kind so they roll from Common. Values per piece, Common / Uncommon / Rare / Epic /
Legendary, anchored on our robes (a Netherite robe piece gives 10 % spell power and 40 mana, an Arcane Silk one 7 %
and 25) and on the Mage's 100 mana and 5 mana per second:

| Affix (working name) | Stat | Robes | Wand |
| --- | --- | --- | --- |
| Sorcerous | spell power | 2–3 / 2–4 / 3–5 / 4–7 / 6–9 % | 4–6 / 5–8 / 7–11 / 10–15 / 14–20 % |
| Deep | max mana | 5–10 / 8–12 / 10–18 / 15–25 / 20–35 | |
| Flowing | mana regeneration | 0.2–0.4 / 0.3–0.5 / 0.4–0.8 / 0.6–1.0 / 0.8–1.5 per s | 0.5–1 / 0.6–1.2 / 1–2 / 1.5–2.5 / 2–3 per s |
| Warded | magic shield | 1 / 1–2 / 1–3 / 2–4 / 3–5 | |
| Swift-Casting | cast time reduction | | from Rare: 2–4 / 3–6 / 5–8 % |
| Timekeeping (Apotheosis's, remapped by RLM-272) | cooldown reduction | from Rare: 1–5 / 5–7.5 / 5–10 %, on the robe head and chest categories too | |
| Timeless | cooldown reduction | | from Rare: 2–4 / 3–6 / 5–8 % |
| Fiery, Frozen, Arcane-Touched | fire, frost or arcane power | from Uncommon: 3–5 / 4–7 / 6–10 / 8–14 % | from Uncommon: 5–8 / 6–10 / 9–15 / 12–20 % |
| Spellwarded | spell resistance | from Rare: 2–4 / 3–5 / 4–7 % (robes only) | |
| Insightful | crit chance | | 2–4 / 2–4 / 3–5 / 4–7 / 5–9 % |
| Apotheosis's kept armor affixes that give no armor (Fortunate, Lucky, Stalwart, Windswept, Aquatic, Feathery, Bolstering and the like) | as Apotheosis's | on robes too: the robe categories join their lists | |

- **No armor or toughness on robes,** so Ironforged, Adamantine and Steel Touched leave them out, and the robe
  categories never take the melee armor affixes.
- **Stats only on wands, at first.** Apotheosis's effect and ability affixes fire on melee hits, arrows and being
  hurt, never on a spell, so a wand's "On hit" would fire only when it is swung. Spell-hit effects (a Frostbolt that
  slows more) need a hook in our spell engine, for a later ticket (open question 3). Effects on robes work as on any
  armor (Bolstering, Revitalizing: on being hurt).
- **Sockets:** robes and wands take sockets as Apotheosis's armor and weapons do; the gems' bonuses for them are in
  [Gems](#gems).

### Ranged and rogue

No ranged class exists in v1 before M3, and no rogue before M8. Bows and crossbows keep Apotheosis's ranged affixes
(RLM-232). The ranged sets ("less armor than melee, dodge chance and movement speed", realm design) get categories
of their own with the first set, so dodge never rolls on plate: Evasive (dodge chance, 1–2 / 1–2 / 2–3 / 2–4 /
3–5 %), Windswept, Intricate and Piercing, and a reduced Ironforged. Until then nothing is built for them.

### Tools

Apotheosis's `breaker` and `shears` affixes as RLM-232 sets them (Omnetic off, it bypasses the tool tiers; Radial
and Super-Massive checked against the dungeons' protected blocks). Tools take no affix of ours.

### Curios

Stat affixes only and attribute gem bonuses, no special affixes (Matěj, 2026-10-16, RLM-205). Four slots: two
rings, one necklace, one bracelet (Matěj, 2026-10-17). A curio's affix is about half of an armor piece's, since the
four slots add up to a fifth and sixth armor piece:

| Slot | Affixes | Examples, Common → Legendary |
| --- | --- | --- |
| Ring | offense or defense: attack damage, spell power, crit chance, armor, health regeneration | crit chance 1–2 → 3–5 %; spell power 1–2 → 4–6 %; armor 0.5–1 → 2–4 |
| Necklace | the school of a build: fire, frost or arcane power, crit damage, lifesteal, max mana | fire power 2–4 → 6–10 %; crit damage 5–8 → 12–20 % |
| Bracelet | utility: movement speed, cooldown reduction, cast time reduction, mana regeneration, knockback resistance | cooldown reduction 1 → 3–4 %; movement speed 2–4 → 6–10 % |

- **No toughness affix on curios.** Toughness stays scarce (Matěj, 2026-10-16): the crafted Diamond Band's fixed
  +1 toughness is the only toughness from curios, two rings give +2 at most.
- **Sockets:** one socket from Rare, as `rarity_override` files per curio category (RLM-273).

## Loot-only gear

Matěj, 2026-10-15: loot-only armor, weapons and curios are stronger than craftable gear by default, relative to
where they come from, with an early dungeon's curio about as strong, relatively, as diamond-tier gear.

### The rule

A loot-only item's **base stats** come from the best set a player can craft at its source's world tier (the
**reference set**), raised by its kind of source. Its **unique effect** comes on top, and Apotheosis's rarity and
affixes on top of both (Matěj: "a piece can carry an upgrade and affixes together"). One step is about +30 %, the
gap between iron and diamond (a chestplate's 6 and 8 armor) and the fixed step per gear tier that LE-7 recommends.

| Source | Base stats | Unique effect | Rarity |
| --- | --- | --- | --- |
| Open-world rare drop, a structure's chest | the reference set | minor or none | the realm's band |
| Dungeon chest or treasury | reference set +15 % | minor | the band |
| Dungeon boss | reference set +30 % (one step) | major | the band's top |
| A realm's final boss | the next tier's crafted set | major, often a set or slot of its own | one above the band |

| World tier | Reference set: melee / caster | Realms |
| --- | --- | --- |
| Haven | diamond / Arcane Silk | Overworld |
| Frontier | netherite / Netherite robes | Undervault, Sunwastes |
| Ascent | the Twins set (Sunsteel, Icesteel) | Everwinter, Deepholm |
| Summit | Quagmire's and Ashfire's sets (not designed) | Quagmire, Ashfire |
| Pinnacle | malachite | Unmade Isles |

**The anchor:** Overworld's players craft iron while they meet its dungeons, and an Overworld dungeon's loot is
diamond-level (the reference set itself, Haven being Overworld's tier), which is Matěj's "an early dungeon's curio is
about as strong as diamond-tier gear". Undervault's Astone drops netherite +30 %: her unique piece is above anything
a level 18 player can craft, and below the Twins set of chapter 3.

### Armor and toughness climb slowest

- **The raise applies to everything but armor and toughness.** A loot-only piece's armor and toughness sit between
  the reference set's and the next tier's, never above the next tier's crafted piece. The rest of its budget is its
  effect: Matěj's "better armor gets effects instead" (2026-10-16), with the best reachable set at about netherite
  × 1.5, 30 armor and 18 toughness.
- **Example, Astone's unique chest piece (melee):** 9 armor (netherite 8, the Twins tier's chestplate about 9),
  toughness 3, +30 % on its other stats, and an effect such as "Unbroken: 10 % less damage from hits while above
  half health".
- **A caster's unique robe** raises magic shield, spell power and mana by the same shares; magic shield counts as its
  armor for the slow climb.

### Curios

Curios have no crafted set to compare with, except the jeweler's few (Diamond Band, RLM-248). Their reference is a
**piece budget**: a Haven curio's fixed stats equal one reference piece's non-armor stats, an Arcane Silk piece's
7 % spell power or 25 mana, or 1–2 attack damage for melee. The table above raises it by source as for armor. A
unique curio's effect is the point of it: the Snow Queen's Frozen Necklace (RLM-246) is the first.

### Second sources

Every loot-only item has a second, deterministic way to it (LE-17, against luck as the only way, LE-15): the
dungeon type's quest shop sells the boss's unique for that dungeon's tokens or coins after a first kill, or a
repeatable quest gives it, priced at about 5–10 runs. A boss's unique is never a 1 % drop with nothing else behind it.

### In data

- A unique is an item of `content/items/` with fixed `gear` stats and its effect, and **no Apotheosis affix loot
  entry**, so Apotheosis's random drops never hand it out: it comes from our tables (with `apotheosis:reforge_item`
  for its rarity) and the second source.
- The craftable sets get **affix loot entries** (`affix_loot_entries/`, generated), so they also drop as affix items
  in Apotheosis's own loot, as vanilla's diamond and netherite gear does today (Apotheosis ships 50 entries).
- Unique effects use our status effects and talent effect types, so they work in the damage pipeline (no hit outside
  it).

## Netherite upgrades

Decided (Matěj, 2026-10-15): eight gems around one base item craft an upgrade for one netherite piece, at most one per
piece and no set bonuses: **Ruby** offense, **Sapphire** defense, **Topaz** utility (Topaz is also the curio
material). It is our own bonus tied to the piece, not an affix, and it follows the piece's archetype. Proposed values
per piece:

| Upgrade | Melee piece | Caster piece |
| --- | --- | --- |
| Ruby (offense) | +3 % attack damage | +3 % spell power |
| Sapphire (defense) | +1 armor, +5 % knockback resistance | +2 magic shield |
| Topaz (utility) | +3 % movement speed | +2 % cooldown reduction |

Four Rubies give +12 % attack damage, about one Legendary Murderous on a sword. The upgrade is a smithing recipe
(template, piece, upgrade) that sets a data component of ours, shown as a tooltip line; the gems go into Undervault's
stones with RLM-433.

## Gems

- **Apotheosis's gems are the socket gems,** and Ruby, Sapphire and Topaz are not sockets (task 4).
- **Our categories get gem bonuses:** each gem that fits a caster or a curio gets a bonus for `realmforge:wand`,
  the robe categories and the curio categories (`extra_gem_bonuses` in RLM-273's data), with our stats: Solar fire
  power, Lunar frost power, Splendor luck, Samurai crit chance on wands. Curios take attribute bonuses only.
- **The Nether's and the End's gems never drop:** Blood Lord, Inferno and Molten Breach name
  `minecraft:the_nether` in their `constraints.dimensions`, Endersurge and Mageslayer `minecraft:the_end`, and the
  pack has neither ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)). Proposal: our data moves their
  constraints to realms of their theme: the Nether's three to Ashfire, the End's two to Unmade Isles. The three
  Overworld gems (Earth, Royalty, Verdant Ruin) stay Overworld's.
- Max health on gems: the Brawler's and the Warlord's, as above.

## What follows from it

Once Matěj approves, these tickets carry it (names for the follow-ups, not tickets yet):

- **RLM-272** (data from our YAML): Blessed, Brawler's and Warlord's max health remapped to Vitality; the robes'
  `loot_category_overrides`; `reforge_item` support in our loot tables.
- **RLM-273** (loot categories): the four robe categories next to the wand and curio ones; the curio affixes, sockets
  and gem bonuses.
- **New: caster affixes** (the table above) through the `apotheosis_affixes` content type, after RLM-272.
- **New: Nether and End gems re-homed** to Ashfire and Unmade Isles, after RLM-272.
- **New: netherite upgrades** (Ruby, Sapphire, Topaz), after RLM-433's gems.
- **New: loot-only rule in validation**: a unique's base stats within its source's band, from the reference sets,
  once the first uniques exist (Astone's, RLM-503).

## Open questions

For Matěj; each has a proposal so the data tickets can go on.

1. **Bands for our loot.** Your table of 2026-10-15 as the band our own loot names per realm, bosses at the band's
   top and a realm's final boss one above it for its unique (proposed); or the world tier alone everywhere?
2. **Loot-only power.** The reference set +0 / +15 / +30 % by source and the next tier's set for a final boss, with
   armor and toughness climbing at most to the next tier's (proposed); or other shares?
3. **Effects on wands.** Stats only on wands now, spell-hit effects later through our spell engine (proposed); or
   no effects on wands at all?
4. **Caster affixes.** The table above (eleven affixes, values per rarity) as the first set (proposed)? Its names are
   working names for the style guide.
5. **Max health on gear.** Blessed and the two gems remapped to Vitality (proposed), or turned off?
6. **Nether and End gems.** Re-homed to Ashfire and Unmade Isles (proposed), or left out?
7. **Netherite upgrades.** The values above (proposed)? Topaz as the utility upgrade and the curio material at once
   (your two notes of 2026-10-15)?
8. **Second sources.** Every boss unique in its dungeon's quest shop after a first kill, at about 5–10 runs' worth
   (proposed)?
