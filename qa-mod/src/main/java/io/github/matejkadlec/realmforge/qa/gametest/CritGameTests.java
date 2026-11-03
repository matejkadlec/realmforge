package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.combat.CombatAttachments;
import io.github.matejkadlec.realmforge.combat.CritCounters;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-526: a player's crits are pseudo-random (Dota 2's distribution, {@code combat/CritDistribution}), on one counter
 * for weapon attacks and one for spells ({@code combat/CritCounters}).
 */
public final class CritGameTests {
    private static final Vec3 EDGE = new Vec3(2.5, 1, 0.5);
    /** Five times the longest run of misses 30 % allows. */
    private static final int ATTACKS = 45;
    private static final double TOLERANCE = 1e-4;

    private CritGameTests() {
    }

    /**
     * At 30 % crit chance a player's full-strength melee hits crit at least once in any 9 (C × 9 ≥ 1): the attack
     * counter never passes 8 misses, a crit (1.5 × the fist's damage) starts it again, and the spell counter stays.
     */
    @QaGameTest
    public static void thirtyPercentCritsWithinAnyNineAttacks(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-crit-streak", EDGE);
        Pillager mob = CombatGameTests.target(helper);
        double fist = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0.3);

        int crits = 0;
        for (int i = 1; i <= ATTACKS; i++) {
            int missesBefore = counters(player).attackMisses();
            double dealt = attack(player, mob);
            CritCounters after = counters(player);
            boolean crit = close(dealt, fist * 1.5);
            helper.assertTrue(crit || close(dealt, fist), "attack " + i + " dealt " + dealt);
            helper.assertValueEqual(after.attackMisses(), crit ? 0 : missesBefore + 1,
                    "attack " + i + ": a crit starts the count again, a miss adds one");
            helper.assertTrue(after.attackMisses() <= 8, "attack " + i + ": 9 attacks in a row without a crit");
            helper.assertValueEqual(after.spellMisses(), 0, "attack " + i + " left the spell counter alone");
            crits += crit ? 1 : 0;
        }
        helper.assertTrue(crits >= ATTACKS / 9, crits + " crits in " + ATTACKS + " attacks");
        logout(player);
        helper.succeed();
    }

    /**
     * Weapon attacks and spells keep their own counters: a school hit the player causes moves only the spell counter, a
     * melee hit only the attack counter, and at 0 % crit chance neither moves.
     */
    @QaGameTest
    public static void attacksAndSpellsAdvanceTheirOwnCounters(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-crit-counters", EDGE);
        Pillager mob = CombatGameTests.target(helper);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        attack(player, mob);
        spellHit(helper, player, mob);
        helper.assertValueEqual(counters(player), CritCounters.NONE, "rolls at 0 % crit chance count nothing");

        // 0.1 %: a crit within three rolls is all but impossible (C is 0.0000016), so the counters show misses; either
        // way each roll moves its own counter alone.
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0.001);
        for (int i = 1; i <= 3; i++) {
            CritCounters before = counters(player);
            spellHit(helper, player, mob);
            CritCounters after = counters(player);
            helper.assertValueEqual(after.attackMisses(), before.attackMisses(), "spell hit " + i + ", attack counter");
            helper.assertTrue(after.spellMisses() == 0 || after.spellMisses() == before.spellMisses() + 1,
                    "spell hit " + i + " moved the spell counter: " + before + " to " + after);
        }
        for (int i = 1; i <= 3; i++) {
            CritCounters before = counters(player);
            attack(player, mob);
            CritCounters after = counters(player);
            helper.assertValueEqual(after.spellMisses(), before.spellMisses(), "attack " + i + ", spell counter");
            helper.assertTrue(after.attackMisses() == 0 || after.attackMisses() == before.attackMisses() + 1,
                    "attack " + i + " moved the attack counter: " + before + " to " + after);
        }
        helper.assertFalse(counters(player).equals(CritCounters.NONE), "the rolls were counted");
        logout(player);
        helper.succeed();
    }

    private static CritCounters counters(ServerPlayer player) {
        return player.getData(CombatAttachments.CRIT_COUNTERS);
    }

    /** A full-strength melee hit on {@code mob}; the damage it dealt. */
    private static double attack(ServerPlayer player, Pillager mob) {
        for (int i = 0; i < 20; i++) {
            player.doTick();
        }
        CombatGameTests.settle(mob);
        float before = mob.getHealth();
        player.attack(mob);
        return before - mob.getHealth();
    }

    /** 4 arcane damage on {@code mob} that {@code player} caused, as a spell's hit. */
    private static void spellHit(GameTestHelper helper, ServerPlayer player, Pillager mob) {
        mob.hurtServer(helper.getLevel(), CombatGameTests.school(helper, GeneratedSpellSchools.ARCANE, player), 4);
    }

    private static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
