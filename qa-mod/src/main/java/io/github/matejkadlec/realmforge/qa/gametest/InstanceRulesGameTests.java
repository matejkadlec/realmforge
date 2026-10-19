package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.dungeon.DungeonAttachments;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceVisit;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * W4c: the rules inside an instance (architecture 15.5). Survival players play in Adventure mode and get their game
 * mode back when they leave, creative ones keep theirs; nothing a player or anything else does changes the rooms; a
 * death inside respawns at the entrance, and a run released while its player lay dead respawns them at their return
 * point, also after a logout and login (RLM-258).
 *
 * <p>Plots are built from the spike rooms (SpikeLayouts). The start room's floor is polished andesite with chiseled
 * stone bricks under the entrance marker; tests add the blocks a tool works on (a log, dirt, a water source) with
 * {@code setBlock} before they look at the room.
 */
public final class InstanceRulesGameTests {
    /** Building and clearing take a few dozen ticks; this leaves room for a slow CI machine. */
    private static final int TIMEOUT = 600;
    /** Fixtures in the start room, relative to the plot's origin: a log for the axe, dirt for the hoe. */
    private static final BlockPos LOG = new BlockPos(3, 1, 3);
    private static final BlockPos DIRT = new BlockPos(11, 1, 3);
    /** A water source set into the floor, solid on every side and below, so it cannot flow. */
    private static final BlockPos WATER = new BlockPos(3, 0, 11);
    /** Floor blocks of the start room that tests click or break. */
    private static final BlockPos FLOOR_FIRE = new BlockPos(5, 0, 5);
    private static final BlockPos FLOOR_PLACE = new BlockPos(9, 0, 5);
    private static final BlockPos FLOOR_BREAK = new BlockPos(9, 0, 9);
    private static final BlockPos FLOOR_PUNCH = new BlockPos(10, 0, 10);

    private InstanceRulesGameTests() {
    }

    /**
     * Done when of W4c: a survival player plays a run in Adventure mode, and a teleport out (not {@code leave}) ends
     * the visit, gives the game mode back and releases the run (the spike 3 review finding).
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void survivalPlaysInAdventureAndATeleportOutGivesItBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerPlayer player = login(helper, "qa-rules-teleport", GameType.SURVIVAL);
        Vec3 start = player.position();
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertValueEqual(player.gameMode(), GameType.ADVENTURE, "game mode inside");
                    helper.assertValueEqual(player.getExistingData(DungeonAttachments.VISIT).flatMap(InstanceVisit::gameMode),
                            Optional.of(GameType.SURVIVAL), "the game mode the visit gives back");

                    player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
                    helper.assertFalse(player.hasData(DungeonAttachments.VISIT), "the teleport ended the visit");
                    helper.assertValueEqual(player.gameMode(), GameType.SURVIVAL, "game mode after the teleport out");
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "its only member left, so the run closes");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> server.getPlayerList().remove(player))
                .thenSucceed();
    }

    /** Creative players (operators) keep their game mode inside, and leaving changes nothing about it. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void creativePlayersKeepTheirGameMode(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerPlayer player = login(helper, "qa-rules-creative", GameType.CREATIVE);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertValueEqual(player.gameMode(), GameType.CREATIVE, "game mode inside");
                    helper.assertValueEqual(player.getExistingData(DungeonAttachments.VISIT).flatMap(InstanceVisit::gameMode),
                            Optional.empty(), "nothing to give back");
                    helper.assertTrue(manager.leave(player), "the player can leave");
                    helper.assertValueEqual(player.gameMode(), GameType.CREATIVE, "game mode after leaving");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> server.getPlayerList().remove(player))
                .thenSucceed();
    }

    /**
     * Done when of W4c: buckets (a plain one, and water, lava and empty buckets with {@code can_place_on}), flint and
     * steel, an axe, a hoe, a tool with {@code can_break}, placing and breaking all leave the room unchanged.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void playersLeaveTheRoomUnchanged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = login(helper, "qa-rules-edits", GameType.SURVIVAL);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    BlockPos origin = plot.origin();
                    instances.setBlockAndUpdate(origin.offset(LOG), Blocks.OAK_LOG.defaultBlockState());
                    instances.setBlockAndUpdate(origin.offset(DIRT), Blocks.DIRT.defaultBlockState());
                    instances.setBlockAndUpdate(origin.offset(WATER).below(), Blocks.STONE.defaultBlockState());
                    instances.setBlockAndUpdate(origin.offset(WATER), Blocks.WATER.defaultBlockState());
                    BoundingBox room = plot.rooms().getFirst();
                    Map<BlockPos, BlockState> before = snapshot(instances, room);

                    // Buckets aim where the player looks: straight down at the block below their feet.
                    BlockPos entranceFloor = origin.offset(SpikeLayouts.MARKER);
                    assertBucketRefused(helper, player, entranceFloor, new ItemStack(Items.WATER_BUCKET),
                            "a plain water bucket");
                    assertBucketRefused(helper, player, entranceFloor,
                            adventure(helper, Items.WATER_BUCKET, DataComponents.CAN_PLACE_ON, Blocks.CHISELED_STONE_BRICKS),
                            "a water bucket that may be placed on the floor");
                    assertBucketRefused(helper, player, entranceFloor,
                            adventure(helper, Items.LAVA_BUCKET, DataComponents.CAN_PLACE_ON, Blocks.CHISELED_STONE_BRICKS),
                            "a lava bucket that may be placed on the floor");
                    assertBucketRefused(helper, player, origin.offset(WATER),
                            adventure(helper, Items.BUCKET, DataComponents.CAN_PLACE_ON, Blocks.WATER),
                            "an empty bucket that may be used on the water");

                    useOn(player, new ItemStack(Items.FLINT_AND_STEEL), origin.offset(FLOOR_FIRE));
                    useOn(player, new ItemStack(Items.IRON_AXE), origin.offset(LOG));
                    useOn(player, new ItemStack(Items.IRON_HOE), origin.offset(DIRT));
                    useOn(player, new ItemStack(Items.STONE), origin.offset(FLOOR_PLACE));
                    useOn(player, adventure(helper, Items.STONE, DataComponents.CAN_PLACE_ON, Blocks.POLISHED_ANDESITE),
                            origin.offset(FLOOR_PLACE));

                    player.setItemInHand(InteractionHand.MAIN_HAND,
                            adventure(helper, Items.DIAMOND_PICKAXE, DataComponents.CAN_BREAK, Blocks.POLISHED_ANDESITE));
                    helper.assertFalse(player.gameMode.destroyBlock(origin.offset(FLOOR_BREAK)),
                            "a pickaxe that may break the floor broke it");
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.assertFalse(player.gameMode.destroyBlock(origin.offset(FLOOR_PUNCH)),
                            "an empty hand broke the floor");

                    assertUnchanged(helper, instances, before, room);
                    helper.assertTrue(manager.leave(player), "the player can leave");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> server.getPlayerList().remove(player))
                .thenSucceed();
    }

    /**
     * Edits by anything but a player are stopped inside the instance dimension and nowhere else: an explosion with
     * fire, a fluid making a block, a mob placing a block or griefing, and trampled farmland.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void nothingElseEditsTheRooms(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        ServerLevel overworld = helper.getLevel();
        ServerLevel instances = InstanceDimension.level(overworld.getServer());
        Plot plot = manager.open(SpikeLayouts.spike(), List.of());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    BoundingBox hall = plot.rooms().get(2);
                    Map<BlockPos, BlockState> before = snapshot(instances, hall);
                    BlockPos middle = hall.getCenter().atY(hall.minY() + 1);
                    instances.explode(null, middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5, 4.0F, true,
                            Level.ExplosionInteraction.TNT);
                    assertUnchanged(helper, instances, before, hall);

                    BlockPos floor = plot.origin().offset(FLOOR_PLACE);
                    BlockPos here = helper.absolutePos(new BlockPos(2, 0, 2));
                    helper.assertValueEqual(EventHooks.fireFluidPlaceBlockEvent(instances, floor, floor,
                            Blocks.STONE.defaultBlockState()), instances.getBlockState(floor), "a fluid's block inside");
                    helper.assertValueEqual(EventHooks.fireFluidPlaceBlockEvent(overworld, here, here,
                            Blocks.STONE.defaultBlockState()), Blocks.STONE.defaultBlockState(), "a fluid's block outside");

                    Zombie inside = spawnZombie(instances, Vec3.atBottomCenterOf(floor.above()));
                    Zombie outside = spawnZombie(overworld, Vec3.atBottomCenterOf(here.above()));
                    helper.assertFalse(EventHooks.canEntityGrief(instances, inside), "a mob griefs inside");
                    helper.assertValueEqual(EventHooks.canEntityGrief(overworld, outside),
                            overworld.getGameRules().get(GameRules.MOB_GRIEFING), "a mob's griefing outside");
                    helper.assertTrue(EventHooks.onBlockPlace(inside, BlockSnapshot.create(instances.dimension(),
                            instances, floor.above()), Direction.UP), "a mob placed a block inside");
                    helper.assertFalse(EventHooks.onBlockPlace(outside, BlockSnapshot.create(overworld.dimension(),
                            overworld, here.above()), Direction.UP), "a mob may place a block outside");
                    inside.discard();
                    outside.discard();

                    BlockState farmland = Blocks.FARMLAND.defaultBlockState();
                    ServerPlayer jumper = login(helper, "qa-rules-trample", GameType.SURVIVAL);
                    helper.assertFalse(CommonHooks.onFarmlandTrample(instances, floor, farmland, 10.0, jumper),
                            "farmland was trampled inside");
                    helper.assertTrue(CommonHooks.onFarmlandTrample(overworld, here, farmland, 10.0, jumper),
                            "farmland outside can be trampled");
                    overworld.getServer().getPlayerList().remove(jumper);
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /** Done when of W4c: a death inside respawns at the entrance, still inside, in the same run and game mode. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aDeathInsideRespawnsAtTheEntrance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = login(helper, "qa-rules-respawn", GameType.SURVIVAL);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    BlockPos hall = plot.rooms().get(2).getCenter();
                    player.teleportTo(instances, hall.getX() + 0.5, plot.origin().getY() + 1, hall.getZ() + 0.5,
                            Set.of(), 0.0F, 0.0F, true);
                    ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                    helper.assertTrue(respawned.level() == instances, "the player respawned outside the instance");
                    Vec3 entrance = Vec3.atBottomCenterOf(plot.origin().offset(SpikeLayouts.ENTRANCE));
                    helper.assertTrue(respawned.position().closerThan(entrance, 0.01),
                            "the player respawned at " + respawned.position() + ", not at the entrance");
                    helper.assertTrue(Math.abs(respawned.getYRot() + 90.0F) < 0.01F,
                            "the player looks east along the row, not at yaw " + respawned.getYRot());
                    helper.assertValueEqual(respawned.gameMode(), GameType.ADVENTURE, "game mode after the respawn");
                    helper.assertValueEqual(respawned.getExistingData(DungeonAttachments.VISIT).map(InstanceVisit::plot),
                            Optional.of(plot.id()), "the respawned player's run");
                    helper.assertTrue(plot.state() == PlotState.ACTIVE && plot.party().contains(respawned.getUUID()),
                            "the run kept its member");
                    helper.assertTrue(manager.leave(respawned), "the player can leave");
                    helper.assertValueEqual(respawned.gameMode(), GameType.SURVIVAL, "game mode after leaving");
                    server.getPlayerList().remove(respawned);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * Leaving by respawn (the spike 3 review finding): a run released while its player lies dead leaves them where
     * they are, and their respawn takes them to their return point, ends the visit and gives the game mode back.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aRunReleasedWhileDeadRespawnsAtTheReturnPoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = login(helper, "qa-rules-dead", GameType.SURVIVAL);
        Vec3 start = player.position();
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    // A fake player's client never reports loading the level or arriving in it, either of which
                    // keeps it invulnerable.
                    player.connection.markClientLoaded();
                    player.hasChangedDimension();
                    player.hurtServer(instances, player.damageSources().genericKill(), Float.MAX_VALUE);
                    helper.assertTrue(player.isDeadOrDying(), "the player survived the kill");
                    manager.release(plot);
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "the run was released");
                    helper.assertTrue(player.level() == instances, "a dead player stays where they lie");

                    ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                    helper.assertTrue(respawned.level() == helper.getLevel(), "the player respawned in the overworld");
                    helper.assertTrue(respawned.position().closerThan(start, 0.01),
                            "the player respawned at " + respawned.position() + ", not where they entered from");
                    helper.assertFalse(respawned.hasData(DungeonAttachments.VISIT), "the respawn ended the visit");
                    helper.assertValueEqual(respawned.gameMode(), GameType.SURVIVAL, "game mode after the respawn");
                    server.getPlayerList().remove(respawned);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * Done when of RLM-258: a player who lies dead when their run is released and logs out before they respawn stays
     * dead where they lie at their next login, and their respawn takes them to their return point, ends the visit and
     * gives their game mode back.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aDeadPlayerWhoseRunWasReleasedRespawnsAtTheReturnPointAfterALogin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = login(helper, "qa-rules-dead-login", GameType.SURVIVAL);
        GameProfile profile = player.getGameProfile();
        Vec3 start = player.position();
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    player.connection.markClientLoaded();
                    player.hasChangedDimension();
                    player.hurtServer(instances, player.damageSources().genericKill(), Float.MAX_VALUE);
                    helper.assertTrue(player.isDeadOrDying(), "the player survived the kill");
                    manager.release(plot);
                    server.getPlayerList().remove(player);

                    ServerPlayer again = QaPlayers.login(server, profile);
                    helper.assertTrue(again.isDeadOrDying(), "the player logs in dead");
                    helper.assertTrue(again.level() == instances, "the login left the dead player where they lie");
                    helper.assertTrue(again.hasData(DungeonAttachments.VISIT), "the visit waits for the respawn");

                    ServerPlayer respawned = server.getPlayerList().respawn(again, false, Entity.RemovalReason.KILLED);
                    helper.assertTrue(respawned.level() == helper.getLevel(), "the player respawned in the overworld");
                    helper.assertTrue(respawned.position().closerThan(start, 0.01),
                            "the player respawned at " + respawned.position() + ", not where they entered from");
                    helper.assertFalse(respawned.hasData(DungeonAttachments.VISIT), "the respawn ended the visit");
                    helper.assertValueEqual(respawned.gameMode(), GameType.SURVIVAL, "game mode after the respawn");
                    server.getPlayerList().remove(respawned);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    // ---- Helpers ----

    /** A fake player in {@code mode}, standing on the test's platform. */
    private static ServerPlayer login(GameTestHelper helper, String name, GameType mode) {
        ServerPlayer player = QaPlayers.login(helper.getLevel().getServer(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(mode);
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
        return player;
    }

    /** {@code item} whose adventure mode predicate {@code component} matches {@code blocks}. */
    private static ItemStack adventure(GameTestHelper helper, Item item,
            DataComponentType<AdventureModePredicate> component, Block... blocks) {
        ItemStack stack = new ItemStack(item);
        BlockPredicate predicate = BlockPredicate.Builder.block()
                .of(helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), Arrays.asList(blocks))
                .build();
        stack.set(component, new AdventureModePredicate(List.of(predicate)));
        return stack;
    }

    /** Stands the player over {@code target}, looking straight down at it, and uses the bucket; nothing may happen. */
    private static void assertBucketRefused(GameTestHelper helper, ServerPlayer player, BlockPos target,
            ItemStack bucket, String what) {
        Vec3 above = Vec3.atBottomCenterOf(target.above());
        player.teleportTo(player.level(), above.x, above.y, above.z, Set.of(), 0.0F, 90.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Item before = bucket.getItem();
        player.gameMode.useItem(player, player.level(), bucket, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(before), what + " was emptied or filled");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Uses {@code stack} on the top face of {@code target}, as a right click on it does. */
    private static void useOn(ServerPlayer player, ItemStack stack, BlockPos target) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target).add(0.0, 0.5, 0.0), Direction.UP, target, false);
        player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND, hit);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static Zombie spawnZombie(ServerLevel level, Vec3 pos) {
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BoundingBox box) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos.betweenClosedStream(box).forEach(pos -> blocks.put(pos.immutable(), level.getBlockState(pos)));
        return blocks;
    }

    private static void assertUnchanged(GameTestHelper helper, ServerLevel level, Map<BlockPos, BlockState> before,
            BoundingBox box) {
        BlockPos.betweenClosedStream(box).forEach(pos -> {
            BlockState now = level.getBlockState(pos);
            helper.assertTrue(now == before.get(pos), "the room changed at " + pos.toShortString() + ": "
                    + before.get(pos) + " became " + now);
        });
    }

    private static InstanceManager manager(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        return manager;
    }
}
