package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
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
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of single dungeon rooms in an instance: a trap's TNT blows open only what its room lists (RLM-257), a
 * drop down the entrance shaft lands in a fall cushion that takes all its damage (RLM-276), and a room's entities come
 * with it (RLM-362). The rooms are the real Stonevaults templates, with what {@code content/rooms/} says about them as
 * the dungeon type carries it.
 */
public final class DungeonRoomGameTests {
    /** Building and clearing take a few dozen ticks, a long fall about four seconds; room for a slow CI machine. */
    private static final int TIMEOUT = 900;
    private static final Identifier TEST_DUNGEON = Identifier.parse("realmforge:test_dungeon");
    private static final Identifier LOCK =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/dungeon/rooms/combination_lock");
    private static final Identifier LOBBY =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/dungeon/lobby/lobby_01");
    private static final Identifier ITEM_LOCK =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/dungeon/rooms/item_combination_lock");
    /** The item lock's chest, behind its piston door, as the template has it. */
    private static final BlockPos ITEM_LOCK_CHEST = new BlockPos(11, 1, 8);
    /** The combination lock's TNT, and the smooth stone wall beside it that the TNT is there to blow open. */
    private static final BlockPos LOCK_TNT = new BlockPos(8, 2, 6);
    private static final BlockPos LOCK_WALL = new BlockPos(7, 2, 6);
    /**
     * Where the lobby's cushion lies: slabs below its 3 x 3 ceiling hole (5 to 7) and a ring of stairs one block past
     * it on every side, their tall half outward.
     */
    private static final BoundingBox CUSHION = new BoundingBox(4, 0, 4, 8, 0, 8);
    /**
     * Where a pig (0.9 wide) lands inside the lobby, relative to it: three where a player who holds a direction while
     * falling comes down after drifting off the shaft's wall below the ceiling, 0.3 past the hole's edge onto the
     * stairs' low halves (east, west and the south-east corner), and one on the full floor beside the cushion.
     */
    private static final List<Vec3> RIM = List.of(new Vec3(7.85, 0, 6.5), new Vec3(5.15, 0, 6.5),
            new Vec3(7.85, 0, 7.85));
    private static final Vec3 FLOOR = new Vec3(10.5, 0, 6.5);
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
     * dropped from the same height onto the lobby's roof dies. Mobs that land with a deadly fall behind them where a
     * drifting player comes down, across the hole's edge onto the rim stairs' low halves, are unharmed too, and one
     * that lands on the floor beside the cushion dies. Once its water has ticked, none is outside the cushion. A mob
     * stands in for the player: water takes a falling player's damage the same way, by resetting the fall in
     * {@code LivingEntity#checkFallDamage}.
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
                        Vec3 at = i < RIM.size() ? RIM.get(i) : FLOOR;
                        landed[i] = pig(instances, lobby.getX() + at.x, lobby.getY() + 3, lobby.getZ() + at.z);
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
                                "the pig that landed on the cushion's rim at " + RIM.get(i)
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

    /**
     * RLM-362: the item combination lock is built with its two item frames, the poppy and the cornflower its
     * comparators read, also turned a quarter; an Adventure player's hits and an explosion leave both frames and their
     * flowers in place, so the lock stays solvable; its chest opens; and clearing the plot removes the frames again.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aRoomsItemFramesComeWithIt(GameTestHelper helper) {
        InstanceLayout layout = new InstanceLayout(
                List.of(new RoomPlacement(ITEM_LOCK, BlockPos.ZERO, Rotation.CLOCKWISE_90)), Optional.empty());
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        Plot plot = manager.open(layout, List.of());
        ServerPlayer player = QaPlayers.login(helper.getLevel().getServer(),
                new GameProfile(UUID.randomUUID(), "qa-item-lock"));
        AABB[] room = new AABB[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    room[0] = AABB.of(plot.rooms().getFirst());
                    List<ItemFrame> frames = instances.getEntitiesOfClass(ItemFrame.class, room[0]);
                    helper.assertValueEqual(frames.size(), 2, "item frames in the item lock");
                    helper.assertValueEqual(frames.stream().map(frame -> frame.getItem().getItem()).collect(
                            Collectors.toSet()), Set.of(Items.POPPY, Items.CORNFLOWER), "the framed flowers");
                    player.setGameMode(GameType.ADVENTURE);
                    for (ItemFrame frame : frames) {
                        player.attack(frame);
                        player.attack(frame);
                        instances.explode(null, frame.getX(), frame.getY(), frame.getZ(), 2.0F,
                                Level.ExplosionInteraction.TNT);
                    }
                    List<ItemFrame> kept = instances.getEntitiesOfClass(ItemFrame.class, room[0]);
                    helper.assertValueEqual(kept.size(), 2, "item frames after hits and explosions");
                    helper.assertValueEqual(kept.stream().map(frame -> frame.getItem().getItem()).collect(
                            Collectors.toSet()), Set.of(Items.POPPY, Items.CORNFLOWER),
                            "the framed flowers after hits and explosions");
                    BlockPos chest = plot.origin().offset(
                            StructureTemplate.transform(ITEM_LOCK_CHEST, Mirror.NONE, Rotation.CLOCKWISE_90,
                                    BlockPos.ZERO));
                    helper.assertValueEqual(instances.getBlockState(chest).getBlock(), Blocks.CHEST, "the chest");
                    player.teleportTo(instances, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 1.5, Set.of(),
                            0.0F, 0.0F, true);
                    player.gameMode.useItemOn(player, instances, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
                    helper.assertTrue(player.containerMenu instanceof ChestMenu, "the chest does not open");
                    player.closeContainer();
                    helper.getLevel().getServer().getPlayerList().remove(player);
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> helper.assertTrue(instances.getEntitiesOfClass(ItemFrame.class, room[0]).isEmpty(),
                        "clearing left the item frames"))
                .thenSucceed();
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
