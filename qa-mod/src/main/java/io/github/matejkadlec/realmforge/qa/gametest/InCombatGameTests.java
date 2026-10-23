package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.setBase;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.target;

import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.StatPoints;
import io.github.matejkadlec.realmforge.combat.CombatConfig;
import io.github.matejkadlec.realmforge.combat.InCombat;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-348: being in combat (architecture 9.4; Matěj, 2026-10-21 and 2026-10-22). A hit dealt to, or taken from,
 * another living entity puts both sides in combat for 10 s ({@code inCombatSeconds}), a dodged one too; damage from
 * the world never does. The Scroll of Oblivion is refused in combat and kept, and works once the fight is over.
 */
public final class InCombatGameTests {
    /** Where the player stands: the platform's north edge, facing the mobs in its middle. */
    private static final Vec3 EDGE = new Vec3(2.5, 1, 0.5);
    /** A mob's hit on the player, small enough for any player's health. */
    private static final float HIT = 2;
    /** How long the timed tests may run: the 10 s window and a margin. */
    private static final int TIMEOUT = 260;

    private InCombatGameTests() {
    }

    /**
     * Done when of RLM-348: a mob's hit puts both the mob and the player in combat for the whole window, and the
     * player's own hit at a mob does the same; 10 s after the last hit both are out of combat.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aHitPutsBothSidesInCombatForTenSeconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fighter(helper, "qa-combat-hit");
        Pillager attacker = target(helper);
        Pillager struck = target(helper);
        int window = CombatConfig.inCombatTicks();
        helper.assertValueEqual(window, 200, "the window is 10 s");
        assertInCombat(helper, player, false, "the player before any hit");

        player.hurtServer(level, level.damageSources().mobAttack(attacker), HIT);
        assertInCombat(helper, player, true, "the player after the pillager's hit");
        assertInCombat(helper, attacker, true, "the pillager that hit the player");
        helper.assertValueEqual(InCombat.ticksLeft(player), (long) window, "the player's ticks left");

        for (int i = 0; i < 20; i++) {
            player.doTick();
        }
        player.attack(struck);
        assertInCombat(helper, struck, true, "the pillager the player hit");
        helper.startSequence()
                .thenExecuteAfter(window - 20, () -> {
                    assertInCombat(helper, player, true, "the player 9 s after the last hit");
                    assertInCombat(helper, attacker, true, "the pillager 9 s after its hit");
                })
                .thenExecuteAfter(40, () -> {
                    assertInCombat(helper, player, false, "the player 11 s after the last hit");
                    assertInCombat(helper, attacker, false, "the pillager 11 s after its hit");
                    assertInCombat(helper, struck, false, "the struck pillager 11 s after the hit");
                    level.getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /** A dodged hit puts both sides in combat too, as it holds rage. */
    @QaGameTest
    public static void aDodgedHitPutsBothSidesInCombat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fighter(helper, "qa-combat-dodge");
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 1);
        Pillager attacker = target(helper);
        float before = player.getHealth();
        player.hurtServer(level, level.damageSources().mobAttack(attacker), HIT);
        helper.assertValueEqual(player.getHealth(), before, "the player dodged");
        assertInCombat(helper, player, true, "the player who dodged");
        assertInCombat(helper, attacker, true, "the pillager whose hit was dodged");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Done when of RLM-348: falls, lava, fire and drowning never start combat; they have no attacker. */
    @QaGameTest
    public static void damageFromTheWorldDoesNotStartCombat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fighter(helper, "qa-combat-world");
        player.hurtServer(level, level.damageSources().fall(), HIT);
        helper.assertTrue(player.getHealth() < player.getMaxHealth(), "the fall hurt the player");
        assertInCombat(helper, player, false, "the player after a fall");
        for (DamageSource source : List.of(level.damageSources().lava(), level.damageSources().inFire(),
                level.damageSources().drown())) {
            player.invulnerableTime = 0;
            player.hurtServer(level, source, HIT);
        }
        player.clearFire();
        assertInCombat(helper, player, false, "the player after lava, fire and drowning");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-348: in combat the Scroll of Oblivion fails, is kept and returns nothing; once the window has
     * passed it returns every spent point and is used up.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void theScrollOfOblivionIsRefusedInCombatAndWorksAfterwards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = StatPointsGameTests.loginWithCharacter(helper, "qa-combat-scroll");
        QaPlayers.onPlatform(helper, player, EDGE);
        makeHurtable(player);
        StatPointsGameTests.reachLevel(helper, server, player, 4);
        StatPoints.spend(player, StatPointsGameTests.key(GeneratedStats.STRENGTH));
        int unspent = StatPoints.unspent(player);
        ItemStack scrolls = new ItemStack(GeneratedItems.OBLIVION_SCROLL.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, scrolls);

        player.hurtServer(level, level.damageSources().mobAttack(target(helper)), HIT);
        assertInCombat(helper, player, true, "the player after the pillager's hit");
        InteractionResult refused = player.gameMode.useItem(player, level, scrolls, InteractionHand.MAIN_HAND);
        helper.assertTrue(refused instanceof InteractionResult.Fail, "in combat the scroll fails: " + refused);
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 2, "the scroll is kept");
        helper.assertValueEqual(StatPoints.unspent(player), unspent, "the spent point stays spent");

        helper.startSequence()
                .thenExecuteAfter(CombatConfig.inCombatTicks() + 5, () -> {
                    assertInCombat(helper, player, false, "the player after the window");
                    InteractionResult used = player.gameMode.useItem(player, level,
                            player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
                    helper.assertTrue(used instanceof InteractionResult.Success,
                            "out of combat the scroll works: " + used);
                    helper.assertValueEqual(Characters.data(player).statPoints(), Map.of(), "nothing is spent");
                    helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1,
                            "one scroll used up");
                    server.getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /** A fake player at the platform's north edge that takes damage and never dodges. */
    private static ServerPlayer fighter(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, EDGE);
        makeHurtable(player);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        return player;
    }

    /**
     * Survival, a client marked as loaded (vanilla protects a player whose client is still loading), no dodge, and
     * full health.
     */
    private static void makeHurtable(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        setBase(player, Attributes.ARMOR, 0);
        player.setHealth(player.getMaxHealth());
    }

    private static void assertInCombat(GameTestHelper helper, LivingEntity entity, boolean expected, String what) {
        helper.assertValueEqual(InCombat.isInCombat(entity), expected, what + " in combat");
    }
}
