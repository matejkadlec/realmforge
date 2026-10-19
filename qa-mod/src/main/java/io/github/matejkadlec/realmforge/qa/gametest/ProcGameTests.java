package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.combat.QaProcs;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Proc;
import io.github.matejkadlec.realmforge.spell.Procs;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * C3a-4 (RLM-212): procs (architecture 12), given through the QA mod's proc source ({@link QaProcs}). The test proc
 * casts {@code test_proc_spark}, 1 arcane damage at the enemy hit, so its hit shows apart from the 5 of the melee hit
 * that triggered it. Every melee hit opens the husk's hurt cooldown first, so each lands in full.
 */
public final class ProcGameTests {
    private static final double TOLERANCE = 1e-4;
    private static final double HEALTH = 1000;
    private static final float MELEE = 5;
    private static final float SPARK = 1;

    private ProcGameTests() {
    }

    /** A proc fires on a hit, waits out its internal cooldown, and fires again after it. */
    @QaGameTest(timeoutTicks = 100)
    public static void aProcFiresOnHitAndWaitsOutItsCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-proc-cooldown");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        Proc spark = spark("spark_with_cooldown", Proc.Trigger.HIT, 40);
        QaProcs.give(player, spark);

        melee(helper, player, husk);
        assertHealth(helper, husk, HEALTH - MELEE - SPARK, "the hit and its proc's spark");
        helper.assertTrue(Procs.isOnCooldown(player, spark.id()), "the proc waits out its internal cooldown");
        melee(helper, player, husk);
        assertHealth(helper, husk, HEALTH - 2 * MELEE - SPARK, "no spark during the internal cooldown");
        helper.runAfterDelay(41, () -> {
            helper.assertFalse(Procs.isOnCooldown(player, spark.id()), "the internal cooldown ended");
            melee(helper, player, husk);
            assertHealth(helper, husk, HEALTH - 3 * MELEE - 2 * SPARK, "the proc fires again after it");
            QaProcs.clear(player);
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Procs never trigger procs: a proc's spark hits once, though the caster's sure on-hit proc has no cooldown, and
     * the husk's on-hit-taken proc reacts to the melee hit but not to the spark. A spark cast as a spell, not by a
     * proc, triggers the on-hit proc like any hit.
     */
    @QaGameTest
    public static void aProcsOwnHitTriggersNoProc(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-proc-chain");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        Proc spark = spark("spark_every_hit", Proc.Trigger.HIT, 0);
        QaProcs.give(player, spark);
        QaProcs.give(husk, new Proc(RealmforgeQa.id("glow_when_hit"), Proc.Trigger.HIT_TAKEN, 1, 0,
                new Proc.ApplyEffect(MobEffects.GLOWING, 100, 0, true)));

        melee(helper, player, husk);
        assertHealth(helper, husk, HEALTH - MELEE - SPARK, "one spark for the hit, and none for the spark's own hit");
        helper.assertTrue(husk.hasEffect(MobEffects.GLOWING), "the husk's proc reacted to the melee hit");

        husk.removeEffect(MobEffects.GLOWING);
        CastResult proc = SpellCaster.castProc(player, spell(helper), 1, SpellAim.at(husk), spark.id());
        helper.assertTrue(proc.success(), "the spark cast as a proc: " + proc.failure());
        assertHealth(helper, husk, HEALTH - MELEE - 2 * SPARK, "a proc's spark triggers no spark");
        helper.assertFalse(husk.hasEffect(MobEffects.GLOWING), "nor the husk's proc");

        Spells.learn(player, spell(helper), 1);
        CastResult cast = SpellCaster.cast(player, GeneratedSpells.TEST_PROC_SPARK, husk);
        helper.assertTrue(cast.success(), "the spark cast as a spell: " + cast.failure());
        assertHealth(helper, husk, HEALTH - MELEE - 4 * SPARK, "a spell's hit triggers the on-hit proc");
        helper.assertTrue(husk.hasEffect(MobEffects.GLOWING), "and the husk's proc");
        QaProcs.clear(husk);
        QaProcs.clear(player);
        logout(player);
        helper.succeed();
    }

    /** A crit and a kill fire their own triggers' procs, and a proc may apply a status effect to its holder. */
    @QaGameTest
    public static void critAndKillProcsFireOnTheirTriggers(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-proc-triggers");
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.tick(player);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        QaProcs.give(player, spark("spark_on_crit", Proc.Trigger.CRIT, 0));
        QaProcs.give(player, new Proc(RealmforgeQa.id("haste_on_kill"), Proc.Trigger.KILL, 1, 0,
                new Proc.ApplyEffect(MobEffects.SPEED, 100, 0, true)));

        melee(helper, player, husk);
        assertHealth(helper, husk, HEALTH - MELEE, "a hit that does not crit fires no crit proc");
        Spells.learn(player, Spells.get(helper.getLevel().registryAccess(), GeneratedSpells.TEST_SURE_CRIT)
                .orElseThrow(), 1);
        CastResult crit = SpellCaster.cast(player, GeneratedSpells.TEST_SURE_CRIT, husk);
        helper.assertTrue(crit.success(), "test_sure_crit: " + crit.failure());
        assertHealth(helper, husk, HEALTH - MELEE - 10 - SPARK, "test_sure_crit's sure crit fires the spark");
        helper.assertFalse(player.hasEffect(MobEffects.SPEED), "nothing died yet");

        husk.setHealth(1);
        melee(helper, player, husk);
        helper.assertTrue(husk.isDeadOrDying(), "the husk died");
        helper.assertTrue(player.hasEffect(MobEffects.SPEED), "the kill gave the player Speed");
        QaProcs.clear(player);
        logout(player);
        helper.succeed();
    }

    /** A sure proc that casts {@code test_proc_spark} at the enemy hit. */
    private static Proc spark(String name, Proc.Trigger trigger, int cooldownTicks) {
        return new Proc(RealmforgeQa.id(name), trigger, 1, cooldownTicks,
                new Proc.CastSpell(GeneratedSpells.TEST_PROC_SPARK, 1));
    }

    private static Holder.Reference<Spell> spell(GameTestHelper helper) {
        return Spells.get(helper.getLevel().registryAccess(), GeneratedSpells.TEST_PROC_SPARK).orElseThrow();
    }

    /** A fake player at the platform's south edge, in survival, at 0 % crit chance. */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        return player;
    }

    /** A melee hit for {@value #MELEE} outside the target's hurt cooldown. */
    private static void melee(GameTestHelper helper, ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), MELEE);
    }

    private static void assertHealth(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(Math.abs(entity.getHealth() - expected) <= TOLERANCE, what + ": health " + expected
                + " expected, got " + entity.getHealth());
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
