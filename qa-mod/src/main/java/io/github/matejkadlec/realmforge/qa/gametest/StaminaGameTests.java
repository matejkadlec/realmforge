package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.StatExpectation;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * RLM-171: Stamina lowers the food exhaustion a player gains, by the share its content entry gives
 * ({@code lowers_exhaustion} in {@code content/stats/stamina.yaml}, through the generated expectations), from
 * sprinting and from attacking alike, at most its cap ({@code mixin/PlayerExhaustionMixin}, architecture 8.1).
 */
public final class StaminaGameTests {
    // Exhaustion is a float in the game.
    private static final double TOLERANCE = 1e-6;

    private StaminaGameTests() {
    }

    @QaGameTest
    public static void staminaLowersTheExhaustionOfSprintingAndAttacking(GameTestHelper helper) {
        StatExpectation stamina = GeneratedStatExpectations.STATS.stream()
                .filter(stat -> stat.id().equals("realmforge:stamina"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no expectation for realmforge:stamina"));
        helper.assertTrue(stamina.exhaustionPerPoint() > 0, "Stamina lowers exhaustion");
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-stamina");
        player.setGameMode(GameType.SURVIVAL);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 2));

        Consumer<ServerPlayer> sprint = sprinter -> {
            sprinter.setOnGround(true);
            sprinter.setSprinting(true);
            sprinter.checkMovementStatistics(1, 0, 0);
        };
        Consumer<ServerPlayer> attack = attacker -> {
            target.invulnerableTime = 0;
            target.setHealth(target.getMaxHealth());
            attacker.attack(target);
        };
        for (Consumer<ServerPlayer> action : List.of(sprint, attack)) {
            String what = action == sprint ? "sprinting a block" : "a hit";
            StatsCommands.setBase(player, GeneratedStats.STAMINA, 0);
            double without = gained(player, action);
            helper.assertTrue(without > 0, what + " exhausts a player without Stamina");
            // Below the cap, and well past it.
            double capped = stamina.exhaustionMax() / stamina.exhaustionPerPoint();
            for (double points : List.of(Math.floor(capped / 2), capped + 30)) {
                StatsCommands.setBase(player, GeneratedStats.STAMINA, points);
                double share = Math.min(points * stamina.exhaustionPerPoint(), stamina.exhaustionMax());
                double with = gained(player, action);
                helper.assertTrue(Math.abs(with - without * (1 - share)) <= TOLERANCE, what + " with " + points
                        + " Stamina: " + without * (1 - share) + " exhaustion expected (" + without + " less "
                        + share * 100 + "%), got " + with);
            }
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** The exhaustion {@code action} adds to the player's food data. */
    private static double gained(ServerPlayer player, Consumer<ServerPlayer> action) {
        float before = exhaustion(player);
        action.accept(player);
        return exhaustion(player) - before;
    }

    /** {@code FoodData} has no getter for its exhaustion, so read it as the player's save holds it. */
    private static float exhaustion(ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        player.getFoodData().addAdditionalSaveData(output);
        return output.buildResult().getFloatOr("foodExhaustionLevel", 0);
    }
}
