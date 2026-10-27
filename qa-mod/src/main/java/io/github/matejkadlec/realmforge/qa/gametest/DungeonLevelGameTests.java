package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * RLM-459 (RLM-240, part 5; architecture 14.1 and 15.5): a dungeon run's level is its type's base raised to the highest
 * level of its party when it opens, and holds for the whole run; the mobs in its plot take it by their rank, a boss
 * ({@code c:bosses}) at it and every other mob two or three below. A run opened without a type gives its mobs no level,
 * as before.
 *
 * <p>The runs are the spike rooms, opened with a base by hand, or the test dungeon through {@code openPortal}. The
 * party enters once the rooms stand, and the mobs spawn where a member stands.
 */
public final class DungeonLevelGameTests {
    /** Building and clearing the spike rooms take a few dozen ticks; this leaves room for a slow CI machine. */
    private static final int TIMEOUT = 600;
    /** Mobs rolled per check: the chance that 40 rolls of two equally likely levels all land on one is 2^-39. */
    private static final int ROLLS = 40;
    private static final ResourceKey<DungeonType> TEST_DUNGEON =
            ResourceKey.create(DungeonRegistries.DUNGEON_TYPE, Identifier.parse("realmforge:test_dungeon"));

    private DungeonLevelGameTests() {
    }

    /**
     * The party's highest player raises the base (10) to 25: the boss is 25 and the other mobs 22 or 23, and they stay
     * so after that player levels up to 40 mid-run.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aRunTakesItsPartysHighestLevelAndHoldsIt(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        ServerPlayer low = QaPlayers.loginOnPlatform(helper, "qa-run-level-low");
        ServerPlayer high = QaPlayers.loginOnPlatform(helper, "qa-run-level-high");
        setPlayerLevel(low, 12);
        setPlayerLevel(high, 25);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(low.getUUID(), high.getUUID()), null, 0L,
                OptionalInt.of(10));
        helper.assertValueEqual(plot.level(), OptionalInt.of(25), "the run's level: the party's highest");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
                    helper.assertTrue(high.level() == instances, "the party entered the run");
                    helper.assertValueEqual(levelsOf(helper, EntityType.SKELETON, instances, high.blockPosition()),
                            Set.of(22, 23), "a skeleton's levels in a level 25 run");
                    helper.assertValueEqual(levelsOf(helper, EntityType.WITHER, instances, high.blockPosition()),
                            Set.of(25), "a boss's level in a level 25 run");

                    setPlayerLevel(high, 40);
                    helper.assertValueEqual(levelsOf(helper, EntityType.SKELETON, instances, high.blockPosition()),
                            Set.of(22, 23), "the run holds its level when a member levels up");
                    helper.assertValueEqual(plot.level(), OptionalInt.of(25), "the run's level after the level-up");
                    close(helper, manager, plot, low, high);
                })
                .thenSucceed();
    }

    /** A level 3 player in a run of base 10 meets the run at 10: its other mobs at 7 or 8. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aRunBelowItsBaseStaysAtTheBase(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-run-level-base");
        setPlayerLevel(player, 3);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()), null, 0L, OptionalInt.of(10));
        helper.assertValueEqual(plot.level(), OptionalInt.of(10), "the run's level: the base");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
                    helper.assertValueEqual(levelsOf(helper, EntityType.ZOMBIE, instances, player.blockPosition()),
                            Set.of(7, 8), "a zombie's levels in a level 10 run");
                    close(helper, manager, plot, player);
                })
                .thenSucceed();
    }

    /**
     * A key's run takes its type's level: the test dungeon's base for a level 1 player, the player's own for a level
     * 30 one. The runs are closed again before they are built.
     */
    @QaGameTest
    public static void aPortalRunTakesItsTypesBaseRaisedToItsOpener(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        Holder.Reference<DungeonType> type = helper.getLevel().registryAccess()
                .lookupOrThrow(DungeonRegistries.DUNGEON_TYPE).getOrThrow(TEST_DUNGEON);
        GlobalPos portal = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BlockPos.ZERO.above()));
        ServerPlayer newcomer = QaPlayers.loginOnPlatform(helper, "qa-run-level-newcomer");
        ServerPlayer veteran = QaPlayers.loginOnPlatform(helper, "qa-run-level-veteran");
        setPlayerLevel(newcomer, 1);
        setPlayerLevel(veteran, 30);
        helper.assertTrue(type.value().level() < 30, "the test dungeon's base is below 30");

        Plot newcomers = manager.openPortal(newcomer, type, portal);
        Plot veterans = manager.openPortal(veteran, type, portal);
        helper.assertValueEqual(newcomers.level(), OptionalInt.of(type.value().level()), "a level 1 opener's run");
        helper.assertValueEqual(veterans.level(), OptionalInt.of(30), "a level 30 opener's run");
        close(helper, manager, newcomers, newcomer);
        close(helper, manager, veterans, veteran);
        helper.assertValueEqual(newcomers.level(), OptionalInt.empty(), "a closed plot holds no level");
        helper.succeed();
    }

    /** A run opened without a dungeon type (the spike rooms of the QA tests) has no level, and neither do its mobs. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aRunWithoutATypeGivesItsMobsNoLevel(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-run-level-none");
        setPlayerLevel(player, 20);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));
        helper.assertValueEqual(plot.level(), OptionalInt.empty(), "a run without a type");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
                    Mob skeleton = EntityType.SKELETON.spawn(instances, player.blockPosition(),
                            EntitySpawnReason.COMMAND);
                    helper.assertTrue(skeleton != null, "a skeleton spawned in the run");
                    try {
                        helper.assertValueEqual(MobLevels.level(skeleton), OptionalInt.empty(),
                                "a mob of a run without a level");
                    } finally {
                        skeleton.discard();
                    }
                    close(helper, manager, plot, player);
                })
                .thenSucceed();
    }

    /**
     * The levels of {@link #ROLLS} mobs of {@code type} spawned at {@code pos}, each with its level's scaling and full
     * health, removed again.
     */
    private static Set<Integer> levelsOf(GameTestHelper helper, EntityType<? extends Mob> type, ServerLevel level,
            BlockPos pos) {
        Set<Integer> seen = new TreeSet<>();
        for (int i = 0; i < ROLLS; i++) {
            Mob mob = type.spawn(level, pos, EntitySpawnReason.COMMAND);
            helper.assertTrue(mob != null, "a " + type.getDescriptionId() + " spawned at " + pos);
            try {
                OptionalInt got = MobLevels.level(mob);
                helper.assertTrue(got.isPresent(), mob.getType().getDescriptionId() + " has no level in the run");
                double want = MobScaling.at(level.registryAccess(), got.getAsInt()).health() - 1;
                AttributeModifier modifier = mob.getAttribute(Attributes.MAX_HEALTH)
                        .getModifier(MobScaling.HEALTH_MODIFIER);
                double amount = modifier != null ? modifier.amount() : 0;
                helper.assertTrue(Math.abs(amount - want) < 1e-4, mob.getType().getDescriptionId() + " at level "
                        + got.getAsInt() + " has its health scaling: " + amount + ", not " + want);
                helper.assertTrue(Math.abs(mob.getHealth() - mob.getMaxHealth()) < 1e-3,
                        mob.getType().getDescriptionId() + " starts with full health");
                seen.add(got.getAsInt());
            } finally {
                mob.discard();
            }
        }
        return seen;
    }

    /** Releases the run, which sends its party back, and logs the players out. */
    private static void close(GameTestHelper helper, InstanceManager manager, Plot plot, ServerPlayer... players) {
        manager.release(plot);
        for (ServerPlayer player : players) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static void setPlayerLevel(ServerPlayer player, int level) {
        player.setData(StatsAttachments.LEVEL, new EntityLevel(level));
    }

    private static InstanceManager manager(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        return manager;
    }
}
