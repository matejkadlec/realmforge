# Lore style guide

> How every player-facing text in Realmforge reads (RLM-43): quests, item, block and mob names, tooltips, realm and
> guide texts, books, inscriptions, dialogs and messages. These are the `LocalizedText` fields in `content/`
> ([content/README.md](../../content/README.md)). The story behind them: the [lore outline](outline.md). Approved by
> Matěj on 2026-10-13 (RLM-139). Updated on 2026-10-16 (RLM-188): realm names take no article, and the rules that
> limited names became recommendations. Updated on 2026-10-20 (RLM-245, RLM-265): Asgard and Atlantis, and when a
> sentence puts "the" before a realm. Updated on 2026-10-27 (RLM-464) for the approved chapters 1 and 2
> ([chapters](chapters/01-the-ominous-tale.md), RLM-351) and the names of RLM-357: their fixed terms, the Nether in
> the lore, and "Name, the Title". How to write new story and quests: the [practices](practices.md) (RLM-413).

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
| Item, block or mob type name | Title Case, like vanilla; no flavour in the name | 1–3 words | Gatewright Bricks |
| Character name (a unique NPC or boss) | a name, a title, or both, written "Name, the Title" with no article before the name ("Fight Illusioner, the Ominous Chief"; without the title, "the Illusioner") ([Names](#names)) | 1–6 words | Maren Vell; Halcyar, the Dust-Crowned; the Stonebound |
| Realm name | the realm's name from the [outline](outline.md#the-realms), never with an article as a name, label or title (Matěj, RLM-188); a sentence that refers to the realm takes "the" where it reads naturally, as with the Nether ("Enter the Nether", "I was at the Nether"), and every realm's entry quest is titled "Enter the {realm}" (Matěj, 2026-10-19, RLM-265); where a player first meets it (the guide, the arrival title), a plain subtitle with its element follows (Matěj, RLM-139) | name 1–3 words, subtitle 2 words | Undervault — Stone Realm |
| Flavour line (item tooltip lore) | one sentence, no mechanics | at most 12 words | "Warm to the touch, though nothing near it burns." |
| Mechanics in a tooltip | generated from data, never written in prose | - | - |
| Realm arrival line | what the player sees, hears or smells | one sentence, at most 15 words | "Cold air rises from below, smelling of dust and old stone." |
| Guide entry | summary on one line; text about its subject (a realm, dungeon, boss or system), not the plot; facts come from data ([guide](../design/guide.md)) | 2–5 sentences | - |
| Boss phase name (boss bar) | Title Case | 1–3 words | Stone Fury |
| Book page, lost page, inscription | the writer's voice | at most 120 words a page | - |
| NPC line | one idea a line; a long speech plays as several lines | at most 25 words | "Ore first, questions after. That's how it works down here." A Cairnfolk Keykeeper |
| Message (chat, action bar, title) | what happened and what to do next; no lore voice needed | one line | "This portal won't let you through until you finish the realm's unlock quest." |

## Words

### Fixed terms

| Use | For | Not |
| --- | --- | --- |
| Realmforge | the whole world | Minecraft, the server, this world |
| realm (lower case); a realm's name in Title Case, without an article except where a sentence reads naturally with one ("Enter the Deepholm") | a custom dimension | dimension, world, plane |
| Overworld, without an article | the starting world, where every gate opens | the surface, home world |
| the Wall | the five forged realms around Overworld | the barrier, the ring |
| Ithari | the long-lived people of the Gatewrights, the Wayfarers and Skyhearth | elves, gods, the old race |
| signet; each realm's is the "Signet of the {Realm}" | the item that opens a realm (Matěj, 2026-10-20) | key, rune, stone (keys are dungeon keys), sigil (the old name), master signet (there is none, RLM-395) |
| seal | a realm's lock | barrier, ward |
| gate | a realm portal, in story texts | door, rift |
| keystone | the sealstone, in story texts | switch, controller |
| Gatewright Bricks, Gatewright Sealstone | the gate's blocks (RLM-394) | Realmforge Bricks, Realmforge Sealstone, Realmforge Stone (the old names) |
| keeper (a Sealbound) | an Ithari bound into a realm's seal, such as the Stonebound | guardian (that is the trial), warden, prisoner |
| the Stonebound | Undervault's keeper | the Scribe in the Stone (the old name) |
| guardian | a realm's main-path boss in story texts, a trial the Gatewrights set | final boss, keeper (that is the Sealbound) |
| shard | the guardian's drop for the next signet, in story texts | core, heart |
| the Void | what lies past the last realm | the abyss, the dark |
| the Chain of Seals, the chain; the road, in the Ithari's words | the order of the realms | the path, the progression |
| Wayfarer | the player, when the Lodge speaks | hero, champion, adventurer |
| Skyhearth's Mirror | the item through which Maren speaks to the player (Matěj, 2026-10-25) | Heaven's Mirror (the old name) |
| Maren's Image | Maren as the mirror shows her, a little see-through | ghost, hologram, projection |
| Wayfarer's Compass | the compass that points at old places (RLM-319) | map, tracker |
| Keykeeper | a generated Cairnfolk who keeps the catacomb key, one in each Cairnfolk house (RLM-448) | merchant, shopkeeper, Torva (she lives in Skyhearth) |
| Plinth Knight | the statue knights of Halcyar's Chamber (RLM-451) | statue, golem |

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
- **Vanilla things keep their vanilla names.** The lore may give one a line (ancient debris as the Gatewrights'
  forge-stock, fused under Undervault's floor), never a new name.
- **The Nether and the End appear in lore only as worlds lost in the war,** 700 years ago: the Nether was destroyed
  with its gates, and the End was reshaped into Unmade Isles (Matěj, 2026-10-22 and 2026-10-27, RLM-351). The game
  has neither ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)), so no text sends a player there:
  netherite comes from Undervault's forge-stock, and the Ancient merchants sell what was saved.
- **Tone, not the real world:** no politics, modern slang, memes or emoji. A modern word may stay only as a
  deliberate reference inside the world, when Matěj gives it that reason: "Hella tough" in "The Right Tool" is a nod
  to the Nether (2026-10-27, [practices](practices.md#3-story-rules)).
- **No quoted text from other games,** WoW included, and no in-jokes, even as a nod. Names from other games are a
  matter of [recommendation](#name-rules), not a rule.

## Names

### Name rules

- **Pronounceable on first read.** One to three syllables per part, no accents in `en_us` names, and no
  apostrophes inside invented words (a plain English possessive, as in the Wayfarers' Lodge, is fine).
- **One name per thing,** the same in every text. A name that changes is a bug.
- **Places:** a realm's name has no article (Undervault, Unmade Isles), though a sentence may put one before it
  ([Text types](#text-types)); other places are one English compound or
  `the <adjective or noun> <noun>` (the Void Cathedral).

**Recommendations, not rules** (Matěj, 2026-10-15, RLM-188: "Original names are better, but not always"):

- **Prefer original names.** A real word or a name another game also uses is fine when it fits: malachite and
  Everwinter. Borrowed mythology is not: Asgard, Odin and Atlantis became Skyhearth, Taveth and Deepholm (Matěj,
  2026-10-22, RLM-357).
- **Don't reuse a vanilla mob or item name for something new** (Warden, Vex, Ravager, Drowned, Deepslate): players
  confuse them.
- **Avoid another game's distinctive invented names, characters and trademarks:** the ones in the research
  report's [licence section](../research/reference-games.md#reference-games-at-a-glance), and the made-up names of
  WoW, League of Legends, Hytale, Advent of Ascension or Craft to Exile 2. Common words they use are fine. Avoid
  real people and brands too.
- **A quick look helps:** searching a new name with "game" or "mod" shows whether another game made it famous. A hit
  is a reason to think twice, not a ban.

### Per people

| People | Names | Examples |
| --- | --- | --- |
| The Ithari (the Wayfarers' Lodge, Skyhearth's people) | a plain given name and a short surname, or "Name, the Title" | Maren Vell; Edric, the Explorer; Bonnie, the Jeweler |
| The Gatewrights | titles of their craft; personal names are rare and old-sounding; a keeper's name is given to the stone and never spoken | the First Gatewright, the Stonebound |
| The Cairnfolk | a short given name with hard sounds, and a clan name from stone or tools; outside Skyhearth a Cairnfolk is generated, with a random name and a role, never a unique name (RLM-448) | Torva Flintmark; a Keykeeper |
| The Hewn | no personal names: "Hewn" and their role | Hewn Sentinel, Hewn Chanter, Hewn Miner, Hewn Overseer |
| The Wellkeepers | longer given names with open vowels, and the oasis they come from | - |
| The Gilded | titles of a court long gone | - |
| The Kettlefolk | homely nicknames and family names | - |
| The Thawed | hard, old-sounding names, rather than real Norse gods or places | - |
| The Emberwrights | a given name and a craft name | - |
| The Chainkeepers | named by the link they keep | the Keeper of the Ninth Link |
| The Unforged | no names: what they look like | - |
| Bosses and guardians | "Name, the Title", or a title alone (Matěj, 2026-10-22, RLM-357) | Astone, the Unbroken; Halcyar, the Dust-Crowned; Illusioner, the Ominous Chief |

Names are filled in as each realm's chapter is written. Matěj approved the outline's names (RLM-139, RLM-188); every
new name follows the [name rules](#name-rules) and weighs their recommendations.

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
- [ ] Every new name follows the [name rules](#name-rules), weighs their recommendations, and is used the same way
  everywhere.
- [ ] It spoils nothing from a later chapter (the guide and the quest book lock by chapter).
- [ ] It stays inside the [outline](outline.md); a new piece of story goes into the outline first.
- [ ] Story and quest texts follow the [practices](practices.md), and a chapter's text agrees with its approved
  [chapter](chapters/01-the-ominous-tale.md).
