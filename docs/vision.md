# Project vision

> Translated on 2026-10-11 from the owner's Czech original (Matěj's brief of 10-11 Oct 2026, kept almost verbatim). The English version is now the source of truth. It is written in the owner's voice ("I" = Matěj).
>
> Project name: **Realmforge**, mod ID `realmforge`, QA mod ID `realmforge_qa`, Java package `io.github.matejkadlec.realmforge`. Research background: [research-notes.md](research-notes.md). Decisions: [decisions/](decisions/README.md). Architecture: [architecture.md](architecture.md). Plan: [roadmap.md](roadmap.md). Realms: [design/realms.md](design/realms.md).

## Owner

I'm a Python full-stack developer (Django, vanilla JS) with 5 years of experience, plus a bit of C/C++/C#, SQL, Linux and ETL. I have only minimal experience with Java and Minecraft modding. We vibe-code this project: AI does most of the work, I set the direction, review and playtest.

Communication with me is in English. Everything in the repo (code, documentation, commit messages, issues) is in English.
The game is English only too (decided 2026-10-13): there will never be Czech texts from me. If someone else makes a translation, it can ship as a resource pack keyed by the same translation keys ([content/README.md](../content/README.md)).

## What I want to build

An RPG experience for Minecraft, open source on GitHub. I'm making it mainly for myself, both to play it and for the fun of developing it. Form: hybrid (our own core mod + proven libraries and QoL mods as a modpack).

- TL;DR: a World of Warcraft-like mod/modpack (the Wrath of the Lich King version; every time I mention "WoW" I mean this expansion, definitely not retail WoW, which is absolutely tragic), with inspiration from other RPG packs and also from Hytale, which people call "MC 2.0", or, from a completely different genre but with interesting boss fights that are actually fun, Hades II.
- Specifically:
  - custom races/classes
    - multiple ways to play the game: generally melee/ranged/mage, but also e.g. battle mage or summoner
    - dual class
  - character leveling (level caps per story chapter)
  - custom spells/skills, some from the race/class, some from items in the game
    - self-buffs, debuffs against bosses, single-target spells, AoE spells, special spells like Teleport or Angel Wings from Iron's Spells 'n Spellbooks
    - spell schools (classes of spells)
  - custom talents (talent tree)/glyphs
    - talents/glyphs integrated with skills and spells (spell power, spell resistance, cooldown reduction, cast time reduction, strengthening a specific spell school, upgrading a spell/skill through a talent, ...)
    - talents that have a big impact on the game, e.g. +50 melee damage/-50% ranged damage (a very simple example; I probably wouldn't want that particular talent)
  - stats and some stat bonuses for milestones (STR, DEX, INT...; 10 STR = +10% attack damage)
  - custom dimensions (e.g. 5-10 or even more new dimensions), procedurally generated
  - improved/overhauled Nether/End, OR maybe remove them completely; I wouldn't mind at all as long as we have our own dimensions. FTB Architect Exodus also has only custom dimensions and that modpack felt incredibly polished to me. The Nether and End are in every, literally every pack; this would make ours stand out, and I'd be fine with that. (Status: decided on 2026-10-12: both are removed, and a late fire realm replaces the Nether; [ADR 0025](decisions/0025-remove-the-nether-and-end.md).)
  - improved/overhauled villages, villagers and trades
  - custom dungeons; 100+ structures, from small structures like a single building up to large dungeons like the Trial Chambers from newer vanilla MC
    - procedurally generated dungeons: not 1 fixed structure; 1 dungeon type can generate in a practically unlimited number of ways, it will look similar and follow the same rules, but no two dungeons will ever be the same (like most modded dungeons or vanilla mineshafts)
    - NOT EVERY DUNGEON HAS TO END WITH A BOSS!
    - make some dungeons, maybe even most of them, instanced like in Hytale, i.e. a custom dimension that contains only that dungeon
    - treasure rooms, traps, puzzles, special dungeons like "you do this whole dungeon on a horse you're given and you can't dismount"
    - I don't want parkour, or only a minimal amount
    - I don't want long pointless walks, hordes of trash mobs, or trash loot like iron nuggets just so the chest doesn't look empty; every piece of loot should be good for something and give materials/gear/maybe food or potions
  - custom mobs; hostile and friendly (animals)
  - custom bosses
    - boss phases!
  - custom boss mechanics that tie in with the skills and talents
  - custom progression; e.g. locked dimensions/unavailable crafting recipes
  - custom status effects; e.g. in phase 2 of the fight a boss gives itself effect X and applies effect Y to the player when it hits them; custom potions, or skills that grant a status effect, e.g. something like Oakskin from Iron's Spells 'n Spellbooks
  - custom gear upgrades; gems, affixes, reforging
  - item rarities (gems and affixes too): common-uncommon-rare-epic-legendary
  - new enchantments, improved vanilla enchantments
  - new weapons, armor sets, foods, blocks
  - custom curios with custom effects
  - custom systems, something like professions in WoW, but with the option to do several at once, so to speak; herbalism, mining, alchemy (e.g. tied to brewing potions, which can be strong and long-lasting, say an hour, like in WoW), smithing (e.g. tied to weapons/armor), jewelcrafting (e.g. tied to curios), tailoring, ...
    - I don't want literally every WoW profession, and some of them don't even make sense to me in MC, e.g. herbalism; I'm only giving it as an example, and it doesn't even have to be called "professions"
    - professions could be leveled, higher level = better _something_, e.g. crafting stronger potions, a higher chance of high-tier gear, etc.
  - a pet system; one pet max, possibly more when playing a specific class, while some class may have it locked; a pet that is genuinely useful, can cast spells, has skills that can also be leveled, maybe its own talents, something like the Hunter pet in WoW, but definitely not limited to animals; it can have dialogues with the player, some kind of "love" progression, the option to resurrect it
  - RNG item chests (as an item, not a block, although of course I also want loot chests in dungeons), also common-legendary, that roll a random item as a reward when opened
  - 500+ quests, questlines, for all sorts of things; crafting, killing mobs, bosses, farming, cooking, reforging, professions, ... with rewards; repeatable quests (more or less something like a shop, or turning in e.g. monster loot); quests available per chapter, i.e. locked until the previous chapter is completed
  - keep vanilla mechanics like armor, armor toughness, HP, hunger, XP (just change XP, and I'd heavily overhaul the whole of Enchanting; I don't like how RNG-based it is)
    - I'd definitely let armor/armor toughness/HP go far beyond vanilla values
  - QoL: map, minimap, what-am-I-looking-at, JEI, waypoints, waystones, backpack ("Sophisticated Backpacks"), a simple mod for organizing items (for me clearly the best at this are "Tom's Simple Storage" + "Sophisticated Storage"), hearthstone?
  - mounts that make sense; again I really like WoW here, where the mount is summoned the moment you use it and is hidden otherwise, or, in newer versions, it's immortal and stays nearby
    - mounts should be worth getting; better than Elytra/Angel Wings for flying mounts, better than a Speed II potion for ground mounts
  - decorative pets: not for now
  - better combat (the "Better Combat" mod is great)
  - all systems should more or less fit into each other and work together as one big ecosystem
  - default vanilla settings:
    - keep inventory on death: ON (`keepInventory`)
      - no grave mod
    - destructive mob actions: OFF (`mobGriefing`; see the side effects in [research notes](research-notes.md#default-gamerules))
    - immediate respawn: ON (`doImmediateRespawn`)
    - "during X explosions, some blocks won't drop their loot": OFF (= every explosion always drops all destroyed blocks/items) (`blockExplosionDropDecay`, `mobExplosionDropDecay`, `tntExplosionDropDecay`)
  - I also want night vision by default, probably best as a toggleable potion effect, just like in the Otherworld pack, where it's called Dark Vision and some races have it; in this mod/modpack everyone would have it
  - seasons can change, but I wouldn't put that into the first version, or even the first several; a mod like Serene Seasons is simply not for now, although I kind of like the idea (Status: open question, explicitly not for now.)
  - shaders: the Iris Shaders mod + Complementary Shaders (we're really not making our own)

## What I don't want

- a custom texture pack; at least not now, it would be a huge amount of work and I don't mind the vanilla look at all
- soulslike, or extra-hard progression; I want it challenging but not "1 mistake = death", reasonably balanced, more chill than stress and perfect movement
  - I hate it when a modpack is unbalanced and on the first night in the Overworld I get one-shot by mobs that teleport onto me...
  - RLCraft is exactly what I DON'T want
- no thirst, no temperature, nothing similar
- "over-modded": I want it big, extensive and complex, but not complicated, and not a gazillion systems, pointless weapons, pointless armor sets; every mob, boss, dungeon, race, class, skill, talent and item should have its place in the game. That doesn't mean no weak talents like +1 attack damage, but the whole talent tree shouldn't look like that (I'm referring to the Passive Skill Tree mod, which I think is pretty good, but it can definitely be done better; WoW talents are also great)
- I hate flying bosses; I want exactly 0 of them
- I hate the blindness/darkness status effects; if a few bosses use them during some phase, OK, but it shouldn't be frequent
- I hate endless grinding, and RNG grinding most of all (WoW raids are really terrible for me, you go for months for a single item; definitely not this)
- some degree of grind is of course fine, it just shouldn't be 5 hours for 1 item
  - when the grind consists of several different activities, e.g. this dungeon, this boss, this crafting, it doesn't actually feel like grind, it's just playing the game
  - meaningful preparation for bosses, like making potions and food with special effects, is fine with me too
- PERFORMANCE IMPROVEMENTS! (performance is a requirement)

## Themes, setting, story

- Themes: Fantasy, Magic, Extra Large, Adventure and RPG, Roguelike, Medieval
- Story: the story in MC modpacks has never interested me much, but we'll add something "in the background"; AI will come up with it, it doesn't have to be extra deep.
- The game world is called **Realmforge**: whenever the story, lore, quests or any texts (items, books, dialogs) refer to the in-game world, this name is used.

## What v0.1 should contain

Everything above, but feel free to cut down everything: fewer races, fewer classes, fewer skills, fewer mobs, dungeons, bosses, maybe just 1-3 custom dimensions, simply less of everything; we'll add more as we go (weeks to months of work). Some complex systems can be skipped at the start.

- I often see Iron's Spells 'n Spellbooks modified in modpacks, typically nerfed. I'd rather narrow it down to a smaller number of spells and spell schools; it feels "over-spelled" to me: 90% of the spells are useless or just worse versions of another spell (why a single-target X for 10 damage when there can be a single-target Y for 20 damage?).
- Hence a custom magic system, with Iron's Spells as inspiration at most; no spellbooks, and a common-legendary tier for spells doesn't make sense to me; I'd rather give spells a level or power.

## Inspirations and previous experience

- I'm playing the modpack Otherworld [Dungeons & Dragons] (Forge 1.20.1). What I enjoy: the story campaign with content locked by chapters and level caps, races/classes/subclasses, leveling through aptitudes, level-scaled loot with affixes and gems, choice rewards with real gear, bosses with lore and locator items, MineColonies, cooking from Farmer's Delight, familiars, and repeatable trade quests (like in Cisco's Medieval RPG).
- Packs I've played through: Cisco Medieval RPG Ultimate (meh), FTB Presents Architects Exodus (S-tier), Medieval MC MCC4 and MMC5 (decent), Craft to Exile 2 (didn't finish: too much grind; the core mod is fairly good, but it isn't balanced and is too hard for me).
- Must-have mods (directly, modified, or as inspiration): Jade (+ JadeAddons), JEI, EMI, EMI Ores, EMI Loot, EMI Trades, JEED, EnchantmentDescriptions, No Recipe Book (could be custom), Mouse Tweaks (could be custom), Toast Remover (could be custom), Quark (not a necessity), Regions Unexplored, Xaero's Minimap/Worldmap (add icons for our custom mobs, otherwise they show up only as dots), Waystones, Just Zoom, Better Combat, FTB Quests, TreeChop, AppleSkin, Overflowing Bars, Sophisticated Storage, Sophisticated Backpacks, Tom's Simple Storage, TrashSlot, Sodium/Lithium/other optimization mods (our own performance mod sounds good too), Damage Numbers, YDM's MobHealthBar.
  - Availability on the pinned platform (NeoForge 26.1.2 since 2026-10-12) and current replacements: see [research notes](research-notes.md#2-platform) and [ADR 0013](decisions/0013-modpack-composition.md) (modpack composition).
- Take only as-is: MineColonies, Distant Horizons, Iris Shaders, EntityCulling.
- Custom even though they exist: our own debugging/testing mod made specifically for this project; Lightweight Inventory Sorting (takes a minute, fully compatible with everything else).
- Inspiration, but build our own: Explorer's Compass (great at its core, but unfinished and buggy), Iron's Spells 'n Spellbooks, Apotheosis, AutoLeveling, Farmer's Delight, Dave's Potioneering, Passive Skill Tree, Medieval Origins, Dummmmmmy (too basic).
- More inspiration: WoW (WotLK), Hytale, Hades II (boss fights), Craft to Exile, and older versions of Advent of Ascension (MC 1.12; the newer versions are terribly cut down).
- I want the core of the pack to be 100% our own: our own quests, dimensions, dungeons, mobs, bosses, boss fights, skills, spells, talents, glyphs, professions. I'm oversaturated with dungeons and bosses from other mods, and 95% of them feel terribly one-dimensional to me. Third-party mods only for QoL that doesn't really impact the game itself (Jade, Xaero, JEI/EMI...), with exceptions like Regions Unexplored or Waystones.

## Procedural generation (questions for the future)

- Procedural quest generation like in No Man's Sky (endless quests): for now as a complement to the fixed campaign (contracts); see [research notes, section 9](research-notes.md#9-procedural-generation).
- Procedurally generated rules for dungeons, bosses, mobs and dimensions ("full No Man's Sky"): rather as a standalone mod, separate from this project; for now just a curiosity.

## Open questions

Not decided yet; not to be treated as decisions:

- Seasons: explicitly not for now (not in v0.1, nor in the first several versions).
