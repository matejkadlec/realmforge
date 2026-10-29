# Immersive Armors: catalogue for Matěj's pick

> Research of 2026-10-29 (RLM-313): every set and piece of Immersive Armors with its stats, effects and recipe, a
> proposed minimum realm and level, early unique quest reward candidates, how it meets our stats, and how config or
> data switch a set off or move its source. Facts come from the NeoForge 26.1.2 jar (Immersive Armors
> 1.8.2+26.1.2, read with `javap -c -p -constants` in a scratch directory; the jar is not in the repository), its
> `neoforge.mods.toml`, its data files and its `en_us` lang. Vanilla numbers come from the 26.1.2 sources
> (`ArmorMaterials`, `ArmorType`, `Mob`). Nothing ran in a game: lines marked *verify* need a dev run. Mechanics only:
> the mod is **GPL-3.0-only**, so none of its code or assets goes into Realmforge (rule 8); the pack would only
> reference its jar.

**In short.** Immersive Armors adds **10 sets of 4 pieces (40 items)**: Wooden, Bone, Wither, Warrior, Heavy, Robe,
Slime, Divine, Prismarine and Steampunk. Their armor is vanilla's range: from 7 (leather's) to 20 (diamond's and
netherite's). Only two sets have toughness: Warrior 4 and **Heavy 16**, which is more than netherite's 12 and close to
Matěj's ceiling of 18, from an iron recipe. Nothing reaches his ceiling of 30 armor. Their value is their **effects**:
arrow blocking, fall immunity and a double jump, a hit negated once a minute, up to +80 % damage when hurt, knockback
immunity, fire and magic resistance. That matches "better armor gets effects instead". But the effects are hard-coded
in Java, act in the damage events outside our pipeline's rules and caps, and cannot be retuned, only switched off all
together. Two recipes need Nether materials (Robe: blaze powder; Wither: nether bricks), which ADR 0034's merchants
supply. The Divine set needs the Robe set as an ingredient. The mod also puts its armor into vanilla chest loot and on
vanilla mobs (Divine pieces on Overworld zombies included). **Proposal:** keep nine sets, with Robe off, Heavy's
toughness and knockback cut to diamond's and netherite's, Warrior and Divine as unique quest rewards instead of crafts,
and the mod's chest loot and mob armor switched off ([Proposal](#proposal-for-matěj)).

## Facts

| What | Value |
| --- | --- |
| Project | Immersive Armors by Luke100000 (Conczin), source [Luke100000/ImmersiveArmors](https://github.com/Luke100000/ImmersiveArmors) (the jar's `issueTrackerURL`), Modrinth and CurseForge |
| Build read | 1.8.2+26.1.2 for NeoForge, Modrinth release of 2026-07-28; 1,290,727 bytes, SHA-1 `a8a9cde8014ec7da2b7fe97694e54a912d617dd0` (computed here; not compared with Modrinth's listing in this session) |
| Requires | Minecraft `[26.1.2,26.2)`, NeoForge `[26.1.2.78,)` (the pack pins 26.1.2.116); no library |
| Sides | both: items, two payloads (`settings` server to client, `armor_command` client to server), two common mixins (`MobMixin`, `MixinNearestAttackableTargetGoal`) and seven client mixins (armor rendering, HUD) |
| Licence | **GPL-3.0-only** (`license` in `neoforge.mods.toml`, `LICENSE_Immersive Armors` in the jar). The jar names no other licence for its assets |
| Content | 40 armor items; 48 recipes (40 shaped, 8 dye recipes for Robe and Divine); 10 recipe advancements; 1 global loot modifier; item tags (its own `armor`, `axes`, `repairs_<set>_armor`, and vanilla's `head_armor`, `chest_armor`, `leg_armor`, `foot_armor`, `trimmable_armor`); damage type tag `immersive_armors:is_magic`; creative tab "Immersive Armors"; data components `set_count`, `last_divine`, `thruster_state` |
| Rendering | no equipment asset files: its own renderer draws the pieces (mixins on the humanoid armor layer) |
| Config | `config/immersive_armors.json` (Gson, `version` 1). The server reads it and sends it to every client at login (`SettingsMessage`), so the server's file wins. A file whose `version` is not 1 is replaced by the defaults |

**Config options** (`immersive_armors.config.Config`, defaults from its field initialisers and annotations):

| Key | Default | What it does |
| --- | --- | --- |
| `enableEffects` | `true` | all effects of all sets on or off (`ExtendedArmorMaterial#getEffects` returns an empty list when off); not the anti-skeleton rule |
| `lootChance` | `0.025` | multiplies every per-table chance of the chest loot (below) |
| `mobEntityUseImmersiveArmorChance` | `0.5` | chance that a vanilla mob's rolled armor piece is replaced by one of this mod (below) |
| `overwriteValues` | `{}` | per-set overrides, keys `<set>:<property>` with `helmetProtection`, `chestplateProtection`, `leggingsProtection`, `bootsProtection`, `weight`, `toughness`, `enchantability`; **appears to do nothing in this build** ([Levers](#levers)) |
| `hideSecondLayerUnderArmor` | `true` | client: hides the skin's outer layer under the armor |
| `enableEnchantmentGlint` | `true` | client: enchantment glint on the pieces |
| `hudClockX`, `hudClockXOffhand`, `hudClockY`, `hudCompassX`, `hudCompassXOffhand`, `hudCompassY` | −111, −140, −20, 95, 124, −20 | client: where the Steampunk Chestplate's clock and compass sit on the HUD |
| `_documentation` | a link | none |

## How it works

### Stats per piece

Each piece is a plain `Item` (`ExtendedArmorItem`) with vanilla's `EQUIPPABLE` component and attribute modifiers
built in `ExtendedArmorItem#createAttributes`. Every piece adds, in its own slot:

- `minecraft:armor`: its protection value (helmet, chestplate, leggings, boots, in that order in
  `ExtendedArmorMaterial#protectionAmount`);
- `minecraft:armor_toughness`: the **set's toughness on every piece**, as vanilla does (Heavy's 4 makes 16);
- `minecraft:knockback_resistance`: the set's value on every piece, when above 0;
- `minecraft:movement_speed`: minus the set's "weight", `add_multiplied_total`, when not 0 (a negative weight makes the
  wearer faster);
- `minecraft:water_movement_efficiency`: the set's value, `add_value`, when not 0.

Durability is vanilla's: the slot's unit durability (helmet 11, chestplate 16, leggings 15, boots 13) times the set's
multiplier. Enchantability goes on the item only when above 0, so a set with 0 cannot be enchanted at a table.

### Effects

An effect is an `ArmorEffect` on the set's material, coded in Java with its numbers in `Items`' static initialiser.
They act in three ways:

- **Per worn piece, multiplicative:** every worn piece applies the effect again. Fire, magic and explosion resistance,
  the Slime's bounce and the Wither set's wither reduction.
- **Once per set, scaled by the pieces worn** (only the first worn piece of the set acts, `isPrimaryArmor`): arrow
  blocking, berserk, weapon efficiency, setting attackers on fire, withering attackers, spikes.
- **Full set only:** Divine's deflection (all four pieces) and the anti-skeleton rule (four pieces of Bone or Wither,
  mixed).

The damage effects run in one listener on `LivingDamageEvent.Pre` (`NeoForgeBusEvents#onLivingDamageEvent`, calling
`DamageUtils#adjustDamage`): the defender's pieces first, then the attacker's. In our pipeline that is step 5, after
dodge, blocking, crits, resistances and armor, before absorption hearts and the magic shield
([architecture 9.1](../architecture.md#91-damage-pipeline)).

| Effect (class) | Sets | What it does, with the jar's numbers |
| --- | --- | --- |
| Arrow blocking (`ArrowBlockArmorEffect`) | Wooden 0.15 | a hit in `#minecraft:is_projectile` is cancelled to 0 with a shield sound, chance 0.15 × pieces worn (60 % with four) |
| Explosion resistance (`ExplosionProtectionArmorEffect`) | Slime 0.2, Wooden 0.1, Steampunk 0.1 | damage in `#minecraft:is_explosion` × (1 − value) per piece: Slime 59 %, Wooden and Steampunk 34 % less with four |
| Fire resistance (`FireResistanceArmorEffect`) | Robe 0.25 | damage in `#minecraft:is_fire` (fire, lava, fireballs) × 0.75 per piece: 68 % less with four |
| Magic resistance (`MagicProtectionArmorEffect`) | Robe 0.2 | damage in `#immersive_armors:is_magic` (`minecraft:magic`, `indirect_magic`, `thorns`, `dragon_breath`, optional `#neoforge:is_magic`) × 0.8 per piece: 59 % less with four. Our school damage types are not in it |
| Fire on attackers (`FireInflictingArmorEffect`) | Robe 20 | the hit's causing entity, ranged attackers included, burns for 20 ticks × pieces more (4 s with four), unless fire-immune |
| Withering attackers (`WitherArmorEffect`) | Wither 20 | the causing entity, ranged included, gets Wither II for 20 ticks × pieces (4 s with four), unless fire-immune |
| Wither reduction (same class) | Wither 0.25 | damage of type `minecraft:wither` × min(1, 1 − 0.25 × pieces), once per worn piece: 25 % less with one piece, 75 % with two, 98 % with three, **immune with four** |
| Berserk (`BerserkArmorEffect`) | Warrior 0.2 | the wearer's outgoing hits × (1 + 0.2 × pieces × (1 − health / max health)): **up to +80 %** with four near death, +40 % at half health |
| Weapon efficiency (`WeaponEfficiency`) | Warrior 0.05, axes | the wearer's direct hits × (1 + 0.05 × pieces) while an item in `#immersive_armors:axes` (= `#minecraft:axes`) is in either hand: +20 % with four |
| Bounce (`BouncingArmorEffect`) | Slime 0.25 | a direct hit's attacker gets 0.25 per worn piece added to its velocity, away from the wearer (1.0 with four). It sets the velocity directly, so **knockback resistance does not stop it** |
| Spikes (`SpikesArmorEffect`) | Prismarine 1 | a direct hit's attacker takes 1 × pieces vanilla thorns damage (4 with four) |
| Deflection (`DivineArmorEffect`) | Divine, 1200 ticks | with all four pieces, **any hit of any kind is cancelled to 0** (anvil sound), then the set recharges for 60 s |
| Steam tech (`SteamTechArmorEffect`) | Steampunk | per slot: helmet, while sneaking, makes every monster within 16 blocks glow for 5 ticks each second ("radar"); chestplate shows a clock and a compass on the HUD; leggings give one double jump (upward speed 0.6) per ground contact; boots keep the fall distance at 1 block or less (no fall damage) and cap the fall speed at 0.75 blocks per tick |

Two rules are not effects, so `enableEffects` does not switch them off:

- **Anti-skeleton** (`MixinNearestAttackableTargetGoal`): a skeleton (`AbstractSkeleton`: skeleton, stray, bogged,
  wither skeleton) drops a player target who wears four Bone or Wither pieces.
- **Mob armor** (`MobMixin` on `Mob#getEquipmentForSlot`): when a vanilla mob rolls an armor piece, it gets one of this
  mod's instead with the chance `mobEntityUseImmersiveArmorChance` (0.5), slot by slot. Tier 0 gets Wooden, 1 Warrior,
  2 Heavy, 3 Divine and 4 Prismarine. In 26.1.2 vanilla's tiers are leather, copper, gold, chainmail, iron, diamond, so
  the mapping looks one step off since copper armor arrived: copper-tier mobs wear Warrior, gold-tier Heavy,
  chainmail-tier **Divine**, iron-tier Prismarine, and diamond-tier mobs keep diamond. Those pieces drop like any mob
  armor (vanilla's 8.5 % per slot, more with Looting).

### Chest loot

One global loot modifier (`data/immersive_armors/loot_modifiers/armor_modifier_serializer.json`, type
`immersive_armors:armor_modifier_serializer`) adds each piece to vanilla chest tables, each piece rolled on its own with
the table's chance × `lootChance` (0.025): 2.5 % per piece at chance 1.

| Set | Tables (chance) |
| --- | --- |
| Wooden | village fletcher (0.25) |
| Bone | village weaponsmith (1), jungle temple (1) |
| Wither | jungle temple (0.5), ruined portal (1), bastion other (1) |
| Warrior | village armorer (1), shipwreck supply (1) |
| Heavy | village armorer (1), stronghold crossing (1) |
| Robe | village shepherd (0.25), woodland mansion (1), igloo (1) |
| Slime | simple dungeon (0.25) |
| Divine | village temple (0.4), bastion treasure (0.25), woodland mansion (0.25), desert pyramid (1) |
| Prismarine | underwater ruin big (1), underwater ruin small (0.5) |
| Steampunk | village toolsmith (0.25), shipwreck treasure (1) |

Bastions are Nether structures and strongholds are off from M1 ([ADR 0044](../decisions/0044-third-party-mods-by-milestone.md)),
so those rows never roll. Ruined portals, villages, temples, pyramids, mansions, igloos, shipwrecks and ocean ruins are
Overworld structures.

## Catalogue

Vanilla neighbours for comparison (26.1.2 `ArmorMaterials`; helmet / chestplate / leggings / boots):

| Vanilla | Armor | Total | Toughness (set) | Knockback res. (set) | Durability multiplier | Enchantability |
| --- | --- | --- | --- | --- | --- | --- |
| Leather | 1 / 3 / 2 / 1 | 7 | 0 | 0 | 5 | 15 |
| Copper | 2 / 4 / 3 / 1 | 10 | 0 | 0 | 11 | 8 |
| Gold | 2 / 5 / 3 / 1 | 11 | 0 | 0 | 7 | 25 |
| Chainmail | 2 / 5 / 4 / 1 | 12 | 0 | 0 | 15 | 12 |
| Iron | 2 / 6 / 5 / 2 | 15 | 0 | 0 | 15 | 9 |
| Diamond | 3 / 8 / 6 / 3 | 20 | 8 | 0 | 33 | 10 |
| Netherite | 3 / 8 / 6 / 3 | 20 | 12 | 0.4 | 37 | 15 |
| Matěj's ceiling (RLM-198, RLM-223) | | 30 | 18 | | | |

### The ten sets at a glance

Totals for a full set; durability per piece. All numbers from `immersive_armors.Items` (static initialiser) and
`ExtendedArmorItem`.

| Set | Armor (H / C / L / B) | Total | Toughness | Knockback res. | Speed | Durability (H / C / L / B) | Ench. | Vanilla neighbour |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Wooden | 1 / 3 / 2 / 1 | 7 | 0 | 0 | | 88 / 128 / 120 / 104 | 4 | leather |
| Bone | 1 / 3 / 2 / 1 | 7 | 0 | 0 | +2 % per piece (+8.2 %) | 88 / 128 / 120 / 104 | 15 | leather |
| Robe | 2 / 3 / 2 / 1 | 8 | 0 | 0 | | 154 / 224 / 210 / 182 | **50** | leather |
| Wither | 2 / 4 / 3 / 2 | 11 | 0 | 0 | +1 % per piece (+4.1 %) | 132 / 192 / 180 / 156 | 0 (no table enchanting) | gold |
| Slime | 3 / 5 / 4 / 2 | 14 | 0 | 0.25 per piece (**1.0**) | | 220 / 320 / 300 / 260 | 10 | chainmail to iron |
| Steampunk | 3 / 6 / 3 / 2 | 14 | 0 | 0 | | 110 / 160 / 150 / 130 | 4 | chainmail to iron |
| Warrior | 2 / 5 / 6 / 2 | 15 | 1 per piece (4) | 0 | | 165 / 240 / 225 / 195 | 5 | iron |
| Heavy | 4 / 6 / 5 / 3 | 18 | 4 per piece (**16**) | 0.5 per piece (2.0, capped at **1.0**) | −5 % per piece (−18.5 %) | 220 / 320 / 300 / 260 | 6 | between diamond and netherite |
| Divine | 3 / 7 / 5 / 3 | 18 | 0 | 0 | | 198 / 288 / 270 / 234 | 30 | diamond without toughness |
| Prismarine | 3 / 8 / 6 / 3 | 20 | 0 | 0 | −2 % per piece (−7.8 %) | 198 / 288 / 270 / 234 | 8 | diamond without toughness |

Speed modifiers are `add_multiplied_total`, one per piece, so they multiply: 1.02⁴ = 1.082, 0.95⁴ = 0.815.
Prismarine also adds 0.25 water movement efficiency per piece (1.0 with four, the attribute's maximum: Depth
Strider III's swimming speed).

**What the armor blocks** in our formula (vanilla's, [architecture 9.1](../architecture.md#91-damage-pipeline):
effective armor = clamp(armor − D / (2 + toughness / 4), armor / 5, 20), the hit loses effective / 25), for one hit of
D damage:

| Full set | D = 10 | D = 20 | D = 40 |
| --- | --- | --- | --- |
| Iron (15 / 0) | 40 % | 20 % | 12 % |
| Warrior (15 / 4) | 47 % | 33 % | 12 % |
| Divine (18 / 0) | 52 % | 32 % | 14 % |
| Prismarine (20 / 0) | 60 % | 40 % | 16 % |
| Diamond (20 / 8) | 70 % | 60 % | 40 % |
| **Heavy (18 / 16)** | 65 % | 59 % | 45 % |
| Heavy with diamond's toughness (18 / 8) | 62 % | 52 % | 32 % |
| Netherite (20 / 12) | 72 % | 64 % | 48 % |

So Heavy, crafted from iron, blocks about as much as diamond and beats it against big hits.

### The sets one by one

Recipes are the jar's `data/immersive_armors/recipe/` files; counts are for all four pieces. "Nether" flags an
ingredient whose vanilla source is the Nether.

#### Wooden

- **Stats:** 7 armor, no toughness; durability ×8; enchantability 4.
- **Effects:** arrow blocking 15 % per piece (60 % of projectile hits cancelled with four); explosion damage −10 % per
  piece (−34 %).
- **Recipe:** 24 logs (`#minecraft:logs`), shaped like vanilla armor. No Nether or End material. Repair: logs.
- **Against vanilla:** leather's armor, less durability, and a strong answer to skeletons and pillagers.
- **Proposed minimum:** Overworld, level 1. **Early unique reward:** no, a starter craft.
- **Recommendation:** keep.

#### Bone

- **Stats:** 7 armor; +8.2 % movement speed with four; durability ×8; enchantability 15.
- **Effects:** none; anti-skeleton with four pieces (skeletons drop the wearer as target).
- **Recipe:** 24 bones. No Nether or End material. Repair: bones.
- **Against vanilla:** leather's armor and enchantability, faster, and vanilla skeletons leave the wearer alone, which
  makes Overworld skeletons a safe kill (and XP) source.
- **Proposed minimum:** Overworld, level 1. **Early unique reward:** no.
- **Recommendation:** keep. It is the base of the Wither set.

#### Wither

- **Stats:** 11 armor; +4.1 % movement speed; durability ×12; enchantability 0: no enchanting table (anvil books
  probably still apply through vanilla's armor tags, *verify*).
- **Effects:** attackers, ranged ones too, get Wither II for 4 s with four pieces; wither damage −25 % per piece,
  applied per piece (75 % less with two, immune with four); anti-skeleton with four (Bone and Wither pieces mix).
- **Recipe:** each Bone piece in a ring of nether bricks, 22 **nether bricks** (`minecraft:nether_brick`, the item)
  in all. **Nether:** the item is smelted from netherrack, which the Ancient Builder sells
  ([ADR 0034](../decisions/0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md)); the Builder's
  nether bricks are the block, which crafts no items back. Repair: bones.
- **Against vanilla:** gold's armor; its point is the summoned Wither (base level 20) and wither skeletons.
- **Proposed minimum:** Undervault, level 18, once the merchants and the summonable Wither exist. **Early unique
  reward:** a candidate for the Wither's "Prepare" quest, not early.
- **Recommendation:** keep (crafted). Its full wither immunity is a tell-free immunity (BM-18), but against one damage
  type and one boss's effect.

#### Warrior

- **Stats:** 15 armor (iron's, with chestplate and leggings swapped), 4 toughness; durability ×15 (iron's);
  enchantability 5.
- **Effects:** berserk, the wearer's hits up to +80 % with four near death (+20 % per piece at 0 health, scaled by the
  missing health); +5 % per piece with an axe in either hand (+20 %).
- **Recipe:** each leather piece with iron: 15 iron ingots, 2 bones, 1 leather and a leather set. No Nether or End
  material. Repair: iron ingots.
- **Against vanilla:** iron's armor plus some toughness, and the strongest damage effect in the mod.
- **Its name:** "Warrior Helmet" reads as gear of our Warrior class, while gear has no class locks. Its names can be
  overridden through our lang file ([Levers](#levers)).
- **Proposed minimum:** Overworld, level 8. **Early unique reward:** yes, chapter 1 (for example the raid quests of
  M2's illager questline); the recipe is then removed.
- **Recommendation:** keep with changes: quest reward only, renamed.

#### Heavy

- **Stats:** 18 armor, **16 toughness** (4 per piece), **knockback resistance 2.0, capped at 1.0** (immune from two
  pieces on), −18.5 % movement speed; durability ×20; enchantability 6.
- **Effects:** none.
- **Recipe:** 12 iron blocks and 12 iron ingots (120 ingots). No Nether or End material. Repair: iron ingots.
- **Against vanilla:** blocks about as much as diamond, more against big hits, from iron; more toughness than
  netherite. It breaks "toughness is scarce on purpose" (RLM-198), and Apotheosis's Steel Touched affix can add more.
  Knockback immunity also cancels our spells' knockback (`spell/KnockbackEffect` calls `LivingEntity#knockback`,
  which scales by 1 − knockback resistance), bosses' included.
- **Proposed minimum:** with cut numbers (2 toughness and 0.1 knockback resistance per piece: diamond's 8 and
  netherite's 0.4), Overworld, level 8, as a slow tank's alternative to diamond. Unchanged, no earlier than netherite
  (Undervault, level 12) and better as a late loot item. **Early unique reward:** no.
- **Recommendation:** keep with changes (the numbers above, through our code: [Levers](#levers)).

#### Robe

- **Stats:** 8 armor; durability ×14; **enchantability 50** (vanilla's best is gold's 25); dyeable (default colour
  `#B00F46`).
- **Effects:** fire, lava and fireball damage −25 % per piece (−68 %); vanilla magic damage −20 % per piece (−59 %);
  attackers burn for 4 s.
- **Recipe:** 19 wool and 5 **blaze powder** (**Nether:** blaze rods, which the Ancient Specialist sells). Repair:
  wool.
- **Against vanilla:** leather's armor with strong resistances to vanilla's fire and magic. Not caster gear: armor,
  but no spell power, mana or magic shield. "Robe Chestplate" next to our "Spellweave Robes" would confuse the caster
  archetype (robes give no armor, realm design, gear archetypes). Its resistances stack on vanilla's Fire Protection
  (with Fire Protection IV on all four pieces, about 94 % less lava damage) outside our 75 % resistance cap. Its 50
  enchantability likely gives the best enchanting rolls in the game (*verify* with Apothic Enchanting).
- **Proposed minimum:** off; if kept, a fire utility set for Ashfire (level 40). **Early unique reward:** no.
- **Recommendation:** off. The Divine recipe then needs a replacement, or Divine comes only as a reward (below).

#### Slime

- **Stats:** 14 armor; **knockback resistance 1.0** with four (0.25 per piece); durability ×20; enchantability 10.
- **Effects:** melee attackers pushed away, 0.25 per piece (1.0 with four), ignoring knockback resistance; explosion
  damage −20 % per piece (−59 %).
- **Recipe:** 24 slime balls. No Nether or End material. Repair: slime balls.
- **Against vanilla:** between chainmail and iron, very durable, knockback immune. The push moves bosses too: a boss
  shoved out of its arena resets (boss framework), and our bosses' melee and knockback abilities lose their reach.
- **Proposed minimum:** Overworld, level 4 (swamp slimes). Quagmire is the slime realm, but 14 armor means nothing at
  its level 36. **Early unique reward:** no.
- **Recommendation:** keep with a check: a GameTest that a full Slime set does not push Astone or the test titan out
  of its arena. If it does, off.

#### Divine

- **Stats:** 18 armor, no toughness; durability ×18; enchantability 30; dyeable.
- **Effects:** with all four pieces, one hit of any kind is cancelled every 60 s: a boss's biggest blow, a spell, a
  damage-over-time tick, a fall or lava, whichever comes first.
- **Recipe:** each Robe piece with gold: 18 gold ingots, 1 emerald, 1 totem of undying (evokers: mansions and raids,
  Overworld) and a Robe set, so blaze powder (**Nether**, through the Ancient Specialist) indirectly. Repair: gold
  ingots.
- **Against vanilla:** diamond's armor less 2, no toughness, and the strongest defensive effect in the mod. Overworld
  zombies of the chainmail tier wear its pieces (mob armor, above) and desert pyramids hold them (chest loot).
- **Proposed minimum:** Undervault, level 16. **Early unique reward:** yes, the strongest candidate: chapter 2's main
  questline before Astone (WoW's Power Word: Shield in armor form); the recipe, the loot and the mob armor go.
- **Recommendation:** keep with changes: quest reward only.

#### Prismarine

- **Stats:** 20 armor, no toughness; −7.8 % movement speed; full water movement efficiency (+1.0); durability ×18;
  enchantability 8.
- **Effects:** spikes, 4 thorns damage to every melee attacker with four.
- **Recipe:** 19 prismarine shards, 4 prismarine crystals, 1 heart of the sea (buried treasure). No Nether or End
  material. Repair: prismarine crystals. The Ocean Monument leaves Overworld for Deepholm
  ([realm design](../design/realms.md#structures-from-other-mods)), so the recipe gates itself to Deepholm.
- **Against vanilla:** diamond's armor without toughness, and Deepholm's swimming.
- **Proposed minimum:** Deepholm, level 26. **Early unique reward:** no.
- **Recommendation:** keep; the Overworld ocean ruins' loot goes with the chest loot (below).

#### Steampunk

- **Stats:** 14 armor; durability ×10; enchantability 4.
- **Effects:** explosion damage −10 % per piece (−34 %); per slot: helmet radar (monsters within 16 blocks glow while
  sneaking), chestplate clock and compass on the HUD, leggings double jump, boots no fall damage and slow falls.
- **Recipe:** 13 gold ingots, 5 pistons, a clock, a compass, a redstone torch and 2 white stained glass. No Nether or
  End material. Repair: gold ingots.
- **Against vanilla:** chainmail-to-iron armor, worn for its utility piece by piece. Fall immunity and a double jump
  fit Undervault's giant cave, and WoW's engineering gear (rocket boots, parachutes) fits a later Engineering
  profession (M5). The double jump trusts the client: the server applies a thrust for every `armor_command` payload
  without checking the thruster state, so a modified client could fly in multiplayer.
- **Proposed minimum:** Overworld, level 8. **Early unique reward:** the Steampunk Boots alone, at chapter 1's end or
  in the "Prepare" quest before Undervault.
- **Recommendation:** keep (crafted); later perhaps through Engineering.

## How it meets our systems

### Our stats

Immersive Armors uses only vanilla attributes that our model keeps
([architecture 8.1](../architecture.md#81-the-attribute-set)): `minecraft:armor`, `armor_toughness`,
`knockback_resistance`, `movement_speed` and `water_movement_efficiency`. It brings no attribute of its own, so ADR
0038's "our stats stay the one attribute model" holds for its stats. Its modifier ids are vanilla's per-slot armor ids
(`minecraft:armor.helmet` and so on), unique per slot as 8.2 asks.

Its effects are not attributes, so the character sheet does not show them, and several overlap our stats:

| Effect | Our stat it resembles | Difference |
| --- | --- | --- |
| Berserk, weapon efficiency | attack damage (STR), crit damage | a multiplier outside our stats: the sheet shows nothing |
| Arrow blocking | dodge chance (kept for future classes, D5) | projectiles only, no "Miss!" text |
| Fire, magic, explosion and wither resistance | school resistances, spell resistance | vanilla damage types only, outside our 75 % cap (BM-18) |
| Bone's, Wither's speed and Heavy's, Prismarine's slow | DEX's movement speed (capped at 20 % in its stat rule) | outside that cap; multiplies with it and with the cast slow (ADR 0047) |
| Knockback resistance | the Sapphire upgrade's +5 % per piece | Heavy and Slime reach immunity |
| Divine's deflection | the magic shield (an absorb) | a full negation, not a pool |

### Our damage pipeline

- **Where it acts:** `LivingDamageEvent.Pre`, after our step 3 (dodge, blocking, crits, school resistances, armor),
  before absorption hearts and the magic shield. Its reductions therefore stack on top of our capped resistances, and
  the shield takes less. `CombatEvent.Hit` at `Post` reports the reduced amount, so the combat text and procs see it.
- **School hits:** Divine cancels them; Berserk raises a wearer's spells too (its only test is that the hit's causing
  entity wears the set); weapon efficiency needs a direct hit with an axe in hand. Robe's resistances and arrow
  blocking ignore school hits: our school damage types are in neither `#minecraft:is_fire`,
  `#minecraft:is_projectile` nor `#immersive_armors:is_magic` (our generated damage type tags add them only to
  `bypasses_armor`, `bypasses_cooldown` and `no_knockback`).
- **Hits on the attacker:** spikes are vanilla thorns hits (`Entity#hurt`), so they pass our pipeline as ordinary
  hits. The Wither set's wither has no source entity, so `combat/DamageOverTime` credits nobody for its ticks. Bounce
  sets velocity and skips knockback resistance.
- **Gear rule:** [gear.md](../design/gear.md#in-data) wants unique effects "in the damage pipeline (no hit outside
  it)". These effects are outside it. Keeping the mod means accepting that for its sets, or switching all effects off.
- **Mobs:** mob armor (above) gives vanilla mobs these effects too (arrow-blocking zombies, Heavy skeletons immune to
  knockback). Whether Divine and spikes work on mobs depends on whether a mob's equipment ticks the `set_count`
  component (not checked).

### Caps and the armor ceiling

The highest armor is 20 (Prismarine), below Matěj's 30. The highest toughness is Heavy's 16: above netherite's 12, near
the ceiling of 18, from iron. The other sets have 0 to 4. Apotheosis's affixes come on top of all of them
(Ironforged, Steel Touched).

### Apotheosis

Apotheosis puts an item into the armor categories when its `EQUIPPABLE` component names an armor slot
([gear.md](../design/gear.md#loot-categories), read in its jar), so all 40 pieces are melee armor to it: reforging
rolls Ironforged, Steel Touched and the other armor affixes on them, including Robe and Divine. That fits, since they
give armor. Apotheosis's own random drops hand out only items its affix loot entries name, so these pieces drop as
affix items only if our data lists them (*verify* with one piece in a dev run).

### Our robes and classes

No set is caster gear: none gives spell power, mana, magic shield or cooldown reduction. Robe and Divine look like
cloth but give armor. With no class locks, a Mage may wear any set and loses only what our robes give. "Warrior" is
also our class's name (above).

### Better Combat

No conflict found: Better Combat's swings are vanilla attacks, so berserk and weapon efficiency apply to each target
of a sweep, and spikes and bounce to each attacker. Weapon efficiency reads `#minecraft:axes`.

## Levers

How to switch a set off or move its source, without touching the mod's internals
([architecture principle 8](../architecture.md#2-principles)):

| Lever | Where | Per set? | What it changes |
| --- | --- | --- | --- |
| `enableEffects: false` | `pack/config/immersive_armors.json` | no, all sets | every effect and its tooltip; stats, the anti-skeleton rule and mob armor stay |
| `lootChance: 0` | config | no | all chest loot |
| `mobEntityUseImmersiveArmorChance: 0` | config | no | all mob armor |
| `overwriteValues` | config | yes (per set and property) | **probably nothing.** `Main#setSharedConfig` applies it only when a client receives the server's settings (the server never applies its own), and it changes the set's material fields and a modifier supplier that nothing reads: `ExtendedArmorItem#getDefaultAttributeModifiers()` takes no `ItemStack`, so it is not NeoForge's `IItemExtension` method, and the real modifiers were fixed at registration (`Item.Properties#attributes`). *Verify* before relying on it |
| Recipe removed | a file of ours at `data/immersive_armors/recipe/<piece>.json` with `"neoforge:conditions": [{"type": "neoforge:never"}]` | per piece | the recipe is skipped (NeoForge's conditional loading); the core mod already does this for Apothic Enchanting's stale loot modifier list |
| Recipe replaced | a recipe of ours in our namespace, next to the removal | per piece | any source: our materials, an Engineering station later |
| Chest loot replaced | IA's modifier file overridden the same way, then `neoforge:add_table` modifiers of ours | the override is all sets at once; ours per set | IA's modifier has one file for every set, so only replacing all of it moves loot per set |
| Stats changed | NeoForge's `ModifyDefaultComponentsEvent` (public API) in our code | per item | armor, toughness, knockback resistance, speed (the default `ATTRIBUTE_MODIFIERS`), durability, enchantability; not the effects |
| Names changed | our `en_us.json`, keys `item.immersive_armors.<piece>` | per item | the player-facing name |
| Hidden | the item tag `c:hidden_from_recipe_viewers` | per item | JEI stops listing it; the creative tab still does |
| Effect numbers | Java only | no | cannot change without reaching into the mod's classes, which principle 8 forbids |

For data of ours to win, the core mod declares Immersive Armors an optional dependency loaded before it
(`ordering = "AFTER"` in `neoforge.mods.toml`, as for Apotheosis), so our data pack and resources sit above its own.

**What our content pipeline would need** (rule 1): a content type for another mod's items, say one YAML per mod in
`content/`, with per item or set: enabled, recipe (theirs, none, or ours), name, stat overrides and hidden. Its
generator writes the never-condition files, our recipes and loot modifiers, the lang entries, the tag and a generated
table for a small `compat/immersive_armors` listener of `ModifyDefaultComponentsEvent`
([architecture 19.4](../architecture.md#194-optional-integrations)). Validation needs the 40 ids in the ID universe:
rule 3's full-pack registry dump (still a TODO), or a pinned id list taken from the jar until then. Quest rewards that
name these items need the same ids.

## Proposal for Matěj

### Keep-list

| Set | Keep? | Source | Minimum | Changes |
| --- | --- | --- | --- | --- |
| Wooden | keep | crafted | Overworld, level 1 | none |
| Bone | keep | crafted | Overworld, level 1 | none |
| Slime | keep, if the boss check passes | crafted | Overworld, level 4 | GameTest: a full set does not push a boss out of its arena |
| Steampunk | keep | crafted (Engineering later, M5) | Overworld, level 8 | the Boots alone as an early quest reward |
| Heavy | keep with changes | crafted | Overworld, level 8 | toughness 2 per piece (8), knockback resistance 0.1 per piece (0.4) |
| Warrior | keep with changes | **unique quest reward**, chapter 1 | Overworld, level 8 | recipe removed; renamed so it does not read as Warrior-class gear |
| Divine | keep with changes | **unique quest reward**, chapter 2 before Astone | Undervault, level 16 | recipe removed (with its dye recipes kept) |
| Wither | keep | crafted (netherrack from the Ancient Builder, smelted) | Undervault, level 18, with the merchants | none |
| Prismarine | keep | crafted (Deepholm's Ocean Monument) | Deepholm, level 26 | none |
| Robe | **off** | none | | recipes removed, hidden from JEI |

Pack-wide: chest loot off (`lootChance: 0`) and mob armor off (`mobEntityUseImmersiveArmorChance: 0`), so every piece
comes from a recipe or one of our rewards and nothing turns up in an Overworld zombie or a desert pyramid. Effects stay
on: they are the reason to take the mod.

### Changes needed

1. **Pack:** Immersive Armors 1.8.2+26.1.2 on both sides; `pack/config/immersive_armors.json` with `"version": 1`,
   `lootChance` 0, `mobEntityUseImmersiveArmorChance` 0, the rest at defaults.
2. **Core mod:** the optional dependency loaded before ours; `compat/immersive_armors` with the
   `ModifyDefaultComponentsEvent` listener for Heavy.
3. **Pipeline:** the content type above: recipe removals (Warrior, Divine, Robe), name overrides (Warrior), the hidden
   tag (Robe), Heavy's stats; the 40 ids in the ID universe.
4. **Quests:** Warrior's set and the Steampunk Boots in chapter 1, Divine's set in chapter 2, once the questlines
   exist.
5. **GameTests:** Heavy's attributes after the override; Divine's deflection through our pipeline (combat text,
   magic shield untouched); the Slime set's push on a boss; the removed recipes gone.
6. **Docs:** the [third-party mods](third-party-mods.md) row, architecture 19.4, and the milestone if Matěj moves the
   mod from M2 (ADR 0044) to M1 with the gear.

## Questions for Matěj

1. **Take Immersive Armors with the keep-list above?** Proposed: yes, nine sets, Robe off. Or another pick, or not at
   all.
2. **Heavy's numbers:** proposed: cut to diamond's toughness (8) and netherite's knockback resistance (0.4) through our
   code, crafted from level 8. Or keep its 16 toughness and knockback immunity and make it a late reward, or switch it
   off.
3. **Warrior:** proposed: a unique chapter 1 quest reward, recipe removed, renamed (for example "Berserker's", checked
   against the style guide) so it does not look like Warrior-class gear. Or crafted with its own name.
4. **Divine:** proposed: a unique chapter 2 quest reward before Astone, recipe removed. Its deflection cancels one hit
   of any kind a minute, boss hits and spells included. Or crafted, or off.
5. **Robe:** proposed: off (armor on a "robe", vanilla-only resistances outside our cap, 50 enchantability). Or keep
   it as Ashfire's fire utility set from level 40.
6. **Chest loot and mob armor:** proposed: both off by config, so recipes and our rewards are the only sources. Or keep
   the mod's chest loot in Overworld structures.
7. **Effects outside our pipeline:** proposed: accept them as these sets' "effects instead of armor", including four
   tell-free immunities (Divine's deflection, full wither immunity, Heavy's and Slime's knockback immunity, which also
   cancels our knockback spells, and skeletons ignoring Bone and Wither). Or switch all effects off and keep the sets as
   plain armor.
8. **Slime:** proposed: keep it if a GameTest shows its push cannot move a boss out of its arena, else off.
9. **Steampunk:** proposed: crafted now, the Boots alone as an early quest reward, and later the set through an
   Engineering profession (M5). The double jump trusts the client in multiplayer; proposed: accept it.
10. **Apotheosis:** proposed: let reforging roll affixes on these pieces (it treats them as armor anyway), but keep
    them out of Apotheosis's random drops (no affix loot entries), so their sources stay ours.
11. **Milestone:** ADR 0044 lists the mod for M2; the gear moved to M1 on 2026-10-29. Proposed: add the mod with M1's
    gear, so chapter 1's rewards can name its pieces. Or keep it in M2.
