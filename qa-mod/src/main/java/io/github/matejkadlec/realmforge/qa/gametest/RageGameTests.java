package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * C3a-4 (RLM-212): rage as a combat resource (architecture 11.5). The Warrior's numbers in {@code content/classes/}
 * give 10 rage for a full-strength melee hit and 50 for losing the whole max health to attackers, and 5 s after the
 * last hit dealt or taken the pool loses 2 every second. A fake player's connection never ticks it, so the tests run
 * its ticks themselves ({@link #seconds}); its combat clock is the level's game time.
 */
public final class RageGameTests {
    private static final double TOLERANCE = 1e-3;

    private RageGameTests() {
    }

    /** A Warrior's rage rises with the hits it deals and takes, holds in combat, and decays once out of it. */
    @QaGameTest(timeoutTicks = 200)
    public static void rageRisesWithHitsAndDecaysOutOfCombat(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-rage-warrior", "warrior");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        seconds(player, 1);
        helper.assertFalse(player.getData(SpellAttachments.COMBAT).hasResource(ResourceTypes.RAGE.getKey()),
                "no rage pool before the first hit");
        helper.assertTrue(player.getAttackStrengthScale(0.5F) >= 1, "a full-strength swing is ready");

        player.attack(husk);
        helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the swing hit the husk");
        assertRage(helper, player, 10, "a full-strength melee hit gives 10");

        float before = player.getHealth();
        player.hurtServer(helper.getLevel(), SpellSchools.damage(helper.getLevel().registryAccess(),
                ResourceKey.create(CombatRegistries.SPELL_SCHOOL,
                        Realmforge.id("arcane")), husk, husk), 4);
        double taken = before - player.getHealth();
        helper.assertTrue(taken > 0, "the husk's hit hurt the Warrior");
        double afterHits = 10 + 50 * taken / player.getMaxHealth();
        assertRage(helper, player, afterHits, "taking " + taken + " of " + player.getMaxHealth() + " health");

        seconds(player, 1);
        assertRage(helper, player, afterHits, "no decay in combat");
        // 5 s after the last hit the Warrior is out of combat.
        helper.runAfterDelay(101, () -> {
            seconds(player, 1);
            assertRage(helper, player, afterHits - 2, "out of combat it decays 2 a second");
            seconds(player, 2);
            assertRage(helper, player, afterHits - 6, "two more seconds");
            logout(player);
            helper.succeed();
        });
    }

    /** A class whose resource is mana gains no rage from the same hits. */
    @QaGameTest
    public static void aManaClassGainsNoRage(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-rage-mage", "mage");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        seconds(player, 1);
        player.attack(husk);
        helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the swing hit the husk");
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), 2);
        helper.assertFalse(player.getData(SpellAttachments.COMBAT).hasResource(ResourceTypes.RAGE.getKey()),
                "a Mage has no rage pool");
        logout(player);
        helper.succeed();
    }

    /** A human of {@code playerClass} at the platform's south edge, in survival, without crits or a magic shield. */
    private static ServerPlayer character(GameTestHelper helper, String name, String playerClass) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        // A fake player's client never reports that it loaded, which keeps the player invulnerable.
        player.connection.markClientLoaded();
        helper.assertValueEqual(CharacterCreation.create(player,
                        ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human")),
                        ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id(playerClass))),
                CharacterCreation.Result.CREATED, "a human " + playerClass);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.MAGIC_SHIELD, 0);
        return player;
    }

    private static void assertRage(GameTestHelper helper, ServerPlayer player, double expected, String what) {
        float rage = Resources.amount(player, ResourceTypes.RAGE);
        helper.assertTrue(Math.abs(rage - expected) <= TOLERANCE, what + ": rage " + expected + " expected, got "
                + rage);
    }

    /** {@code count} seconds of player ticks, as {@code SpellGameTests} plays them; the game time stands still. */
    private static void seconds(ServerPlayer player, int count) {
        for (int i = 0; i < count * Resources.INTERVAL_TICKS; i++) {
            player.tickCount++;
            player.doTick();
        }
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
