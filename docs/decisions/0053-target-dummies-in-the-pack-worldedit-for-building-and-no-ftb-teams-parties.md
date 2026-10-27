# 0053. Target dummies in the pack, WorldEdit for building only, and no FTB Teams parties

Date: 2026-10-25

Status: Accepted (amends [0013](0013-modpack-composition.md) and [0044](0044-third-party-mods-by-milestone.md))

## Context

Three pack tickets of one batch (RLM-393, RLM-206, RLM-382, 2026-10-25):

- **Target dummies for everyone** (Matěj, 2026-10-23, RLM-393): MmmMmmMmmMmm's dummy with a hay-bale head joins the
  pack. Maren's Trial (RLM-397) uses it, dressed in a zombie head and coloured leather armour, and players can
  practise on it. It does not replace the QA mod's training dummy. Its 26.1.2 build (3.0.1) is a release, but it
  needs Moonlight Lib, and every Moonlight build for 26.1.2 is a beta (Modrinth, checked on 2026-10-25), while 0013
  allows releases only, apart from the exceptions it names.
- **WorldEdit for Matěj's building, not for players** (Matěj, 2026-10-23, RLM-393): he builds arenas and halls by
  hand and needs WorldEdit, which the [research note](../research/third-party-mods.md) and 0044 reject for the pack.
  A packwiz optional mod would still ship to players as an option.
- **FTB Teams parties** (RLM-206): FTB Quests keeps quest progress per FTB Teams team, so a player who joins someone's
  party gets that team's realm unlocks and chapter advancements at their next login. Matěj's rule for shared quests
  (RLM-156, 2026-10-15) shares a quest only with members who have it unlocked and are in the party when it is
  completed, which FTB Teams' parties break.

The beta library conflicts with 0013, so the batch named it to Matěj as the first item of its playtest, and he
accepted the exception in his answer to it (2026-10-27; CLAUDE.md, rule 10).

## Decision

1. **MmmMmmMmmMmm 26.1.2-3.0.1 joins the pack** for everyone (both sides), with **Moonlight Lib 26.1.2-4.1.1** as a
   new release-channel exception, pinned like Advanced Loot Info, so `packwiz update --all` leaves it; they are
   updated together, by hand ([pack README](../../pack/README.md#release-channel-exceptions)). Both come from
   Modrinth; their licence (Supplementaries Team License 1.5, in the jars) allows modpacks that download the
   unmodified jar from the official pages, which is what packwiz-installer does. The dummy's own floating damage
   numbers are off in the pack's config (`damage_numbers.enabled = false`): our combat text already shows every hit,
   and Matěj's playtest (2026-10-27) wants only ours.
2. **WorldEdit stays out of the pack** (0044's rejection holds for players) and serves Matěj's building only:
   - the dev client loads WorldEdit 7.4.3 (NeoForge 26.1.2, GPL-3.0) with `-PwithWorldEdit`, full or lean;
     `qa-mod/build.gradle` pins it by Modrinth version id and checks its hash like the pack's mods;
   - `./gradlew :qa-mod:installWorldEditToPrism` copies the same jar into his Prism instance, next to our mods;
     packwiz-installer deletes only the files its manifest lists, so the jar survives pack updates;
   - nothing of it enters `pack/`, and Matěj's guide is a Confluence page in his space ("WorldEdit guide (RLM-393)").
3. **FTB Teams' parties are off:** the core mod sets FTB Teams' own switch for packs with parties of their own,
   `FTBTeamsAPI.api().setPartyCreationFromAPIOnly(true)`, at common setup (`compat/ftbteams`,
   [architecture 19.4](../architecture.md#194-optional-integrations)). Creating a party through the command or the My
   Team screen is refused for every player, operators included, so nobody can invite anyone or join one, and quest
   progress, realm unlocks included, stays per player until our own parties (RLM-68) share quests by Matěj's rule
   (RLM-156). FTB Teams' server config has no such option (26.1.2.4 holds only `limited_lives`).
4. **Other mods' missing texts are the core mod's resources:** a text another mod lacks or words badly (a Key Binds
   header, FTB Teams' refusal) goes into `mod/src/main/resources/assets/<mod id>/lang/en_us.json`, like the data
   fixes of 19.4. It is no content of ours, so it stays out of `content/` and the generated `lang/en_us.json`; the game
   merges every namespace's language files, and the core mod loads after a mod whose text it replaces.

## Consequences

- The pack has a beta library outside Matěj's earlier picks; a Moonlight release for 26.1.2 replaces it when one
  exists.
- WorldEdit is a dev-client flag and a Prism task, not a pack choice: players never see it, and the GameTests and the
  dev servers never load it.
- A party for several players waits for RLM-68; FTB Teams' "My Team" screen stays (it shows the player's own team).
- Four Key Binds headers needed no code of ours: three translations in the core mod's resources, and Just Zoom labels
  its category itself. The QA mod's dev client logs an error for a raw header, so a mod update that brings one back
  shows on the next launch (RLM-382).
