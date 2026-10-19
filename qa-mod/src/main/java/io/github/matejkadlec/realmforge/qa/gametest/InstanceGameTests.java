package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.dungeon.DungeonAttachments;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.DataMarker;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import io.github.matejkadlec.realmforge.generated.GeneratedBlocks;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.instance.InstanceBench;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spike 3 and W4b: instanced dungeons in plots of {@code realmforge:instances}. A plot is built from the spike rooms,
 * entered at its entrance marker, completed, left and cleared with the margin around its rooms; a player who
 * reconnects stays in a live run and is sent back from a gone one; a pending member can leave; a build whose template
 * is gone is released; and plots built at the same time share the builder's time.
 *
 * <p>The GameTest world has the dimension only through {@code GameTestServerMixin}. Coordinates in the test
 * structure are relative to the template's corner: the stone floor is at y = 0.
 */
public final class InstanceGameTests {
    /** Building and clearing take a few dozen ticks; this leaves room for a slow CI machine. */
    private static final int TIMEOUT = 600;

    private InstanceGameTests() {
    }

    @QaGameTest
    public static void instanceDimensionIsAFixedTimeVoid(GameTestHelper helper) {
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        helper.assertTrue(instances != null, "the GameTest world has no realmforge:instances dimension");
        helper.assertTrue(instances.dimensionType().hasFixedTime(), "instances should have a fixed time");
        helper.assertFalse(instances.dimensionType().hasSkyLight(), "instances should have no sky light");
        helper.assertTrue(instances.getChunkSource().getGenerator() instanceof FlatLevelSource flat
                        && flat.settings().getLayersInfo().isEmpty(),
                "instances should be generated as an empty flat world (a void)");
        helper.succeed();
    }

    /**
     * Done when of spike 3 and W4b: a plot is built and entered at its entrance marker, looking along the row, with its
     * markers recorded and none placed (the dungeon exit lies at the entrance and exit markers, W4d); its run completes
     * and is left; and it is cleared, a block beside a room included.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void instanceIsBuiltEnteredLeftAndCleared(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-instance"));
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);

        InstanceLayout layout = SpikeLayouts.spike();
        Plot plot = manager.open(layout, List.of(player.getUUID()));
        BlockPos origin = plot.origin();
        BlockPos chest = origin.offset(SpikeLayouts.FIRST_CHEST);
        // Two blocks west of the start room's wall: outside every room, inside the margin cleanup reaches.
        BlockPos besideTheRoom = origin.offset(-2, 1, 7);
        helper.assertValueEqual(plot.markers(), List.of(
                        new DataMarker(DataMarker.Kind.ENTRANCE, 0, SpikeLayouts.ENTRANCE),
                        new DataMarker(DataMarker.Kind.MOB, 1,
                                layout.rooms().get(1).offset().offset(SpikeLayouts.CORRIDOR_MOB)),
                        new DataMarker(DataMarker.Kind.EXIT, 2,
                                layout.rooms().get(2).offset().offset(SpikeLayouts.HALL_EXIT))),
                "the rooms' markers, read from their templates when the plot opened");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(plot.chunks().containsAll(plot.wantedChunks()), "a built plot holds its chunks");
                    for (long chunk : plot.chunks()) {
                        helper.assertTrue(instances.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)),
                                "a held chunk is loaded: " + ChunkPos.unpack(chunk));
                    }
                    helper.assertTrue(player.level() == instances, "the party member entered when the plot was built");
                    Vec3 spawn = Vec3.atBottomCenterOf(origin.offset(SpikeLayouts.ENTRANCE));
                    helper.assertTrue(player.position().closerThan(spawn, 0.01),
                            "the player stands at the entrance marker, not at " + player.position());
                    helper.assertTrue(Math.abs(player.getYRot() + 90.0F) < 0.01F,
                            "the player looks east along the row, not at yaw " + player.getYRot());
                    helper.assertTrue(instances.getBlockState(origin.offset(SpikeLayouts.MARKER))
                            .is(Blocks.CHISELED_STONE_BRICKS), "the start room was placed");
                    for (DataMarker marker : plot.markers()) {
                        // W4d lays the dungeon exit where players arrive (the entrance marker) and at exit markers.
                        BlockState placed = instances.getBlockState(origin.offset(marker.pos()));
                        boolean exit = marker.kind() != DataMarker.Kind.MOB;
                        helper.assertTrue(exit ? placed.is(GeneratedBlocks.DUNGEON_EXIT.get()) : placed.isAir(),
                                "the " + marker.kind().getSerializedName() + " marker was not placed: " + placed);
                    }
                    helper.assertTrue(instances.getBlockEntity(chest) instanceof ChestBlockEntity box
                            && box.getItem(0).is(Items.BREAD), "the hall's chest was placed with its bread");
                    helper.assertTrue(player.getExistingData(DungeonAttachments.VISIT).isPresent(),
                            "the player remembers the run");
                    instances.setBlockAndUpdate(besideTheRoom, Blocks.STONE.defaultBlockState());

                    helper.assertTrue(manager.complete(plot), "an active run can be completed");
                    helper.assertTrue(plot.state() == PlotState.COMPLETED && plot.state().isPlayable(),
                            "a completed run stays playable");
                    helper.assertFalse(manager.complete(plot), "a run completes once");

                    helper.assertTrue(manager.leave(player), "the player can leave");
                    helper.assertTrue(player.level() == helper.getLevel(), "the player is back in the overworld");
                    helper.assertTrue(player.position().closerThan(start, 0.01), "the player is back where they were");
                    helper.assertFalse(player.hasData(DungeonAttachments.VISIT), "the visit is gone");
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "the last member left, so the plot closes");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(instances.getBlockState(origin.offset(SpikeLayouts.MARKER)).isAir(),
                            "the start room was cleared");
                    helper.assertTrue(instances.getBlockState(chest).isAir(), "the chest was cleared");
                    helper.assertTrue(instances.getBlockState(besideTheRoom).isAir(),
                            "cleanup reached the block beside the room");
                    AABB aroundChest = new AABB(chest).inflate(8);
                    helper.assertTrue(instances.getEntitiesOfClass(ItemEntity.class, aroundChest).isEmpty(),
                            "clearing the chest dropped nothing");
                    helper.assertTrue(plot.chunks().isEmpty(), "a free plot holds no chunks");
                    server.getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /** A turned row's markers turn with their rooms, as the builder turns the rooms' blocks. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void markersTurnWithTheirRooms(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        InstanceLayout straight = SpikeLayouts.row(3, false);
        Plot plot = manager.open(SpikeLayouts.row(3, true), List.of());
        helper.assertValueEqual(plot.markers(), List.of(
                        new DataMarker(DataMarker.Kind.ENTRANCE, 0, SpikeLayouts.turn(SpikeLayouts.ENTRANCE, true)),
                        new DataMarker(DataMarker.Kind.MOB, 1, SpikeLayouts.turn(
                                straight.rooms().get(1).offset().offset(SpikeLayouts.CORRIDOR_MOB), true)),
                        new DataMarker(DataMarker.Kind.EXIT, 2, SpikeLayouts.turn(
                                straight.rooms().get(2).offset().offset(SpikeLayouts.HALL_EXIT), true))),
                "the turned row's markers");
        manager.release(plot);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /** Done when of W4b: a party member can leave a run that is still being built, and is never moved in. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aPendingMemberLeavesBeforeEntry(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-pending-leave"));
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.assertTrue(plot.state() == PlotState.BUILDING, "the plot is still being built");
        helper.assertTrue(manager.leave(player), "a member of a run still being built can leave it");
        helper.assertTrue(manager.plotOf(player).isEmpty(), "the player belongs to no run");
        helper.assertTrue(plot.state() == PlotState.CLOSING, "its last member left, so the run is released");
        helper.assertFalse(manager.leave(player), "a player in no run cannot leave one");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(player.level() == helper.getLevel(), "the player was never moved in");
                    helper.assertTrue(player.position().closerThan(start, 0.01), "the player stayed where they were");
                    server.getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /**
     * Done when of W4b: a build whose room template no longer resolves, as after a restart in which a room was renamed
     * or removed, releases its plot instead of failing every tick; the rooms it placed are cleared.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aBuildWhoseTemplateIsGoneIsReleased(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        StructureTemplateManager templates = helper.getLevel().getStructureManager();
        Identifier gone = RealmforgeQa.id("gametest/gone");
        templates.getOrCreate(gone).fillFromWorld(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                new Vec3i(3, 2, 3), false, List.of());
        InstanceLayout layout = new InstanceLayout(List.of(
                new RoomPlacement(SpikeLayouts.START, BlockPos.ZERO, Rotation.NONE),
                new RoomPlacement(gone, new BlockPos(15, 0, 6), Rotation.NONE)), Optional.empty());
        Plot plot = manager.open(layout, List.of());
        BlockPos startRoom = plot.origin().offset(SpikeLayouts.MARKER);
        templates.remove(gone);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(templates.get(gone).isEmpty(), "the second room's template is gone");
                    helper.assertTrue(instances.getBlockState(startRoom).isAir(), "the room it placed was cleared");
                })
                .thenSucceed();
    }

    /**
     * Done when of W4b: plots opened in the same tick both make progress in every tick once their chunks are held:
     * the builder's time goes round the plots, and each plot being built runs a unit every tick.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void plotsOpenedInTheSameTickBothProgressEveryTick(GameTestHelper helper) {
        InstanceManager manager = manager(helper);
        List<Plot> plots = List.of(manager.open(SpikeLayouts.row(SpikeLayouts.MAX_ROOMS, false), List.of()),
                manager.open(SpikeLayouts.row(SpikeLayouts.MAX_ROOMS, true), List.of()));
        int[] lastStep = {-1, -1};
        int[] checkedTicks = {0, 0};
        helper.onEachTick(() -> {
            for (int i = 0; i < plots.size(); i++) {
                Plot plot = plots.get(i);
                boolean building = plot.state() == PlotState.BUILDING
                        && plot.chunks().containsAll(plot.wantedChunks());
                if (building && lastStep[i] >= 0) {
                    helper.assertTrue(plot.step() > lastStep[i],
                            "plot " + plot.id() + " made no progress this tick (step " + plot.step() + ")");
                    checkedTicks[i]++;
                }
                lastStep[i] = building ? plot.step() : -1;
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> plots.forEach(plot -> helper.assertTrue(plot.state() == PlotState.READY,
                        "plot " + plot.id() + " is " + plot.state())))
                .thenExecute(() -> {
                    helper.assertTrue(checkedTicks[0] > 1 && checkedTicks[1] > 1,
                            "both plots were built over several ticks: " + checkedTicks[0] + ", " + checkedTicks[1]);
                    plots.forEach(manager::release);
                })
                .thenWaitUntil(() -> plots.forEach(plot -> helper.assertTrue(plot.state() == PlotState.FREE,
                        "plot " + plot.id() + " is " + plot.state())))
                .thenSucceed();
    }

    /** A player who disconnects inside a run is still inside on reconnect, and is sent back once the run is gone. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void reconnectKeepsALiveRunAndLeavesAGoneOne(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-reconnect");
        ServerPlayer first = QaPlayers.login(server, profile);
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        first.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(profile.id()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    server.getPlayerList().remove(first);
                    ServerPlayer second = QaPlayers.login(server, profile);
                    helper.assertTrue(second.level() == instances, "a reconnecting player stays in a live run");
                    helper.assertTrue(plot.state() == PlotState.ACTIVE && plot.party().contains(profile.id()),
                            "the run kept its member");

                    server.getPlayerList().remove(second);
                    manager.release(plot);
                    ServerPlayer third = QaPlayers.login(server, profile);
                    helper.assertTrue(third.level() == helper.getLevel(), "a player whose run is gone is sent back");
                    helper.assertTrue(third.position().closerThan(start, 0.01), "...to where they entered from");
                    helper.assertFalse(third.hasData(DungeonAttachments.VISIT), "the stale visit is gone");
                    server.getPlayerList().remove(third);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * A player who disconnects while their plot is still being built never enters it; when they reconnect, the run
     * they never entered is released instead of waiting for them with its chunks held.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void reconnectBeforeEnteringReleasesTheRun(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-early-leave");
        ServerPlayer first = QaPlayers.loginOnPlatform(helper, profile);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(profile.id()));
        server.getPlayerList().remove(first);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(plot.party().contains(profile.id()),
                            "the built run still waits for its member");
                    ServerPlayer again = QaPlayers.login(server, profile);
                    helper.assertTrue(again.level() != InstanceDimension.level(server), "the player never entered");
                    helper.assertTrue(manager.plotOf(again).isEmpty(), "the run the player never entered is gone");
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "the empty run is released");
                    server.getPlayerList().remove(again);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * W4b: a reload asks for the room templates from inside its server task and empties the structure manager's cache
     * right after ({@code MinecraftServer#reloadResources}), so the templates must load once that task has finished,
     * not at once, when the emptied cache would throw them away. Runs what the reload runs in a server task of its
     * own, without reloading the server under the other tests.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void roomTemplatesLoadAgainAfterAReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureTemplateManager templates = server.getStructureManager();
        Map<Identifier, ?> cache = templateCache(templates);
        DungeonType type = server.registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(Identifier.parse("realmforge:test_dungeon"));
        helper.assertTrue(type != null, "realmforge:test_dungeon is not loaded");
        List<Identifier> rooms = type.rooms().stream().map(DungeonRoom::template).distinct().toList();
        // {ran, loaded inside the task}
        boolean[] task = {false, false};
        server.schedule(server.wrapRunnable(() -> {
            templates.onResourceManagerReload(server.getResourceManager());
            InstanceManager.preloadAfterReload(server);
            task[1] = rooms.stream().anyMatch(cache::containsKey);
            task[0] = true;
        }));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(task[0], "the reload's task has not run yet"))
                .thenExecute(() -> helper.assertFalse(task[1],
                        "the templates loaded inside the reload's task, where its emptied cache would lose them"))
                .thenWaitUntil(() -> helper.assertTrue(rooms.stream().allMatch(cache::containsKey),
                        "the room templates were loaded again after the task"))
                .thenSucceed();
    }

    /** The bench's median takes the middle sample, or the mean of the two middle ones for an even count. */
    @QaGameTest
    public static void benchMedianAveragesTheTwoMiddleSamples(GameTestHelper helper) {
        helper.assertValueEqual(InstanceBench.median(new long[] {1, 5, 100}), 5.0, "odd count");
        helper.assertValueEqual(InstanceBench.median(new long[] {1, 100}), 50.5, "even count");
        helper.assertValueEqual(InstanceBench.median(new long[] {1, 2, 4, 100}), 3.0, "even count");
        helper.succeed();
    }

    /** The bench samples no tick that started it: a console command's save and disk walk would count as idle time. */
    @QaGameTest
    public static void benchSkipsTheTickThatStartedIt(GameTestHelper helper) {
        helper.assertFalse(InstanceBench.sampled(41, 41), "the tick that ran the command");
        helper.assertTrue(InstanceBench.sampled(42, 41), "the next tick");
        helper.succeed();
    }

    /** The structure manager's cache of loaded templates (Minecraft ships unobfuscated, so the name is stable). */
    @SuppressWarnings("unchecked")
    private static Map<Identifier, ?> templateCache(StructureTemplateManager templates) {
        try {
            Field field = StructureTemplateManager.class.getDeclaredField("structureRepository");
            field.setAccessible(true);
            return (Map<Identifier, ?>) field.get(templates);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the structure manager's template cache", e);
        }
    }

    private static InstanceManager manager(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        return manager;
    }
}
