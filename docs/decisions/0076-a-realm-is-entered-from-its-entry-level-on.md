# 0076. A realm is entered from its entry level on

Date: 2026-11-11

Status: Accepted (amends [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md): Undervault's "no hard
level lock")

## Context

[0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md) says the first sealed realm, Undervault, "opens at
about level 6 through chapter 1's main questline, with no hard level lock": a realm's only lock was its unlock, the
advancement its unlock quest grants.

On the same day, 2026-10-16, Matěj decided how mob levels work (RLM-240; every rule there is his). Mobs follow the
nearest player's level, floored at the realm's entry level, and:

- "**Entry level (floor)** = the level needed to enter the realm. It never changes." Overworld 1, Undervault 6,
  Sunwastes 16, Everwinter 20, Prestoria 26, Quagmire 36, Ashfire 40, Unmade Isles 48.
- "**A player below the floor cannot happen:** a realm's portal checks the minimum level and the realm's quest. Still,
  the floor wins (all mobs at the floor) if it ever happens, for example a level set by command."

RLM-303 built the floor. The portal's check was split off as RLM-306, which this ADR records, since 0032 is accepted
and says the opposite.

## Decision

1. **Entering a realm needs its entry level as well as its unlock.** The realm's dimension rules
   (`content/dimensions/<realm>.yaml`) name both in their access requirements: the unlock advancement, then
   `{type: realmforge:level_at_least, level: <entry level>}`. Validation keeps that level equal to
   `mob_levels.entry_level`, the floor of the realm's mobs, so the two never drift apart; a realm whose entry level
   is 1 needs none.
2. **Every way in is checked the same way,** as for the unlock (realm portal spec, "Access and unlocks"): the
   sealstone opens no portal onto the realm for a player below the level, the portal does not start for them, and
   `/tp`, `/execute in`, ender pearls and warps are refused.
3. **The refusal says which of the two the player lacks.** The first requirement they do not meet, in the rules'
   order, decides the hint: without the unlock, the realm's unlock hint, as before; with the unlock but below the
   level, "Requires level 6." A requirement may say such a sentence of itself (`Requirement#unmetHint`); the level
   requirement does.
4. **No operator bypass,** as for the unlock: a creative player needs the level too, so tests and playtests see the
   real rule (`/qa level 6` in the dev client).
5. **Leaving is always open, and a player already inside stays.** The check is on the way in. A player whose level
   falls below the entry level inside the realm (a command, a lost chapter) is not thrown out, and the mob floor
   holds for them, as RLM-240 says.

## Consequences

- Undervault, the only realm so far, needs level 6. Its unlock comes with the end of chapter 1's main questline,
  where a player is about level 10, so the level lock rarely shows in normal play; it matters for a player who gets
  the unlock early (a command, a later way to share it) and for the later realms' bands.
- A level set below the entry level by `/qa level` or `/realmforge xp` now also keeps the player out of the realm
  until they are back at it.
- Later realms get their entry level from RLM-240's table when their dimension rules are written, and validation
  refuses a realm whose access does not name it.
- 0032's other decisions stand.
