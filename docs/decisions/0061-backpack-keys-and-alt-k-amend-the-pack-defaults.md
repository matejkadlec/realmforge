# 0061. The backpack keys and Alt+K amend the pack's default keys

Date: 2026-10-29

Status: Accepted (amends [0043](0043-default-keys-and-key-search-in-the-pack.md))

## Context

[0043](0043-default-keys-and-key-search-in-the-pack.md) decision 2 (2026-10-19, RLM-279) unbound every Sophisticated
Backpacks and Sophisticated Core key, because B was shared by Iris, Just Zoom's Open Options, Open Backpack and Tom's
Simple Storage's terminal. Since then Iris's keys and Open Options are unbound, and the terminal is on V. In his
playtest of RLM-494 on 2026-10-29 Matěj found Open Backpack and Sort Storage/Backpack unbound and asked for Open
Backpack on B and the sort on `;`, the key Inventory Sorting's Sort and FTB Quests' Open Quests already share (RLM-501).

Apotheosis 9.1.0 binds Toggle Attribute Bonuses to Ctrl+K. NeoForge picks a key's Ctrl binding over its plain one
while Ctrl is held, so a player who holds Ctrl to sprint and presses K toggles the bonuses instead of opening the
character sheet (RLM-347). [0046](0046-spell-bar-default-keys-amend-the-pack-defaults.md) moved Apotheosis's Open
World Tier Select from Ctrl+T to Alt+T for the same reason.

0043 is accepted, so this ADR records the change ([0001](0001-record-architecture-decisions.md),
[0030](0030-adrs-bind-agents-not-the-owner.md)).

## Decision

1. **Open Backpack on B** (`key.sophisticatedbackpacks.open_backpack`) in `pack/config/defaultoptions/keybindings.txt`.
   A boot of the whole pack (`rf pack-boot`, 2026-10-29) leaves no other key mapping on B but vanilla's F3+B (Show
   Hitboxes), which `KeyboardHandler` handles as a debug key while F3 is held, without pressing B's other mappings.
2. **Sort Storage/Backpack on `;`** (`key.sophisticatedcore.sort`, `key.keyboard.grave.accent`). It does not fight
   Inventory Sorting's Sort on the same key: in a Sophisticated screen, Sophisticated Core handles the key in NeoForge's
   `ScreenEvent.KeyPressed.Pre`, sorts the storage and cancels the event, so the screen's own `keyPressed`, where
   Inventory Sorting's mixin reads the key, never runs. Over a slot of the player's inventory Sophisticated Core leaves
   the key alone, and Inventory Sorting sorts the inventory (Sophisticated Core 1.5.10, Inventory Sorting 3.0.1, read
   with `javap`). Sophisticated Core's slot blacklist for Inventory Sorting goes through an inter-mod message that
   Inventory Sorting 3.0.1 no longer reads, so the event order is what keeps the two apart.
3. **The other Sophisticated keys stay unbound.**
4. **Toggle Attribute Bonuses on Alt+K** (`key.apotheosis.toggle_attribute_bonuses`), like Open World Tier Select's
   Alt+T: holding Ctrl to sprint no longer takes K from the character sheet.

## Consequences

- A new profile, or Reset Keys in Key Binds, gets B and `;` for the backpack and Alt+K for the attribute bonuses; an
  existing profile keeps its own keys until then.
- `;` now does three things: it opens the quest book while no screen is open, sorts a Sophisticated storage in its
  screen, and sorts any other inventory through Inventory Sorting.
- An update of Sophisticated Core or Inventory Sorting that moves its key handling between the screen event and the
  screen could make both sort a backpack; the playtest step "`;` sorts an open backpack once" checks it again.
- 0043's other keys stand, with 0046's changes.
