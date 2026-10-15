package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.combat.HealthRegeneration;
import io.github.matejkadlec.realmforge.combat.MagicShields;
import io.github.matejkadlec.realmforge.combat.Overheal;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-158 and RLM-160: the combat stats that act on a hit or over time (architecture 9.1): armor pierce, spell
 * resistance, lifesteal, overheal, health regeneration and the magic shield. Fake players attack and take damage in
 * survival mode (the GameTest world is creative), at 0 % crit chance, with their food where vanilla's food
 * regeneration stays out of the way (17 food, no saturation).
 */
public final class CombatStatGameTests {
    private CombatStatGameTests() {
    }

    /** Done when of RLM-158: armor pierce lowers the armor a hit meets, against a training dummy with 15 armor. */
    @QaGameTest
    public static void armorPierceLowersTheArmorAHitMeets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-pierce");
        Husk dummy = TrainingDummy.spawn(level, helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 15);
        DamageSource hit = level.damageSources().playerAttack(player);

        assertMetered(helper, dummy, hit, 10 * (1 - CombatGameTests.blocked(helper, 15, 1)), "no armor pierce");
        StatsCommands.setBase(player, GeneratedStats.ARMOR_PIERCE, 5);
        assertMetered(helper, dummy, hit, 10 * (1 - CombatGameTests.blocked(helper, 10, 1)),
                "5 armor pierce leaves 10 armor");
        StatsCommands.setBase(player, GeneratedStats.ARMOR_PIERCE_PERCENT, 0.5);
        assertMetered(helper, dummy, hit, 10 * (1 - CombatGameTests.blocked(helper, 5, 1)),
                "then 50 % armor pierce takes half of the 10 left");
        StatsCommands.setBase(player, GeneratedStats.ARMOR_PIERCE, 20);
        assertMetered(helper, dummy, hit, 10, "more armor pierce than armor: the hit lands in full");
        helper.assertValueEqual(TrainingDummy.armorLine(dummy, player).getString(),
                "15 Armor (0% Damage Reduction)", "the dummy's label counts the nearest player's armor pierce");
        TrainingDummy.remove(dummy);
        logout(player);
        helper.succeed();
    }

    /** Done when of RLM-160: spell resistance adds to a magic school's resistance, and the sum stops at 75 %. */
    @QaGameTest
    public static void spellResistanceAddsToTheSchoolsResistanceUpTo75Percent(GameTestHelper helper) {
        Zombie zombie = CombatGameTests.attacker(helper);
        Husk husk = CombatGameTests.target(helper);
        CombatGameTests.setBase(husk, GeneratedStats.FIRE_RESISTANCE, 0.25);
        CombatGameTests.setBase(husk, GeneratedStats.SPELL_RESISTANCE, 0.2);

        CombatGameTests.assertDealt(helper, husk, CombatGameTests.school(helper, GeneratedSpellSchools.FIRE, zombie),
                10, 10 * 0.55, "fire: 25 % fire resistance and 20 % spell resistance");
        CombatGameTests.settle(husk);
        CombatGameTests.assertDealt(helper, husk, CombatGameTests.school(helper, GeneratedSpellSchools.FROST, zombie),
                10, 10 * 0.8, "frost: spell resistance alone");
        CombatGameTests.settle(husk);
        CombatGameTests.assertDealt(helper, husk,
                CombatGameTests.school(helper, GeneratedSpellSchools.PHYSICAL, zombie), 10, 10,
                "physical: spell resistance does not apply");
        CombatGameTests.setBase(husk, GeneratedStats.FIRE_RESISTANCE, 0.5);
        CombatGameTests.setBase(husk, GeneratedStats.SPELL_RESISTANCE, 0.5);
        CombatGameTests.settle(husk);
        CombatGameTests.assertDealt(helper, husk, CombatGameTests.school(helper, GeneratedSpellSchools.FIRE, zombie),
                10, 10 * 0.25, "50 % and 50 %: the sum stops at 75 %");
        helper.succeed();
    }

    /** Done when of RLM-158: lifesteal heals its share of the damage a melee or ranged hit dealt, never a spell's. */
    @QaGameTest
    public static void lifestealHealsTheAttackerOnMeleeAndRangedHitsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-lifesteal");
        StatsCommands.setBase(player, GeneratedStats.LIFESTEAL, 0.25);
        Husk husk = CombatGameTests.target(helper);
        player.setHealth(10);

        CombatGameTests.assertDealt(helper, husk, level.damageSources().playerAttack(player), 8, 8, "the melee hit");
        assertHealth(helper, player, 12, "a quarter of the melee hit's 8");
        CombatGameTests.settle(husk);
        CombatGameTests.assertDealt(helper, husk, level.damageSources().thrown(player, player), 4, 4,
                "the thrown hit");
        assertHealth(helper, player, 13, "a quarter of the projectile's 4");
        CombatGameTests.settle(husk);
        CombatGameTests.assertDealt(helper, husk, CombatGameTests.school(helper, GeneratedSpellSchools.FIRE, player),
                4, 4, "the fire hit");
        assertHealth(helper, player, 13, "a spell heals nothing by lifesteal");
        logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-158: healing beyond max health becomes absorption up to the overheal cap; damage takes it
     * first, and it expires {@value Overheal#DURATION_TICKS} ticks after the last heal that added to it.
     */
    @QaGameTest(timeoutTicks = Overheal.DURATION_TICKS + 40)
    public static void overhealTurnsHealingBeyondMaxHealthIntoAbsorptionThatExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-overheal");
        StatsCommands.setBase(player, GeneratedStats.OVERHEAL, 0.25);
        player.setHealth(18);

        player.heal(4);
        assertHealth(helper, player, 20, "the heal filled the health bar");
        assertAbsorption(helper, player, 2, "2 of the 4 went past max health");
        player.heal(10);
        assertAbsorption(helper, player, 5, "the cap: 25 % of 20 max health");
        player.hurtServer(level, level.damageSources().generic(), 1);
        assertHealth(helper, player, 20, "the hit took absorption, not health");
        assertAbsorption(helper, player, 4, "the hit took 1 of the 5");
        helper.runAfterDelay(Overheal.DURATION_TICKS + 5, () -> {
            assertAbsorption(helper, player, 0, "the overheal expired");
            helper.assertFalse(player.getAttribute(Attributes.MAX_ABSORPTION).hasModifier(Overheal.MODIFIER),
                    "its max absorption modifier went with it");
            logout(player);
            helper.succeed();
        });
    }

    /** Overheal's absorption goes before a golden apple's: damage takes the overheal first. */
    @QaGameTest
    public static void damageTakesOverhealBeforeOtherAbsorption(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-overheal-2");
        StatsCommands.setBase(player, GeneratedStats.OVERHEAL, 0.25);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200));
        player.heal(3);
        assertAbsorption(helper, player, 7, "the golden apple's 4 and the overheal's 3");
        hurt(helper, player, 2);
        assertAbsorption(helper, player, 5, "the hit took 2");
        helper.assertTrue(CombatGameTests.close(Overheal.amount(player), 1),
                "the hit took them from the overheal: 1 left, got " + Overheal.amount(player));
        logout(player);
        helper.succeed();
    }

    /** Done when of RLM-160: health regeneration heals its points every second, never beyond max health. */
    @QaGameTest
    public static void healthRegenerationHealsItsPointsEverySecond(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-regen");
        StatsCommands.setBase(player, GeneratedStats.HEALTH_REGENERATION, 2);
        StatsCommands.setBase(player, GeneratedStats.OVERHEAL, 0.5);
        player.setHealth(10);

        ticks(player, HealthRegeneration.INTERVAL_TICKS);
        assertHealth(helper, player, 12, "one second heals 2");
        ticks(player, 3 * HealthRegeneration.INTERVAL_TICKS);
        assertHealth(helper, player, 18, "three more seconds heal 6");
        player.setHealth(19.5F);
        ticks(player, HealthRegeneration.INTERVAL_TICKS);
        assertHealth(helper, player, 20, "the last half point fills the health bar");
        assertAbsorption(helper, player, 0, "regeneration never overheals");
        logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-160: the magic shield takes damage before health, after absorption hearts, keeps the whole hit
     * for the hurt cooldown, and refills {@value MagicShields#REFILL_DELAY_TICKS} ticks after the last damage.
     */
    @QaGameTest
    public static void theMagicShieldTakesDamageBeforeHealthAndRefills(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-shield");
        StatsCommands.setBase(player, GeneratedStats.MAGIC_SHIELD, 10);
        ticks(player, 1);
        assertShield(helper, player, 10, "a tick fills a new pool");

        hurt(helper, player, 6);
        assertHealth(helper, player, 20, "the shield took the hit");
        assertShield(helper, player, 4, "6 of its 10");
        helper.assertTrue(CombatGameTests.close(player.lastHurt, 6),
                "the hurt cooldown measures the whole hit: " + player.lastHurt);
        hurt(helper, player, 8);
        assertShield(helper, player, 0, "the shield took its last 4");
        assertHealth(helper, player, 16, "health took the other 4");

        ticks(player, MagicShields.REFILL_DELAY_TICKS - 2);
        assertShield(helper, player, 0, "no refill before the delay without damage has passed");
        ticks(player, 3);
        assertShield(helper, player, 10, "refilled once the delay after the last damage has passed");

        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200));
        assertAbsorption(helper, player, 4, "a golden apple's absorption");
        hurt(helper, player, 6);
        assertAbsorption(helper, player, 0, "absorption hearts take a hit first");
        assertShield(helper, player, 8, "then the shield");
        assertHealth(helper, player, 16, "and health nothing");
        logout(player);
        helper.succeed();
    }

    /**
     * A fake player that can be hurt (survival mode, its client counted as loaded), at 0 % crit chance, with food that
     * neither regenerates nor starves, after its first tick.
     */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.login(helper.getLevel().getServer(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.getFoodData().setFoodLevel(17);
        player.getFoodData().setSaturation(0);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        ticks(player, 1);
        return player;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    /**
     * Plays server ticks of the player: the level counts its {@code tickCount} ({@code ServerLevel#tickNonPassenger}),
     * then the connection runs its tick ({@code doTick}). A fake player's connection never does.
     */
    private static void ticks(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            player.tickCount++;
            player.doTick();
        }
    }

    /** A hit that bypasses nothing and scales with nothing, outside any hurt cooldown window. */
    private static void hurt(GameTestHelper helper, ServerPlayer player, float damage) {
        CombatGameTests.settle(player);
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), damage);
    }

    /** One hit on the training dummy, measured by its meter from a fresh session. */
    private static void assertMetered(GameTestHelper helper, Husk dummy, DamageSource source, double expected,
            String what) {
        TrainingDummy.reset(dummy);
        CombatGameTests.settle(dummy);
        dummy.hurtServer(helper.getLevel(), source, 10);
        TrainingDummy.Session session = TrainingDummy.session(dummy);
        double dealt = session != null ? session.total() : 0;
        helper.assertTrue(CombatGameTests.close(dealt, expected), what + ": " + expected + " expected, dealt " + dealt);
    }

    private static void assertHealth(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(CombatGameTests.close(entity.getHealth(), expected),
                what + ": health " + expected + " expected, got " + entity.getHealth());
    }

    private static void assertAbsorption(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(CombatGameTests.close(entity.getAbsorptionAmount(), expected),
                what + ": absorption " + expected + " expected, got " + entity.getAbsorptionAmount());
    }

    private static void assertShield(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(CombatGameTests.close(MagicShields.points(entity), expected),
                what + ": shield " + expected + " expected, got " + MagicShields.points(entity));
    }
}
