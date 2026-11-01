package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-233: Physical, Projectile, Explosion and Fall Resistance lower the hits of their damage type tags (architecture
 * 9.1), each against a vanilla source, Physical Resistance also against our physical school, and none against hits
 * outside its tag. A fake player in survival takes the hits: mobs have none of these stats. Each hit is compared with
 * the same hit without resistances, so vanilla's difficulty scaling of mob and explosion damage cancels out.
 */
public final class DamageTypeResistanceGameTests {
    private static final float HIT = 4;

    private DamageTypeResistanceGameTests() {
    }

    @QaGameTest
    public static void physicalResistanceLowersMeleeHitsProjectilesAndPhysicalSpells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-physical-resistance");
        Zombie zombie = CombatGameTests.attacker(helper);
        DamageSource melee = level.damageSources().mobAttack(zombie);
        DamageSource thrown = level.damageSources().thrown(zombie, zombie);
        DamageSource physical = CombatGameTests.school(helper, GeneratedSpellSchools.PHYSICAL, zombie);
        DamageSource fire = CombatGameTests.school(helper, GeneratedSpellSchools.FIRE, zombie);
        DamageSource fall = level.damageSources().fall();
        double meleeBase = taken(helper, player, melee);
        double thrownBase = taken(helper, player, thrown);
        double physicalBase = taken(helper, player, physical);
        double fireBase = taken(helper, player, fire);
        double fallBase = taken(helper, player, fall);

        StatsCommands.setBase(player, GeneratedStats.PHYSICAL_RESISTANCE, 0.4);
        assertTaken(helper, player, melee, meleeBase * 0.6, "a zombie's melee hit, vanilla's");
        assertTaken(helper, player, thrown, thrownBase * 0.6, "a thrown projectile, vanilla's");
        assertTaken(helper, player, physical, physicalBase * 0.6, "a physical school hit, ours");
        assertTaken(helper, player, fire, fireBase, "a fire school hit is no physical hit");
        assertTaken(helper, player, fall, fallBase, "a fall is no physical hit");
        logout(player);
        helper.succeed();
    }

    @QaGameTest
    public static void projectileResistanceMultipliesWithPhysicalResistanceUpTo75Percent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-projectile-resistance");
        Zombie zombie = CombatGameTests.attacker(helper);
        DamageSource thrown = level.damageSources().thrown(zombie, zombie);
        DamageSource melee = level.damageSources().mobAttack(zombie);
        double thrownBase = taken(helper, player, thrown);
        double meleeBase = taken(helper, player, melee);

        StatsCommands.setBase(player, GeneratedStats.PROJECTILE_RESISTANCE, 0.5);
        assertTaken(helper, player, thrown, thrownBase * 0.5, "a thrown projectile, vanilla's");
        assertTaken(helper, player, melee, meleeBase, "a melee hit is no projectile");
        StatsCommands.setBase(player, GeneratedStats.PHYSICAL_RESISTANCE, 0.3);
        assertTaken(helper, player, thrown, thrownBase * 0.7 * 0.5, "30 % physical and 50 % projectile multiply");
        StatsCommands.setBase(player, GeneratedStats.PHYSICAL_RESISTANCE, 0.6);
        assertTaken(helper, player, thrown, thrownBase * 0.25,
                "60 % and 50 % would block 80 %: the whole stops at 75 %");
        logout(player);
        helper.succeed();
    }

    @QaGameTest
    public static void explosionResistanceLowersExplosions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-explosion-resistance");
        Zombie zombie = CombatGameTests.attacker(helper);
        DamageSource explosion = level.damageSources().explosion(zombie, zombie);
        DamageSource melee = level.damageSources().mobAttack(zombie);
        double explosionBase = taken(helper, player, explosion);
        double meleeBase = taken(helper, player, melee);

        StatsCommands.setBase(player, GeneratedStats.EXPLOSION_RESISTANCE, 0.5);
        assertTaken(helper, player, explosion, explosionBase * 0.5, "an explosion, vanilla's");
        assertTaken(helper, player, melee, meleeBase, "a melee hit is no explosion");
        logout(player);
        helper.succeed();
    }

    @QaGameTest
    public static void fallResistanceLowersFallsUpTo75Percent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-fall-resistance");
        Zombie zombie = CombatGameTests.attacker(helper);
        DamageSource fall = level.damageSources().fall();
        DamageSource melee = level.damageSources().mobAttack(zombie);
        double fallBase = taken(helper, player, fall);
        double meleeBase = taken(helper, player, melee);

        StatsCommands.setBase(player, GeneratedStats.FALL_RESISTANCE, 0.5);
        assertTaken(helper, player, fall, fallBase * 0.5, "a fall, vanilla's");
        assertTaken(helper, player, melee, meleeBase, "a melee hit is no fall");
        StatsCommands.setBase(player, GeneratedStats.FALL_RESISTANCE, 0.9);
        assertTaken(helper, player, fall, fallBase * 0.25, "the stat stops at 75 %");
        logout(player);
        helper.succeed();
    }

    /** A survival fake player at full health, no crit and no food regeneration in the way. */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(0.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.getFoodData().setFoodLevel(17);
        player.getFoodData().setSaturation(0);
        return player;
    }

    /** What a {@value #HIT} hit of {@code source} takes from the player's full health, its hurt cooldown closed. */
    private static double taken(GameTestHelper helper, ServerPlayer player, DamageSource source) {
        player.setHealth(player.getMaxHealth());
        CombatGameTests.settle(player);
        float before = player.getHealth();
        player.hurtServer(helper.getLevel(), source, HIT);
        double taken = before - player.getHealth();
        helper.assertTrue(taken > 0, source.getMsgId() + " hurts the player");
        return taken;
    }

    private static void assertTaken(GameTestHelper helper, ServerPlayer player, DamageSource source, double expected,
            String what) {
        double taken = taken(helper, player, source);
        helper.assertTrue(CombatGameTests.close(taken, expected), what + ": " + expected + " expected, took " + taken);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
