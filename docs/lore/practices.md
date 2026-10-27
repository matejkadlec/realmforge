# Storyline and quest practices

> The rules for writing Realmforge's story and quests (RLM-413), from what Matěj corrected in chapter 1, "The Ominous
> Tale" (RLM-351). He asked for them on 2026-10-24, after approving chapter 1: the next chapters should start from
> what was learned, not from scratch. Jira holds them (RLM-413's description, the practices of 2026-10-24), and the
> Confluence page "Storyline and quest practices" in his space shows the same text. The wording is kept. A rule is
> added only from a later decision by Matěj, with its source: those of 2026-10-25 to 2026-10-27 came from chapter 2's
> reviews (RLM-351), and each says so. One renaming changed two rules: Heaven's Mirror is Skyhearth's Mirror in story
> rules 8 and 13 (Matěj, 2026-10-25). The texts these rules shaped are [chapter 1](chapters/01-the-ominous-tale.md)
> and [chapter 2](chapters/02-the-weight-of-stone.md).

## What this is

Every agent reads this before writing a chapter's story beats, its full text or its quests. These are the rules behind
what Matěj corrected in chapter 1, "The Ominous Tale" (RLM-351, 23 and 24 October 2026). That took two rounds on the
beats (13 comments), one on the text (15 comments) and six rounds of answers in chat. He approved chapter 1 on
24 October 2026.

The lore style guide ([style.md](style.md), RLM-139) still applies in full. Its rules appear here only where chapter 1
broke them. Each rule names the chapter 1 case that taught it, so the next chapter does not repeat it.

## 1. Before you write

1. **Read the newest sources first.** In order:
   - Matěj's decisions in Jira: RLM-351's comments, RLM-357's names, and every ticket they name;
   - the newest story outline draft;
   - [`docs/design/realms.md`](../design/realms.md) for the realm;
   - the style guide.

   Older docs drift. Where sources disagree, the newest decision by Matěj wins, and the page says which older text it
   overtakes. Chapter 1: draft 8 still had the "master signet" and Realmforge Stone, but both were already gone
   (RLM-395, RLM-394).
2. **Check every structure in its mod's data before a beat depends on it.** Open the jar and read the structure's
   pieces (NBT): mobs, spawners, loot tables, notable blocks, biome tags and placement. Then check the realm table in
   `docs/design/realms.md` (Structures from other mods), because each structure appears only in its own realm.
   Chapter 1 broke this three times:
   - an evoker was written into the Tower of the Undead, which holds none;
   - the Monster Maze's "boss" room holds no mob;
   - Dungeons Enhanced's Deep Crypt was used in the Overworld, but it belongs to Undervault (RLM-316).
3. **Check vanilla mechanics in** `build/mc-sources/`. Raids start by day or by night, and only a pillager captain
   drops an Ominous Bottle.
4. **Check the milestone** for every beat, and tag the beat with it:
   - what exists now (M1 has no custom NPCs and no dialogues, so the quest book speaks);
   - which ticket brings each system a beat needs.
5. **Keep a list of the structures the chapter uses**, on the main path and in optional quests, so none is used twice.
6. **Read the repo's facts from a fresh `origin/master`** (`git fetch origin`, `git show origin/master:<path>`), never
   from an older checkout or memory. An old checkout hid W3's gem Sentinels, and Undervault's ores were described
   without reading `content/worldgen/`. _(Added 2026-10-25, chapter 2's first review, RLM-351.)_
7. **When a beat leans on a recorded rule, find the comment that decided it and name it.** A rule Matěj doesn't
   remember may be a misreading: Undervault's full-speed tool exception (RLM-188 → RLM-194). _(Added 2026-10-25,
   chapter 2's first review, RLM-351.)_

## 2. Working with Matěj

1. **The order of the work:**
   1. the beats;
   2. the resolved beats;
   3. the full text.

   He reads the text only after the beats are resolved. After each round, update the beats page first, then the text
   page, and keep both in sync.
2. **Answer his questions first, one bullet each, before any context.** A question he has to ask twice is a failure.
   Chapter 1: "what is Manor?" got buried in a long reply.
3. **Explain every third-party thing the first time it appears:** what it is, which mod it comes from, and why it
   fits. For a structure, give its size, its mobs and its biome.
4. **Mark the state of everything on the pages:**
   - **Proposal** marks Claude's ideas, and **Decided (date)** marks Matěj's;
   - a status line at the top says what is still open;
   - after every edit, tell him whether both pages are updated and in sync, with their version numbers.
5. **Reply to each** `@claude` comment in its own thread: what changed, where it is recorded (the ticket), and "You
   can resolve this comment." If you didn't follow the comment exactly, say why and offer to switch.
6. **When he says "answer here, change nothing until we agree", don't touch Confluence or Jira** until he agrees.
7. **Ask follow-up questions as a numbered list.** Give each question its options, with your recommendation first, so
   he can answer inline. A yes/no question needs a default: his "optional or none? then optional".
8. **Jira is the record.** Write every decision into RLM-351 and into the ticket it belongs to, and turn a new mechanic
   into a new ticket. Confluence pages are views of the decisions, not their record.
9. **When a proposal turns out wrong, say so plainly and replace it.** Chapter 1 proposed the compass in "the first
   crypt", but crypts are underground, so a player can't find one without a compass.
10. **Page mechanics:**
    - edit in HTML, so comment anchors survive;
    - check for new comments before each publish;
    - after publishing, compare the page's text with your file.

    Never edit pages the sync generates (they say "Don't edit this page"), and never edit RLM-357's tables: Matěj
    edits those himself.
11. **Introduce our own characters and places like third-party things, with where they come from.** Torva and the
    Scribe came from the lore outline without saying so. _(Added 2026-10-25, chapter 2's first review, RLM-351.)_
12. **Keep beat numbers stable while he reads** ("I'll proceed with beat #16"). _(Added 2026-10-25, chapter 2's first
    review, RLM-351.)_
13. **Every beat says where it lives:** in the game, quest text or lore only, in a table per chapter (his request).
    _(Added 2026-10-25, chapter 2's first review, RLM-351.)_
14. **When he asks for the answers in chat** ("read all of them, do what they say, and answer in this chat"), answer
    there instead of in each thread, and keep the comment anchors on the rewritten text so he can resolve the
    threads. _(Added 2026-10-27, chapter 2's text review, RLM-351.)_

## 3. Story rules

1. **Every line follows the outline's rules and says exactly what they mean.** A loose line reads as a contradiction.
   Chapter 1's line made it sound as if Wayfarers can't travel the realms. In fact they pass every gate, but the
   trials stand aside for them, so they never grow stronger.
2. **Every villain needs a cause the player can follow.** Give each antagonist a history, a grievance and a goal.
   Matěj rewrote the illagers that way:
   - they were left behind and forced to fight and hide;
   - they stayed bitter for 700 years;
   - now they claim the gates.
3. **No cheap lines.** "Left for someone like you" was "weak and cheap". A longer passage with meaning beats a short
   cliché, and a big reveal carries its reasoning.
4. **No twist for its own sake.** The illusion villages were dropped, because pillagers are bad on their own. A twist
   must pay off, and its purpose is explained to Matěj plainly when it is proposed. The chapter 8 guard (no cells,
   bars, cages or "prisoner") applies only around the founder and the seals. Say so wherever it matters: the
   Bastille's cages are fine.
5. **A medieval world has no modern words.** "Home address" broke the theme.
6. **Say plainly what the player must do, and why.** "The player knows what the theft costs" confused him.
7. **After a pick, remove what leaned on the dropped choices.** No sentence may depend on a beat he didn't pick.
   Chapter 1 kept "the same way it broke the village's illusion" after the village beat was gone.
8. **Keep item logic simple: the player keeps what they earn.**
   - Nothing is handed over, copied or cut. Matěj asked, "Why does Maren need it?"
   - Only Skyhearth's Mirror crosses between Skyhearth and the Overworld (RLM-395).
9. **Make lines personal with dynamic strings** where a line speaks to the player: `{className}` now, and later race
   and specialization. Example: "Strength comes quickly to you, {className}." The article before a dynamic word ("a"
   or "an") comes from data (RLM-397).
10. **Use the fixed terms everywhere.**
    - "Signet", never "key": keys are dungeon keys.
    - Never "master signet".
    - Each realm's item is the "Signet of the {Realm}".
    - The portal blocks are Gatewright Bricks and the Gatewright Sealstone; the compass is the Wayfarer's Compass.
    - Names come from RLM-357.
11. **Signets** (RLM-395):
    - a final boss drops the next signet's shard at 100 %, never the signet itself;
    - every signet's recipe has its shard in the middle, 4 of the previous realm's ore or gem on the sides and 4 of
      the new realm's theme block in the corners;
    - the Lodge Merchant sells a lost signet up to 3 times.

    _Later decision (Matěj, 2026-10-26, RLM-432):_ the twins' signets keep the shard in the middle but take 2 gems,
    2 Netherite Scraps and two pairs of theme blocks instead (the Signet of the Sunwastes: 2 Rubies at the top and
    bottom, 2 Netherite Scraps left and right, 2 Sand and 2 Red Sand in opposite corners; the Signet of the
    Everwinter: 2 Sapphires, 2 Netherite Scraps, 2 Snow Blocks and 2 Ice in the same places). The Signet of the
    Undervault keeps the rule above.
12. **Clues live only in the story:** no map items and no clue items. The quest journal, the Wayfarer's Compass and
    Xaero's map lead the player.
13. **How Maren speaks:** the quest journal holds every quest's short text in her voice. Skyhearth's Mirror is only
    for the 5 to 8 scenes per chapter where the story turns.
14. **A story moment that changes the world may become a mechanic.** "The gate lights" became RLM-406. Mark every
    effect a beat describes as one of three: story only, an existing ticket, or a proposed ticket.
15. **Unique NPCs live only in Skyhearth;** elsewhere NPCs are generated with random names and a role (the Cairnfolk
    Keykeepers, RLM-448). A generated role never gets a unique name. _(Added 2026-10-25, chapter 2's first review,
    RLM-351.)_
16. **Friendly and hostile structures must look nothing alike** (Cairnfolk houses vs the Hewn's Mine). _(Added
    2026-10-25, chapter 2's first review, RLM-351.)_
17. **The story never claims what the game contradicts.** Matěj: "is it really good telling it in story if untrue
    in-game?" (a debris seam under a house that the game doesn't build). The reverse holds too: a game-only shortcut
    doesn't go into the lore. The Miners never break blocks in the game, but in the story they dig the debris out:
    "They never break it because to code such behavior would be insane." _(Added 2026-10-27, chapter 2's text review,
    RLM-351.)_
18. **A line must answer the question it raises.** "so that someone can follow you" left him asking why; the
    hall-of-welcome line opened "what walkers? what trial? what arena? what tremors?". Name who, what and why in the
    line itself, or cut it. _(Added 2026-10-27, chapter 2's text review, RLM-351.)_
19. **Name the referent.** "Your thief" was unclear; say "the thief who stole your signet". _(Added 2026-10-27,
    chapter 2's text review, RLM-351.)_
20. **A boss needs no story reason to repeat.** "There's an infinite amount of every boss, why this specific boss would
    have it explained"; once a boss is defeated in the story, it stays defeated. _(Added 2026-10-27, chapter 2's text
    review, RLM-351.)_
21. **A modern word may stay when it is a deliberate reference inside the world.** "Hella tough" in the quest "The
    Right Tool" is a nod to the Nether: "it's a nether reference, keep it. I wouldn't use it if it didn't have the word
    'hell' in it." The no-slang rule still holds for everything else; flag a modern word once, and keep it if Matěj
    gives it such a reason. _(Added 2026-10-27, RLM-351 chat.)_

## 4. Quest rules

1. **A structure quest is "visit" plus one goal inside that structure** (RLM-320). The visit is always a condition, so
   a goal farmed elsewhere doesn't count. Goals that work:
   - collect a loot item the structure holds (10 Zombie Heads in YUNG's Catacombs);
   - reach a place (the Tower of the Undead's top floor);
   - defeat a number of mobs inside it;
   - win the structure's own trial, or earn its own advancement.
2. **Nothing is added to third-party structures:** no elite keepers and no story bosses. Matěj: "those dungeons are
   made not to have elite keepers". Use what the structure already holds, such as the Illager Hideout's own evoker.
3. **Numbers come from data.** Count the item or mob in the structure's pieces, then pick a number well below what a
   typical structure holds (10 of about 25 heads). Confirm it in a test world.
4. **No two similar structures in a row, and no structure twice.** Matěj: "not another tower". Optional quests use
   structures no other quest uses.
5. **Essential items come early, without RNG.** The compass comes from the first loot container a player opens outside
   a village, and a recipe makes another. Never hide the tool that finds structures inside a structure the player must
   find first.
6. **Every progression item has a source that never runs out:** a boss that drops its material at 100 %, a recipe, or
   the Lodge Merchant.
7. **Optional quests** reward XP, a few items and coins. They may tie loosely to the story or not at all, and not every
   structure needs a quest. High-level structures such as the Ancient City wait for later.
8. **Give drops a use.** Heads and trash loot sell at the Lodge Merchant for low prices, without limit: 64 Rotten Flesh
   for 10 copper, a Zombie Head for 5 copper (RLM-407).
9. **Respect vanilla:** raids stay vanilla, by day or by night, and the outpost with its captain's bottle comes before
   them.
10. **Scenes work without cinematics.** A walk-and-talk scene comes first (RLM-408), and the camera framework comes
    later (RLM-396).
11. **Instanced dungeons have no fixed place in the world.** A Dungeon Portal opens them wherever it stands, so the
    story never places an instance ("at the far end of the cave").
12. **Matěj designs some quests himself, in full detail** (Maren's Trial). Record his spec word for word in its ticket
    and build around it.
13. **No place check when a mob lives only in one structure.** "Defeat 3 Hewn Overseers" is enough, since they spawn
    nowhere else, and a kill just outside the structure should count. _(Added 2026-10-27, chapter 2's text review,
    RLM-351.)_

## 5. Form

1. **Quest titles are in Title Case**, the headline style Jira titles use.
   - Capitalize every word except articles (a, an, the), short conjunctions (and, but, or, nor, for, so, yet) and
     prepositions of up to three letters (of, in, on, at, to, by, for) inside the title.
   - The first and the last word are always capitalized.
   - Examples: "The Crypts Break Open", "The Maze Under the Dark Oak", "The Tower of the Undead", "Home With the
     Shards".
   - A beat's title is its quest's name, so beat titles follow the rule too. The style guide already had this rule
     (RLM-150), and chapter 1's beats broke it.
2. **Each realm's entry quest is "Enter the {realm}"** (RLM-265).
3. **An NPC line has at most 25 words;** a long speech plays as several lines.
4. **Bosses and NPCs are written "Name, the Title".** With the title there is no article in front: "Fight Illusioner,
   the Ominous Chief". Without it, "the Illusioner".
5. **American spelling:** armor, neighbors, traveler, recognize.
6. **A chapter title has no number,** only the level range from data.

## 6. Checklist before you hand a chapter to Matěj

1. Every beat's facts are checked against Jira, the outline, the realm design, the mods' data and the milestone.
2. Every quest has a goal, a number from data and a milestone tag.
3. No structure is used twice, and each stands in its own realm.
4. Fixed terms, Title Case, line lengths and American spelling are right.
5. Every alternative stands on its own and comes with your recommendation.
6. Every effect a beat describes is marked: story only, an existing ticket, or a proposed ticket.
7. Open questions sit at the end, numbered, each with options and a recommendation.
8. Both pages are in sync, the status line is current, and the decisions are in Jira.
9. Every beat says where it lives (in the game, quest text or lore only), in the chapter's table. _(Added 2026-10-25,
   chapter 2's first review, RLM-351.)_
10. Every fact about the repo was read from a fresh `origin/master`. _(Added 2026-10-25, chapter 2's first review,
    RLM-351.)_
