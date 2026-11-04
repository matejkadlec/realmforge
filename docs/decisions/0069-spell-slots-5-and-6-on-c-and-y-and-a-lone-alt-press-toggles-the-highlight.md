# 0069. Spell slots 5 and 6 on C and Y, and a lone Alt press toggles the highlight

Date: 2026-11-04

Status: Accepted (amends [0046](0046-spell-bar-default-keys-amend-the-pack-defaults.md) and
[0061](0061-the-target-circle-is-a-smooth-light-ring-and-the-highlight-toggles.md))

## Context

[0046](0046-spell-bar-default-keys-amend-the-pack-defaults.md) decision 1 gave the spell bar's eight slots R, T, F, G,
H, C, Ů and §, and its decision 4 kept `key.keyboard.z` (Y on a Czech QWERTZ keyboard) free for a quick-drink potion
key, which Xaero's Minimap's Enlarge Minimap holds in the pack. On 2026-11-01 Matěj asked for the bar's defaults to be
R, T, F, G, C, Y, Ů and §: H drops out, C moves from slot 6 to slot 5 and Y takes slot 6, with Enlarge Minimap unbound
(RLM-573). He rejected the quick-drink key the same day (RLM-343), so nothing else waits for Y.

[0061](0061-the-target-circle-is-a-smooth-light-ring-and-the-highlight-toggles.md) made Highlight Spell Target a
toggle on Left Alt: a press switches it on until the next press. Left Alt is also a modifier: Apotheosis's Open World
Tier Select is Alt+T and its Toggle Attribute Bonuses Alt+K in the pack, and Windows switches windows with Alt+Tab.
NeoForge counts Alt's press for our mapping in each of them, so every one flipped the highlight (RLM-522). The ticket
keeps Matěj's decisions: Left Alt, a toggle by default.

## Decision

1. **The spell bar's defaults** (`client/input/SpellKeys`) are R, T, F, G, C, Y, Ů and § (GLFW's `R`, `T`, `F`, `G`,
   `C`, `Z`, `;` and `'`), and X for the racial slot. H is no spell key by default.
2. **Xaero's Enlarge Minimap goes unbound** in the pack's default keys (`key_gui.xaero_enlarge_map`, whose default is
   `key.keyboard.z`). No other key of the pack has `key.keyboard.z` (Just Zoom's Zoom is on `key.keyboard.y`, Z on a
   Czech keyboard).
3. **A toggle counts only a lone press** of the Highlight Spell Target key: it goes down in game, with no screen open,
   and comes up with no other key going down in between, while the window keeps its focus (`client/input/LonePress`,
   read by `client/spell/TargetCircle`). The toggle flips on that release. Keys already held when it went down don't
   count, so tapping Alt while walking still toggles; mouse buttons don't count either. Hold mode is unchanged: the
   highlight is on while the key is held. Vanilla's F3 counts its debug overlay the same way.

## Consequences

- 0046's decision 1 (H and C on slots 5 and 6) and decision 4 (Y kept for a potion key) are replaced; its other
  decisions stand. 0061's "a press switches it on until the next press" now means a lone press.
- Minecraft saves every key binding a client has: a profile that already played keeps H and C on slots 5 and 6, and
  Enlarge Minimap on Y, until the player presses Reset Keys in Key Binds (or Reset on those rows).
- Alt+T, Alt+K and Alt+Tab no longer flip the highlight; a lone Left Alt press does, on its release rather than on its
  press.
- In Hold mode the highlight still shows while Alt is held for Alt+T or Alt+K, as before; it goes when Alt comes up.
