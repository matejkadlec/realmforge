package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotGrid;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

/**
 * The rules of single dungeon rooms in an instance: a trap's TNT blows open only what its room lists (RLM-257), and a
 * drop down the entrance shaft lands in a fall cushion that takes all its damage (RLM-276). The rooms are the real
 * Stonevaults templates, with what {@code content/rooms/} says about them as the dungeon type carries it.
 */
public final class DungeonRoomGameTests {
    /** Building and clearing take a few dozen ticks, a long fall about four seconds; room for a slow CI machine. */
    private static final int TIMEOUT = 900;
    private static final Identifier TEST_DUNGEON = Identifier.parse("realmforge:test_dungeon");
    private static final Identifier LOCK =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/dungeon/rooms/combination_lock");
    private static final Identifier LOBBY =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/dungeon/lobby/lobby_01");
    /** The combination lock's TNT, and the smooth stone wall beside it that the TNT is there to blow open. */
    private static final BlockPos LOCK_TNT = new BlockPos(8, 2, 6);
    private static final BlockPos LOCK_WALL = new BlockPos(7, 2, 6);
    /** Where the lobby's cushion lies: the floor below its 3 x 3 ceiling hole and one block past it on every side. */
    private static final BoundingBox CUSHION = new BoundingBox(4, 0, 4, 8, 0, 8);
    /**
     * Landing spots inside the lobby, as block columns: two on the cushion's rim, where a player who holds a direction
     * while falling lands after drifting off the shaft's wall below the ceiling, and one on the full floor beside it.
     */
    private static final List<BlockPos> RIM = List.of(new BlockPos(4, 0, 6), new BlockPos(8, 0, 8));
    private static final BlockPos FLOOR = new BlockPos(10, 0, 6);
    /** A fall distance that kills a pig landing anywhere but in water. */
    private static final double DEADLY_FALL = 64.0;
    /** Lowest a room may go in a plot, so no shaft the planner lays out drops further than onto this lobby. */
    private static final int LOWEST = PlotGrid.limits().minY();

    private DungeonRoomGameTests() {
    }

    /**
     * Done when of RLM-257: an explosion in the combination lock (with the room's {@code explosion_breaks} as the
     * dungeon type carries them) breaks exactly blocks of that list, the smooth stone beside its TNT among them, and an
     * explosion in a room without the list breaks nothing.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aTrapsTntBreaksOnlyItsListedBlocks(GameTestHelper helper) {
        DungeonRoom lock = poolRoom(helper, LOCK);
        helper.assertTrue(!lock.explosionBreaks().isEmpty(), LOCK + " lists no blocks for explosions");
        InstanceLayout layout = new InstanceLayout(List.of(
                new RoomPlacement(LOCK, BlockPos.ZERO, Rotation.NONE, lock.explosionBreaks()),
                new RoomPlacement(LOCK, new BlockPos(20, 0, 0), Rotation.NONE)), Optional.empty());
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        Plot plot = manager.open(layout, List.of());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    Set<Identifier> listed = Set.copyOf(lock.explosionBreaks());
                    BoundingBox trap = plot.rooms().get(0);
                    Map<BlockPos, BlockState> before = snapshot(instances, trap);
                    BlockPos tnt = plot.origin().offset(LOCK_TNT);
                    helper.assertValueEqual(instances.getBlockState(tnt).getBlock(), Blocks.TNT, "the lock's TNT");
                    explode(instances, tnt);
                    int broken = 0;
                    for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
                        BlockState now = instances.getBlockState(entry.getKey());
                        if (now != entry.getValue()) {
                            Identifier was = BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock());
                            helper.assertTrue(listed.contains(was) && now.isAir(), "the explosion turned " + was
                                    + " at " + entry.getKey().toShortString() + " into " + now + "; only "
                                    + listed + " may break");
                            broken++;
                        }
                    }
                    helper.assertTrue(instances.getBlockState(plot.origin().offset(LOCK_WALL)).isAir(),
                            "the smooth stone beside the TNT still stands");
                    helper.assertTrue(broken > 1, "the explosion broke only " + broken + " block(s)");

                    BoundingBox other = plot.rooms().get(1);
                    Map<BlockPos, BlockState> untouched = snapshot(instances, other);
                    explode(instances, plot.origin().offset(20, 0, 0).offset(LOCK_TNT));
                    untouched.forEach((pos, state) -> helper.assertTrue(instances.getBlockState(pos) == state,
                            "a room without the list changed at " + pos.toShortString()));
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * Done when of RLM-276: a mob dropped from above the entrance's floor into the lobby set at the lowest a plot's
     * rooms reach (a longer fall than any shaft the planner can lay out) lands in the cushion unharmed, while one
     * dropped from the same height onto the lobby's roof dies. Mobs that land on the cushion's rim with a deadly fall
     * behind them are unharmed too, and one that lands on the floor beside it dies. Once its water has ticked, none is
     * outside the cushion. A mob stands in for the player: water takes a falling player's damage the same way, by
     * resetting the fall in {@code LivingEntity#checkFallDamage}.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aFallOntoTheCushionDealsNoDamage(GameTestHelper helper) {
        InstanceLayout layout = new InstanceLayout(
                List.of(new RoomPlacement(LOBBY, new BlockPos(0, LOWEST, 0), Rotation.NONE)), Optional.empty());
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        Plot plot = manager.open(layout, List.of());
        Pig[] pigs = new Pig[2];
        Pig[] landed = new Pig[RIM.size() + 1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    BlockPos lobby = plot.origin().offset(0, LOWEST, 0);
                    BlockPos.betweenClosedStream(cushion(lobby)).forEach(pos -> {
                        helper.assertTrue(instances.getFluidState(pos).is(Fluids.WATER),
                                "no water in the cushion at " + pos.toShortString());
                        instances.scheduleTick(pos.immutable(), Fluids.WATER, 1);
                        instances.updateNeighborsAt(pos.immutable(), instances.getBlockState(pos).getBlock());
                    });
                    // Two blocks above the plot's origin, where the entrance's floor is: the cushion is 66 blocks
                    // below, the lobby's roof 61.
                    pigs[0] = pig(instances, plot.origin().getX() + 6.5, plot.origin().getY() + 2,
                            plot.origin().getZ() + 6.5);
                    pigs[1] = pig(instances, plot.origin().getX() + 2.5, plot.origin().getY() + 2,
                            plot.origin().getZ() + 2.5);
                    // Under the ceiling, already falling as fast and as far as at the end of the shaft.
                    for (int i = 0; i < landed.length; i++) {
                        BlockPos column = lobby.offset(i < RIM.size() ? RIM.get(i) : FLOOR);
                        landed[i] = pig(instances, column.getX() + 0.5, column.getY() + 3, column.getZ() + 0.5);
                        landed[i].setDeltaMovement(0.0, -3.0, 0.0);
                        landed[i].fallDistance = DEADLY_FALL;
                    }
                })
                .thenWaitUntil(() -> {
                    // A failed step before this one leaves the pigs unset; the sequence still ticks this step.
                    helper.assertTrue(landed[RIM.size()] != null, "the pigs were never dropped");
                    helper.assertTrue(pigs[0].getY() < plot.origin().getY() + LOWEST + 2
                            && (pigs[0].onGround() || pigs[0].isInWater()), "the pig is still falling");
                    helper.assertTrue(!pigs[1].isAlive(), "the pig on the roof is still alive");
                    helper.assertTrue(!landed[RIM.size()].isAlive(), "the pig on the floor beside the cushion is "
                            + "still alive");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(pigs[0].isAlive() && pigs[0].getHealth() == pigs[0].getMaxHealth(),
                            "the pig that fell into the cushion has " + pigs[0].getHealth() + " of "
                                    + pigs[0].getMaxHealth() + " health");
                    for (int i = 0; i < RIM.size(); i++) {
                        helper.assertTrue(landed[i].isAlive() && landed[i].getHealth() == landed[i].getMaxHealth(),
                                "the pig that landed on the cushion's rim at " + RIM.get(i).toShortString()
                                        + " has " + landed[i].getHealth() + " of " + landed[i].getMaxHealth()
                                        + " health");
                    }
                    BlockPos lobby = plot.origin().offset(0, LOWEST, 0);
                    BoundingBox cushion = cushion(lobby);
                    Set<BlockPos> wet = BlockPos.betweenClosedStream(plot.rooms().getFirst().inflatedBy(1))
                            .filter(pos -> !instances.getFluidState(pos).isEmpty())
                            .map(BlockPos::immutable)
                            .collect(Collectors.toSet());
                    wet.removeIf(cushion::isInside);
                    helper.assertTrue(wet.isEmpty(), "water outside the cushion at " + wet);
                    pigs[0].discard();
                    for (int i = 0; i < RIM.size(); i++) {
                        landed[i].discard();
                    }
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    private static BoundingBox cushion(BlockPos lobby) {
        return CUSHION.moved(lobby.getX(), lobby.getY(), lobby.getZ());
    }

    private static DungeonRoom poolRoom(GameTestHelper helper, Identifier template) {
        DungeonType type = helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(TEST_DUNGEON);
        helper.assertTrue(type != null, TEST_DUNGEON + " is not loaded");
        DungeonRoom found = type.rooms().stream().filter(room -> room.template().equals(template)).findFirst()
                .orElse(null);
        helper.assertTrue(found != null, TEST_DUNGEON + " has no room " + template);
        return found;
    }

    private static void explode(ServerLevel level, BlockPos at) {
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4.0F, false,
                Level.ExplosionInteraction.TNT);
    }

    private static Pig pig(ServerLevel level, double x, double y, double z) {
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        pig.snapTo(x, y, z, 0.0F, 0.0F);
        pig.setPersistenceRequired();
        level.addFreshEntity(pig);
        return pig;
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BoundingBox box) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos.betweenClosedStream(box).forEach(pos -> blocks.put(pos.immutable(), level.getBlockState(pos)));
        return blocks;
    }

    private static InstanceManager manager(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        return manager;
    }
}
