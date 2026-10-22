package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.assertDealt;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.attacker;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.close;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.setBase;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.settle;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.target;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-311 (ADR 0049): a player's damage never loses anything to vanilla's 10-tick hurt cooldown, melee and
 * projectiles alike, while mobs' and the world's hits on a player keep vanilla's window, so lava, fire, cactus and
 * a crowd of mobs hit a player no more often than in vanilla.
 */
public final class HurtCooldownGameTests {
    /** Where a player stands: the platform's north edge, facing the target in its middle. */
    private static final Vec3 EDGE = new Vec3(2.5, 1, 0.5);
    /** A player's health in the hazard tests, so 40 ticks of lava kill nobody. */
    private static final double HEALTH = 1000;
    /** How many ticks the hazard tests run: vanilla's window lets a hit through every 10. */
    private static final int TICKS = 40;

    private HurtCooldownGameTests() {
    }

    /**
     * Done when of RLM-311: two melee hits of a player 6 ticks apart both land in full. Vanilla would drop the second,
     * which is no bigger than the first, because the window the first opened is still above 10 ticks.
     */
    @QaGameTest
    public static void playerMeleeSixTicksApartLandsInFull(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-cooldown-melee", EDGE);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        Pillager mob = target(helper);
        double fist = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        meleeHit(helper, player, mob, fist, "the first hit");
        helper.startSequence()
                .thenExecuteAfter(6, () -> {
                    helper.assertTrue(mob.invulnerableTime > 10,
                            "the first hit's window is still open 6 ticks later: " + mob.invulnerableTime);
                    meleeHit(helper, player, mob, fist, "the second hit, 6 ticks later");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /**
     * A player's arrows land in full inside the window too, and so does a smaller one; a skeleton's arrow, which has
     * the same damage type, still meets the window. Hits through the pipeline's damage sources, as an arrow's impact
     * sends them.
     */
    @QaGameTest
    public static void playerArrowsLandInFullAndMobArrowsMeetTheWindow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-cooldown-arrow", EDGE);
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(0, 1, 0));
        skeleton.setNoAi(true);
        Pillager mob = target(helper);

        assertDealt(helper, mob, arrow(level, player), 6, 6, "a player's arrow");
        helper.assertTrue(mob.invulnerableTime > 10, "the arrow opened a window: " + mob.invulnerableTime);
        assertDealt(helper, mob, arrow(level, player), 6, 6, "a second arrow of the same damage in the window");
        assertDealt(helper, mob, arrow(level, player), 3, 3, "a smaller arrow in the window");
        assertDealt(helper, mob, arrow(level, skeleton), 3, 0, "a skeleton's arrow no bigger than the last hit");
        assertDealt(helper, mob, arrow(level, skeleton), 8, 5,
                "a bigger skeleton arrow deals its difference to the last hit");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Lava, fire and cactus hit a player at most every 10 ticks, as in vanilla. */
    @QaGameTest
    public static void theWorldHitsAPlayerNoMoreOftenThanVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-cooldown-world", EDGE);
        setBase(player, Attributes.MAX_HEALTH, HEALTH);
        assertVanillaWindow(helper, player, List.of(() -> level.damageSources().lava()), 4, "lava");
        assertVanillaWindow(helper, player, List.of(() -> level.damageSources().inFire(),
                () -> level.damageSources().onFire()), 1, "fire, and burning");
        assertVanillaWindow(helper, player, List.of(() -> level.damageSources().cactus()), 1, "cactus");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Five zombies that each hit a player every tick land a full hit at most every 10 ticks, as in vanilla. */
    @QaGameTest
    public static void aCrowdOfMobsHitsAPlayerNoMoreOftenThanVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-cooldown-crowd", EDGE);
        setBase(player, Attributes.MAX_HEALTH, HEALTH);
        List<Supplier<DamageSource>> crowd = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Zombie zombie = attacker(helper);
            crowd.add(() -> level.damageSources().mobAttack(zombie));
        }
        assertVanillaWindow(helper, player, crowd, 3, "five zombies");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Ticks the player's hurt cooldown {@value #TICKS} times, as {@code LivingEntity#baseTick} counts it down, and each
     * tick hurts it by every source for {@code damage}; then checks that a hit landed in full (opening a new window)
     * at most every 10 ticks and at least once.
     */
    private static void assertVanillaWindow(GameTestHelper helper, ServerPlayer player,
            List<Supplier<DamageSource>> sources, float damage, String what) {
        settle(player);
        player.setHealth(player.getMaxHealth());
        int fullHits = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            if (player.invulnerableTime > 0) {
                player.invulnerableTime--;
            }
            for (Supplier<DamageSource> source : sources) {
                int before = player.invulnerableTime;
                player.hurtServer(helper.getLevel(), source.get(), damage);
                if (player.invulnerableTime > before) {
                    fullHits++;
                }
            }
        }
        helper.assertTrue(fullHits >= 1, what + " hurt the player at all");
        helper.assertTrue(fullHits <= TICKS / 10,
                what + ": " + fullHits + " full hits in " + TICKS + " ticks, vanilla allows " + TICKS / 10);
        player.clearFire();
    }

    /** A player's melee hit with full attack strength, which deals {@code expected}. */
    private static void meleeHit(GameTestHelper helper, ServerPlayer player, Pillager target, double expected,
            String what) {
        for (int i = 0; i < 20; i++) {
            player.doTick();
        }
        float before = target.getHealth();
        player.attack(target);
        double dealt = before - target.getHealth();
        helper.assertTrue(close(dealt, expected), what + ": " + expected + " expected, dealt " + dealt);
    }

    /** An arrow hit from {@code shooter}, as an arrow's impact sends it. */
    private static DamageSource arrow(ServerLevel level, LivingEntity shooter) {
        Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
        return level.damageSources().arrow(arrow, shooter);
    }
}
