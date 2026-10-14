package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.PlannedRoom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * W4a (architecture 15.3): a layout the planner draws for every dungeon type builds into a plot of
 * {@code realmforge:instances} with spike 3's builder. Every room stands where the plan put it, and no jigsaw block is
 * left: each became its final state. The {@code /qa dungeon} commands plan and build the same layouts. JUnit fuzzes the
 * planner itself over thousands of seeds.
 */
public final class DungeonPlanGameTests {
    /** A plot of a dozen to two dozen rooms builds in well under this, even on a slow CI machine. */
    private static final int TIMEOUT = 1200;
    private static final long SEED = 20261014L;

    private DungeonPlanGameTests() {
    }

    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void plannedLayoutsBuildIntoPlots(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        Registry<DungeonType> registry =
                helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE);
        List<DungeonPlan> plans = new ArrayList<>();
        List<Plot> plots = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            if (id.getNamespace().equals(Realmforge.MOD_ID)) {
                DungeonPlan plan = registry.getValue(id).plan(SEED);
                plans.add(plan);
                plots.add(manager.open(plan.toInstanceLayout(), List.of()));
            }
        }
        helper.assertFalse(plans.isEmpty(), "no dungeon type to plan");

        helper.startSequence()
                .thenWaitUntil(() -> plots.forEach(plot -> helper.assertTrue(plot.state() == PlotState.READY,
                        "plot " + plot.id() + " is " + plot.state())))
                .thenExecute(() -> {
                    for (int i = 0; i < plans.size(); i++) {
                        BlockPos origin = plots.get(i).origin();
                        for (PlannedRoom room : plans.get(i).rooms()) {
                            BoundingBox box = room.box().moved(origin.getX(), origin.getY(), origin.getZ());
                            checkRoom(helper, instances, box, room.room().template().toString());
                        }
                    }
                    plots.forEach(manager::release);
                })
                .thenWaitUntil(() -> plots.forEach(plot -> helper.assertTrue(plot.state() == PlotState.FREE,
                        "plot " + plot.id() + " is " + plot.state())))
                .thenSucceed();
    }

    /** {@code /qa dungeon plan} and {@code build}, as typed: the same layout as the type plans for the seed. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void qaCommandsPlanAndBuildALayout(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = InstanceManager.get(server);
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source =
                server.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS).withSuppressedOutput();
        DungeonType type = helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(Identifier.parse("realmforge:test_dungeon"));
        helper.assertTrue(type != null, "realmforge:test_dungeon is not loaded");
        DungeonPlan plan = type.plan(42);

        helper.assertValueEqual(dispatcher.execute("qa dungeon plan realmforge:test_dungeon 42", source),
                plan.rooms().size(), "/qa dungeon plan returns the layout's room count");
        try {
            dispatcher.execute("qa dungeon plan realmforge:no_such_dungeon 42", source);
            helper.fail("an unknown dungeon type is refused");
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(expected.getMessage().contains("Unknown dungeon type"), expected.getMessage());
        }
        Plot plot = manager.plot(dispatcher.execute("qa dungeon build realmforge:test_dungeon 42", source))
                .orElseThrow();
        helper.assertValueEqual(plot.layout().orElseThrow(), plan.toInstanceLayout(),
                "/qa dungeon build builds the planned layout");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> manager.release(plot))
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /** The room stands in its box (it holds blocks), and none of them is a jigsaw block. */
    private static void checkRoom(GameTestHelper helper, ServerLevel level, BoundingBox box, String room) {
        int blocks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.JIGSAW)) {
                        helper.fail(room + " kept a jigsaw block at " + pos.toShortString());
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        blocks++;
                    }
                }
            }
        }
        helper.assertTrue(blocks > 0, room + " was not placed in " + box);
    }
}
