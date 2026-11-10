package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.ResourceBarView;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.InCombat;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * C3a-4 (RLM-212): rage as a combat resource (architecture 11.5). The Warrior's numbers in {@code content/classes/}
 * give 10 rage for a full-strength melee hit and 50 for losing the whole max health to attackers, and once the Warrior
 * is out of combat, 10 s after the last hit dealt or taken, the pool loses 5 every second (RLM-594). A swing gives its
 * rage once, however many mobs it hits (RLM-278). A fake player's connection never ticks it, so the tests run its ticks
 * themselves ({@link #seconds}); its combat clock is the level's game time.
 */
public final class RageGameTests {
    private static final double TOLERANCE = 1e-3;
    /** A tick past the 10 s of combat after a hit (the common config's default, {@code CombatConfig}). */
    private static final int OUT_OF_COMBAT_TICKS = 201;
    /** Ten ticks before the 10 s of combat after a hit end. */
    private static final int LAST_COMBAT_TICK = 190;

    private RageGameTests() {
    }

    /** A Warrior's rage rises with the hits it deals and takes, holds in combat, and decays once out of it. */
    @QaGameTest(timeoutTicks = 300)
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
        // RLM-594: 5 s after the last hit the Warrior is still in combat, and its rage holds.
        helper.runAfterDelay(101, () -> {
            seconds(player, 1);
            assertRage(helper, player, afterHits, "no decay 5 s after the last hit");
        });
        // 10 s after the last hit the Warrior is out of combat.
        helper.runAfterDelay(OUT_OF_COMBAT_TICKS, () -> {
            helper.assertFalse(InCombat.isInCombat(player), "out of combat 10 s after the last hit");
            seconds(player, 1);
            assertRage(helper, player, afterHits - 5, "out of combat it decays 5 a second");
            seconds(player, 2);
            assertRage(helper, player, Math.max(0, afterHits - 15), "two more seconds");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * RLM-594's numbers: a full pool holds for the 10 s of combat after the last hit, then loses 5 a second, so it is
     * empty 20 s later, 30 s after the last hit.
     */
    @QaGameTest(timeoutTicks = 300)
    public static void fullRageHoldsTenSecondsThenEmptiesInTwenty(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-rage-full", "warrior");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        seconds(player, 1);
        player.attack(husk);
        Resources.set(player, ResourceTypes.RAGE, ResourceTypes.RAGE_MAXIMUM);
        helper.runAfterDelay(LAST_COMBAT_TICK, () -> {
            helper.assertTrue(InCombat.isInCombat(player), "still in combat just before 10 s");
            seconds(player, 1);
            assertRage(helper, player, 100, "a full pool holds in combat");
        });
        helper.runAfterDelay(OUT_OF_COMBAT_TICKS, () -> {
            seconds(player, 1);
            assertRage(helper, player, 95, "the first second out of combat");
            seconds(player, 18);
            assertRage(helper, player, 5, "19 seconds out of combat");
            seconds(player, 1);
            assertRage(helper, player, 0, "empty 20 s after it left combat");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * RLM-594: a hit during the decay puts the Warrior back in combat, which stops it for another 10 s. Every step is
     * registered up front: the test framework runs them from a map it iterates, which a step must not add to.
     */
    @QaGameTest(timeoutTicks = 500)
    public static void aHitDuringTheDecayStopsItForAnotherTenSeconds(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-rage-restart", "warrior");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        seconds(player, 1);
        player.attack(husk);
        Resources.set(player, ResourceTypes.RAGE, ResourceTypes.RAGE_MAXIMUM);
        double[] afterHit = new double[1];
        helper.runAfterDelay(OUT_OF_COMBAT_TICKS, () -> {
            seconds(player, 2);
            assertRage(helper, player, 90, "two seconds of decay");
            // A hit taken, not dealt: its rage is the share of max health it took.
            float before = player.getHealth();
            player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(husk), 2);
            afterHit[0] = 90 + 50 * (before - player.getHealth()) / player.getMaxHealth();
            helper.assertTrue(InCombat.isInCombat(player), "the hit puts the Warrior back in combat");
            seconds(player, 1);
            assertRage(helper, player, afterHit[0], "no decay right after the hit");
        });
        helper.runAfterDelay(OUT_OF_COMBAT_TICKS + LAST_COMBAT_TICK, () -> {
            seconds(player, 1);
            assertRage(helper, player, afterHit[0], "no decay just before 10 s after the hit");
        });
        helper.runAfterDelay(2 * OUT_OF_COMBAT_TICKS, () -> {
            seconds(player, 1);
            assertRage(helper, player, afterHit[0] - 5, "the decay starts again 10 s after the hit");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * RLM-278: a full-strength swing whose sword sweeps the mobs beside its target gives the per-hit rage once, not
     * once for every mob it hurts. Better Combat turns vanilla's sweep off for the weapons it knows (the stone sword
     * among them), so with it loaded the swing hurts only its target; {@link #aSwingThroughSeveralMobsGivesRageOnce}
     * covers its own swing.
     */
    @QaGameTest
    public static void aSweepGivesRageOnce(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-rage-sweep", "warrior");
        Husk target = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        Husk left = SpellGameTests.husk(helper, new BlockPos(1, 1, 2));
        Husk right = SpellGameTests.husk(helper, new BlockPos(3, 1, 2));
        helper.assertTrue(player.getMainHandItem().is(Items.STONE_SWORD), "the Warrior holds its stone sword");
        seconds(player, 1);
        helper.assertTrue(player.getAttackStrengthScale(0.5F) >= 1, "a full-strength swing is ready");
        // A fake player's client never reports where it stands, and vanilla sweeps only on the ground.
        player.setOnGround(true);

        player.attack(target);
        helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the swing hit its target");
        if (!ModList.get().isLoaded("bettercombat")) {
            helper.assertTrue(left.getHealth() < left.getMaxHealth() && right.getHealth() < right.getMaxHealth(),
                    "the sweep hurt both mobs beside the target");
        }
        assertRage(helper, player, 10, "a swing that sweeps three mobs gives 10 once");
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-278: Better Combat hits each target of a swing with its own full-strength {@code Player#attack}, all in the
     * same game tick. The tests' player ticks leave the game time standing, so recharging between two attacks plays
     * that swing: the rage comes once, and the next swing, a tick later, gives it again.
     */
    @QaGameTest
    public static void aSwingThroughSeveralMobsGivesRageOnce(GameTestHelper helper) {
        ServerPlayer player = character(helper, "qa-rage-swing", "warrior");
        Husk first = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        // Out of the first hit's sweep, so only its own attack hurts it.
        Husk second = SpellGameTests.husk(helper, new BlockPos(0, 1, 2));
        seconds(player, 1);
        player.attack(first);
        seconds(player, 1);
        helper.assertTrue(player.getAttackStrengthScale(0.5F) >= 1, "the second target's hit is at full strength");
        player.attack(second);
        helper.assertTrue(first.getHealth() < first.getMaxHealth() && second.getHealth() < second.getMaxHealth(),
                "the swing hit both mobs");
        assertRage(helper, player, 10, "two full-strength hits in one tick give 10 once");
        helper.runAfterDelay(1, () -> {
            seconds(player, 1);
            CombatGameTests.settle(first);
            player.attack(first);
            assertRage(helper, player, 20, "the next tick's swing gives 10 more");
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
    /**
     * RLM-400: the resource bar reads the class's pool from the data synced to its owner. A Warrior shows "0 / 100"
     * before the first hit and the rage a swing gives after it; a Mage shows a full mana pool once its first second has
     * filled it; a player without a character shows no bar.
     */
    @QaGameTest
    public static void theResourceBarShowsTheClassPool(GameTestHelper helper) {
        ServerPlayer nobody = QaPlayers.loginOnPlatform(helper, "qa-bar-nobody", new Vec3(0.5, 1, 0.5));
        helper.assertTrue(ResourceBarView.of(nobody).isEmpty(), "no character, no bar");
        logout(nobody);

        ServerPlayer warrior = character(helper, "qa-bar-warrior", "warrior");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        seconds(warrior, 1);
        ResourceBarView rage = ResourceBarView.of(warrior).orElseThrow();
        helper.assertTrue(rage.type().is(ResourceTypes.RAGE.getKey()), "a Warrior's bar is rage");
        helper.assertValueEqual(rage.text(), "0 / 100", "before the first hit");
        warrior.attack(husk);
        helper.assertValueEqual(ResourceBarView.of(warrior).orElseThrow().text(), "10 / 100", "after a full swing");
        logout(warrior);

        ServerPlayer mage = character(helper, "qa-bar-mage", "mage");
        seconds(mage, 1);
        ResourceBarView mana = ResourceBarView.of(mage).orElseThrow();
        helper.assertTrue(mana.type().is(ResourceTypes.MANA.getKey()), "a Mage's bar is mana");
        helper.assertValueEqual(mana.text(), "100 / 100", "a full pool");
        helper.assertFalse(mana.leadingEdge(), "a full pool has no leading edge");
        logout(mage);
        helper.succeed();
    }

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
