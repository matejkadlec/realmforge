# Better Key Binds

A client-side NeoForge mod for Minecraft 26.1.2: every key bind can have a **secondary key**, as in World of
Warcraft, and either key triggers it. Bind Open Chat to Enter and T, Walk Forwards to W and the Up arrow, Attack to a
mouse button and a key.

It is a mod of its own that lives in the Realmforge repository: it does not depend on Realmforge, Realmforge does not
depend on it, and servers don't need it ([ADR 0048](../docs/decisions/0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md)).

## Using it

- Options → Controls → Key Binds shows a second key button right of each key bind's primary one. Click it and press
  a key (with Ctrl, Shift or Alt held first for a combination) or a mouse button; Esc unbinds it.
- Either key works: a key bind is held while either of its keys is held (letting go of one while the other is still
  down keeps it held), and a press of either key counts as a click. A toggle key (Sneak, Sprint set to Toggle) flips
  on a press of either key.
- The secondary key follows the same rules as the primary: NeoForge's key modifiers and its conflict contexts (an
  in-game key and a GUI key may share a key).
- A key bind's Reset returns both keys to their defaults; a secondary key's default is unbound. "Reset Keys" resets
  every secondary key too.
- Conflicts show as on the primary column: `[ key ]` in yellow, a yellow stripe and a tooltip naming the other key
  binds. With [Controlling](https://www.curseforge.com/minecraft/mc-mods/controlling), "Show Conflicts" lists rows
  whose conflict involves a secondary key, and the search by key (`key:T`) finds key binds by their secondary key.

Default: every secondary key is unbound.

## How it works

| Part | Where |
| --- | --- |
| Each key bind's secondary key: a hidden `KeyMapping` with its own key and `KeyModifier` and its primary's conflict context. It is in vanilla's key lookup, never in `Options.keyMappings`, so key presses reach it like any key and neither the Key Binds screen nor `options.txt` lists it. | `SecondaryKeyMapping` |
| The link from a key bind to its secondary key, the merge rules, loading and saving | `SecondaryKeys` |
| The secondary key file | `SecondaryKeysFile` |
| `isDown()` (held while either key is held), `consumeClick()` (both keys' clicks), `matches()` (a screen's key check), `isDefault()` and `setToDefault()` (both keys) | `mixin/KeyMappingMixin`, `mixin/ToggleKeyMappingMixin` |
| The second key column on vanilla's Key Binds screen | `SecondaryKeyColumn`, `mixin/KeyBindsListKeyEntryMixin`, `mixin/KeyBindsListMixin` |
| Waiting for a pressed button's key (NeoForge screen events, on any screen that extends vanilla's) | `SecondaryKeyCapture`, `KeyCapture` |
| Controlling: its rows, "Show Conflicts" and the search by key | `mixin/controlling/` |

The secondary keys are saved in `config/better_key_binds-secondary_keys.txt`, one key bind per line:

```text
key.chat:key.keyboard.t
key.inventory:key.keyboard.e:SHIFT
key.attack:key.mouse.4
```

The key bind is its name as in `options.txt` (without `key_`), the key a key name, the modifier `CONTROL`, `SHIFT`
or `ALT` (none without one). Key binds of mods that are not installed keep their lines. The file is read once every
mod has registered its key mappings, not from `options.txt`, whose early startup save drops the lines of key binds it
does not know yet.

The Controlling mixins are in their own mixin config, which is not required: they only apply when Controlling is
loaded, and should a Controlling update change the code they hook into, its screen loses the secondary column
instead of the game crashing. After a Controlling update in the pack, move the `compileOnly` versions in
`build.gradle` and check the column in game.

## Building and testing

From the repository root ([CLAUDE.md](../CLAUDE.md#commands) has the setup):

```bash
./gradlew :better-key-binds:build          # jar in better-key-binds/build/libs/, JUnit
./gradlew :better-key-binds:installToPrism # copy the jar into the Prism instance (docs/dev-setup.md)
./gradlew runClient                        # the dev client loads it next to Realmforge's mods
```

The JUnit tests cover the file format, the key capture, the merge of both keys and the conflicts with secondary keys.

## Releases

Version: `better_key_binds_version` in `gradle.properties` in this folder, shown as `<version>+mc<minecraft
version>`, from 0.1.0. A release is tagged `better-key-binds-v<version>` (for example `better-key-binds-v0.1.0`) and
carries the jar; Realmforge's own releases keep their `v*` tags. The Realmforge modpack ships this jar next to the
core mod's.

## License

MPL-2.0, see [LICENSE](LICENSE).
