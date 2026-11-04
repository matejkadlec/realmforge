# Apotheosis gems: the catalogue and the caster stats

> Research for RLM-536: every gem of Apotheosis 9.1.0 (NeoForge 26.1.2) with its bonus per gear category and purity,
> what our data does to it today, the findings, and Matěj's decision of 2026-10-30 on which gems give spell stats,
> Magic Shield and Dodge Chance, and how wands and robes take gems. Read from the pinned snapshot
> `tools/id_universe/apotheosis-9.1.0.json` ([ADR 0063](../decisions/0063-apotheosis-data-is-fitted-from-a-pinned-snapshot.md),
> Apotheosis commit `f2e7686`) and our `content/apotheosis_stat_remaps/` on `master` at `ecb2154` (2026-11-03). The
> proposal went to Matěj as the "Apotheosis Gem Review" page, which he reviewed gem by gem; his
> [decision](#matějs-decision-2026-10-30) is recorded on RLM-536 and below, and its implementation tickets are RLM-546
> to RLM-550.

**In short.** Apotheosis has 21 gems: 13 core gems that drop everywhere, three Overworld gems from the Frontier tier up,
and five Nether and End gems. A gem gives one bonus per gear category it fits, and fits only the categories it has a
bonus for. Melee stats are well covered (attack damage, armor pierce and shred, crit, armor and toughness); spell stats,
Magic Shield and Mana Regeneration are not there at all, and Dodge Chance comes only from Slipstream's boots, Samurai's
shield and Royalty's all-stats. Our data remaps Apothic Attributes' stats to ours (crit, dodge, pierce, shred,
lifesteal, overheal, cooldown reduction) and switches protection pierce and shred off. When the review began, Apotheosis
filed our wands as melee weapons and our robes as armor, so a wand took Ballast's attack damage and a robe Guardian's
armor; RLM-273 and RLM-546 gave both categories of their own, and until the extra bonuses land (RLM-547, RLM-548) no gem
fits a wand or a robe. Matěj's decision gives 14 gems a wand bonus, the robes the armor bonuses with swaps to caster
stats, and no gem any spell or school resistance.

## Matěj's decision (2026-10-30)

He reviewed the proposal gem by gem and answered four follow-up questions in chat (RLM-536, comment of 2026-10-30).

### Cross-cutting

- **G1, accepted:** wands are their own gem category (RLM-273); a gem fits a wand only with a wand bonus.
- **G2, accepted:** four robe categories (head, chest, legs, feet). Robes take each armor gem bonus as it is, with
  swaps: Armor → Magic Shield, Toughness → Magic Shield, Toughness % → Magic Shield %, Attack Damage % → Spell
  Power %, Inferno's Fire Damage % → Fire Power.
- **G3, accepted:** the new bonuses through Apotheosis's `extra_gem_bonuses`, generated from a new content type;
  changes to existing bonuses through `apotheosis_gems`.
- **G4, changed:** `realmforge:physical` joins the common physical tag (`#c:is_physical`, NeoForge's
  `#neoforge:is_physical` on 26.1), but the spell schools stay out of the magic one (`#c:is_magic`), so no Apotheosis
  magic reduction stacks with Magic Protection on a player (RLM-537). Built by RLM-549
  ([architecture 9.1](../architecture.md#91-damage-pipeline)).
- **No Spell Resistance or school resistance in any gem:** they are mob-only stats (RLM-233).

### Wand bonuses (cracked → perfect)

| Gem | Wand bonus |
| --- | --- |
| Ballast | Spell Power +1 / 2 / 3.5 / 5 / 7 / 10 (as a sword's Attack Damage) |
| Brawler's | Cast Time Reduction +5 / 7.5 / 10 / 12.5 / 15 / 20 % |
| Breach | Spell Penetration +5 / 10 / 15 / 20 / 25 / 30 % |
| Combatant | Spell Power +5 / 10 / 15 / 20 / 25 / 30 % (of base) |
| Lightning | Cooldown Reduction +5 / 7.5 / 10 / 12.5 / 15 / 20 % |
| Lunar, Solar, Tyrannical | Frost, Fire and Arcane Power +5 / 10 / 15 / 22.5 / 30 / 40 % |
| Samurai, Warlord, Splendor | the same bonus as on weapons (Crit Chance, Crit Damage, Experience Gained) |
| Slipstream | Mana Regeneration +1 / 1.5 / 2 / 2.5 / 3.5 / 5 per second |
| Verdant Ruin (from flawed) | Spell Penetration +20 / 30 / 40 / 50 % with Magic Shield −5 / −10 / −15 / −20 % |
| Molten Breach (from flawed) | Spell Penetration +15 / 20 / 25 / 30 % |

**No wand bonus:** Guardian (Matěj: no Magic Shield on wands), Earth (until the wand enchantments, RLM-538), Royalty,
Blood Lord, Inferno and Mageslayer. Endersurge fits everything already.

### Armor and robes

- **Accepted as proposed:** Guardian's and Tyrannical's robe Magic Shield, Samurai's robe Magic Shield %, Warlord's,
  Blood Lord's and Molten Breach's robe Spell Power %, Royalty's all-stats with the caster stats added, and
  Slipstream's new helmet Dodge Chance (half its boots').
- **Inferno's robe head, adjusted:** Fire Power +10 / 20 / 30 / 40 %.
- **Earth, Mageslayer, Splendor and the other armor bonuses:** on robes as on armor.

### Where the Nether and End gems drop

Accepted (RLM-511, question 6): the Nether's three to Ashfire, the End's two to Unmade Isles, once those realms exist.

### Tickets

| Ticket | What |
| --- | --- |
| RLM-546 | the robe loot categories (after RLM-273) |
| RLM-547 | the extra-bonus content type and the wand bonuses (after RLM-534, RLM-535, RLM-273) |
| RLM-548 | the robe bonuses, Slipstream's helmet, Royalty's caster stats (after RLM-546, RLM-547) |
| RLM-549 | the damage type tags (G4) |
| RLM-550 | the Nether and End gems re-homed (backlog, M4, waits for the realms) |

The fit with the wand enchantments (the ticket's task 4) is RLM-538's: it checks its enchantments against RLM-547's
numbers, so that a socketed and enchanted wand never stacks into one oversized bonus.

## How gems work in Apotheosis 9.1

- **A gem** is an entry of Apotheosis's gem registry (`data/apotheosis/apotheosis/gems/`, the path our
  `apotheosis_gems/` content writes to), dropped at one of six purities: cracked, chipped, flawed, normal, flawless,
  perfect. An entry may set a lowest purity
  (`min_purity`), so Earth, Royalty, Verdant Ruin, Blood Lord, Inferno and Molten Breach start at flawed, Mageslayer at
  normal and Endersurge at flawless. A `unique` gem fits an item only once.
- **Bonuses by category:** each bonus names a gem class, one or more loot categories (`apotheosis:melee_weapon`,
  `apotheosis:chestplate`, …), and its values per purity. A gem fits a socket only if the item's category has one of
  its bonuses. The bonus types are attributes (with Minecraft's three operations), several attributes at once, all
  stats, durability, a damage reduction by damage type, enchantment levels, a status effect on a trigger, and a few
  special ones (area mining, drop transforms, Bloody Arrow, Leech Block, Mageslayer's heal).
- **Categories are code, their assignment data:** the loot categories are a NeoForge registry
  (`Apoth.BuiltInRegs.LOOT_CATEGORY`) that a mod registers in code, each with a test, a priority and its equipment
  slots; an item takes the first category whose test it passes, unless the data map
  `apotheosis:loot_category_overrides` names one. Our `realmforge:wand` and four robe categories are registered by
  `compat/apotheosis/ApotheosisCompat` ([architecture 13](../architecture.md#13-items-gear-and-loot)).
- **Extra bonuses are data:** Apotheosis's `extra_gem_bonuses` adds a bonus for another category to an existing gem
  without replacing its file, which is how the wand and robe bonuses arrive (G3).
- **Drops:** a gem's `weights` give its weight and quality per world tier (core gems at every tier; the Overworld gems
  from Frontier, the Nether gems from Ascent, the End gems from Summit), and `constraints.dimensions` limits where it
  drops. Purities follow the tier's `purity_weights` ([Apotheosis design](../design/apotheosis.md)).

## The catalogue

The 21 gems as they ship, with our remaps applied: `→` names the stat of ours that replaces Apothic Attributes' stat
wherever the bonus grants it, and **off** marks a bonus our data drops (a multi-attribute bonus as a whole,
[content README](../../content/README.md)). Chances and lifesteal are fractions (0.05 = 5 %). Crit Damage's remap also
converts its scale (Apothic Attributes' multiplier of 1.5 against our +0.5 bonus: an `of base` bonus becomes a flat one
of its values × 1.5). Values run from the gem's lowest purity to perfect. "Of base" and "of total" are Minecraft's
`add_multiplied_base` and `add_multiplied_total`.

### Core gems

#### Ballast (`apotheosis:core/ballast`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `minecraft:attack_damage` 1 / 2 / 3.5 / 5 / 7 / 10 |
| pickaxe and tools, shears | durability 10 % / 15 % / 25 % / 35 % / 45 % / 60 % |
| chestplate, leggings | `minecraft:knockback_resistance` 0.1 / 0.2 / 0.3 / 0.4 / 0.5 / 0.7 |

#### Brawler's (`apotheosis:core/brawlers`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `minecraft:attack_speed` (of base) 10 % / 15 % / 20 % / 30 % / 35 % / 50 % |
| chestplate, leggings | `minecraft:max_health` 1 / 2 / 4 / 6 / 8 / 12 |
| shield | `minecraft:max_health` (of total) 5 % / 10 % / 15 % / 20 % / 25 % / 30 % |

#### Breach (`apotheosis:core/breach`)

Drops: any dimension, every world tier, from cracked, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `apothic_attributes:armor_pierce` → `realmforge:armor_pierce` 2 / 3 / 5 / 7 / 9 / 12 |
| pickaxe and tools, shears | `minecraft:block_interaction_range` 0.5 / 1 / 1.5 / 2 / 2.5 / 3 |
| bow | `apothic_attributes:prot_pierce` (**off**) 4 / 5 / 7 / 8 / 10 / 15 |

#### Combatant (`apotheosis:core/combatant`)

Drops: any dimension, every world tier, from cracked, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| bow, trident | `apothic_attributes:arrow_damage` (of base) 5 % / 15 % / 20 % / 30 % / 40 % / 55 % |
| chestplate, leggings | **physical damage reduction** 5 % / 7.5 % / 12.5 % / 17.5 % / 22.5 % / 27.5 % |
| melee weapon | durability 5 % / 10 % / 15 % / 22.5 % / 30 % / 40 % |

#### Guardian (`apotheosis:core/guardian`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| chestplate, leggings | `minecraft:armor` 0.5 / 1 / 2.5 / 4 / 6 / 8 |
| melee weapon, trident | `apothic_attributes:life_steal` → `realmforge:lifesteal` 0.05 / 0.1 / 0.15 / 0.2 / 0.25 / 0.3 |
| shield | `minecraft:armor` (of total) 5 % / 10 % / 20 % / 25 % / 30 % / 45 % |

#### Lightning (`apotheosis:core/lightning`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| bow | `apothic_attributes:arrow_velocity` (of base) 5 % / 15 % / 25 % / 35 % / 42.5 % / 55 % |
| pickaxe and tools, shears | `minecraft:block_break_speed` 0.05 / 0.15 / 0.25 / 0.35 / 0.425 / 0.5 |
| leggings, boots | `minecraft:movement_speed` (of total) 10 % / 22.5 % / 37.5 % / 45 % / 55 % / 70 % |

#### Lunar (`apotheosis:core/lunar`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon | `apothic_attributes:cold_damage` 1 / 1.5 / 2.5 / 4 / 6 / 8 |
| chestplate, leggings | `minecraft:gravity` (of total) -10 % / -25 % / -45 % / -65 % / -85 % / -104 % |
| boots | `neoforge:swim_speed` (of base) 10 % / 20 % / 30 % / 45 % / 60 % / 80 % |
| bow, trident | arrows that ignore gravity (from flawless) |

#### Samurai (`apotheosis:core/samurai`)

Drops: any dimension, every world tier, from cracked, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident, bow | `apothic_attributes:crit_chance` → `realmforge:crit_chance` 0.05 / 0.15 / 0.25 / 0.35 / 0.4 / 0.5 |
| leggings, boots | `minecraft:armor_toughness` (of total) 10 % / 15 % / 17.5 % / 22.5 % / 27.5 % / 35 % and `minecraft:movement_speed` (of total) -2.5 % / -5 % / -7.5 % / -10 % / -12.5 % / -15 % |
| helmet | `apothic_attributes:arrow_velocity` (of total) 7.5 % / 15 % / 25 % / 37.5 % / 42.5 % / 50 % |
| shield | `apothic_attributes:dodge_chance` → `realmforge:dodge_chance` 0.01 / 0.02 / 0.04 / 0.06 / 0.08 / 0.1 |

#### Slipstream (`apotheosis:core/slipstream`)

Drops: any dimension, every world tier, from cracked, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| bow | `apothic_attributes:draw_speed` (of base) 10 % / 25 % / 35 % / 45 % / 50 % / 60 % |
| pickaxe and tools, shears | `minecraft:block_break_speed` (of base) 10 % / 15 % / 22.5 % / 30 % / 37.5 % / 45 % |
| boots | `apothic_attributes:dodge_chance` → `realmforge:dodge_chance` 0.025 / 0.05 / 0.075 / 0.1 / 0.125 / 0.15 |

#### Solar (`apotheosis:core/solar`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `apothic_attributes:fire_damage` 1 / 1.5 / 2.5 / 4 / 6 / 8 |
| chestplate, leggings | `minecraft:gravity` (of total) 10 % / 25 % / 45 % / 65 % / 85 % / 104 % |
| boots | `minecraft:step_height` 0.25 / 0.5 / 1 / 1.5 / 2 / 3 |

#### Splendor (`apotheosis:core/splendor`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| all armor | `minecraft:luck` 0.5 / 1.25 / 2 / 2.25 / 3.5 / 5 |
| weapons, bow, tools | `apothic_attributes:experience_gained` (of base) 7.5 % / 15 % / 22.5 % / 30 % / 40 % / 60 % |

#### Tyrannical (`apotheosis:core/tyrannical`)

Drops: any dimension, every world tier, from cracked, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `minecraft:attack_knockback` 0.5 / 1 / 1.5 / 2 / 3 / 3.5 |
| chestplate, leggings | `minecraft:armor_toughness` 0.5 / 1 / 2 / 3 / 4 / 6 |
| shield | `minecraft:armor_toughness` (of base) 5 % / 15 % / 22.5 % / 30 % / 37.5 % / 50 % |
| boots | `minecraft:max_health` (of total) 5 % / 7.5 % / 12.5 % / 17.5 % / 22.5 % / 40 % and `apothic_attributes:life_steal` → `realmforge:lifesteal` -0.025 / -0.05 / -0.075 / -0.1 / -0.125 / -0.2 |
| bow | none: its `apothic_attributes:bleeding` I / II on the arrow's target (from flawless) is gone since RLM-600 (Matěj, 2026-11-03: the Apotheosis bleed leaves the pack), so it fits no bow |

#### Warlord (`apotheosis:core/warlord`)

Drops: any dimension, every world tier, from cracked.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, bow | `apothic_attributes:crit_damage` → `realmforge:crit_damage` (of base) 10 % / 20 % / 35 % / 45 % / 55 % / 70 % |
| chestplate | `minecraft:max_health` (of base) 5 % / 10 % / 15 % / 20 % / 25 % / 35 % |
| helmet | `minecraft:attack_damage` (of total) 5 % / 10 % / 12.5 % / 15 % / 17.5 % / 22.5 % |
| trident | `minecraft:strength` I / II on the thrower when the trident hits (from flawless) |

The crit damage bonus's gem class is `weapons`, melee weapons and bows only: a trident gets only the Strength effect.

### Overworld gems

#### Earth (`apotheosis:overworld/earth`)

Drops: Overworld, Frontier to Pinnacle, from flawed, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `minecraft:sharpness` +1 / +2 / +3 / +4 (only if the item has it) |
| chestplate, leggings | `minecraft:protection` +1 / +2 / +3 / +4 (only if the item has it) |
| pickaxe and tools, shears | `minecraft:fortune` +1 / +2 / +3 / +4 (only if the item has it) |

#### Royalty (`apotheosis:overworld/royalty`)

Drops: Overworld, Frontier to Pinnacle, from flawed, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| helmet | all stats (of total) 5 % / 7.5 % / 10 % / 15 % (31 attributes; protection pierce and shred leave the list, the remapped ones count as ours) |
| pickaxe | copper ore drops raw gold at 15 / 20 / 25 / 40 % |
| bow | **off** as a whole: `apothic_attributes:prot_shred` (**off**) 0.25 / 0.3 / 0.35 / 0.4 and `apothic_attributes:draw_speed` (of total) -35 % / -45 % / -55 % / -65 % |
| shield | `minecraft:armor` (of base) 15 % / 25 % / 35 % / 50 % and `minecraft:armor_toughness` (of base) 7.5 % / 12.5 % / 22.5 % / 30 % and `minecraft:movement_speed` (of total) -25 % / -30 % / -35 % / -40 % |

The bow bonus is one multi-attribute bonus, and our data drops it whole because protection shred is switched off, draw
speed with it. Royalty therefore no longer fits a bow.

#### Verdant Ruin (`apotheosis:overworld/verdant_ruin`)

Drops: Overworld, Frontier to Pinnacle, from flawed, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident, bow | `apothic_attributes:armor_shred` → `realmforge:armor_shred` 0.075 / 0.15 / 0.25 / 0.4 and `minecraft:armor` (of total) -2.5 % / -5 % / -7.5 % / -10 % |
| chestplate | **magic damage reduction** 12.5 % / 15 % / 17.5 % / 20 % |
| boots | `minecraft:knockback_resistance` (of total) 5 % / 10 % / 15 % / 20 % and `minecraft:attack_knockback` (of total) -7.5 % / -10 % / -12.5 % / -15 % |
| pickaxe and tools | mines an area: 3 × 2 up to 5 × 5 |

### End gems

#### Endersurge (`apotheosis:the_end/endersurge`)

Drops: the End (**never in Realmforge**: no stronghold, no End portal), Summit and Pinnacle, from flawless, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| everything | `minecraft:sharpness` +1 / +2 (to every enchantment the item has) |

#### Mageslayer (`apotheosis:the_end/mageslayer`)

Drops: the End (**never in Realmforge**: no stronghold, no End portal), Summit and Pinnacle, from normal, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | **off** as a whole: `apothic_attributes:prot_shred` (**off**) 0.25 / 0.35 / 0.45 and `apothic_attributes:armor_shred` (of base) -15 % / -30 % / -50 % |
| helmet | **heals on magic damage** 15 % / 22.5 % / 35 % |
| shield | `minecraft:resistance` I / I / II on the blocker after a block |

The melee weapon and trident bonus is one multi-attribute bonus, and our data drops it whole because protection shred
is switched off, the armor shred with it. Mageslayer therefore no longer fits a melee weapon or a trident.

### Nether gems

#### Blood Lord (`apotheosis:the_nether/blood_lord`)

Drops: the Nether (until M4 removes it, then Ashfire, RLM-550), Ascent to Pinnacle, from flawed, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `apothic_attributes:life_steal` → `realmforge:lifesteal` 0.1 / 0.125 / 0.15 / 0.15 and `apothic_attributes:life_steal` → `realmforge:lifesteal` (of total) 5 % / 10 % / 20 % / 30 % |
| helmet | `minecraft:attack_damage` (of total) 25 % / 35 % / 45 % / 60 % and `minecraft:max_health` (of total) -30 % / -40 % / -50 % / -65 % |
| chestplate | `apothic_attributes:healing_received` (of base) 20 % / 30 % / 40 % / 50 % |
| bow | Bloody Arrow: an arrow costs health for more damage, every 22.5 s |
| shield | Leech Block: a block heals a share of the blocked damage, every 22.5 s |

#### Inferno (`apotheosis:the_nether/inferno`)

Drops: the Nether (until M4 removes it, then Ashfire, RLM-550), Ascent to Pinnacle, from flawed, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `apothic_attributes:detonation` I / II on the target (from flawless) |
| chestplate | `apothic_enchanting:berserkers_fury` +1 / +1 / +2 / +2 |
| pickaxe and tools | `minecraft:efficiency` +1 / +2 / +3 / +4 |
| helmet | `apothic_attributes:fire_damage` (of total) 50 % / 62.5 % / 75 % / 90 % |

#### Molten Breach (`apotheosis:the_nether/molten_breach`)

Drops: the Nether (until M4 removes it, then Ashfire, RLM-550), Ascent to Pinnacle, from flawed, unique.

| Category | Bonus (lowest purity → perfect) |
| --- | --- |
| melee weapon, trident | `apothic_attributes:prot_pierce` (**off**) 5.5 / 8 / 9.5 / 12 |
| bow | `apothic_attributes:armor_pierce` → `realmforge:armor_pierce` 4 / 5.5 / 8 / 10 |
| shield | `minecraft:entity_interaction_range` 0.5 / 0.75 / 1.25 / 1.75 |
| chestplate | `minecraft:attack_damage` (of base) 5 % / 10 % / 12.5 % / 17.5 % and `apothic_attributes:healing_received` (of total) -5 % / -15 % / -25 % / -35 % |
| leggings | `minecraft:scale` 0.075 / 0.125 / 0.15 / 0.25 |
| pickaxe and tools, shears | the tool also mines as a diamond, then a netherite axe, shovel, pickaxe, sword and hoe |

## Findings

1. **Wands were melee weapons and robes armor to Apotheosis.** `melee_weapon` takes any item whose main-hand modifiers
   give more than 1 attack damage, our wands (2 to 5) too, so a socketed wand took Ballast's attack damage and Samurai's
   crit chance; and every item with an armor slot is armor, so a robe took Guardian's armor and Tyrannical's toughness
   against the caster rule of no armor. **Fixed:** RLM-273 registered `realmforge:wand` before `melee_weapon`, and
   RLM-546 the four robe categories, filled through the data map
   ([architecture 13](../architecture.md#13-items-gear-and-loot)). Until RLM-547 and RLM-548 add their bonuses, no gem
   fits a wand or a robe at all.
2. **The two End gems never drop; the three Nether gems drop in the Nether until M4.** Endersurge and Mageslayer name
   `minecraft:the_end` in their `constraints.dimensions`, and the End is unreachable: vanilla strongholds are off from
   M1, so no End portal generates ([ADR 0044](../decisions/0044-third-party-mods-by-milestone.md)). Blood Lord, Inferno
   and Molten Breach name `minecraft:the_nether`, which stays as it is until M4 removes it
   ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md): "M1 does not change them"), so they drop there from
   the Ascent tier up. Their new homes are Ashfire and Unmade Isles (RLM-550).
3. **Damage reductions by damage type.** No gem gives a player a school resistance or Spell Resistance. Three bonuses
   test damage type tags instead:
   - **Verdant Ruin's chestplate** (magic damage reduction 12.5 to 20 %) and **Mageslayer's helmet** (it heals a share
     of magic damage taken) read the common magic tag (`#neoforge:is_magic`). The spell schools stay out of it (G4), so
     neither touches our spells; they still act on the vanilla damage types in that tag.
   - **Combatant's chestplate and leggings** (physical damage reduction 5 to 27.5 %) read the common physical tag,
     which `realmforge:physical` joined (RLM-549), so it now lowers Slam and a mob's physical spells too.
   - **Mageslayer's shield** gives vanilla's Resistance after a block, a reduction of most damage, spells included; it
     drops nowhere until RLM-550, which re-homes it.
4. **An overlapping extra bonus fails quietly.** An `extra_gem_bonuses` entry for a category the gem already has a
   bonus for is refused with a WARN in the log, and the gem keeps its own bonus. RLM-547's validation should therefore
   refuse an extra bonus for a category the snapshot's gem already covers.
5. **Royalty's all-stats** lists 31 attributes, Apothic Attributes' and vanilla's. Our remaps act on the list too:
   protection pierce and shred leave it, and the remapped stats count as ours. RLM-548 adds the caster stats.
6. **Max health on gems:** the Brawler's and the Warlord's keep theirs, as the gear design says
   ([gear design](../design/gear.md#gems), RLM-565).

## What data covers and what needs code

| Change | How | Risk |
| --- | --- | --- |
| A wand or robe bonus for a gem | data: `extra_gem_bonuses`, generated from a new content type (RLM-547) | an overlap is only a WARN (finding 4); validation refuses it |
| A changed or removed existing bonus | data: `apotheosis_gems/` replaces the gem's file from the snapshot | an Apotheosis update changes the snapshot; `rf ids apotheosis` and the reproducibility check catch it ([ADR 0063](../decisions/0063-apotheosis-data-is-fitted-from-a-pinned-snapshot.md)) |
| A gem's drops by realm | data: the gem's `constraints.dimensions` through `apotheosis_gems/` (RLM-550) | realm dimensions must exist first |
| Apothic Attributes' stats replaced by ours | data: `apotheosis_stat_remaps/` | a stat left where a remap cannot reach is reported by validation |
| New loot categories (wand, robes) | code: `ApotheosisCompat` registers them, the data map assigns items | done (RLM-273, RLM-546); registered only when Apotheosis is loaded |
| Physical and magic tags | data: damage type tags (RLM-549) | other mods reading the same tags (Apothic Attributes' melee damage bonuses) act on our physical spells too |
| Our stats in a bonus | data: an attribute bonus names `realmforge:` attributes | our attributes must be registered and syncable; tooltips read their names |

Nothing in the decision needs a mixin: the wand and robe bonuses, the swaps and the re-homing are data, and the
categories are code that already exists.

## Today's remaps

| Apothic Attributes stat | Ours |
| --- | --- |
| `apothic_attributes:armor_pierce` | `realmforge:armor_pierce` |
| `apothic_attributes:armor_shred` | `realmforge:armor_shred` |
| `apothic_attributes:cooldown_reduction` | `realmforge:cooldown_reduction` |
| `apothic_attributes:crit_chance` | `realmforge:crit_chance` |
| `apothic_attributes:crit_damage` | `realmforge:crit_damage` (× 1.5, `source_base`) |
| `apothic_attributes:dodge_chance` | `realmforge:dodge_chance` (Apothic Attributes' own dodge never fires, RLM-271) |
| `apothic_attributes:life_steal` | `realmforge:lifesteal` |
| `apothic_attributes:overheal` | `realmforge:overheal` |
| `apothic_attributes:prot_pierce` | off |
| `apothic_attributes:prot_shred` | off |

Every other Apothic Attributes stat in a gem stays Apothic Attributes' own: arrow damage, velocity and draw speed,
cold and fire damage, experience gained, healing received.
