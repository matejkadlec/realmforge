# In-game guide

> Design of the in-game guide (RLM-102), written on 2026-10-12 from Matěj's requirements of the same day: a key
> opens a screen about the realms, dungeons, bosses and rewards; it is searchable; chapters unlock with progression.
> Research: PR-2 in [reference-games.md](../research/reference-games.md) (one source for quest texts and guide
> entries). How things are built: [architecture](../architecture.md). Approval and milestone: Matěj's decision
> (RLM-116).

## What it is

- **A screen, not a book.** A key opens it (proposed default `J`, rebindable under Realmforge in Controls). Book
  guides such as Patchouli fit one small mod, but for a whole pack they have no search and fit little text on a
  page.
- **About the world first:** realms, dungeons, bosses and what they reward. A fourth category, Basics, explains the
  systems (stats, classes, spells, talents, later professions) as they arrive.
- **Separate from the quest book.** FTB Quests keeps its own screen
  ([ADR 0006](../decisions/0006-quests-ftb-quests-generated-from-data.md)); quests point to guide entries, and the
  guide never shows quest progress.
- **It follows the player's chapter.** In chapter 2 the player reads about chapters 1 and 2, not 3.

## Screen

```text
+------------------------------------------------------------------------------+
| Realmforge Guide                                   [ search...           ] X |
+---------------------------+--------------------------------------------------+
| Realms Dungeons Bosses    | Catacombs                              Chapter 1 |
| Basics                    | Dungeon in the stone realm, levels 6-8           |
+---------------------------+--------------------------------------------------+
| Chapter 1                 | An open crypt under the stone realm's mines...   |
|   Stone realm             |                                                  |
| > Catacombs               | Where      Stone realm, below the third mine     |
|   The bone warden         | Entry      a dungeon key from the mine trader    |
| Chapter 2                 | Bosses     The bone warden (level 8)            |
|   Desert realm            | Rewards    [item] [item] [item] [item]           |
|   ...                     |                                                  |
| Chapter 3: locked         | Related    Stone realm, The bone warden          |
|                           |                                         [< Back] |
+---------------------------+--------------------------------------------------+
```

- **Left:** category tabs, then the entries of the selected category grouped by chapter, newest chapter last. A
  chapter the player has not reached is one line ("Chapter 3: locked") without titles, so nothing is spoiled.
- **Right:** the selected entry: title, chapter, a one-line summary, the text, a facts block and related entries.
  Item icons in the facts block show their tooltip on hover; a related entry opens on click. Back returns to the
  previous entry; the guide reopens where the player left it.
- **Search** (top right) replaces the left list with the matching entries of all categories while it holds text.
- Names in the sketch are examples, not content.

Rewards are not a separate list: each realm, dungeon and boss entry lists what it rewards, and searching an item's
name finds the entries that reward it ("where does this drop?").

## Data: `content/guide/`

Guide entries are content like everything else
([ADR 0005](../decisions/0005-content-pipeline-yaml-single-source-of-truth.md)): one YAML file per entry.

```yaml
# content/guide/catacombs.yaml (sketch; names are examples)
id: catacombs
category: dungeon                 # realm | dungeon | boss | basics
chapter: realmforge:chapter_1     # the realmforge:chapter whose players can read it
subject: realmforge:catacombs     # optional: the dungeon type, boss or dimension it describes
title: {en_us: Catacombs, cs_cz: Katakomby}
summary: {en_us: "Dungeon in the stone realm, levels {level_min}-{level_max}", cs_cz: "..."}
text:
  en_us: |
    An open crypt under the stone realm's mines...
related: [realmforge:stone_realm, realmforge:bone_warden]
keywords: {en_us: [crypt, undead]}
requires: []                      # optional extra unlocks, same types as dimension access
```

- **Facts come from the subject, never from prose** ([architecture 6.3](../architecture.md#63-conventions)): the
  generator fills the facts block and placeholders such as `{level_min}` from the dungeon type, boss or dimension
  rules the entry describes (level range, where it is, how to enter, its bosses and their levels, its notable
  rewards). A changed boss level changes the guide with it.
- **One source for quests and the guide (PR-2):** a quest names a guide entry (`guide: realmforge:catacombs`) and
  its FTB Quests description gets the entry's summary and a "see the guide" line, generated from the same text.
  Teaching quests and Basics entries share their text the same way, so nothing is written twice.
- **Generated:** a synced datapack registry `realmforge:guide_entry`
  (`data/realmforge/realmforge/guide_entry/<id>.json`) with the category, chapter, subject, related entries, the
  computed facts and translation keys (`guide_entry.realmforge.<id>.title`, …); the texts go into the generated
  `lang/en_us.json` and `cs_cz.json`.
- **Validation** (Python): the chapter, subject, related entries and requirements exist; the subject fits the
  category; `requires` uses only requirement types the server recomputes on ([Unlock rule](#unlock-rule)); every
  placeholder resolves; every realm, dungeon type and boss has a guide entry once the guide ships.

## Unlock rule

- **An entry is readable once its chapter is reached:** chapter 1 from the start, chapter N once chapter N − 1 is
  completed (its chapter advancement, [architecture 16](../architecture.md#16-progression-and-quests)).
- **Optional `requires`** uses the requirement types the architecture defines for unlocking dimensions and dungeons
  (`realmforge:requirement_type`, [architecture 5.3](../architecture.md#53-type-registries)), for an entry that should
  wait for more than its chapter, such as a secret boss.
- **The server decides** ([principle 1](../architecture.md#2-principles)): it evaluates the rule and sends the
  player the set of readable entry ids on login and after `/reload`, and recomputes it whenever a requirement
  type's input can change: an earned advancement (`AdvancementEvent.AdvancementEarnEvent`) for `chapter_completed`
  (chapters complete through their advancements) and `has_advancement`, a level-up for `level_at_least`, and a
  class change for `class_is`. It sends the set again only when it changed, since recipe unlocks are advancements
  too. Validation accepts in `requires` only the requirement types with such a trigger, so a type added later
  needs its trigger here first. The client shows and searches only the readable entries. The texts themselves
  reach every client with the registry and the lang files, which is fine: locking prevents spoilers, it does not
  keep secrets.

## Search

- Runs on the client over the readable entries, in the player's language: title, keywords, summary, text, and the
  names of the items in the facts block.
- Ignores case and accents, so "lebka" finds "Lébka" (Unicode NFD, combining marks removed, lower case).
- Orders matches by where they hit (title, then keywords, then summary, then text, then item names), then by
  chapter and title. Results update while typing; Enter opens the first one.
- A few hundred entries are expected, so a plain scan per keystroke is enough; no index.

## Code shape

For the task that builds it ([architecture 4 and 18](../architecture.md#4-code-layout)):

- a `guide/` feature package: `GuideFeature`, the `realmforge:guide_entry` registry, the unlock rule as plain Java,
  and an owner-only payload with the readable entry ids;
- `client/screen/GuideScreen` and `client/input/GuideKey`, drawing from a `GuideView` view model in common code
  (grouping, filtering, search, ordering), built from vanilla widgets (`EditBox`, a selection list, buttons, wrapped
  text, `GuiGraphics#renderItem` for icons);
- tests: pytest for the schema, validation and generation; JUnit for the unlock rule and the search (accents,
  ordering); GameTests that a completed chapter advancement adds the next chapter's entries to the payload, and
  that an entry gated by `requires` (`has_advancement`, `level_at_least`) joins it in the same session once the
  player earns that advancement or reaches that level.

Size M for the screen, the pipeline and the entries of the content that exists then; each later chapter adds its
entries with its content.

## Milestone

The guide is worth opening once there is content to look up, and its unlock rule matters once there are two
chapters. M1 (v0.1.0) has one realm, its dungeons and one boss, which the quest book describes; adding the guide
there would widen the vertical slice.

**Proposal: M4** (The world grows), which brings chapters 2 and 3, realms 2 and 3 and more dungeons and
bosses; the entries for chapter 1 content are written in the same milestone. **Alternative: M2**, to ship it
with the first release after v0.1.0, when gear and loot make the reward lists useful. Either way, Q1 (the quest
pipeline, M1) keeps quest descriptions as data, so a quest can later point to a guide entry without rewriting it.

## Open questions

1. **Approve the design:** a screen with Realms, Dungeons, Bosses and Basics; rewards inside the entries and
   through item search; the chapter unlock rule; `content/guide/` as the source.
2. **Milestone:** M4 (proposed) or M2.
3. **Default key:** `J`, unless a mod in the pack already uses it (checked when it is built; RLM-42's Dark Vision
   key takes `N`).
4. **Locked chapters:** one "Chapter 3: locked" line (proposed), or hidden completely?
5. **Basics:** should the guide explain the systems too, or only the world?
