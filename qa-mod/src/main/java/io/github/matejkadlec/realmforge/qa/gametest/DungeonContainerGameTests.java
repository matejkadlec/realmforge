package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.PlannedRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.RoomContainers;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * RLM-365: chance tables decide how many chests and barrels each room of a dungeon places. Over many seeds every
 * room of the test dungeon keeps each count about as often as its table says, and a run holds about 3 containers per
 * spawner (Matěj's "~6x loot container per 2 spawners"); a built layout places exactly what its plan drew.
 */
public final class DungeonContainerGameTests {
    private static final int TIMEOUT = 1200;
    private static final Identifier TEST_DUNGEON = Identifier.parse("realmforge:test_dungeon");
    /** The throne room's chests are a sight behind unbreakable iron bars, not loot: its table keeps all six. */
    private static final Identifier THRONE_ROOM =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/dungeon/treasure/mooshroom_king");
    private static final int SEEDS = 300;
    /** Matěj's target, and how far a run's average may lie from it with the test dungeon's tables. */
    private static final double CONTAINERS_PER_SPAWNER = 3.0;
    private static final double RATIO_TOLERANCE = 0.5;

    private DungeonContainerGameTests() {
    }

    /** Done when of RLM-365: over many seeds the drawn counts lie within tolerance of each table. */
    @QaGameTest
    public static void eachRoomKeepsItsContainersAsItsTableSays(GameTestHelper helper) {
        DungeonType type = testDungeon(helper);
        // Per room and kind: how often each count stayed.
        Map<String, Map<Integer, Integer>> kept = new TreeMap<>();
        Map<String, List<Integer>> tables = new HashMap<>();
        Map<String, Integer> held = new HashMap<>();
        long containers = 0;
        long spawners = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonPlan plan = type.plan(seed);
            InstanceLayout layout = plan.toInstanceLayout();
            for (int i = 0; i < plan.rooms().size(); i++) {
                PlannedRoom room = plan.rooms().get(i);
                RoomContainers roomContainers = room.room().containers();
                spawners += room.room().spawners();
                Set<BlockPos> swapped = new HashSet<>();
                layout.rooms().get(i).swaps().forEach(swap -> swapped.add(swap.pos()));
                for (RoomContainers.Kind kind : RoomContainers.Kind.values()) {
                    List<RoomContainers.Spot> spots = roomContainers.spots().stream()
                            .filter(spot -> spot.kind() == kind).toList();
                    if (spots.isEmpty()) {
                        continue;
                    }
                    int stayed = (int) spots.stream().filter(spot -> !swapped.contains(spot.pos())).count();
                    if (!room.room().template().equals(THRONE_ROOM)) {
                        containers += stayed;
                    }
                    String key = room.room().template().getPath() + " " + kind.getSerializedName();
                    kept.computeIfAbsent(key, k -> new TreeMap<>()).merge(stayed, 1, Integer::sum);
                    tables.put(key, roomContainers.table(kind));
                    held.put(key, spots.size());
                }
            }
        }
        helper.assertFalse(kept.isEmpty(), "no room of the test dungeon has a chance table");
        for (Map.Entry<String, Map<Integer, Integer>> entry : kept.entrySet()) {
            String key = entry.getKey();
            double[] expected = capped(tables.get(key), held.get(key));
            int samples = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            for (int count = 0; count < expected.length; count++) {
                double share = entry.getValue().getOrDefault(count, 0) / (double) samples;
                double p = expected[count];
                double tolerance = 4 * Math.sqrt(p * (1 - p) / samples) + 0.02;
                helper.assertTrue(Math.abs(share - p) <= tolerance, key + " kept " + count + " in "
                        + String.format(java.util.Locale.ROOT, "%.3f", share) + " of " + samples + " rooms, not "
                        + String.format(java.util.Locale.ROOT, "%.3f", p) + ": " + entry.getValue());
            }
        }
        double ratio = containers / (double) spawners;
        RealmforgeQa.LOGGER.info("Test dungeon over {} seeds: {} containers to {} spawners a run, {} per spawner",
                SEEDS, containers / (double) SEEDS, spawners / (double) SEEDS, ratio);
        helper.assertTrue(Math.abs(ratio - CONTAINERS_PER_SPAWNER) <= RATIO_TOLERANCE,
                "a run holds " + ratio + " containers per spawner, not about " + CONTAINERS_PER_SPAWNER);
        helper.succeed();
    }

    /** A built layout holds the containers its plan kept, and the blocks the dropped ones left behind. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aBuiltLayoutPlacesTheContainersItsPlanKept(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        DungeonPlan plan = testDungeon(helper).plan(20261027L);
        InstanceLayout layout = plan.toInstanceLayout();
        helper.assertTrue(layout.rooms().stream().mapToInt(room -> room.swaps().size()).sum() > 0,
                "the plan drops no container");
        Plot plot = manager.open(layout, List.of());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    for (int i = 0; i < plan.rooms().size(); i++) {
                        RoomPlacement room = layout.rooms().get(i);
                        Set<BlockPos> swapped = new HashSet<>();
                        for (RoomPlacement.Swap swap : room.swaps()) {
                            swapped.add(swap.pos());
                            BlockState placed = instances.getBlockState(world(plot, room, swap.pos()));
                            helper.assertTrue(placed.is(swap.state().getBlock()), room.template() + " placed "
                                    + placed + " where a dropped container left " + swap.state());
                        }
                        for (RoomContainers.Spot spot : plan.rooms().get(i).room().containers().spots()) {
                            if (!swapped.contains(spot.pos())) {
                                BlockState placed = instances.getBlockState(world(plot, room, spot.pos()));
                                helper.assertTrue(placed.is(spot.kind() == RoomContainers.Kind.CHEST ? Blocks.CHEST
                                        : Blocks.BARREL), room.template() + " lost a kept " + spot.kind()
                                        .getSerializedName() + " at " + spot.pos().toShortString() + ": " + placed);
                            }
                        }
                    }
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /** A template position of {@code room} where the builder put it in the world. */
    private static BlockPos world(Plot plot, RoomPlacement room, BlockPos local) {
        return plot.origin().offset(room.offset())
                .offset(StructureTemplate.transform(local, Mirror.NONE, room.rotation(), BlockPos.ZERO));
    }

    /** The chance of keeping each count of a room that holds {@code held}: the table, its higher counts capped. */
    private static double[] capped(List<Integer> table, int held) {
        double total = table.stream().mapToInt(Integer::intValue).sum();
        double[] chances = new double[held + 1];
        for (int count = 0; count < table.size(); count++) {
            chances[Math.min(count, held)] += table.get(count) / total;
        }
        return chances;
    }

    private static DungeonType testDungeon(GameTestHelper helper) {
        DungeonType type = helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(TEST_DUNGEON);
        helper.assertTrue(type != null, TEST_DUNGEON + " is not loaded");
        return type;
    }
}
