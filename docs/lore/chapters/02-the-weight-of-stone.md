# Chapter 2: The Weight of Stone

> The full text of chapter 2 (Undervault, levels 6–18), from start to end, as Matěj approved it on 2026-10-27 (RLM-351:
> "after you change this, it's approved from me"; "I read it whole and everything else is good"). It was written on
> Confluence from the beats he resolved on 2026-10-25 and 2026-10-26, revised for his 42 comments of 2026-10-27 (version
> 7 of the text page), and copied here for RLM-464. Where the older beats page (version 16) differs, this text wins; a
> later decision by Matěj changes this file and names its source. RLM-351's comments are the decision log, and RLM-357
> holds the names. The story around it is the [outline](../outline.md), its texts follow the [style guide](../style.md),
> and it comes after [chapter 1](01-the-ominous-tale.md).

**How to read it**

- Each part is the story first, then **In the game**: the quests, structures and tickets behind it, with the beat
  numbers of the beats page.
- Maren's lines in quotes are drafts for her dialogue and the quest journal. The Keykeeper's and the Stonebound's lines
  are drafts for their quest text.
- **Proposal** marks something new that the beats needed, all listed again at the end ([Proposals](#proposals)).
- **Milestones:** M1 has the realm, the netherite quests, the Hewn Sentinels and Chanters, the catacombs, the stone
  arena and Astone, with the quest book as the only voice. M2 brings the Hewn's Mine, the Golem Works, Halcyar's
  Chamber, the gem ores and the mirror scenes; Rubia's quest and Skyhearth come in M3 or later. The part [In M1](#in-m1)
  shows M1's short version.

## Part 1: Under the World

The signet warms in your hand, the gate lights, and you step through.

Cold air rises from below, smelling of dust and old stone. You stand in a cave so wide that its far walls are lost in
the dark. Glowstone glitters in the ceiling, high above, like stars that came too close. Pale stone runs away from your
feet in hills, pillars and cracks, and the only sound is your own footsteps.

This is Undervault, the stone realm: the first realm the Gatewrights ever forged.

You call Maren's Image. For once she says nothing at first. She only looks around, and up, and around again.

> "I've read about this place all my life. It's bigger than the books. Everything down here is bigger than the books."

Then she tells you why she sent you. Every seal on the chain has a keeper, and in time every keeper is relieved: a new
one comes from Skyhearth and takes his place. The Undervault's keeper has held his seal longer than any of them, and no
relief has reached him since the war, seven hundred years ago. Now the Hewn, the Gatewrights' stone servants, block
every way to his chamber, so even if the Lodge had someone to send, they couldn't get through.

> "Three things, then. Clear the Hewn out of his halls, so that the keeper who relieves him can get through one day.
> Reach him. And pass the trial in his chamber: it guards the shards of the next two signets. Take what it gives. It's
> meant for whoever earns it."

She hesitates, which she rarely does.

> "He has waited a very long time. Be kind to him."

**In the game**

- **Quest 1, "Enter the Undervault"** (beat 2; M1; the entry quest's title rule, RLM-265): step through the gate. The
  arrival title is "Undervault — Stone Realm", and the arrival line is "Cold air rises from below, smelling of dust and
  old stone."
- **Quest 2, "Why the Wayfarer Goes Down"** (beat 3): a mirror scene in M2 (with the arrival); in M1 the same lines are
  quest book text. In M2 her Image looks around and up before she speaks, played by the scene runner that Maren's
  walk-and-talk scene brings (RLM-408).
- **Levels:** about 6 to 10 near the arrival, 10 to 14 farther out and 14 to 18 in the deepest parts (the realm design).
  Mobs take the nearest player's level, never below the realm's floor (W1, RLM-303).
- **The glowstone** is already in Undervault's worldgen (`content/worldgen/`).

## Part 2: The Forge-Stock

Maren's Image comes back with a first errand for you, and she sounds almost embarrassed by how much she wants it done.

> "The Wall needed stone and metal, more than any quarry in the Overworld could give. So the Gatewrights forged this
> realm first, and kept their forge-stock in it for every realm after. It's still here. Under your feet."

The forge-stock is ancient debris: dark, heavy lumps of old metal, sunk into the rock below the walkable floor and fused
there over an age. It never lies in the open. You have to dig for it, and it doesn't come easily.

> "It came from the Nether, long before the war, carried in through the Nether's gates: metal born in fire. When the
> Nether was destroyed, its gates were destroyed with it. What lies under this floor is the last netherite in
> Realmforge."

The Ancient merchants, who still sell the Nether's stone for coins, know where it went. The Cairnfolk, when you meet
them, will only tell you that it is "hard to dig but worth it".

You dig up four pieces. Wedged in the rock beside the last one lies a smith's pattern plate, left by the Gatewrights
with their stock: a template for turning the metal into something worth carrying.

> "Of course they left instructions. They always left instructions. You should make some copies of it before you use
> it."

The stone down here is tougher than anything above, and the realm's own wood and earth are just as stubborn. A diamond
pickaxe will get through it, slowly. Netherite is better: it lasts longer, digs faster, and doesn't burn.

> "Make yourself the best pickaxe there is. You'll be swinging it a lot."

**In the game**

- **Quest 3, "The Gatewrights' Forge-Stock"** (beats 4 and 5; M1): dig up 4 Ancient Debris. The reward is one Netherite
  Upgrade Smithing Template. The number is confirmed in a test world. The errand's opening is a mirror scene in M2.
  Ancient debris generates only below the walkable floor, at a medium rate, never exposed to air (RLM-449). The Nether's
  story is the quest's text.
- **Quest 4, "The Right Tool"** (beat 6; M1): craft a Netherite Pickaxe. Its text (decided 27 October; "Hella" is a nod
  to the Nether): "Hella tough. Lasts longer, digs faster and doesn't burn on top of that. You may want to make some
  copies of the smithing template first." The copies come from the copy recipe, which takes Vaultstone instead of
  netherrack (RLM-447). There is no tool-tier rule: Undervault's own blocks are harder (RLM-450), and every pickaxe
  mines at its normal speed.
  - Vaultstone and Vaultstone Bricks have twice deepslate's hardness, the other Undervault stones less, the ores
    deepslate's, and the Vaultwood set, Vaultdirt and the Vaultgrass Block twice their vanilla counterparts.
  - Vanilla blocks and other mods' blocks keep their own hardness.
- **Side quests "An Axe to Align" and "A Shovel to Suit"** (beat 6; optional; renamed 27 October): a Netherite Axe and a
  Netherite Shovel, each rewarding vanilla XP and maybe an item. Their templates come from Undervault's dungeon loot, or
  from the copy recipe, which takes Vaultstone instead of netherrack (RLM-447).

## Part 3: The Stone Awakens

You aren't alone down here. Somewhere in the dark, someone is singing a mining song, loudly and badly, in time with a
pick.

> "The Cairnfolk," Maren says, and she sounds pleased. "The realm was forged with its people, and they awaken with the
> stone. Their sole purpose is to mine it, as the Gatewrights forged them to. They've never seen the sky, so it's better
> not to mention it. It upsets them."

The Cairnfolk are a sturdy, cheerful mining folk, and they are amazed that anyone comes from above at all. They are not
what blocks the way, though.

The Hewn are. The Gatewrights carved them to guard their storehouse and their catacombs, and for a very long time they
stood still. Then the cracks began to shake the cave, and the tremors woke them. With no one left to give them orders,
they do the last thing they were told: keep everyone out.

The Hewn Sentinels hold the doors with stone fists. The Hewn Chanters stand back, able to form a stone out of thin air,
throwing it afterwards.

> "That's the Hewn's work, all of it," Maren says. "The Gatewrights built things to last. They didn't build them to
> listen. To anyone other than them, that is. They're not cruel. They're obedient. That's worse, in a way: you can't
> argue with an order nobody is left to cancel."

Now and then the whole cave shivers. Dust sifts down from the ceiling, a rockfall closes an old tunnel, and somewhere a
mine caves in. The tremors come from below, from the seal chamber nobody has reached.

**In the game**

- **The Cairnfolk** (beat 7): quest text in M1 and M2. Their houses and Keykeepers come in M4 (RLM-448).
- **Quest 5, "Overcome the Obedient"** (beat 8; M1; renamed 27 October): defeat 10 Hewn Sentinels and 10 Hewn Chanters.
  Both are in the game now (W3) and stay Undervault's most common mobs. More Undervault mobs come later: the Hewn Hound,
  the Stone Snail and the Stone Bat (RLM-430, M4).
- **The tremors** (beat 9): story only in M1. The real ambient tremor (a short camera shake and dust from the ceiling,
  no blocks broken) comes later (RLM-431, M4).

## Part 4: The Hewn's Mine

Deeper in, the tunnels change. They grow square and wide, held up by beams of Vaultwood. Rails run along every one of
them. Minecarts stand on the rails, some empty and some loaded with chests, waiting for someone to haul them away.

This is the Hewn's Mine, and it's still fully operational.

Hewn Miners work the rock with tireless picks, following the seams in search of ancient debris. Where they find it, they
dig it out, and here and there a dark lump of it shows in the walls, laid bare and not yet taken. Hewn Overseers guard
the shafts, and they attack anyone who isn't Hewn on sight.

Miners are not looking for a fight. However, try to reach for the debris, or open a chest, and the Miners turn on you.
Their picks crack armor as easily as stone, and you'll want yours whole when an Overseer comes for you.

> "I think I understand them," Maren says slowly. "The Gatewrights carved the Miners to dig the forge-stock and the
> Overseers to guard it. Then the Gatewrights left, and nobody came to take the ore away. So the Miners keep digging,
> and anyone who takes the Gatewrights' ore is a thief. You included."

You have to find the Overseers, one after another, and defeat them.

**In the game**

- **Quest 6, "Find the Hewn's Mine"** (beat 10; M2, RLM-429): visit it.
- **Quest 7, "The Overseers' Watch"** (beat 10; M2; the title is a Proposal): defeat 3 Hewn Overseers. They live only in
  the mine, so the quest doesn't check where they fall (decided 27 October).
- **The mine** (RLM-429, Matěj's design): an open-world structure in Undervault with 5×5 shafts of the Vaultwood set,
  rails, empty minecarts and minecarts with Vaultwood Chests, loot in Vaultwood Chests, ancient debris exposed in the
  rock (an exception to the realm design's rule), and no boss.
- **Its mobs** (RLM-428): about 32–48 Hewn Miners and 8–12 Hewn Overseers placed when it generates, none despawning. The
  Overseers attack on sight, with a charge and a stab.
- **When a Miner turns hostile** (decided 27 October): when a player breaks an ancient debris block, or opens a chest,
  within 8 blocks of it and in its line of sight, with no wall between them (it doesn't have to be facing the player).
  Among the ores only ancient debris counts, and only a broken block: starting to mine and stopping does nothing.
  Attacking an Overseer turns Miners hostile too (decided 25 October, confirmed 27 October). In the game Miners never
  break blocks; in the story they dig the debris out.
- **Armor Break** (decided 27 October): only the Miners' hits give Armor Break II, never the Overseers'. It keeps the
  Miners from being ignored: they are easy to kill, but a player who fights an Overseer with Armor Break on pays for it.
- **The lore** (decided 27 October): the Miners dig, the Overseers guard, and anyone who takes the Gatewrights' ore is a
  thief to them. The Hewn were carved, never crystallized.

## Part 5: The Golem Works

Not everything the old builders left down here is stone.

Deep under the floor of the cave lies a works of a different kind. Only its top rises out of the floor, half garden,
half ruin, and an elevator shaft leads down into halls, stairs and crossings of copper and iron: gears frozen mid-turn,
parts of bodies that were never finished. Someone made servants of metal here, not of stone. The Cairnfolk give the
place a wide berth and won't say who built it.

> "Gatewright work? Perhaps," says Maren. "Their smiths made more than statues. But whoever ran this place, they left in
> a hurry, and they left the machines running."

Deep inside, in the works' great room, the golem spawner still holds its trial. Step close, and it wakes. Golems of
copper, iron and gold march out in waves: great metal golems first, then smaller ones with swords, bows and crossbows,
each wave stronger than the last. There are seven waves in all, and the last two bring the works' best.

When the seventh wave falls, the spawner goes quiet, and so do the works, for the first time in a very long time.

**In the game**

- **Quest 8, "The Golem Works"** (beat 11; M2; main story, decided 26 October): finish all 7 waves of the Abandoned
  Factory's golem trial, "Factory Remnant", which the mod marks with its own advancement ("Mechanical Warfare"). Nothing
  is added to the trial or replaced, and there is no boss (decided 27 October). Its place after the Hewn's Mine is
  decided too (27 October).
- **The structure:** Golem Dungeons' Abandoned Factory, placed under Undervault's floor with its stones swapped for
  Undervault's (RLM-316). **Decided (27 October):** its lowest part lies at about y 5–6, just above the bedrock and
  never in it. The factory is about 53 blocks tall, from its great room to the top of its garden, and Undervault's floor
  lies at about y 32 (24 to 40), so its great room, halls and stairs sit in the rock, and its top (the upper part of the
  elevator shaft, the garden and the pillar) rises out of the floor, as Matěj allowed. Its pieces are a top with a
  garden, an elevator, halls, stairs, crossings and dead ends, and one trial room with the golem spawner. The golems are
  mostly copper, iron and gold, from Modular Golems, with some random armor: big metal golems and humanoid golems with
  melee weapons, bows, tipped arrows and rocket crossbows, and in waves 6 and 7 the strongest mix. The trial reward
  includes the Ancient Forge, a golem weapon. The mod's Eye of Ancient Factory locates the factory; its recipe needs an
  Eye of Ender, to be checked against the Nether removal (RLM-316).
- **The lore** (decided 27 October): someone made servants of metal here, perhaps the Gatewrights' smiths, and left the
  machines running. Who it was stays unknown in chapter 2. Skyhearth can explain it later, for example through a
  merchant who sells functional blocks.

## Part 6: Ore for the Cairnfolk

The singing you heard on the first day leads you to a Cairnfolk house, cut into the cave wall. It has a front of
Vaultstone Bricks, a round door, Vaultwood beams and lanterns, an ore cart by the door and an anvil beside it. Inside, a
fire burns in the hearth. Nothing about it looks like the Hewn's Mine, and that is very much on purpose.

The Keykeeper who lives there is short, broad and loud. They have heard about you already.

> "You're the one from above! Ha! Sit. No, don't sit. Ore first, questions after. That's how it works down here."

The Keykeeper holds the key to the Gatewright catacombs, and would much rather someone else used it. The price is
sixteen Emeralds and eight Ancient Debris. It isn't cheap, and the Keykeeper doesn't haggle.

While you are there, you tell the Keykeeper about the Illusioner, who stole the stone signet from the Lodge's hall to
reach the Gatewrights' storehouse. The Keykeeper laughs so hard that the lanterns shake.

> "The thief who stole your signet wanted the Gatewrights' metal? Ha! Let him try taking it from the Hewn."

The Keykeeper has opinions about gems, too. Where the stone signet pressed hardest into the Void, the stone set into
gems: ruby, sapphire and topaz, in small veins. The Cairnfolk call them the signet's marks, and a mine with gems in it
is a mine the Gatewrights meant.

> "Since the cracks began, the old veins go dull and new ones push out of the walls. When the stone gives gems for free,
> it's sick."

And never mine near a sleeping Hewn. Every Hewn Sentinel was carved around a gem, a ruby, a sapphire or a topaz, and it
shows through the cracks in its shell. The gem keeps it moving.

> "Leave a stone its heart."

**In the game**

- **Quest 9, "Ore for the Cairnfolk"** (beat 12; M1 as a quest): hand in 16 Emeralds and 8 Ancient Debris, and get the
  catacomb key (decided 27 October; it was 10 Raw Gold and 10 Emeralds). In M1 and M2 it is a quest in the quest book,
  with the Keykeeper's lines as its text and no NPC.
- **The Cairnfolk house and its Keykeeper** (beat 12; M4, RLM-448): a generated Cairnfolk with a random name in every
  house, like a villager with a profession, who trades the same and sells the key again for repeat runs. The house's
  look is a Proposal (RLM-448), sometimes with 2–3 houses together as a hamlet. Until M4 this part is quest text, and
  "they" is the Keykeeper's pronoun because each one is generated.
- **"What the Thief Wanted"** (beat 13): one of the Keykeeper's lines, quest text, about chapter 1's Illusioner. Nothing
  is built for it: there is no debris under the house (decided 27 October).
- **The ores** (RLM-449): iron, gold and emerald anywhere, below the floor too; ruby, sapphire and topaz anywhere, less
  often (RLM-433); ancient debris only below the floor; coal and diamond stay Overworld's. Gravel and tuff will generate
  in Undervault too.
- **Quest 10, "The Signet's Marks"** (beat 14; M2, with the gem ores, RLM-433): mine one ore of each gem. Only mining
  counts, not gems from Sentinels or chests. The dull veins are story only: the world doesn't change, the Cairnfolk say
  it does.
- **The gem hearts** (beat 15; in the game since W3): Sentinels come in Sapphire, Ruby and Topaz variants, and each
  drops its gem at 20 % (5 % more per Looting level).

## Part 7: The Catacombs

The key fits a Dungeon Portal Base. Set it in, and the old doorway opens onto the Gatewright catacombs.

The Gatewrights buried their dead here. Tombs line the halls, and the sarcophagi carry riddles cut into their lids, in
the blocky script the Lodge has slowly learned to read. Crystals stand in the corners, dull now. Once they steered light
through the vaults, from one hall to the next.

In hall after hall stands the same tall statue: a Gatewright holding a signet up to the light. Its boots are polished
bright. The Cairnfolk used to come down here for luck, before the Hewn woke.

You call Maren's Image in front of one of them.

> "The First Gatewright," she says. "The founder. Every hall they built has one. I suppose they liked to remember who
> started all this."

You fight your way through the catacombs to the treasury at their end. Among the old grave goods lies a heavy key: the
key to the stone arena, where the keeper waits.

**In the game**

- **Quest 11, "The Catacombs"** (beat 16; M1): clear them to the treasury. The treasury holds the arena key, every time.
- **The dungeon** (W6, RLM-36): an instanced dungeon behind a Dungeon Portal Base, opened with the catacomb key, and a
  dungeon type of its own, separate from Prisoner's Kingdom (decided 25 October), which is a real dungeon in
  Overworld, not only a test dungeon (decided 27 October, RLM-456). Each run is built for the player who opens it, so
  the story never gives it a fixed place.
- **The riddles and light beams** come with the dungeon puzzle work, so M1's catacombs may have none yet.
- **The founder's statues** (beat 17): a set piece in the catacombs' rooms (RLM-280); Maren's line is a mirror scene in
  M2. What it means is lore: it is the face the player will recognize much later (chapter 8). Nothing near it shows
  cells, bars or cages.

## Part 8: Halcyar's Chamber

One more door stands between you and the keeper, and the Cairnfolk won't go near it. Somewhere in the deep halls, they
say, there is a room nobody enters twice.

You find it: a small chamber of a palace, cut into the rock. It was bright once. The bricks are white, with gold between
them, and the gold still shows where the dust is thin. Mostly the dust is thick. Everything is gray with it: the
pillars, the pedestals, the bars across the room at the far end.

Six statues stand along the corridor, three on each side, each on its own pedestal between the pillars. Knights, by the
look of them, with their hands folded on their sword hilts.

You call Maren's Image. She goes very still.

> "That stone. We build with that stone. All of Skyhearth is made of it."

As you walk between the pedestals, the first knight lifts its head. Then it draws its sword. The Plinth Knights step
down from their pedestals, one after another, and they are quite strong.

At the end of the corridor, behind the dusty golden bars, stands the largest statue of all. Dust lies on its head like a
crown. When the last knight falls, it moves.

This is Halcyar, the Dust-Crowned. He is enormous and slow, and he hits like a falling ceiling. When he charges, the
floor shakes under you. Get out of his way: a few blows like that would fell anyone, even in netherite. But after every
charge he stands still for a few breaths, as if trying to remember what he was doing, and that is your moment.

> "I think I know what this was," Maren says afterwards, quietly. "A hall of welcome. The Gatewrights built it for
> whoever would come down one day to face the keeper's trial, as you have. The knights were to test them, and the great
> statue to judge them, before they went on to the keeper. That being said, nobody came to challenge the trial, not in
> thousands of years. Only the dust."

**In the game**

- **Quest 12, "Halcyar's Chamber"** (beat 19; M2 as a Proposal; main story, the last boss quest before Astone; RLM-451):
  defeat Halcyar, the Dust-Crowned.
- **The structure** (decided 26 October): generated in Undervault, an open-world structure like the Hewn's Mine, with
  its Plinth Knights and Halcyar placed when it generates. It needs no key. It can be one of our dungeon types placed as
  a world structure (RLM-317, architecture 15.6).
- **The Plinth Knights:** 6 statue mobs, 3 on each side of the corridor, on pedestals between pillars; they look like
  statues until a player comes close, then draw a sword and attack.
- **Halcyar** (Matěj's design): extremely strong but slow; a charge-and-attack pattern like the Hewn Overseer's, but the
  charge causes a small earthquake and hits heavily. In full netherite, a player who doesn't dodge falls in 3 hits, but
  the attacks are easy to dodge. After a charge he needs about 3–5 s to recover, and the fight lasts no more than about
  3–5 minutes. A boss bar and the bosses tag (RLM-173).
- **The blocks:** Skyhearth's white bricks with gold between them, dusted (about 25 % bright, 25 % dusted, 50 % heavily
  dusted), with pillars, pedestals and Dusty Golden Bars in the boss room.
- **Proposal, a repeat:** a chamber whose statues were all broken wakes again after a while with no player near, so the
  fight repeats and a second player finds it whole.
- **Proposal, the lore** (rewritten 27 October): a Gatewright hall of welcome, built for whoever would one day come for
  the keeper's trial. The knights were to test them and the great statue to judge them, but nobody came until the
  player. Maren's line about Skyhearth's stone is a mirror scene: a quiet hint that Skyhearth's builders learned from
  the Gatewrights.

## Part 9: The Stonebound

The arena key opens a Dungeon Portal Base onto the stone arena. You fight through its halls to their end, and there lies
the seal chamber.

Its walls are covered in murals, cut deep and filled with old paint. They show the First Gatewright raising a gate in
the Overworld, five thousand years ago. He sets a stone signet into its sealstone and opens it onto the Void. The signet
presses its pattern into the Void like a ring into wax, and a realm takes shape there: one giant cave of stone, its
people included. It is the same signet that waited in the Lodge's hall, until the Illusioner stole it and broke it. The
Gatewrights kept their forge-stock in the new realm, and buried their dead in it, and called it their vault, with stone
above and below: Undervault.

At the far end of the chamber stands the cracked seal gate, set into the wall, with a signet glowing at its heart. In
the stone of its frame is a face.

It opens its eyes.

> "A walker. We heard you in the halls. It has been a long time since we heard anyone."

This is the keeper. The Cairnfolk call him the Stonebound, because he is bound into the stone seal, as every Sealbound
keeper is bound into his.

> "Our name was given to the stone, as is the custom. It is not spoken."

He explains the trial in few words. Beyond the altar waits a guardian of living stone. The Gatewrights sealed the shards
of the next signets inside her, so that only someone strong enough to defeat her could carry them on.

You ask him about the statue in the catacombs, the one with the polished boots.

> "The founder. He came here often. He asked what the Void looked like from this seal."

He gives you the means to call the trial to the altar. Then, slowly, he says the thing he has clearly been waiting to
say.

The cracks in the chamber run through the walls, the floor and the frame around his face. They are his tiredness, made
visible.

> "We ask nothing for ourselves. Carry word to Skyhearth: this seal needs a relief."

**In the game**

- **The First Forging** (beat 1): lore only, on the seal chamber's murals and in a guide entry.
- **Quest 13, "The Stonebound"** (beat 20; M1, the stone arena's end room): reach the seal chamber. The keeper gives the
  item that calls the trial to the altar; in M1 the quest book gives it.
- **The stone arena** (W6, RLM-36): an instanced dungeon behind a Dungeon Portal Base, opened with the arena key, ending
  in the seal chamber.
- **The Stonebound** (renamed 25 October; he was the Scribe in the Stone in the lore outline): Undervault's Sealbound.
  Every sealed realm has one. His words are quest text, in the Gatewrights' voice: terse, formal, "we".
- **"The Stonebound Is Tired"** (beat 21): quest text. His request leads to Skyhearth (Part 11).
- **Proposal, the summoning item:** its name and look are open. "The Weighing Stone" would suit the trial and the
  chapter's title.

## Part 10: Astone, the Unbroken

You place the Stonebound's gift on the altar, and the arena answers.

The floor rises. Stone climbs on stone, and a colossus stands up out of the arena itself, taller than the pillars around
her. The Gatewrights set her here to weigh whoever comes for the next signets. She is a trial, not a villain. She won't
be mad at you. She will simply try very hard to stop you.

This is Astone, the Unbroken.

When you finally bring her down, she falls apart into rubble, and the rubble lies still. You broke what was supposed to
be the unbreakable.

In the rubble lies her heart, a stone that still beats faintly. Beside it lie two shards, each with a pattern pressed
into it: the Sun Signet Shard and the Ice Signet Shard.

Only by defeating the guardian could anyone take them. That is why she had to fall.

**In the game**

- **Quest 14, "Astone, the Unbroken"** (beat 22; M1, W5's boss with three phases): call her to the altar and defeat her.
  Astone is she/her (decided 27 October). In the story she falls once and for all (decided 27 October). In the game
  every run of the stone arena is a new instance with its own Astone, so the fight repeats like any dungeon boss's, with
  no story reason. The fight comes in the same run as quest 13, right after it, since the seal chamber is the arena's
  end room; if the run closes first, the summoning item stays with the player for a later run.
- **Her drops** (beat 23; decided 25 October): the Stone Heart, the Sun Signet Shard and the Ice Signet Shard, all three
  on every kill (confirmed 27 October).
- **The twins' signets** (decided 26 October, RLM-432): each shard in the middle of its recipe.
  - The Signet of the Sunwastes: 2 Rubies at the top and bottom, 2 Netherite Scraps left and right, 2 Sand in the top
    left and bottom right corners, and 2 Red Sand in the other two.
  - The Signet of the Everwinter: 2 Sapphires, 2 Netherite Scraps, 2 Snow Blocks and 2 Ice in the same places.
  - The Stone Heart goes into the Skyhearth Medallion (Part 11).

## Part 11: Home With the Shards

You call Maren's Image one last time in the stone realm, and show her the shards. You keep them; she only looks.

She turns them over with her see-through hands, very carefully, and reads the patterns in them.

> "Two realms. Two signets. Forged as twins: sun and ice. Of course they were. The Gatewrights never could resist a
> matched pair."

Then she looks at you properly.

> "You did it. All of it. And you heard him, didn't you? The keeper."

You tell her what the Stonebound asked.

> "Then I'll carry it to the council myself. And you'll have to come up and say it with me. It's time for you to visit
> Skyhearth."

The bond on your strength loosens again. The seals have weighed you twice now, and twice they let you grow.

**In the game**

- **Quest 15, "Home With the Shards"** (beat 24; M1, the chapter's last quest): show the shards to Maren's Image (a
  mirror scene in M2, quest book text in M1). The quest grants chapter 2's advancement, and the level cap rises to 30.
  Chapter 3 begins there.
- **Maren's invitation** (decided 27 October) leads into "Rise to Skyhearth" (below).

## Later (M3 or later): Rise to Skyhearth

Skyhearth admits from below only those who carry a guardian's heart. You craft the Skyhearth Medallion from the Stone
Heart and the three Undervault gems, build the platform, stand in its middle, and rise.

Maren meets you in person, for the first time, and hears the keeper's message. The council hall holds the law of relief,
carved into its wall, and a map of the Wall whose stops light up as you open them. The last one is dark. Taveth, the
king the refugees named, welcomes the walker and sends you on to the twins.

Skyhearth's people are Hearthvale's refugees. Edric, the Explorer remembers the green land and the dragon in the fire.
Bonnie, the Jeweler wants a Living Ruby, if you ever meet Rubia. Maren's archive holds the founder's old letters, and
one line in them comes back again and again: "The Void is not empty. It is patient." Maren reads them as exhaustion.

And there, blinking at a sky for the first time in her life, is Torva Flintmark: the first Cairnfolk to see one, who
came up after you opened the way. She sells the late-game dungeon keys now. She is not sure yet what she thinks of the
sky.

**In the game**

- **Quest "Rise to Skyhearth"** (beat 25; the mandatory side quest from Lv. 15): the medallion, the platform and the
  rise. The Skyhearth dimension and this quest come in M3 or later (decided 27 October; on 26 October its quests could
  still be M2), because Matěj builds part of the realm by hand and with WorldEdit and the rest generates. RLM-302 holds
  the platform and Holy Stone, RLM-245 the medallion.
- **Skyhearth's people** (beat 26): Skyhearth's unique NPCs, later. The founder's letters are lore.
- **Torva** (decided 26 October): the first Cairnfolk to see a sky; her story is written with Skyhearth's.

## Optional quests

Not part of the main story, each with "visit" plus one goal, rewarding XP, a few items and some coins:

- **"Rubia in the Ruby Seams"** (beat 18; M3 or later, RLM-253; decided 27 October: the fortress can come in M2, but her
  quest works only from M3 on): where the tremors split the deepest ruby seams, the stone itself woke. Rubia is living
  rock of ruby and stone, no servant and no villain: she guards the seams as a beast guards its den. She lives in the
  dwarf-like fortress in the rock, the Cairnfolk's old hold, which they left to her (RLM-317). She drops the Living
  Ruby, which Bonnie, the Jeweler wants in Skyhearth. She stays optional (decided 26 October).
- **"The Crypt Under the Stone"** (beat 27; M2, RLM-316): Dungeons Enhanced's Deep Crypt, moved from chapter 1. An
  underground crypt of tombs, tunnels and a treasure room, built from about ten kinds of rooms; most tombs and tunnels
  hold one or two skeleton spawners. The goal: break 2 of its spawners.
- **"An Axe to Align" and "A Shovel to Suit"** (beat 6): the netherite side quests of Part 2.
- **No quest:** Dungeons Enhanced's Dungeon Variant, a small spawner room set into the cave walls.
- **More optional quests later** (27 October), in chapters 1 and 2, in M3: crafting from gems, and an Apotheosis branch,
  with quests for each of its tier materials, affix materials, bookshelves and affixed items (RLM-457).

**The structures this chapter uses, each once:** the Hewn's Mine (RLM-429), Golem Dungeons' Abandoned Factory (RLM-316),
the Cairnfolk houses (RLM-448), the catacombs and the stone arena (W6), Halcyar's Chamber (RLM-451), the dwarf-like
fortress (RLM-317), Dungeons Enhanced's Deep Crypt and Dungeon Variant (RLM-316).

## In M1

M1 has the realm, the Sentinels and Chanters, the catacombs, the stone arena and Astone, and no NPCs or mirror scenes.
Its quest book tells chapter 2 with Maren's lines as letters and the Keykeeper's and the Stonebound's as quest text:

1.  "Enter the Undervault", with Maren's letter on why you go down;
2.  "The Gatewrights' Forge-Stock", then "The Right Tool" and its two side quests;
3.  "Overcome the Obedient";
4.  "Ore for the Cairnfolk", which gives the catacomb key;
5.  "The Catacombs", whose treasury gives the arena key;
6.  "The Stonebound", where the quest book gives the item that calls the trial;
7.  "Astone, the Unbroken";
8.  "Home With the Shards", which raises the level cap to 30.

When M2 brings the rest (the Hewn's Mine, the Golem Works, the gem ores, Halcyar's Chamber and the mirror scenes), Parts
1 to 11 replace it. Rubia's quest and Skyhearth follow in M3 or later.

## Proposals

Matěj approved the text with these proposals in it; each can still change in its ticket.

- **Maren's lines** throughout, and the Keykeeper's and the Stonebound's beyond the ones Matěj approved ("Ore first,
  questions after", "When the stone gives gems for free, it's sick", "Leave a stone its heart", "He asked what the Void
  looked like from this seal").
- **The quest order** around Matěj's quests: "The Signet's Marks" with the Keykeeper (Part 6).
- **"The Overseers' Watch"**, the title of the mine's second quest (Part 4).
- **The Cairnfolk house's look** (Part 6, RLM-448).
- **Halcyar's Chamber:** M2, the repeat after a while, the hall-of-welcome lore (rewritten 27 October) and Maren's line
  about Skyhearth's stone (Part 8, RLM-451).
- **The summoning item's name,** "the Weighing Stone" (Part 9).
- **New lines of 27 October**, written for Matěj's comments: the keeper's relief (Part 1), the Miners' picks cracking
  armor (Part 4), the Keykeeper on the thief (Part 6), the hall of welcome (Part 8) and the shards sealed inside Astone
  (Parts 9 and 10).
- **The mirror scenes:** five in M2, where the story turns: the arrival with "Why the Wayfarer Goes Down" (Part 1), the
  Forge-Stock (Part 2), the founder's statue (Part 7), Halcyar's Chamber (Part 8) and "Home With the Shards" (Part 11).
  Maren's other lines are quest journal text in her voice, and she is never in the seal chamber: the Stonebound's
  message has to be carried.
