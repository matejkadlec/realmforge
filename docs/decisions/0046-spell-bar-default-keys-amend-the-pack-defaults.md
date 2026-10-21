# 0046. The spell bar's default keys amend the pack's default keys

Date: 2026-10-21

Status: Accepted (amends [0043](0043-default-keys-and-key-search-in-the-pack.md))

## Context

C3b (RLM-25) built the spell bar with the keys Matěj chose for decision D4 (RLM-54) on RLM-25: R, T, F, G, H and C
for the bar's first six slots and two slots unbound for each player to bind (2026-10-15), X for the racial ability
beside the bar (2026-10-16), Y for a quick-drink potion, V for Tom's Simple Storage, and vanilla's chat moved to Enter
as in WoW. Swap Item With Offhand stays unbound.

[0043](0043-default-keys-and-key-search-in-the-pack.md) decision 2, accepted on 2026-10-19 with the keys Matěj picked
for the pack (RLM-279), kept chat on T "for now" and moved our Dark Vision key from N to H, which the spell bar now
takes. Vanilla also binds the creative toolbar's Save and Load keys to C and X and Social Interactions to P, the
spellbook's key. 0043 is accepted, so this ADR records the change
([0001](0001-record-architecture-decisions.md), [0030](0030-adrs-bind-agents-not-the-owner.md)).

## Decision

1. **Our key mappings carry the spell bar's defaults** (`client/input/SpellKeys`): R, T, F, G, H, C, two unbound
   slots and X for the racial slot, under Realmforge Spell Bar, and P for the spellbook under Realmforge. GLFW names
   keys by their US position, which all of these share with Matěj's Czech QWERTZ keyboard.
2. **The pack's default keys** (`pack/config/defaultoptions/keybindings.txt`) move vanilla's chat from T to Enter, and
   unbind the creative toolbar's Save and Load keys (C and X) and Social Interactions (P), which the pause menu's
   Player Reporting still opens. Apotheosis's Open World Tier Select moves from Ctrl+T to Alt+T: NeoForge picks a
   key's Ctrl binding over its plain one while Ctrl is held, so a player who holds Ctrl to sprint would open the world
   tier screen instead of casting T's spell.
3. **Dark Vision moves from H to U** (`client/input/DarkVisionKey`). This is the agent's pick, as no free key was
   named: a boot of the whole pack (`rf pack-boot`) leaves B, I and U as the only letters without an in-game key, and
   J, next to H, is Toast Control's Clear Toasts. Matěj confirms U, or names another key, in the C3b playtest.
4. **The quick-drink potion key (Y) is not part of C3b:** what "the best potion" is and how often one may drink need
   a design of their own, so it is a follow-up ticket; `key.keyboard.z` (Y on QWERTZ) stays free for it.

## Consequences

- A player with the pack starts with the spell bar's keys and no conflict with vanilla; Reset Keys returns to them.
- Without the pack (the core mod alone), vanilla's chat and the toolbar keys still share T, C and X with the spell bar
  until the player rebinds them.
- 0043's Dark Vision on H is replaced by U; its other keys stand.
- The quick-drink potion's planned key, `key.keyboard.z` (Y on QWERTZ), is Xaero's Minimap's Enlarge Map in the pack;
  the potion's ticket settles that.
