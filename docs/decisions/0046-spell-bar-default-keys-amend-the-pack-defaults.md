# 0046. The spell bar's default keys amend the pack's default keys

Date: 2026-10-21

Status: Accepted (amends [0043](0043-default-keys-and-key-search-in-the-pack.md))

## Context

C3b (RLM-25) built the spell bar with the keys Matěj chose for decision D4 (RLM-54) on RLM-25: R, T, F, G, H and C
for the bar's first six slots and two slots unbound for each player to bind (2026-10-15), X for the racial ability
beside the bar (2026-10-16), Y for a quick-drink potion, V for Tom's Simple Storage, and vanilla's chat moved to Enter
as in WoW. Swap Item With Offhand stays unbound. In his playtest of C3b on 2026-10-21 he bound the last two slots to
Ů and § (the two keys right of L on his Czech QWERTZ keyboard) and put Dark Vision on ¨ (by Enter), and asked for the
§ key's label, which Minecraft shows empty, to be visible.

[0043](0043-default-keys-and-key-search-in-the-pack.md) decision 2, accepted on 2026-10-19 with the keys Matěj picked
for the pack (RLM-279), kept chat on T "for now" and moved our Dark Vision key from N to H, which the spell bar now
takes. Vanilla also binds the creative toolbar's Save and Load keys to C and X and Social Interactions to P, the
spellbook's key. 0043 is accepted, so this ADR records the change
([0001](0001-record-architecture-decisions.md), [0030](0030-adrs-bind-agents-not-the-owner.md)).

## Decision

1. **Our key mappings carry the spell bar's defaults** (`client/input/SpellKeys`): R, T, F, G, H, C, Ů and § (GLFW's
   `;` and `'`, a US keyboard's keys in their place) and X for the racial slot, under Realmforge Spell Bar, and P for
   the spellbook under Realmforge. GLFW names keys by their US position. Our key labels draw `§` from our own font
   (`client/input/KeyLabels`), because Minecraft reads it in any text as a formatting code.
2. **The pack's default keys** (`pack/config/defaultoptions/keybindings.txt`) move vanilla's chat from T to Enter, and
   unbind the creative toolbar's Save and Load keys (C and X) and Social Interactions (P), which the pause menu's
   Player Reporting still opens. Apotheosis's Open World Tier Select moves from Ctrl+T to Alt+T: NeoForge picks a
   key's Ctrl binding over its plain one while Ctrl is held, so a player who holds Ctrl to sprint would open the world
   tier screen instead of casting T's spell.
3. **Dark Vision moves from H to ¨** (`client/input/DarkVisionKey`; GLFW's `\`, the key by Enter): Matěj's pick in
   the C3b playtest, a key on the keyboard's edge that nothing in the pack binds. The handoff had proposed U, one of
   the three letters (B, I, U) a boot of the whole pack (`rf pack-boot`) leaves without an in-game key, and JEI's
   Show Uses has it in screens.
4. **The quick-drink potion key (Y) is not part of C3b:** what "the best potion" is and how often one may drink need
   a design of their own, so it is a follow-up ticket; `key.keyboard.z` (Y on QWERTZ) stays free for it.

## Consequences

- A player with the pack starts with the spell bar's keys and no conflict with vanilla; Reset Keys returns to them.
- Without the pack (the core mod alone), vanilla's chat and the toolbar keys still share T, C and X with the spell bar
  until the player rebinds them.
- 0043's Dark Vision on H is replaced by ¨; its other keys stand.
- Ů, § and ¨ have no binding in the pack (a pack boot's `options.txt`, 2026-10-21).
- The quick-drink potion's planned key, `key.keyboard.z` (Y on QWERTZ), is Xaero's Minimap's Enlarge Map in the pack;
  the potion's ticket settles that.
