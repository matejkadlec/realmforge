package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.dungeon.DungeonAttachments;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Spike 3: instanced dungeons in plots of {@code realmforge:instances}. A plot is built from the spike rooms,
 * entered, left and cleared; a player who reconnects stays in a live run and is sent back from a gone one.
 *
 * <p>The GameTest world has the dimension only through {@code GameTestServerMixin}. Coordinates in the test
 * structure are relative to the structure block: the stone floor is at y = 1.
 */
@GameTestHolder(RealmforgeQa.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InstanceGameTests {
    private static final String PLATFORM = "platform_5x5";
    /** Building and clearing take a few dozen ticks; this leaves room for a slow CI machine. */
    private static final int TIMEOUT = 600;

    private InstanceGameTests() {
    }

    @GameTest(template = PLATFORM)
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

    /** Done when of spike 3: a GameTest builds, enters and frees an instance. */
    @GameTest(template = PLATFORM, timeoutTicks = TIMEOUT)
    public static void instanceIsBuiltEnteredLeftAndCleared(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = login(server, new GameProfile(UUID.randomUUID(), "qa-instance"));
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        player.teleportTo(helper.getLevel(), start.x, start.y, start.z, 0.0F, 0.0F);

        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));
        BlockPos origin = plot.origin();
        BlockPos chest = origin.offset(SpikeLayouts.FIRST_CHEST);
        helper.assertFalse(plot.chunks().isEmpty(), "an open plot holds its chunks");
        for (long chunk : plot.chunks()) {
            helper.assertTrue(instances.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)),
                    "a held chunk is loaded: " + new ChunkPos(chunk));
        }

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(player.level() == instances, "the party member entered when the plot was built");
                    Vec3 spawn = Vec3.atLowerCornerOf(origin).add(SpikeLayouts.spike().spawn());
                    helper.assertTrue(player.position().closerThan(spawn, 0.01), "the player stands at the spawn");
                    helper.assertTrue(instances.getBlockState(origin.offset(SpikeLayouts.MARKER))
                            .is(Blocks.CHISELED_STONE_BRICKS), "the start room was placed");
                    helper.assertTrue(instances.getBlockEntity(chest) instanceof ChestBlockEntity box
                            && box.getItem(0).is(Items.BREAD), "the hall's chest was placed with its bread");
                    helper.assertTrue(player.getExistingData(DungeonAttachments.VISIT).isPresent(),
                            "the player remembers the run");

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
                    AABB aroundChest = new AABB(chest).inflate(8);
                    helper.assertTrue(instances.getEntitiesOfClass(ItemEntity.class, aroundChest).isEmpty(),
                            "clearing the chest dropped nothing");
                    helper.assertTrue(plot.chunks().isEmpty(), "a free plot holds no chunks");
                    server.getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /** A player who disconnects inside a run is still inside on reconnect, and is sent back once the run is gone. */
    @GameTest(template = PLATFORM, timeoutTicks = TIMEOUT)
    public static void reconnectKeepsALiveRunAndLeavesAGoneOne(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-reconnect");
        ServerPlayer first = login(server, profile);
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        first.teleportTo(helper.getLevel(), start.x, start.y, start.z, 0.0F, 0.0F);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(profile.getId()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    server.getPlayerList().remove(first);
                    ServerPlayer second = login(server, profile);
                    helper.assertTrue(second.level() == instances, "a reconnecting player stays in a live run");
                    helper.assertTrue(plot.state() == PlotState.ACTIVE && plot.party().contains(profile.getId()),
                            "the run kept its member");

                    server.getPlayerList().remove(second);
                    manager.release(plot);
                    ServerPlayer third = login(server, profile);
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
    @GameTest(template = PLATFORM, timeoutTicks = TIMEOUT)
    public static void reconnectBeforeEnteringReleasesTheRun(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-early-leave");
        ServerPlayer first = login(server, profile);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(profile.getId()));
        server.getPlayerList().remove(first);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(plot.party().contains(profile.getId()),
                            "the built run still waits for its member");
                    ServerPlayer again = login(server, profile);
                    helper.assertTrue(again.level() != InstanceDimension.level(server), "the player never entered");
                    helper.assertTrue(manager.plotOf(again).isEmpty(), "the run the player never entered is gone");
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "the empty run is released");
                    server.getPlayerList().remove(again);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    private static InstanceManager manager(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        return manager;
    }

    /**
     * Logs a fake player in through the normal login path, so saved data (position, dimension, attachments) is
     * loaded and the login event fires, as for a real reconnect. Like
     * {@code GameTestHelper#makeMockServerPlayerInLevel}, but with a chosen profile.
     */
    private static ServerPlayer login(MinecraftServer server, GameProfile profile) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
