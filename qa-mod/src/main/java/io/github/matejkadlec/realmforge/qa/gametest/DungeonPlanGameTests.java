package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.PlannedRoom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * W4a and W4b (architecture 15.3, 15.5): a layout the planner draws for every dungeon type builds into a plot of
 * {@code realmforge:instances}. Every room stands where the plan put it, and no jigsaw block is left: each became its
 * final state. The {@code /qa dungeon} commands plan, build and enter the same layouts. JUnit fuzzes the planner itself
 * over thousands of seeds.
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

    /**
     * Done when of W4b: {@code /qa dungeon enter} builds an instance of a dungeon type and moves the player in at its
     * entrance: on a floor inside the entrance room with room for their head (the test dungeon's entrances have no
     * entrance marker), looking at the way on; {@code /qa instance leave} brings them back.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aDungeonIsBuiltAndEnteredAtItsEntrance(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = InstanceManager.get(server);
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-dungeon-enter"));
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source =
                player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS).withSuppressedOutput();
        DungeonType type = helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(Identifier.parse("realmforge:test_dungeon"));
        helper.assertTrue(type != null, "realmforge:test_dungeon is not loaded");

        Plot plot = manager.plot(dispatcher.execute("qa dungeon enter realmforge:test_dungeon " + SEED, source))
                .orElseThrow();
        InstanceLayout layout = type.plan(SEED).toInstanceLayout();
        helper.assertValueEqual(plot.layout().orElseThrow(), layout, "/qa dungeon enter builds the planned layout");
        helper.assertTrue(layout.lookAt().isPresent(), "the layout knows the way on");
        try {
            dispatcher.execute("qa dungeon enter realmforge:test_dungeon " + SEED, source);
            helper.fail("a player in a run cannot enter another");
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(expected.getMessage().contains("already in an instance run"), expected.getMessage());
        }

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(player.level() == instances, "the player entered when the dungeon was built");
                    Vec3 relative = plot.spawn().orElseThrow();
                    Vec3 spawn = Vec3.atLowerCornerOf(plot.origin()).add(relative);
                    helper.assertTrue(player.position().closerThan(spawn, 0.01),
                            "the player stands at the spawn " + spawn + ", not at " + player.position());
                    BlockPos feet = BlockPos.containing(spawn);
                    helper.assertTrue(plot.rooms().getFirst().isInside(feet) && plot.rooms().getFirst()
                            .isInside(feet.above()), "the spawn " + feet.toShortString() + " is in the entrance room");
                    helper.assertTrue(instances.getBlockState(feet.below()).isFaceSturdy(instances, feet.below(),
                            Direction.UP), "the player stands on a floor");
                    for (BlockPos open : List.of(feet, feet.above())) {
                        helper.assertTrue(instances.getBlockState(open).getCollisionShape(instances, open).isEmpty(),
                                "the player has room at " + open.toShortString());
                    }
                    helper.assertTrue(Math.abs(player.getYRot() - layout.yawFrom(relative)) < 0.01F,
                            "the player looks at the way on, not at yaw " + player.getYRot());
                    try {
                        helper.assertValueEqual(dispatcher.execute("qa instance leave", source), 1,
                                "/qa instance leave");
                    } catch (CommandSyntaxException e) {
                        helper.fail("/qa instance leave failed: " + e.getMessage());
                    }
                    helper.assertTrue(player.level() == helper.getLevel(), "the player is back in the overworld");
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "the last member left, so the plot closes");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> server.getPlayerList().remove(player))
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
