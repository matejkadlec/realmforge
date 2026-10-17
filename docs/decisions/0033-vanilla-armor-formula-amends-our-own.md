# 0033. Vanilla's armor formula amends "our own formula"

Date: 2026-10-17

Status: Accepted (amends decision 6 of [0014](0014-core-mod-architecture.md))

## Context

Decision 6 of [0014](0014-core-mod-architecture.md) says armor mitigation is "our own formula applied through
NeoForge's damage container". Architecture 8.4 planned it as a level-aware formula, because the vision wanted armor
and toughness far beyond vanilla and vanilla's formula never blocks more than 80 %. F5 (RLM-21) then built it with
armor and armor toughness merged into one value, A / (A + K), and the curves `realmforge:armor_halving` (K at the
attacker's level) and `realmforge:armor_reduction_cap`.

Matěj decided on 2026-10-16 (RLM-223) that armor and armor toughness must not be merged: vanilla keeps them apart,
armor working best against small hits and toughness against big ones, and players rely on that. He chose vanilla's
formula itself over a formula of our own that splits the two the same way. He also corrected the vision's scale: the
best reachable armor and toughness is about netherite × 1.5 (30 armor, 18 toughness), and better armor gets effects
instead of more armor. 0014 is accepted, so this ADR records the change instead of rewriting it.

## Decision

- **Armor mitigation is vanilla's formula** (`CombatRules#getDamageAfterAbsorb`): with D the hit's damage,
  effective armor = clamp(armor − D / (2 + toughness / 4), armor / 5, 20), and the hit loses effective / 25 of its
  damage, then vanilla's armor-effectiveness enchantment hook (Breach) adjusts that share.
- **It runs after our armor pierce and shred**, which lower the armor a hit meets (flat points first, then the shred
  share of the rest); armor toughness is never pierced.
- **It stays in our code** (`combat/DamageCalculator`, checked against vanilla's in JUnit) and is still applied
  through NeoForge's damage container (the ARMOR reduction), as 0014 says. So a mod that replaces vanilla's
  `CombatRules` (Apothic Attributes, RLM-205) does not change our result.
- **No level-aware part:** the attacker's level reaches armor only through its hit size, as mob damage grows with
  level. The curves `armor_halving` and `armor_reduction_cap` are gone.

Everything else in decision 6 stands: primary stats are attributes, derived stats come from data rules, and
attribute caps are raised in one place (the raised armor and toughness caps stay, Matěj, 2026-10-16).

## Consequences

- Nothing blocks more than 80 % of a hit, and big hits get through high armor unless toughness backs it.
- Armor and toughness from gear, affixes and curios keep their vanilla meaning, so third-party gear behaves as its
  players expect.
- Mob armor (`mob_armor`, up to 25 at level 60) lets a player's big hits through more easily than before; RLM-204
  retunes mob scaling.
- **Docs:** architecture 8.4, 9.1, 14.1 and 20, and the vision's armor note follow this decision (RLM-223).
