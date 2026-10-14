# Lore style guide

> How every player-facing text in Realmforge reads (RLM-43): quests, item, block and mob names, tooltips, realm and
> guide texts, books, inscriptions, dialogs and messages. These are the `LocalizedText` fields in `content/`
> ([content/README.md](../../content/README.md)). The story behind them: the [lore outline](outline.md). Approved by
> Matěj on 2026-10-13 (RLM-139).

## Voice

- **Warm, plain and adventurous.** The vision wants "more chill than stress": danger is exciting, never dreadful. A
  light touch of humour is welcome, mostly from the characters.
- **An old world with secrets.** Texts hint and let the player connect things; they never lecture. One idea per
  sentence, short sentences, concrete words (stone, rope, ore) over abstract ones (destiny, darkness).
- **Epic where it is earned:** boss names, guardians and the finale may sound grand. Everything else is everyday
  speech.
- **Never:** horror, gore, cruelty for its own sake, despair, darkness as a theme (RE-18). Enemies are dangerous,
  strange or tragic; they are not disgusting.
- **Characters sound like themselves:** Maren writes like a scholar who likes you, the Cairnfolk talk short and
  loud, Gatewright inscriptions are terse and formal and say "we". A reader should tell who speaks without a name
  above the line.

## The player

- **Address the player as "you".** Never assume their race, class, gender or name; a text that needs the class says
  so through data, not prose.
- **The Lodge calls the player a Wayfarer**, a word for any of its travellers. The player is capable and curious,
  never "the chosen one".
- **No fourth wall in story texts.** A tutorial hint may name a key or a screen, but keeps it to one plain line
  apart from the story. It names a key through its key mapping (a keybind text component, which shows the
  player's own binding), never as a literal letter: every key is rebindable, and defaults change.

## Text types

| Type | Form | Length | Example |
| --- | --- | --- | --- |
| Quest title | Title Case, like vanilla advancements; no end punctuation | 1–5 words | Raise the Gate |
| Quest description | the giver's voice, then what to do; the task list comes from data | 1–3 sentences, at most 50 words | "The plan shows a frame of plain stone around one keystone. Stand it up somewhere you can find again." Maren |
| Item, block or mob type name | Title Case, like vanilla; no flavour in the name | 1–3 words | Portal Stone |
| Character name (a unique NPC or boss) | a name, a title, or both ([Names](#names)) | 1–6 words | Torva Flintmark, the Scribe in the Stone |
| Realm name | the realm's name from the [outline](outline.md#the-realms); where a player first meets it (the guide, the sigil tooltip, the arrival title), a plain subtitle with its element follows (Matěj, RLM-139) | name 1–3 words, subtitle 2 words | The Undervault — Stone Realm |
| Flavour line (item tooltip lore) | one sentence, no mechanics | at most 12 words | "Warm to the touch, though nothing near it burns." |
| Mechanics in a tooltip | generated from data, never written in prose | - | - |
| Realm arrival line | what the player sees, hears or smells | one sentence, at most 15 words | "Cold air rises from below, smelling of dust and old stone." |
| Guide entry | summary on one line; text about its subject (a realm, dungeon, boss or system), not the plot; facts come from data ([guide](../design/guide.md)) | 2–5 sentences | - |
| Boss phase name (boss bar) | Title Case | 1–3 words | Stone Fury |
| Book page, lost page, inscription | the writer's voice | at most 120 words a page | - |
| NPC line | one idea a line | at most 25 words | "Ore first, questions after. That's how it works down here." Torva |
| Message (chat, action bar, title) | what happened and what to do next; no lore voice needed | one line | "This portal won't let you through until you finish the realm's unlock quest." |

## Words

### Fixed terms

| Use | For | Not |
| --- | --- | --- |
| Realmforge | the whole world | Minecraft, the server, this world |
| realm (lower case); a realm's name in Title Case | a custom dimension | dimension, world, plane |
| the Overworld | the starting world | the surface, home world |
| sigil | the item that opens a realm | key, rune, stone (keys are dungeon keys) |
| seal | a realm's lock | barrier, ward |
| gate | a realm portal, in story texts | door, rift |
| keystone | the realm selector, in story texts | switch, controller |
| guardian | a realm's main-path boss in story texts | final boss |
| shard | the guardian's drop for the next sigil, in story texts | core, heart |
| the Void | what lies past the last realm | the abyss, the dark |
| the Chain of Seals, the chain | the order of the realms | the path, the progression |
| Wayfarer | the player, when the Lodge speaks | hero, champion, adventurer |

Item names come from content, so a story text that mentions an item uses its exact in-game name; the words above are
for the story around it.

### Word rules

- **Facts from data, never from prose** ([architecture 6.3](../architecture.md#63-conventions)): levels, damage,
  cooldowns, counts and drop chances go through the generator's placeholders (`{level_min}`), so a balance change
  never leaves a text wrong.
- **Title Case** for names and titles follows the label rule in [content/README.md](../../content/README.md):
  every word capitalized except articles, conjunctions and prepositions of up to three letters in the middle
  (`Home With the Shards`, `Ring of the Deep`). Whole sentences stay in sentence case.
- **American spelling in `en_us`,** as in vanilla: armor, color, gray. The docs keep their own spelling.
- **Story texts use story words:** "the seal knows you now", not "realm unlocked". Messages and UI may use game
  terms (level, cooldown, quest).
- **Third-party mods keep their names:** a Waystone is a Waystone, a Warp Scroll a Warp Scroll; never rename
  another mod's item.
- **Vanilla things keep their vanilla names.** The lore may give one a line (ancient debris as the slag of the Sun
  Court's forges), never a new name.
- **The Nether and the End never appear in lore:** Realmforge has neither
  ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)).
- **No real world:** no brands, real places, real people, religions, politics, modern slang, memes or emoji.
- **No other games:** no names, quotes or in-jokes from other games, WoW included, even as a nod.

## Names

### Name rules

- **Pronounceable on first read.** One to three syllables per part, no accents in `en_us` names, and no
  apostrophes inside invented words (a plain English possessive, as in the Wayfarers' Lodge, is fine).
- **One name per thing,** the same in every text. A name that changes is a bug.
- **Places:** one English compound (the Undervault) or `the <adjective or noun> <noun>` (the Void Cathedral).
- **Never:**
  - names from other games: the list in the research report's
    [licence section](../research/reference-games.md#reference-games-at-a-glance), and anything from WoW, League of
    Legends, Hytale, Advent of Ascension or Craft to Exile 2;
  - vanilla mob or item names for something new (Warden, Vex, Ravager, Drowned, Deepslate), which would confuse
    players;
  - real places and people, brands and trademarks.
- **Check a new name** before it goes into content: search the repository and the web for it together with "game"
  or "mod". A name that is a common English word or a plain compound of two is safe.

### Per people

| People | Names | Examples |
| --- | --- | --- |
| The Wayfarers' Lodge (Overworld folk) | a plain given name and a short surname | Maren Vell |
| The Gatewrights | titles of their craft; personal names are rare and old-sounding | the First Wright, the Scribe in the Stone |
| The Cairnfolk | a short given name with hard sounds, and a clan name from stone or tools | Torva Flintmark |
| The Hewn | no personal names: "Hewn" and their role | Hewn Sentinel, Hewn Chanter |
| The Wellkeepers | longer given names with open vowels, and the oasis they come from | - |
| The Gilded | titles of a court long gone | - |
| The Kettlefolk | homely nicknames and family names | - |
| The Thawed | hard, old-sounding names; no real Norse gods or places | - |
| The Emberwrights | a given name and a craft name | - |
| The Chainkeepers | named by the link they keep | the Keeper of the Ninth Link |
| The Unforged | no names: what they look like | - |
| Bosses and guardians | a title, or a name and a title | the Unbroken |

Names are filled in as each realm's chapter is written. Matěj approved the outline's names (RLM-139); every new
name follows the [name rules](#name-rules).

## English only

The game ships `en_us` alone (Matěj, 2026-10-13, [vision](../vision.md)), so every text is written in English and
there are no rules for other languages. A community translation would ship as a resource pack keyed by the same
translation keys ([content/README.md](../../content/README.md)).

## Checklist

Before a text is committed:

- [ ] It reads well aloud and fits its length in [Text types](#text-types).
- [ ] The world is Realmforge, and every term is the one in [Fixed terms](#fixed-terms).
- [ ] No number the data owns is in the prose.
- [ ] Nothing assumes the player's race, class, gender or name.
- [ ] Every new name passed the [name rules](#name-rules) and is used the same way everywhere.
- [ ] It spoils nothing from a later chapter (the guide and the quest book lock by chapter).
- [ ] It stays inside the [outline](outline.md); a new piece of story goes into the outline first.
