# In-game guide

> Design of the in-game guide (RLM-102), written on 2026-10-12 from Matěj's requirements of the same day: a key
> opens a screen about the realms, dungeons, bosses and rewards; it is searchable; chapters unlock with progression.
> **Approved by Matěj on 2026-10-13 with changes, for M4 (RLM-116)**: nested tabs with a Character tab, the systems
> in Basics, ores and loot in the entries ([Decisions](#decisions)). Research: PR-2 in
> [reference-games.md](../research/reference-games.md) (one source for quest texts and guide entries). Art:
> [guide UI spike](../../art/spike-guide-ui/README.md). How things are built: [architecture](../architecture.md).

## What it is

- **A screen, not a book.** `J` opens it (rebindable under Realmforge in Controls), unless a mod in the pack already
  uses `J`. Book guides such as Patchouli fit one small mod, but for a whole pack they have no search and fit little
  text on a page.
- **Our own screen, not GuideME.** GuideME (AE2's guidebook library) was evaluated on 2026-10-12 and dropped:
  locking chapters per player would need its internal API, and its layout is fixed.
- **About the world and the systems:** realms, dungeons, bosses and what they reward, and a Character and a Basics
  tab that explain what a stat, class, race, talent or spell does and how the other systems work (Matěj: "I 100% want
  it there").
- **It replaces EMI Loot and EMI Ores**, which the 26.1.2 port removed (EMI has no newer build): realm entries list
  their ores, dungeon entries their notable loot, boss entries their full loot table ([Loot and ores](#loot-and-ores)).
- **Separate from the quest book.** FTB Quests keeps its own screen
  ([ADR 0006](../decisions/0006-quests-ftb-quests-generated-from-data.md)); quests point to guide entries, and the
  guide never shows quest progress.
- **It follows the player's chapter.** In chapter 2 the player reads about chapters 1 and 2, not 3.
- **It leaves some things to Olympus** (Matěj, RLM-188): from chapter 4 on, a realm entry may end with a hint such
  as "… Olympus citizens could know more about this prehistoric realm.", and the campaign hints themselves come from
  Olympus's NPCs ([realm design](realms.md#the-hub-realm)).
- **English only**, like the rest of the game.

## Screen

```text
+------------------------------------------------------------------------------+
| Realmforge Guide                                   [ search...           ] X |
+---------------------------+--------------------------------------------------+
| Realms Dungeons Bosses    | Catacombs                              Chapter 2 |
| Character Basics          | Dungeon in Undervault, levels 9-12               |
+---------------------------+--------------------------------------------------+
| Chapter 1                 | An open crypt under Undervault's mines...        |
|   Overworld               |                                                  |
| Chapter 2                 | Where      Undervault, below the third mine      |
|   Undervault              | Entry      a dungeon key from the mine trader    |
| > Catacombs               | Bosses     The bone warden (level 12)            |
|   The bone warden         | Loot       In this dungeon, you can find:        |
|   ...                     |            [item] [item] [item] [item]           |
| Chapter 3: locked         | Related    Undervault, The bone warden           |
|                           |                                         [< Back] |
+---------------------------+--------------------------------------------------+
```

The Character tab opens its sub-tabs as a second row:

```text
+---------------------------+
| Realms Dungeons Bosses    |
| Character Basics          |
|  Classes Races Talents    |
|  Stats                    |
+---------------------------+
| Chapter 1                 |
| > Warrior                 |
|   Mage                    |
+---------------------------+
```

- **Tabs nest:** a tier-1 tab can hold tier-2 sub-tabs. The tier-1 tabs are Realms, Dungeons, Bosses, Character
  (sub-tabs Classes, Races, Talents and Stats) and Basics, for the remaining systems (leveling and the level cap,
  chapters, dungeon instances, later professions and enchanting).
- **Spells sit with what grants them:** a class entry lists its spells and a race entry its innate spells (every
  race's racial ability, [architecture 10.1](../architecture.md#101-races-and-classes)), each with its icon and a
  tooltip (school, cost, cast time, cooldown, the level that unlocks it), generated from the class or race data. A
  spell that another system grants, such as summoning a mount (M4), appears in that system's Basics entry. A Spells
  sub-tab would repeat these lists; searching a spell's name finds the entry that lists it.
- **Left:** the tabs, then the entries of the selected tab grouped by chapter, newest chapter last. A chapter the
  player has not reached is one line ("Chapter 3: locked") without titles, so nothing is spoiled. Character and Basics
  entries mostly belong to chapter 1 and are readable from the start; a system that arrives with a later chapter
  names that chapter.
- **Right:** the selected entry: title, chapter, a one-line summary, the text, a facts block and related entries.
  Item and spell icons in the facts block show their tooltip on hover; a related entry opens on click. Back returns
  to the previous entry; the guide reopens where the player left it.
- **Search** (top right) replaces the left list with the matching entries of all tabs while it holds text.
- Names in the sketches are examples, not content.

Rewards are not a separate list: each realm, dungeon and boss entry lists what it gives, and searching an item's
name finds the entries that list it ("where does this drop?").

### Look

- **Style:** the "modern fantasy hybrid" mockup Matěj picked: a dark navy panel, a thin gold border, a gold crest.
- **Frame, for now:** round 2's RD Fast frame c (`art/spike-guide-ui/candidates/frame_r2/rd-fast-c.png`), chosen by
  Matěj on 2026-10-13: 384x272, horizontal, a 3 px gold border and a near-black navy panel. Its known flaws are filled
  brown corner wedges instead of the mockup's filigree and a light-blue line along the top edge.
- **Tab icons:** round 1's RD Fast icons (`art/spike-guide-ui/candidates/icon_<tab>/rd-fast.png`: realms, dungeons,
  bosses, basics and the locked chapter), accepted on 2026-10-12. The Character tab still needs one, made with the
  guide's art (Retro Diffusion, so in a local session).
- **Font:** a custom heading font needs only ASCII, with the vanilla font as its fallback provider.

## Loot and ores

What EMI Loot and EMI Ores showed now lives in the entries, generated like every other fact, so it follows the data:

- **Realms: ores.** A realm entry lists the ores found in its dimension, generated from the dimension's ore
  features (Overworld's from vanilla's).
- **Dungeons: notable loot.** One line, "In this dungeon, you can find: {items}", not a loot table: the items that
  no other source gives (the pipeline's "every item obtainable" rule,
  [architecture 6.1](../architecture.md#61-stages), knows every source) plus the rare ones (below a drop chance the
  generator sets when the guide is built).
- **Bosses: the full loot table.** Every item the boss can drop, with its chance per kill in %, computed by the
  generator from the loot table's pools, rolls, weights and chance conditions. A condition it cannot turn into a
  chance fails validation until the generator learns it, so the guide never shows a wrong number.

## Data: `content/guide/`

Guide entries are content like everything else
([ADR 0005](../decisions/0005-content-pipeline-yaml-single-source-of-truth.md)): one YAML file per entry.

```yaml
# content/guide/catacombs.yaml (sketch; names are examples)
id: catacombs
category: dungeon                 # realm | dungeon | boss | class | race | talent | stat | basics
chapter: realmforge:chapter_2     # the realmforge:chapter whose players can read it
subject: realmforge:catacombs     # optional: the dungeon type, boss, dimension, class, race, talent tree or stat
title: {en_us: Catacombs}
summary: {en_us: "Dungeon in Undervault, levels {level_min}-{level_max}"}
text:
  en_us: |
    An open crypt under Undervault's mines...
related: [realmforge:stone_realm, realmforge:bone_warden]
keywords: {en_us: [crypt, undead]}
requires: []                      # optional extra unlocks, same types as dimension access
```

- **Categories map to tabs:** `class`, `race`, `talent` and `stat` are the Character tab's sub-tabs; the others are
  tier-1 tabs.
- **Facts come from the subject, never from prose** ([architecture 6.3](../architecture.md#63-conventions)): the
  generator fills the facts block and placeholders such as `{level_min}` from the dungeon type, boss, dimension
  rules, class, race, talent tree or stat rules the entry describes (level range, where it is, how to enter, its
  bosses and their levels, its ores or loot; a class's resource and spells, a race's bonuses and innate spells,
  what a stat point gives). A changed boss level changes the guide with it.
- **One source for quests and the guide (PR-2):** a quest names a guide entry (`guide: realmforge:catacombs`) and
  its FTB Quests description gets the entry's summary and a "see the guide" line, generated from the same text.
  Teaching quests and Basics entries share their text the same way, so nothing is written twice.
- **Generated:** a synced datapack registry `realmforge:guide_entry`
  (`data/realmforge/realmforge/guide_entry/<id>.json`) with the category, chapter, subject, related entries, the
  computed facts and translation keys (`guide_entry.realmforge.<id>.title`, …); the texts go into the generated
  `lang/en_us.json`.
- **Validation** (Python): the chapter, subject, related entries and requirements exist; the subject fits the
  category; `requires` uses only requirement types the server recomputes on ([Unlock rule](#unlock-rule)); every
  placeholder resolves; every loot chance can be computed; every realm, dungeon type, boss, class, race, talent tree
  and stat has a guide entry once the guide ships, and every spell a player can get appears in one.

## Unlock rule

- **An entry is readable once its chapter is reached:** chapter 1 from the start, chapter N once chapter N − 1 is
  completed (its chapter advancement, [architecture 16](../architecture.md#16-progression-and-quests)).
- **Optional `requires`** uses the requirement types the architecture defines for unlocking dimensions and dungeons
  (`realmforge:requirement_type`, [architecture 5.3](../architecture.md#53-type-registries)), for an entry that should
  wait for more than its chapter, such as a secret boss.
- **The server decides** ([principle 1](../architecture.md#2-principles)): it evaluates the rule and sends the
  player the set of readable entry ids on login and after `/reload`, and recomputes it whenever a requirement
  type's input can change: any advancement criterion granted or revoked
  (`AdvancementEvent.AdvancementProgressEvent`, both `ProgressType`s) for `chapter_completed` (chapters complete
  through their advancements) and `has_advancement`, a level-up for `level_at_least`, and a class change for
  `class_is`. Not `AdvancementEarnEvent`: NeoForge posts it only for advancements with a display (still so in
  26.1.2), and never on a revoke. It sends the set again only when it changed, since every criterion of every
  advancement (recipe unlocks included) posts the event. Validation accepts in `requires` only the requirement types
  with such a trigger, so a type added later needs its trigger here first. The client shows and searches only the
  readable entries. The texts themselves reach every client with the registry and the lang files, which is fine:
  locking prevents spoilers, it does not keep secrets.

## Search

- Runs on the client over the readable entries: title, keywords, summary, text, and the names of the items and
  spells in the facts block.
- Ignores case. Ignoring accents too is cheap and may stay, but is no longer a requirement, since the game is
  English only.
- Orders matches by where they hit (title, then keywords, then summary, then text, then item and spell names), then
  by chapter and title. Results update while typing; Enter opens the first one.
- A few hundred entries are expected, so a plain scan per keystroke is enough; no index.

## Code shape

For the task that builds it ([architecture 4 and 18](../architecture.md#4-code-layout)):

- a `guide/` feature package: `GuideFeature`, the `realmforge:guide_entry` registry, the unlock rule as plain Java,
  and an owner-only payload with the readable entry ids;
- `client/screen/GuideScreen` and `client/input/GuideKey`, drawing from a `GuideView` view model in common code
  (tabs and sub-tabs, grouping, filtering, search, ordering), built from vanilla widgets (`EditBox`, a selection
  list, buttons, wrapped text, `GuiGraphicsExtractor#item` for icons);
- tests: pytest for the schema, validation and generation (the ore lists, the notable loot, the loot chances);
  JUnit for the unlock rule and the search (ordering); GameTests that a completed chapter advancement adds the next
  chapter's entries to the payload, and that an entry gated by `requires` (`has_advancement`, `level_at_least`)
  joins it in the same session once the player earns that advancement (one without a display included) or reaches
  that level, and leaves it when the advancement is revoked.

Size M to L for the screen, the pipeline, the loot and ore facts and the entries of the content that exists then;
split it if it grows (the screen and pipeline first, then the loot and ore facts). Each later chapter adds its
entries with its content.

## Milestone

**M4** (The world grows; Matěj, 2026-10-13), which brings chapter 3 with its twin realms (Sunwastes and
Everwinter) and more dungeons and bosses, so the unlock rule matters and there is content to look up. The entries
for the content of chapters 1 and 2 (Overworld and Undervault, M1) are written in the same milestone. M1 (v0.1.0) has
one sealed realm, its dungeons and one boss, which the quest book describes; the guide there would widen the
vertical slice. Q1 (the quest pipeline, M1) keeps quest descriptions as data, so a quest
can later point to a guide entry without rewriting it.

## Decisions

Matěj's answers of 2026-10-13 (RLM-116); none is open.

1. **The design is approved**, with nested tabs and the new Character tab (Classes, Races, Talents, Stats) beside
   Realms, Dungeons, Bosses and Basics. Where spells go was left to the design: with what grants them, a class, a
   race or another system ([Screen](#screen)).
2. **Milestone:** M4.
3. **Default key:** `J`, unless a mod in the pack already uses it (checked when it is built; RLM-42's Dark Vision key
   takes `N`).
4. **Locked chapters:** one "Chapter N: locked" line.
5. **Basics:** the systems too, not only the world.
6. **Loot and ores**, replacing EMI Loot and EMI Ores: realms list their ores, dungeons their notable loot, bosses
   their full loot table with drop chances ([Loot and ores](#loot-and-ores)).

Decided earlier and recorded with these: our own screen instead of GuideME, the look ([Look](#look)), English only
with optional accent-insensitive search, and an ASCII heading font.
