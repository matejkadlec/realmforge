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
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * RLM-412: the husk temple's and the egg room's spawners show on one side, never the side a run enters the room from
 * (Matěj, 2026-10-28). A built layout clears the blocks of the side its plan drew and leaves the other sides closed.
 */
public final class DungeonOpeningGameTests {
    private static final int TIMEOUT = 1200;
    private static final Identifier TEST_DUNGEON = Identifier.parse("realmforge:test_dungeon");
    /** A layout that holds the husk temple and the egg room. */
    private static final long SEED = 42L;

    private DungeonOpeningGameTests() {
    }

    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aBuiltRoomOpensTheSideItsPlanDrewAndNoOther(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        DungeonType type = helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(TEST_DUNGEON);
        helper.assertTrue(type != null, TEST_DUNGEON + " is not loaded");
        DungeonPlan plan = type.plan(SEED);
        InstanceLayout layout = plan.toInstanceLayout();
        long opening = plan.rooms().stream().filter(room -> !room.room().openings().sides().isEmpty()).count();
        helper.assertTrue(opening >= 2, "seed " + SEED + " holds " + opening + " rooms with openings, not the husk"
                + " temple and the egg room");
        Plot plot = manager.open(layout, List.of());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    for (int i = 1; i < plan.rooms().size(); i++) {
                        DungeonRoom room = plan.rooms().get(i).room();
                        RoomPlacement placed = layout.rooms().get(i);
                        Set<BlockPos> cleared = placed.swaps().stream().map(RoomPlacement.Swap::pos)
                                .collect(Collectors.toSet());
                        Direction wayIn = room.connectors().get(plan.wayIn(i).orElseThrow()).front();
                        int open = 0;
                        for (Map.Entry<Direction, List<BlockPos>> side : room.openings().sides().entrySet()) {
                            boolean drawn = cleared.containsAll(side.getValue());
                            open += drawn ? 1 : 0;
                            helper.assertTrue(!drawn || side.getKey() != wayIn, room.template()
                                    + " opened the side it is entered from, " + wayIn);
                            for (BlockPos pos : side.getValue()) {
                                BlockState block = instances.getBlockState(world(plot, placed, pos));
                                helper.assertTrue(drawn == block.is(Blocks.AIR), room.template() + "'s "
                                        + side.getKey().getSerializedName() + " side holds " + block + " at "
                                        + pos.toShortString() + (drawn ? ", where it opens" : ", which stays closed"));
                            }
                        }
                        helper.assertTrue(room.openings().sides().isEmpty() || open == 1,
                                room.template() + " opened " + open + " sides");
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
}
