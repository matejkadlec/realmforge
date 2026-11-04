# Wand enchantments

> Research and proposal for RLM-538 (2026-11-03): which vanilla and Apothic Enchanting enchantments fit a wand, how
> Looting can count for spell kills, new wand enchantments sized for scaling system C (RLM-534), how they reach a
> wand (tags, table, anvil, Apothic Enchanting's levels), and how they sit next to the wand gems (RLM-536,
> RLM-547) and the wand affixes (RLM-562). Matěj's wish (2026-10-30): wands need enchantments too; Looting on a wand
> counts for mobs killed by spells, Unbreaking and Mending are obvious; together with gems they help bring a realm
> guardian from the 60–80 casts modelled to about 40 in game (RLM-503). Nothing here is decided: the
> [questions](#questions-for-matěj) at the end are for his pick.

## Summary

- **Already on wands today:** Unbreaking, Mending, Apothic Enchanting's Life Mending and the Curse of Vanishing, through
  `#minecraft:enchantable/durability`, which the pipeline puts every wand into. Unbreaking also saves the one point of
  wear each cast costs (`SpellCaster#wearWand` goes through `hurtAndBreak`). Nothing else fits a wand yet.
- **Looting works for spell kills without code:** a mob's loot reads the killer from the damage source
  (`source.getEntity()`, the caster of our spells and of our damage over time) and Looting's level from the killer's
  main hand. Only Looting's `supported_items` must take wands. The same holds for Apothic Enchanting's Scavenger and
  Knowledge of the Ages, which read the killer's main-hand weapon on any kill.
- **Every "on hit" sword enchantment is melee only** (Sharpness, Smite, Bane of Arthropods, Fire Aspect, Knockback,
  Sweeping Edge): on a wand they would boost its weak melee poke and nothing else. They get spell twins instead.
- **Proposed new enchantments, all data** (vanilla's `minecraft:attributes` effect on the main hand, our stats as
  the attributes): **Potency** (flat Spell Power, Sharpness's twin), **Frost, Fire and Arcane Focus** (a school's
  power, Smite's twins), **Spell Breach** (Spell Penetration, Breach's twin; needs RLM-535) and **Swift Recovery**
  (Cooldown Reduction). **Quick Casting** (Cast Time Reduction, Quick Charge's twin) and **Thrift** (mana cost, a new
  stat) are options.
- **Numbers:** Potency copies Sharpness's own curve in Spell Power points (+3 at V, +5 at IX), which adds about 12 %
  (V) to 20 % (IX) to a level-18 Frostbolt, where Sharpness V to IX adds about 20 % to 33 % to a Warrior's melee
  hit but nothing to his spells. The percent enchantments stop at V and stay at or below a Flawed gem's value.
  Quick Casting is only an option: a level-18 Mage has 47 % Cast Time Reduction from Intellect and the wand (61 % with
  all 17 points in Intellect), and one Cast Time Reduction affix and gem on the wand reach the 0.5 s global
  cooldown's wall, where Quick Casting gives nothing; on a wand without them it gives the most of all of them.
  Cooldown Reduction reaches its cap late without Swift Recovery, so that one matters early and mid game.
- **Gems:** every wand gem bonus adds to the same stat as an enchantment, so nothing multiplies. The Earth gem, which
  RLM-536 left without a wand bonus "until wand enchantments", gets one: +1 to +4 levels of Potency, as it adds
  Sharpness levels to a sword.

## Sources and method

- **Vanilla 26.1.2:** the 43 enchantments and their tags read from the client jar
  (`minecraft_26.1.2_client.jar`: `data/minecraft/enchantment/`, `tags/enchantment/`, `tags/item/enchantable/`), and
  the loot and enchantment code in `build/mc-sources/` (`LivingEntity#dropFromLootTable`'s loot context,
  `EnchantmentHelper#getEnchantmentLevel`, `EnchantedCountIncreaseFunction`,
  `LootItemRandomChanceWithEnchantedBonusCondition`).
- **Apothic Enchanting 2.1.0 for 26.1.2** (the pack's jar, `pack/mods/apothic-enchanting.pw.toml`, SHA-512 starting
  `3eb146a2b22c18be`): its 20 enchantments, its data overrides and data map, and its classes read with `javap`
  (`EnchantmentInfo`, `ApothicEnchanting#computeDefaultMaxLevel`, `PowerFunction$DefaultMinPowerFunction`,
  `ApothEnchEvents#drops`). It has no sources jar.
- **Apotheosis 9.1.0:** gems and rarities from our snapshot `tools/id_universe/apotheosis-9.1.0.json`.
- **Ours:** `content/items/*_wand.yaml`, `content/enchantments/` (RLM-537's Magic Protection), the generated
  enchantable tags, architecture [9.1](../architecture.md#91-damage-pipeline),
  [11.1](../architecture.md#111-model) and [13](../architecture.md#13-items-gear-and-loot),
  [gear.md](../design/gear.md), and the tickets RLM-503, RLM-534, RLM-535, RLM-536 (its decision comment of
  2026-10-30), RLM-547 and RLM-562.

### Apothic Enchanting's highest levels

Apothic Enchanting raises an enchantment's highest level unless its data map `apothic_enchanting:enchantment_info`
names one. Its default (`computeDefaultMaxLevel`): an enchantment of vanilla level I stays at I; otherwise the
level climbs while the enchantment's minimum power stays below 200. Above vanilla's maximum the minimum power is
vanilla's linear `min_cost` plus the last level step (15 when that step is 0) × ⌊(level − vanilla maximum)^1.6⌋. That
gives, for the enchantments this document uses:

| Enchantment | Vanilla | With Apothic Enchanting |
| --- | --- | --- |
| Sharpness | V | IX |
| Smite, Bane of Arthropods | V | X |
| Looting, Unbreaking, Fortune | III | VIII |
| Breach | IV | VIII |
| Quick Charge, Knockback, Fire Aspect | III, II, II | V |
| Protection | IV | VIII |
| Mending, Infinity, Silk Touch | I | I |

The numbers match what the repository already relies on: Protection VIII (Magic Protection's twin, RLM-537),
Sharpness IX (Matěj's Astone run of 2026-10-29). The anvil combines up to the same level (`maxAnvilCombineLevel`
defaults to the highest level), and loot rolls up to vanilla's level unless the data map says more
(`maxLootLevel`).

## What a wand can take today

The pipeline puts every wand into one vanilla enchantable tag, `#minecraft:enchantable/durability`, and no other
(content README, gear's item tags). `#minecraft:enchantable/vanishing` contains that tag. So a wand takes:

| Enchantment | How it works on a wand |
| --- | --- |
| Unbreaking (III, VIII) | each point of wear is skipped with a chance of level / (level + 1) for a non-armor item: 89 % at VIII. Casting wears the wand by one per cast, through `hurtAndBreak`, so it counts for casts too |
| Mending (I, treasure) | repairs from experience orbs, as on any tool |
| Life Mending (Apothic Enchanting, a curse, exclusive with Mending) | consumes healing received to repair the item |
| Curse of Vanishing | the item vanishes on death |

The enchanting table already works on wands: each has vanilla's enchantability of its material (stone 5, diamond
10, iron 14, wood and netherite 15, copper 13, gold 22), so a Golden Wand enchants best and a Stone Wand worst, as
their tools do. Today the table can offer a wand Unbreaking and Life Mending, which Apothic Enchanting lists as
  non-treasure (Mending is a treasure).

## Catalogue: vanilla and Apothic Enchanting

Only what could mean something on a caster's main-hand weapon. Armor, tool, fishing, trident, mace, shear and hoe
enchantments are left out: they act on a slot or an action a wand never has (Protection, Efficiency, Lure,
Riptide, Density, Chromatic, Nature's Blessing and their kin).

| Enchantment (source) | Sword or bow behaviour | On a wand | Verdict |
| --- | --- | --- | --- |
| Looting (vanilla, III / VIII) | more drops and a higher equipment drop chance for kills while it is in the main hand | works for every kill while the wand is in the main hand, spells included ([below](#looting-and-other-kill-enchantments-on-spells)) | **add** (Matěj's wish) |
| Scavenger (Apothic, III, loot I) | a kill may roll the mob's loot table again, 2.5 % a level | the same, on any kill by a player holding the wand | **add** with Looting |
| Knowledge of the Ages (Apothic, III, loot I) | a kill's drops turn into experience, 25 a level | the same | option: it competes with Looting, as on swords |
| Unbreaking, Mending, Life Mending, Curse of Vanishing | durability | already on wands | keep |
| Sharpness (vanilla, V / IX) | +1 damage, +0.5 a level above I, on melee hits | the wand's own melee hit only: our spells never ask `EnchantmentHelper#modifyDamage` | no; **Potency** is its twin |
| Smite, Bane of Arthropods (V / X) | +2.5 a level against undead or arthropods, melee only | as Sharpness | no; the **Focus** enchantments are their twins |
| Fire Aspect (II / V) | sets the target on fire, only for a direct hit | a spell's damage comes from a projectile or an area, never direct | no; Fiery affixes and fire spells carry burning |
| Knockback, Sweeping Edge, Punch | knockback, a sweep, an arrow's knockback | melee or arrows only | no |
| Breach (vanilla, mace only, IV / VIII) | the target's armor counts 15 % less a level | armor stops no spell | no; **Spell Breach** is its twin |
| Quick Charge (crossbow, III / V) | 0.25 s shorter reload a level | not a cast | no; **Quick Casting** is its twin |
| Power, Flame, Infinity, Multishot, Piercing (bows, crossbows) | arrows | wands shoot no arrows; Echoing (RLM-562) is the spell's "multishot" | no |
| Berserker's Fury, Icy Thorns, Rebounding, Reflective Defenses, Shield Bash, Crescendo of Bolts, Endless Quiver (Apothic) | chest, legs, shield, crossbow, bow | not a wand's slot or action | no |

Apothic Enchanting also overrides two vanilla files (Sharpness and Protection leave `#minecraft:exclusive_set/damage`
and `/armor`, so a sword combines Sharpness with Smite or Bane), and adds shears to three tool tags. Neither touches
a wand.

### Looting and other kill enchantments on spells

- **How a kill's loot finds Looting:** `LivingEntity#dropFromLootTable` builds the loot context with
  `ATTACKING_ENTITY = source.getEntity()`. Looting's loot parts (`enchanted_count_increase`,
  `random_chance_with_enchanted_bonus`, including our own `dropped_by` drops, architecture 13) take that entity's
  Looting level through `EnchantmentHelper#getEnchantmentLevel`, which looks at the enchantment's slots: Looting's is
  the main hand. Its third part, the equipment drop chance, reads the attacker the same way.
- **Our spells name the caster:** `SchoolDamageSource` passes the caster as the causing entity, for a projectile, an
  area, a channel's tick and the ticks of our status effects (C3c remembers who applied them; a tick names the caster
  while it is alive and in the level). So a spell kill with
  the wand in the main hand gets Looting with no code, as long as the wand may carry Looting.
- **Scavenger and Knowledge of the Ages** (`ApothEnchEvents#drops`) read `source.getEntity()`, check it is a player,
  and run the enchantments on `Player#getWeaponItem()`, the main hand. Spell kills count for them too.
- **What it takes:** Looting's `supported_items` is `#minecraft:enchantable/melee_weapon`, and Scavenger's and
  Knowledge's `#minecraft:enchantable/sharp_weapon`. Putting wands into those tags would also let Sharpness, Smite,
  Bane of Arthropods, Knockback and Fire Aspect onto them through the anvil, which only touch the melee poke and
  clutter the wand. So the proposal widens the three enchantments instead: the pipeline writes their files with
  `supported_items` as NeoForge's `neoforge:or` holder set of the old tag and a new item tag
  `#realmforge:enchantable/wand` (Apothic Enchanting uses `neoforge:or` itself, for Rebounding). Looting's file is a
  vanilla override like Apothic Enchanting's two; Scavenger's and Knowledge's override Apothic Enchanting's, so the
  core mod must load after it (`neoforge.mods.toml` ordering, as for Sword Blocking Mechanics). No wand item class
  is needed (architecture 13 keeps gear as plain items, and `IItemExtension#supportsEnchantment` would need one).
- **A side effect:** Looting counts for every kill while the wand is in the main hand, including a melee poke with
  it, exactly as a sword's counts for a kill with a thrown trident. That is vanilla's rule and harmless.

## Proposed wand enchantments

All of them are vanilla data-driven enchantments with the `minecraft:attributes` effect: an attribute modifier on
the main hand while the wand is there, as Swift Sneak gives sneaking speed on the legs. Our stats are attributes, so
the spell engine, the damage pipeline and the character sheet see them with no new code, exactly like the wand's own
Spell Power. Table numbers (weight, costs, anvil cost) copy the twin's, so the table and the anvil treat them as
players expect. Names are working names for Matěj.

| Enchantment | Stat (main hand) | Per level | Levels: vanilla / with Apothic | Twin (weight, costs) | Exclusive set |
| --- | --- | --- | --- | --- | --- |
| **Potency** | Spell Power, flat (`add_value`) | Sharpness's curve: 1, then +0.5 a level (V: +3, IX: +5) | V / IX | Sharpness (10; min 1 + 11 a level) | wand power |
| **Frost Focus**, **Fire Focus**, **Arcane Focus** | that school's power | +3 % (V: 15 %, IX: 27 %) | V / IX | Smite (5; min 5 + 8) | wand power |
| **Spell Breach** | Spell Penetration (RLM-535) | +4 % (V: 20 %) | V / V | Breach (2; min 15 + 9) | none |
| *option:* **Quick Casting** | Cast Time Reduction | +2 % (V: 10 %) | V / V | Quick Charge (5; min 12 + 20) | none |
| **Swift Recovery** | Cooldown Reduction | +2 % (V: 10 %) | V / V | Quick Charge (5; min 12 + 20) | none |
| *option:* **Thrift** | mana cost reduction, a new stat | +3 % (V: 15 %) | V / V | Unbreaking (5; min 5 + 8) | none |

- **Wand power set** (`#realmforge:exclusive_set/wand_power`): Potency and the three Focuses exclude each other,
  vanilla's original Sharpness-or-Smite choice: a wand is either for every school or strong in one. Apothic
  Enchanting lets a sword combine Sharpness with Smite; doing that here would give a single-school Mage both, about
  +27 % at V on every hit, which is what made Astone too easy before (RLM-503, 2026-11-01). The other option is
  [question 3](#questions-for-matěj).
- **Why the percent ones stop at V:** they add to stats with hard caps: Cast Time Reduction and Cooldown Reduction
  at 75 % (and the 0.5 s global cooldown makes cast time past 66.7 % worthless), Spell Penetration at a proposed 50 %.
  Their other sources reach the caps on their own late ([fit](#fit-with-the-wand-gems-and-affixes)): the stats
  (Intellect, Dexterity and their 25-point milestones), the Netherite set's 20 % Cooldown Reduction, the wand, and
  Legendary affixes and Perfect gems. Potency and the Focuses extend like Sharpness and Smite, because a flat Spell
  Power bonus shrinks as Spell Power grows with level.
- **Quick Casting is an option, not a recommendation:** Intellect, the caster's main stat, gives 2 % Cast Time
  Reduction a point: 47 % with the wand at level 18 in the model, 61 % with all 17 points in Intellect
  ([numbers](#numbers-against-a-level-18-frostbolt)). Intellect alone stays below the wall at 66.7 %, but a Cast Time
  Reduction affix (Hastened) and gem (Brawler's) on the wand reach it, and Quick Casting then gives nothing. On a wand
  without them it is the strongest of these enchantments (+23 % casts a second at level 18), which would put the
  Mage well ahead of the Warrior.
- **Thrift** is the one that needs code: there is no mana cost stat, so a `realmforge:mana_cost_reduction` stat (in
  `content/stats/`, applied where `SpellCaster` pays the cost, including a channel's each second) comes first. Mana
  regeneration is not proposed: the Flowing affix and Slipstream's wand gem already give it.
- **Where they come from:** all of them in `#minecraft:non_treasure`, so the enchanting table, random loot and mobs'
  gear can roll them, with `primary_items` the wand tag, so a sword's table never offers them. The library stores
  them like any enchantment, and books go through the anvil.
- **Sockets and rarity are untouched:** these are vanilla enchantments, so Apotheosis's reforging keeps them and its
  affixes ignore them.

### Numbers against a level-18 Frostbolt

RLM-503's level-18 model: Spell Power about 19 with the Netherite set and wand, Frostbolt at rank 5 = 12 + 200 % of
Spell Power ≈ 50 a cast, 1.5 s cast. A Human Mage starts with 6 Intellect (class 5, race 1) and 2 Dexterity and
gets a stat point a level from level 2, 17 by level 18 (architecture 10.2). The Spell Power of 19 is 14 from gear and
starting Intellect (the wand 5, the robes 6, Intellect 3) plus 10 points in Intellect at 0.5 each, so the model's
Mage has 16 Intellect: 47 % Cast Time Reduction (Intellect 32 %, the wand 15 %) before any affix or gem. Cooldown
Reduction is 38 % (the wand 15 %, the Netherite set 5 % a piece, Dexterity 1.5 % a point for a caster) with no
points in Dexterity. The Warrior's comparison: Attack
Damage about 15 with a netherite sword; Sharpness adds to his melee hits only, never to Slam, Rend or Overpower,
which scale with the Attack Damage stat.

| Source | Adds to a 50 Frostbolt | The sword's comparison |
| --- | --- | --- |
| Potency V (+3 Spell Power) | +6 (+12 %) | Sharpness V: +3 a melee hit (+20 %) |
| Potency IX (+5) | +10 (+20 %) | Sharpness IX: +5 (+33 %) |
| Frost Focus V (+15 % frost power) | +7.5 (+15 %), Frost spells only | Smite V: +12.5 against undead only |
| Frost Focus IX (+27 %) | +13.5 (+27 %) | Smite X: +25 against undead |
| Spell Breach V (20 %) | +20 % against a target with 50 % resistance, nothing against none | Breach (mace only) |
| Quick Casting V (10 %) | on a wand without Cast Time Reduction rolls 47 → 57 %: a Frostbolt takes 0.645 s instead of 0.795 s, +23 % casts a second; with Astone's expected Rare affix (Hastened 6–10 %) and Flawed gem (Brawler's 10 %) the Mage is at 63–67 %, the wall, and it adds at most 4 points | none |
| Swift Recovery V (10 %) | 38 → 48 %: Ice Lance, Frost Nova and Cone of Cold are ready 19 % more often (Ice Lance has had no cooldown since RLM-572) | none |

- At Astone's expected gear (gear.md: "level V for every enchantment") a wand with Potency V and Swift Recovery V
  gives the Mage +12 % a Frostbolt and the cooldown spells 19 % more often; a sword with Sharpness V gives the
  Warrior +20 % on melee hits, which are most of his damage. That is about par. Quick Casting on top adds nothing at
  the wall, but up to +23 % casts a second on a wand that rolled no Cast Time Reduction, which would put the Mage
  well ahead: the reason it is only an option ([question 2](#questions-for-matěj)).
- At level 60 (Spell Power about 60) Potency IX's +5 is a tenth of Frostbolt's Spell Power share, and the percent
  enchantments have been overtaken by gems and affixes.
- Toward RLM-503's "about 40 casts": Potency V or a Focus V takes 12–15 % off a guardian's cast count on its own
  (60 → about 52); gems (Ballast and Combatant, Flawed: about +7 Spell Power on this wand, +14 a Frostbolt),
  Sorcerous and the crits do the rest. Astone is now balanced at 1,500 health against the current spells (RLM-503,
  2026-11-03), so whatever the enchantments add should be checked in the same playtest as RLM-547's gems.

## How they reach the game

What the implementation needs, all through `content/` and `rf gen` (rule 1):

1. **A wand item tag.** `#realmforge:enchantable/wand`, written from every item whose `gear.kind` is `wand`, next to
   `#minecraft:enchantable/durability` (content README's gear tags).
2. **The enchantment content type grows an `attributes` effect** beside RLM-537's `protection`: a stat, an
   operation, an amount for level I and per level above, the slot `mainhand`. It writes vanilla's
   `minecraft:attributes` effect (`id`, `attribute`, `amount` as a `minecraft:linear` level-based value,
   `operation`), `supported_items` and `primary_items` (the wand tag), the twin's weight and costs, the vanilla
   enchantment tags it joins, `vanilla_max_level` and `max_level` (Apothic Enchanting's data map, as Magic
   Protection's), an `exclusive_set` tag, and the name and `.desc` lang lines (Enchantment Descriptions, in the
   pack, shows them on the item).
3. **Widened vanilla and Apothic Enchanting enchantments:** a small content type (or a field) that rewrites an
   existing enchantment's `supported_items` as `neoforge:or` with the wand tag, for Looting, Scavenger and (if
   picked) Knowledge of the Ages. The generator copies the rest of the file from the vanilla snapshot or Apothic
   Enchanting's jar data, so a version update regenerates it. The core mod loads after Apothic Enchanting.
4. **The Earth gem's wand bonus** goes into RLM-547's extra gem bonuses: `apotheosis:enchantment`,
   `realmforge:potency`, mode `existing`, Flawed / Normal / Flawless / Perfect +1 / +2 / +3 / +4, as its sword
   bonus is for Sharpness. `existing` only raises an enchantment the wand already has, so a wand without Potency
   gets nothing from it.
5. **Tests:** GameTests that Potency V gives +3 Spell Power in the main hand and nothing in the off hand, that a
   Focus refuses Potency at the anvil, that a spell kill with a Looting III wand rolls Looting's drops (a mob with
   a `dropped_by` item), that the table offers a wand our enchantments and a sword none of them, and that Apothic
   Enchanting's levels are those of the table above.

Spell Breach waits for RLM-535's stat, and Thrift for its own.

## Fit with the wand gems and affixes

Every wand bonus from gems (RLM-547, decided on RLM-536), affixes (RLM-562) and enchantments adds to the same
attribute, and the attribute's total meets one cap, together with the character's own sources: Intellect (+0.5
Spell Power and +2 % Cast Time Reduction a point, +25 % Cooldown Reduction at 25 points), Dexterity (+1.5 % Cooldown
Reduction a point for a caster, +20 % at 25 points) and the robes (the Netherite set's 5 % Cooldown Reduction a
piece). Nothing multiplies another source except Spell Power's percent modifiers, which multiply the flat total
(Combatant). What one wand can stack, at Rare with two Flawed gems (level 18) and at Legendary with two Perfect gems
(late game), next to the character's base (a Human Mage's 6 Intellect and 2 Dexterity, the Netherite set):

| Stat | Character base | Wand | Enchantment (V) | Affix (Rare / Legendary) | Gem (Flawed / Perfect) | Cap |
| --- | --- | --- | --- | --- | --- | --- |
| Spell Power, flat | Intellect 3, the robes 6 | 5 | Potency +3 (IX: +5); Earth +1 / +4 levels | Sorcerous 3–5 / 5–8 | Ballast +3.5 / +10 | none |
| Spell Power, % of flat | – | – | – | – | Combatant +15 / +30 % | none |
| A school's power | – | – | Focus +15 % (IX: 27 %) | Fiery, Frozen, Arcane-Touched 6–10 / 12–20 % | Lunar, Solar, Tyrannical +15 / +40 % | 1000 % |
| Cast Time Reduction | Intellect 12 %, 32 % with 10 points | 15 % | (option) Quick Casting 10 % | Hastened 6–10 / 10–18 % | Brawler's 10 / 20 % | 75 % (wall at 66.7 %) |
| Cooldown Reduction | Netherite set 20 %, Dexterity 3 % | 15 % | Swift Recovery 10 % | Timeless 6–10 / 10–18 % | Lightning 10 / 20 % | 75 % |
| Spell Penetration | – | – | Spell Breach 20 % | Spellbreaking 6–10 / 10–15 % | Breach 15 / 30 %, Molten Breach 15 / 30 %, Verdant Ruin 20 / 50 % (with −5 / −20 % Magic Shield) | 50 % proposed (RLM-535) |

- **No enchantment exceeds a Flawed gem** of the same stat, and none is a new kind of bonus: the enchantment is the
  reliable, chosen part; gems and affixes are the rolled part on top.
- **Cast Time Reduction** at level 18 with 10 points in Intellect: 32 + wand 15 + Rare affix 6–10 + Flawed gem 10 =
  63–67 %, already at the wall at 66.7 %. Late, the starting 12 % alone + 15 + 18 + 20 = 65 %. Quick Casting only
  counts on a wand that rolled no Cast Time Reduction, or for a Mage who skips Intellect.
- **Cooldown Reduction** at Legendary: the Netherite set 20 % + Dexterity 3 % + wand 15 + affix 18 + gem 20 = 76 %,
  over the 75 % cap without Swift Recovery, before the 25-point milestones (+25 % for Intellect, +20 % for
  Dexterity). At level 18 (38 % plus a Rare affix 6–10 % and a Flawed gem 10 %, 54–58 %) it still counts.
- So Swift Recovery is an early and mid game enchantment that becomes a dead slot on a late wand, and Quick Casting
  is one already at level 18 for an Intellect Mage, while Potency or a Focus stays useful; that is why neither
  extends past V.
- **Spell Penetration** already passes 50 % with gems and Spellbreaking alone late (RLM-562 says so). Spell Breach
  matters early, when one source is all a player has; whether the cap stays 50 % is Matěj's call on RLM-535.
- **A school's power** is the one that grows large: a Legendary Frost wand with Frozen 20 %, a Perfect Lunar gem
  40 % and Frost Focus IX 27 % reaches 87 %, nearly doubling Frost hits, and each further Lunar gem adds 40 %. That
  is the gems' own size (RLM-547); the Focus adds under a third of it. If that is too much, the gems are the lever,
  not the enchantment.
- **Guardian, Royalty, Blood Lord, Inferno and Mageslayer** keep no wand bonus (RLM-536's decision); only Earth's
  changes, as above.

## Questions for Matěj

Each has a recommendation; one answer can take all of them ("as recommended"). His pick is the decision ticket
RLM-606.

1. **Shared enchantments:** Looting and Apothic Enchanting's Scavenger on wands, counting for spell kills
   (recommended), plus Knowledge of the Ages (option, as on swords)? Unbreaking, Mending, Life Mending and Vanishing
   already apply.
2. **The new enchantments:** Potency, the three Focuses, Spell Breach and Swift Recovery (recommended); Quick
   Casting (option: Intellect gives a level-18 Mage 47–61 % with the wand, a Cast Time Reduction affix and gem then
   reach the wall at 66.7 %, where it gives nothing, and below the wall it puts the Mage well ahead of the Warrior);
   Thrift with its mana cost stat (option, more code)? Names are working names.
3. **Exclusivity:** Potency and the Focuses exclude each other, vanilla's Sharpness-or-Smite rule (recommended), or
   they combine, as Apothic Enchanting lets Sharpness combine with Smite?
4. **Numbers:** Potency on Sharpness's curve (+3 at V, +5 at IX) (recommended), or +1 a level (+5 at V, +9 at IX,
   the same share of a Frostbolt as Sharpness's of a melee hit); Focus +3 % a level to IX; the percent ones +2 % or
   +4 % a level and stopping at V? If Quick Casting is picked, +1 % a level keeps it closer to par.
5. **The Earth gem on wands:** +1 to +4 Potency levels, like its Sharpness levels on swords (recommended)?

### Implementation tickets after the pick

- **Wand enchantments in the pipeline:** the wand tag, the `attributes` effect, the picked enchantments, Apothic
  Enchanting's levels, GameTests (after RLM-535 for Spell Breach).
- **Kill enchantments on wands:** Looting, Scavenger (and Knowledge) widened to the wand tag, the load order, a
  GameTest of a spell kill's Looting.
- **Thrift** (if picked): the mana cost stat and the enchantment.
- **Earth's wand bonus:** a line in RLM-547 (it is not built yet), not a ticket of its own.

All of them change gameplay, so they end in Matěj's playtest.
