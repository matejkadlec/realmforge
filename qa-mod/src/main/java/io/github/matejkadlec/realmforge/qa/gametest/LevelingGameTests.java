package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.character.CharacterBonuses;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterCreation.Result;
import io.github.matejkadlec.realmforge.character.CharacterData;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.KillXp;
import io.github.matejkadlec.realmforge.character.XpTable;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.progression.Chapter;
import io.github.matejkadlec.realmforge.progression.Chapters;
import io.github.matejkadlec.realmforge.progression.ProgressionRegistries;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedChapterExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedChapterExpectations.ChapterExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedClassExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedLevelingExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedLevelingExpectations.KillSample;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedLevelingExpectations.LevelSample;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedLevelingExpectations.LevelXp;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRaceExpectations;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * C2 (RLM-23): leveling in the game (architecture 10.2). The XP table, kill XP and level caps match the pipeline's
 * expectations; XP accumulates past the level cap and granting the chapter advancement raises the level; hostile kills
 * give XP by the victim's level; players without a character gain none; {@code realmforge:level_reached} completes
 * advancements; {@code /realmforge xp} and {@code /qa level} work. Players log in through {@link QaPlayers#login}.
 */
public final class LevelingGameTests {
    /** {@code data/realmforge_qa/advancement/test/level_5.json}: done once the character reaches level 5. */
    private static final Identifier LEVEL_5 = RealmforgeQa.id("test/level_5");

    private LevelingGameTests() {
    }

    @QaGameTest
    public static void xpTableMatchesThePipeline(GameTestHelper helper) {
        Curve curve = curve(helper, GeneratedCurves.XP_TO_NEXT_LEVEL);
        helper.assertFalse(GeneratedLevelingExpectations.LEVELS.isEmpty(), "no level expectations were generated");
        for (LevelXp expected : GeneratedLevelingExpectations.LEVELS) {
            helper.assertValueEqual(XpTable.toNext(curve, expected.level()), expected.toNext(),
                    "XP from level " + expected.level());
            helper.assertValueEqual(XpTable.toReach(curve, expected.level()), expected.toReach(),
                    "XP for level " + expected.level());
        }
        for (LevelSample sample : GeneratedLevelingExpectations.LEVEL_SAMPLES) {
            helper.assertValueEqual(XpTable.levelFor(curve, sample.totalXp(), sample.cap()), sample.level(),
                    "the level of " + sample.totalXp() + " XP under cap " + sample.cap());
        }
        helper.succeed();
    }

    @QaGameTest
    public static void killXpMatchesThePipeline(GameTestHelper helper) {
        Curve base = curve(helper, GeneratedCurves.KILL_XP);
        Curve below = curve(helper, GeneratedCurves.KILL_XP_BELOW_PLAYER);
        Curve above = curve(helper, GeneratedCurves.KILL_XP_ABOVE_PLAYER);
        helper.assertFalse(GeneratedLevelingExpectations.KILLS.isEmpty(), "no kill expectations were generated");
        for (KillSample sample : GeneratedLevelingExpectations.KILLS) {
            helper.assertValueEqual(KillXp.of(base, below, above, sample.victimLevel(), sample.playerLevel(),
                    sample.multiplier()), sample.xp(), "a level " + sample.playerLevel() + " player killing level "
                    + sample.victimLevel() + " at " + sample.multiplier());
        }
        helper.succeed();
    }

    /** The ticket's "Done when": XP accumulates past the cap, and granting the chapter advancement raises the level. */
    @QaGameTest
    public static void xpAccumulatesPastTheCapAndTheChapterRaisesTheLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-lvl-cap");
        ChapterExpectation chapter = GeneratedChapterExpectations.CHAPTERS.getFirst();
        Curve curve = curve(helper, GeneratedCurves.XP_TO_NEXT_LEVEL);
        helper.assertValueEqual(Characters.data(player).levelCap().orElse(0), chapter.levelCap(),
                "a new character's cap, cached at creation");

        long beyond = XpTable.toReach(curve, chapter.completedLevelCap() + 5);
        helper.assertValueEqual(Characters.rewardXp(player, beyond), beyond, "XP gained at 100 % XP multiplier");
        helper.assertValueEqual(Characters.data(player).totalXp(), beyond, "every point counts, past the cap");
        helper.assertValueEqual(Characters.level(player), chapter.levelCap(), "the cap holds the level");
        assertClassBonusesAtLevel(helper, player);
        Characters.rewardXp(player, 500);
        helper.assertValueEqual(Characters.data(player).totalXp(), beyond + 500, "XP keeps counting at the cap");
        helper.assertValueEqual(Characters.level(player), chapter.levelCap(), "and the level stays");

        AdvancementHolder advancement = server.getAdvancements().get(Identifier.parse(chapter.advancement()));
        helper.assertTrue(player.getAdvancements().award(advancement, "completed"), "the chapter advancement granted");
        helper.assertTrue(Chapters.isCompleted(player, chapterKey(chapter)), "the chapter is completed");
        helper.assertValueEqual(Characters.data(player).levelCap().orElse(0), chapter.completedLevelCap(),
                "the cap the chapter raises it to");
        helper.assertValueEqual(Characters.level(player), chapter.completedLevelCap(),
                "the banked XP levels the player up to the new cap at once");
        assertClassBonusesAtLevel(helper, player);

        helper.assertTrue(player.getAdvancements().revoke(advancement, "completed"), "the chapter advancement revoked");
        helper.assertValueEqual(Characters.level(player), chapter.levelCap(), "revoking it lowers the cap again");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    @QaGameTest
    public static void killingAHostileMobGivesXpByItsLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = loginWithCharacter(helper, "qa-lvl-kill");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        MobLevels.setLevel(zombie, 3);
        long expected = KillXp.of(curve(helper, GeneratedCurves.KILL_XP),
                curve(helper, GeneratedCurves.KILL_XP_BELOW_PLAYER), curve(helper, GeneratedCurves.KILL_XP_ABOVE_PLAYER),
                3, Characters.level(player), 1.0);
        helper.assertTrue(expected > 0, "a level 3 zombie gives a level 1 player XP");
        kill(level, zombie, player);
        helper.assertTrue(zombie.isDeadOrDying(), "the zombie died");
        helper.assertValueEqual(Characters.data(player).totalXp(), expected, "XP for the level 3 zombie");

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 1, 3));
        kill(level, pig, player);
        helper.assertValueEqual(Characters.data(player).totalXp(), expected, "a pig is no enemy and gives nothing");

        ServerPlayer newcomer = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-lvl-nochar"));
        Zombie another = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        kill(level, another, newcomer);
        helper.assertValueEqual(Characters.data(newcomer), CharacterData.NONE, "no XP, and no data, without a character");
        server.getPlayerList().remove(newcomer);
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    @QaGameTest
    public static void playersWithoutACharacterGainNoXp(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-lvl-none"));
        helper.assertValueEqual(Characters.rewardXp(player, 1000), 0L, "a reward");
        helper.assertFalse(Characters.setTotalXp(player, 1000), "setting the XP");
        run(server, player, "realmforge xp add @s 1000");
        helper.assertValueEqual(Characters.data(player), CharacterData.NONE, "the player still has no character data");
        helper.assertValueEqual(Characters.level(player), 1, "and level 1");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    @QaGameTest
    public static void theXpMultiplierScalesWhatAPlayerGains(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-lvl-mult");
        AttributeInstance multiplier = player.getAttribute(GeneratedStats.XP_MULTIPLIER);
        helper.assertTrue(multiplier != null, "players have the XP multiplier");
        helper.assertValueEqual(multiplier.getValue(), 1.0, "100 % by default");
        multiplier.setBaseValue(1.5);
        helper.assertValueEqual(Characters.rewardXp(player, 100), 150L, "100 XP at 150 %");
        helper.assertValueEqual(Characters.data(player).totalXp(), 150L, "the player's XP");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    @QaGameTest
    public static void levelReachedCompletesAdvancements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-lvl-trigger");
        AdvancementHolder level5 = server.getAdvancements().get(LEVEL_5);
        helper.assertTrue(level5 != null, "the QA advancement " + LEVEL_5 + " is loaded");
        Curve curve = curve(helper, GeneratedCurves.XP_TO_NEXT_LEVEL);
        Characters.rewardXp(player, XpTable.toReach(curve, 4));
        helper.assertValueEqual(Characters.level(player), 4, "level 4");
        helper.assertFalse(player.getAdvancements().getOrStartProgress(level5).isDone(), "not done at level 4");
        // Two levels at once: "level 5 or more" still matches level 6.
        Characters.rewardXp(player, XpTable.toReach(curve, 6) - XpTable.toReach(curve, 4));
        helper.assertValueEqual(Characters.level(player), 6, "level 6");
        helper.assertTrue(player.getAdvancements().getOrStartProgress(level5).isDone(), "done at level 6");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    @QaGameTest
    public static void xpAndLevelCommandsChangeTheCharactersXp(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-lvl-cmd");
        Curve curve = curve(helper, GeneratedCurves.XP_TO_NEXT_LEVEL);
        ChapterExpectation chapter = GeneratedChapterExpectations.CHAPTERS.getFirst();

        run(server, player, "realmforge xp add @s " + XpTable.toReach(curve, 3));
        helper.assertValueEqual(Characters.level(player), 3, "/realmforge xp add");
        run(server, player, "realmforge xp set @s 0");
        helper.assertValueEqual(Characters.data(player).totalXp(), 0L, "/realmforge xp set");
        helper.assertValueEqual(Characters.level(player), 1, "the level follows the XP down");

        int high = chapter.completedLevelCap();
        run(server, player, "qa level " + high);
        helper.assertValueEqual(Characters.data(player).totalXp(), XpTable.toReach(curve, high),
                "/qa level gives the XP of the level");
        helper.assertValueEqual(Characters.level(player), chapter.levelCap(), "which the cap holds back");
        run(server, player, "qa level 5");
        helper.assertValueEqual(Characters.level(player), 5, "/qa level below the cap");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** A player who logs in, at the platform's middle, and creates a character of the first race and its first class. */
    private static ServerPlayer loginWithCharacter(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.login(helper.getLevel().getServer(), new GameProfile(UUID.randomUUID(), name));
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        var race = GeneratedRaceExpectations.RACES.getFirst();
        Result result = CharacterCreation.create(player,
                ResourceKey.create(CharacterRegistries.RACE, Identifier.parse(race.id())),
                ResourceKey.create(CharacterRegistries.CLASS, Identifier.parse(race.classes().getFirst())));
        helper.assertValueEqual(result, Result.CREATED, "character creation");
        return player;
    }

    /** The class and race modifiers on the player are the ones their current level gives. */
    private static void assertClassBonusesAtLevel(GameTestHelper helper, ServerPlayer player) {
        helper.assertFalse(GeneratedClassExpectations.CLASSES.isEmpty(), "classes exist");
        for (StatBonus bonus : CharacterBonuses.of(Characters.data(player), Characters.level(player),
                player.registryAccess())) {
            AttributeInstance instance = player.getAttribute(bonus.attribute());
            helper.assertTrue(instance != null && instance.getModifier(bonus.modifier().id()) != null,
                    "modifier " + bonus.modifier().id() + " at level " + Characters.level(player));
            helper.assertValueEqual(instance.getModifier(bonus.modifier().id()).amount(), bonus.modifier().amount(),
                    bonus.modifier().id() + " at level " + Characters.level(player));
        }
    }

    private static void kill(ServerLevel level, LivingEntity victim, ServerPlayer player) {
        victim.hurtServer(level, level.damageSources().playerAttack(player), 100_000);
    }

    private static Curve curve(GameTestHelper helper, ResourceKey<Curve> key) {
        Registry<Curve> curves = helper.getLevel().registryAccess().lookupOrThrow(CoreRegistries.CURVE);
        Curve curve = curves.getValue(key);
        helper.assertTrue(curve != null, "curve is not loaded: " + key.identifier());
        return curve;
    }

    private static ResourceKey<Chapter> chapterKey(ChapterExpectation chapter) {
        return ResourceKey.create(ProgressionRegistries.CHAPTER, Identifier.parse(chapter.id()));
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }
}
