# Gear: rarity, affixes and loot-only gear

> How gear grows stronger in Realmforge (RLM-198): what a rarity changes and where a rarity comes from, which affixes
> roll on each gear archetype (melee, caster, ranged and rogue, tools, curios), how much stronger loot-only gear is
> than crafted gear, and how the netherite upgrades and the gems fit in. Written on 2026-10-29 from Matěj's decisions
> on RLM-198 (2026-10-15 to 2026-10-17), RLM-201, RLM-205 and RLM-232, and read in Apotheosis 9.1.0's 26.1.2 jar
> (`Apotheosis-26.1.2-9.1.0.jar`, the pack's build: its data files and its `LootCategory` and loot function classes
> with `javap`). Rarity, affixes, gems and reforging come from Apotheosis
> ([ADR 0038](../decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)); its world tiers,
> loot and reforging loop are in [apotheosis.md](apotheosis.md). Matěj approved the design on 2026-10-30 and
> 2026-10-31 (RLM-511, [Decisions](#decisions)) with the changes written in below; a playtest tunes its numbers, and
> one point is still open ([Open questions](#open-questions)).

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
  only by luck without another way to it (LE-15, LE-17): a boss's unique drops at 100 %, and one that ever drops below
  that also goes into its dungeon's quest shop ([Second sources](#second-sources)).

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
| `realmforge:wand` | `#realmforge:wands` | main hand | RLM-273 (its affixes: RLM-562) |
| `realmforge:robe_head`, `robe_chest`, `robe_legs`, `robe_feet` | the caster sets (piece names by archetype: Cap, Robes, Pants, Sandals) | the armor slot | RLM-546 (accepted on RLM-536, 2026-10-30): four categories, each robe mapped in `apotheosis:loot_category_overrides` (generated from the item's `gear`), so the order of the predicates never matters |
| `realmforge:ring`, `necklace`, `bracelet` | `#curios:ring` and so on | the curio slot | RLM-556, once Curios and our curio items are in the pack |
| ranged and rogue armor | the first ranged set (Hood, Tunic, Greaves, Tabi) | the armor slot | when that set exists (M3), the same way as robes |

Melee armor, melee weapons, bows, tools, shields and tridents keep Apotheosis's categories.

### Melee: armor and weapons

Apotheosis's affixes as RLM-232 sets them (68 kept, 31 off, Executing and Runed among them; [the affix set](apotheosis.md#the-affix-set)), remapped to our stats by RLM-272:
crit chance (Intricate), crit damage (Lacerating), armor pierce (Piercing), lifesteal (Vampiric), overheal
(Berserking), cooldown reduction (Timekeeping), and vanilla's attack damage, attack speed, armor and toughness as
they are. Warrior spells scale with attack damage, so Violent and Murderous are the Warrior's spell power too.

**Max health on gear: kept as is** (Matěj, 2026-10-30). Apotheosis's **Blessed** affix (armor, 2–8 health) and the
**Brawler's** and **Warlord's** gems keep their `minecraft:max_health`; no remap to Vitality. Architecture 8.2 forbade
gear that adds max health directly, because equipment modifiers apply one tick after a login and the saved health is
cut to the lower maximum first (the item schema refuses it for our gear). That rule predates Apotheosis and changes
instead: RLM-565 keeps the health across the login, so the clamp no longer cuts it.

### Caster: robes and wands

New affixes of our own, through the `apotheosis_affixes` content type (`type: apotheosis:attribute`, like
`lucky.yaml`), all `stat` kind so they roll from Common. Matěj approved the robe affixes on 2026-10-30 (RLM-511,
question 4; RLM-557 builds them). Values per piece, Common / Uncommon / Rare / Epic / Legendary, anchored on our robes
and on the Mage's 100 mana and 5 mana per second. Spell Power is flat points since RLM-534 (scaling system C): a
Netherite robe piece gives 0.9–2.4 Spell Power and 40 mana (6 Spell Power for the set), an Arcane Silk one 0.7–1.8 and
25 (4.5 for the set), and a wand 1–5.

| Affix (working name) | Stat | Robes, per piece |
| --- | --- | --- |
| Sorcerous | Spell Power, flat (`add_value`): half a wand's (Matěj, 2026-10-31) | 1–1.5 / 1–1.5 / 1.5–2.5 / 2–3 / 2.5–4 |
| Deep | max mana | 5–10 / 8–12 / 10–18 / 15–25 / 20–35 |
| Flowing | mana regeneration | 0.2–0.4 / 0.3–0.5 / 0.4–0.8 / 0.6–1.0 / 0.8–1.5 per s |
| Warded | magic shield | 1 / 1–2 / 1–3 / 2–4 / 3–5 |
| Timekeeping (Apotheosis's, remapped by RLM-272) | cooldown reduction | from Rare: 1–5 / 5–7.5 / 5–10 %, on the robe head and chest categories |
| Fiery, Frozen, Arcane-Touched | fire, frost or arcane power | from Uncommon: 3–5 / 4–7 / 6–10 / 8–14 % |
| Apotheosis's kept armor affixes that give no armor (Fortunate, Lucky, Stalwart, Windswept, Aquatic, Feathery, Bolstering and the like) | as Apotheosis's | the robe categories join their lists |

- **No Spellwarded.** Spell Resistance and the school resistances are mob-only stats (Matěj, 2026-10-30, RLM-233): a
  player's magic defense is Magic Protection (RLM-537), Magic Shield, Dodge and health. So the caster affixes are ten,
  and Spell Penetration (RLM-535) takes Spellwarded's place on wands.
- **No armor or toughness on robes,** so Ironforged, Adamantine and Steel Touched leave them out, and the robe
  categories never take the melee armor affixes.
- **Wands roll affixes like melee weapons, converted to spells** (Matěj, 2026-10-31, RLM-562, which replaces this
  table's former wand column). Stat affixes: Sorcerous (flat Spell Power 2–3 / 2–3 / 3–5 / 4–6 / 5–8, as Violent),
  Hastened (cast time reduction), Timeless (cooldown reduction), Spellbreaking (Spell Penetration), Insightful (crit
  chance), Devastating (crit damage), Flowing (mana regeneration) and Fiery, Frozen and Arcane-Touched; then effects on
  spell hit (Enfeebling, Chilling, Illuminating, Blighting, Uplifting, Hexing, Far-Reaching) and abilities (Explosive,
  Echoing, Siphoning, Warding, Staggering) through a spell-hit hook in our spell engine, since Apotheosis's own effect
  and ability affixes fire on melee hits, arrows and being hurt, never on a spell. RLM-562 holds the values. This
  supersedes the first answer to question 3 (2026-10-30: stats only on wands now, spell-hit effects later). Effects on
  robes work as on any armor (Bolstering, Revitalizing: on being hurt).
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
| Ring | offense or defense: attack damage, spell power, crit chance, armor, health regeneration | crit chance 1–2 → 3–5 %; spell power: to size in flat points ([open](#open-questions)); armor 0.5–1 → 2–4 |
| Necklace | the school of a build: fire, frost or arcane power, crit damage, lifesteal, max mana | fire power 2–4 → 6–10 %; crit damage 5–8 → 12–20 % |
| Bracelet | utility: movement speed, cooldown reduction, cast time reduction, mana regeneration, knockback resistance | cooldown reduction 1 → 3–4 %; movement speed 2–4 → 6–10 % |

- **No toughness affix on curios.** Toughness stays scarce (Matěj, 2026-10-16): the crafted Diamond Band's fixed
  +1 toughness is the only toughness from curios, two rings give +2 at most.
- **Sockets:** one socket from Rare, as `rarity_override` files per curio category (RLM-273).
- **Artifacts' slots** (Matěj, 2026-10-30, RLM-512): the curio rule ended, and Artifacts' head, belt, two hands and feet
  slots join ours for its items, which roll no affixes; the necklace is one slot that its necklaces share with ours
  ([artifacts.md](../research/artifacts.md#matějs-decision-and-where-each-item-goes)).

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
diamond-level: its chests diamond +15 % and its boss diamond +30 % (Haven's reference set, the table above), which is
Matěj's "an early dungeon's curio is about as strong as diamond-tier gear". Undervault's dungeon chests and bosses give
netherite +15 % and +30 %, and Astone, Undervault's final boss, the next tier's set: her unique piece has the Twins
set's numbers, above anything a level 18 player can craft, and equal to what chapter 3 crafts.

### Armor and toughness climb slowest

- **The raise applies to everything but armor and toughness.** A loot-only piece's armor and toughness sit between
  the reference set's and the next tier's, never above the next tier's crafted piece. The rest of its budget is its
  effect: Matěj's "better armor gets effects instead" (2026-10-16), with the best reachable set at about netherite
  × 1.5, 30 armor and 18 toughness.
- **Example, Astone's unique chest piece (melee):** the Twins tier's chestplate (not designed yet; about 9 armor and
  3 toughness against netherite's 8 and 3), its other stats at the Twins tier's, and an effect such as "Unbroken:
  10 % less damage from hits while above half health".
- **A caster's unique robe** raises magic shield, spell power and mana by the same shares; magic shield counts as its
  armor for the slow climb.

### Curios

Curios have no crafted set to compare with, except the jeweler's few (Diamond Band, RLM-248). Their reference is a
**piece budget**: a Haven curio's fixed stats equal one reference piece's non-armor stats, an Arcane Silk piece's
0.7–1.8 Spell Power or 25 mana, or 1–2 attack damage for melee. The table above raises it by source as for armor. A
unique curio's effect is the point of it: the Snow Queen's Frozen Necklace (RLM-246) is the first.

### Second sources

Matěj, 2026-10-31 (RLM-511, question 8): yes, in principle, against luck as the only way (LE-15, LE-17).

- **Every boss drop designed so far drops at 100 %,** so no item needs the shop now, and low drop chances such as
  10 % are not wanted.
- **If a boss's unique ever drops below 100 %,** it also goes into its dungeon type's quest shop after the first kill,
  as bad-luck protection, for that dungeon's tokens or coins at about 5–10 runs' worth (or a repeatable quest gives
  it). A boss's unique is never a rare drop with nothing else behind it.

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
piece and no set bonuses: **Ruby** offense, **Sapphire** defense, **Topaz** utility. It is our own bonus tied to the
piece, not an affix, and it follows the piece's archetype. Values per piece (Matěj, 2026-10-30, RLM-511; RLM-566
builds them):

| Upgrade | Melee piece | Caster piece | Later, ranged piece (M3) |
| --- | --- | --- | --- |
| Ruby (offense) | +5 % Attack Damage | +5 % Spell Power | +5 % Arrow Damage |
| Sapphire (defense) | +2 armor, +1 armor toughness | +3 Magic Shield | +1 armor, +2 % Dodge Chance |
| Topaz (utility) | +5 % movement speed | +5 % Cooldown Reduction | +5 % Draw Speed |

- Four Rubies give +20 % Attack Damage, or +20 % Spell Power on the caster's flat points (system C: a percent
  modifier multiplies the flat total).
- The ranged Sapphire's Dodge Chance is Claude's suggestion (Matěj was unsure of it): 2 % a piece, 8 % for a full set.
- **Every gem has other uses too:** Topaz is also the curio material, and Ruby and Sapphire go into curios or other
  recipes, never only into the upgrade.
- The upgrade is a smithing recipe (template, piece, upgrade) that sets a data component of ours, shown as a tooltip
  line. The gems drop from the Hewn Sentinel today and go into Undervault's stones with RLM-433.

## Gems

- **Apotheosis's gems are the socket gems,** and Ruby, Sapphire and Topaz are not sockets (task 4).
- **Our categories get gem bonuses:** each gem that fits a caster or a curio gets a bonus for `realmforge:wand`,
  the robe categories and the curio categories (Apotheosis's `extra_gem_bonuses`), with our stats: the wand bonuses in
  RLM-547 (Matěj's picks on RLM-536, 2026-10-30), the robes' in RLM-548 (the armor gems' bonuses with swaps: Armor and
  Toughness to Magic Shield, Attack Damage % to Spell Power %), the curios' with RLM-556. Curios take attribute bonuses
  only.
- **The Nether's and the End's gems never drop:** Blood Lord, Inferno and Molten Breach name
  `minecraft:the_nether` in their `constraints.dimensions`, Endersurge and Mageslayer `minecraft:the_end`, and the
  pack has neither ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)). Accepted (Matěj, 2026-10-30, on
  RLM-536): our data moves their constraints to realms of their theme once those realms exist, the Nether's three to
  Ashfire, the End's two to Unmade Isles (RLM-550). The three Overworld gems (Earth, Royalty, Verdant Ruin) stay
  Overworld's.
- Max health on gems: the Brawler's and the Warlord's keep theirs, as above (RLM-565).

## Expected gear for balance tests

When a handoff asks Matěj to judge the balance of a dungeon, an instance or a boss, its command block line also gives
him the gear a player is expected to bring there (Matěj, 2026-10-30, RLM-551; the rule:
[Reaching the state](../ai-development-flow.md#reaching-the-state)). Expected is the best a player can have on
entering: the reference set of the content's world tier ([above](#loot-only-gear)), its enchantments, an Apotheosis
rarity with its affixes and filled sockets, and the level and stat points when the check needs them, for the class
the check is about. Where exact affixes or gems are impractical, stronger base gear or enchantments of equal power
stand in, and the handoff says so. Later handoffs add rows; Matěj's corrections from playtests update them.

| Content | Level | Melee (Warrior) | Caster (Mage) | Enchantments | Rarity and sockets | Source |
| --- | --- | --- | --- | --- | --- | --- |
| Astone, Undervault's final boss | 18 | full netherite armor and a netherite sword | the Netherite set (cap, robes, pants, sandals) and the Netherite Wand | level V for every enchantment, Protection and Unbreaking included: Apothic Enchanting raises vanilla's maximum levels (RLM-552) | Rare, every socket filled with a Flawed gem | Matěj, 2026-10-30: "full netherite, enchantments at about level V, rare gems"; gems have purities, not rarities, so "rare gems" reads as Rare pieces with Flawed gems until a playtest says otherwise. RLM-534's target: such a player deals about 50 per spell to her |

## What follows from it

These tickets carry the approved design:

- **RLM-272** (Done): Apotheosis's data from our YAML. RLM-273 (Done): the wand loot category.
- **RLM-546:** the four robe loot categories and their `loot_category_overrides`.
- **RLM-557:** the pipeline for affixes of our own and the robe affixes (the table above).
- **RLM-562:** the wand affixes (stats, effects on spell hit, abilities), a stun ability for every weapon, and Hexed.
- **RLM-547** and **RLM-548:** the gem bonuses for wands and robes; **RLM-556:** the curio categories, their affixes,
  sockets and gem bonuses.
- **RLM-550:** the Nether's and the End's gems drop in Ashfire and Unmade Isles, once those realms exist.
- **RLM-565:** max health from gear survives a login (Blessed and the two gems keep theirs).
- **RLM-566:** the netherite upgrades (Ruby, Sapphire, Topaz).
- **RLM-567** (M2): our own loot names its band through `apotheosis:reforge_item`, and validation keeps loot-only
  uniques within their source's step, once the first uniques exist.

## Decisions

Matěj's answers to the design's questions (RLM-511, 2026-10-30 and 2026-10-31), written into the sections above:

1. **Bands for our loot:** yes, his band per realm of 2026-10-15, bosses at its top and a realm's final boss one
   above it for its unique; Apotheosis's random drops by world tier.
2. **Loot-only power:** yes, +0 / +15 / +30 % by source and the next tier's set for a final boss, armor and toughness
   climbing slowest.
3. **Effects on wands:** first stats only, spell-hit effects later through our spell engine (2026-10-30); on
   2026-10-31 RLM-562 brought the spell-hit effects and abilities in now.
4. **Caster affixes:** yes, ten, without Spell Resistance (a mob-only stat); Spell Penetration (RLM-535) takes its
   place on wands. Sorcerous is flat Spell Power under system C (2026-10-31): robes 1–1.5 / 1–1.5 / 1.5–2.5 / 2–3 /
   2.5–4 a piece, wands 2–3 / 2–3 / 3–5 / 4–6 / 5–8.
5. **Max health on gear:** kept as is; architecture 8.2's rule changes instead, and the login clamp is solved
   (RLM-565).
6. **Nether and End gems:** re-homed to Ashfire and Unmade Isles once those realms exist (on RLM-536).
7. **Netherite upgrades:** new values, the table above, and every gem has other uses too.
8. **Second sources:** yes, in principle: boss drops at 100 %, no low drop chances, and a unique below 100 % goes into
   its dungeon's quest shop after the first kill.

## Open questions

1. **Spell Power on curios under system C.** Spell Power is flat points since RLM-534, and the ring's spell power
   affix (Curios, above) was sized as a percent, 1–2 → 4–6 %, against the old stat. Its size in flat points waits for
   Matěj (RLM-511 comment 23232: no agent picks new numbers); RLM-556 needs it for the ring.
