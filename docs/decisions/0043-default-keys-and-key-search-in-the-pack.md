# 0043. Default keys and key search in the pack

Date: 2026-10-19

Status: Accepted (amends [0013](0013-modpack-composition.md))

## Context

The playtest of the whole-pack dev client (RLM-279, [0042](0042-the-dev-runs-load-the-whole-pack.md)) showed the
pack's Key Binds screen as a player meets it: dozens of conflicts (Iris, Sophisticated Backpacks and Sophisticated
Core, Just Zoom's options and Open Backpack and Tom's Simple Storage's terminal all on B), and no defaults for the
keys Matěj had asked for (Open Terminal on V, decided on RLM-25; Open Quests on `;`; Zoom on Z). Nothing in the pack
could set another mod's default key: packwiz-installer copies `config/` on every launch, so shipping an
`options.txt` would overwrite each player's own settings. Key Binds also had no search, and Matěj had picked
Controlling with Searchables for M1 on RLM-264 (2026-10-18).

## Decision

1. **Default Options** (BlayTheNinth, 26.1.2.2, client; it needs Balm, already in the pack) joins the pack. Its
   `config/defaultoptions/keybindings.txt` holds the pack's default keys. It changes each key mapping's default, so
   a new profile starts with them, Reset Keys returns to them, and a player's own bindings stay.
2. **The default keys** are Matěj's picks of 2026-10-19, named by their position on his Czech QWERTZ keyboard as on
   RLM-25 (the [pack README](../../pack/README.md#configuration) lists them):
   - Quick Actions and Swap Offhand are free for spells;
   - Open Quests is on `;`, Open Terminal on V and Zoom on Z;
   - Xaero's Minimap gets Matěj's keys of 2026-10-20, and our Dark Vision key moves from N to H in our code;
   - every Iris and Sophisticated key is unbound, as are Just Zoom's Open Options, Xaero's Quick Waypoint and
     vanilla's Open Command.
3. **Controlling** (26.1.2.7) with its library **Searchables** (1.0.2), both client-only, join the pack: Key Binds
   gets a search by name or key and a filter for conflicts.

## Consequences

- C3b (RLM-25) adds the spell keys to this file, or registers them as our own key mappings' defaults.
- A mod update can rename a key mapping. Default Options then skips the line, and the key falls back to the mod's
  default. After a mod update, the `key_` lines of a booted client's `options.txt` show the current names.
- A key named by its QWERTZ position shows another label on a US keyboard: Zoom is on the key labelled Y there.
- The dev runs load the three mods with the rest of the pack (0042) and get the file through `seedPackConfig`.
