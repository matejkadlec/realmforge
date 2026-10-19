package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.dungeon.DungeonAttachments;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.instance.PortalLink;
import io.github.matejkadlec.realmforge.dungeon.instance.PortalTimer;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.dungeon.portal.DungeonPortalBlock;
import io.github.matejkadlec.realmforge.generated.GeneratedBlocks;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * W4d: the dungeon portal (architecture 15.5). A key opens a portal to its dungeon type for its owner alone; they enter
 * through it once the dungeon is built, and the exit, {@code /instance leave} or the portal's timer brings them back
 * to it; completing the dungeon leaves 5 minutes; a key whose dungeon cannot be built comes back; and no portal opens
 * inside a dungeon.
 *
 * <p>The portal stands on the test's platform, two blocks south of the middle where the players stand. The dungeons
 * are {@code realmforge:test_dungeon}, built for real, so a test waits for its build.
 */
public final class DungeonPortalGameTests {
    /** A test dungeon's build and clearing take a few hundred ticks; this leaves room for a slow CI machine. */
    private static final int TIMEOUT = 1200;
    private static final BlockPos PORTAL = new BlockPos(2, 1, 4);
    private static final Identifier TEST_DUNGEON = Identifier.parse("realmforge:test_dungeon");
    private static final long OPEN_TICKS = 30L * 60L * 20L;

    private DungeonPortalGameTests() {
    }

    /**
     * Done when of W4d: a key opens the portal and is used up; the portal is lit, cannot be broken, and takes no
     * second key; only the player who opened it enters, once the dungeon is built, where the exit lies; the run closes
     * after the type's open time; and the exit brings the player back to the portal, closing it.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aKeyOpensAPortalThatOnlyItsOwnerEnters(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer owner = player(helper, "qa-portal-owner");
        ServerPlayer stranger = player(helper, "qa-portal-stranger");
        Vec3 start = owner.position();
        BlockPos portal = placePortal(helper);

        long openedAt = helper.getLevel().getGameTime();
        use(helper, owner, portal, key(2));
        helper.assertValueEqual(owner.getMainHandItem().getCount(), 1, "keys left after opening the portal");
        helper.assertTrue(isOpen(helper, portal), "the portal is lit");
        Plot plot = runOf(helper, owner);
        helper.assertValueEqual(plot.portal(), Optional.of(new PortalLink(TEST_DUNGEON,
                GlobalPos.of(helper.getLevel().dimension(), portal))), "the run's portal");
        helper.assertValueEqual(plot.closesAt(), OptionalLong.of(openedAt + OPEN_TICKS),
                "the run closes after the type's 30 minutes");

        use(helper, stranger, portal, key(1));
        helper.assertValueEqual(stranger.getMainHandItem().getCount(), 1, "an open portal takes no second key");
        helper.assertTrue(manager.plotOf(stranger).isEmpty(), "the stranger has no run");
        helper.assertFalse(stranger.gameMode.destroyBlock(portal), "an open portal cannot be broken");
        helper.assertTrue(isOpen(helper, portal), "the portal still stands");
        helper.assertTrue(plot.state() == PlotState.BUILDING, "plot is " + plot.state());
        use(helper, owner, portal, ItemStack.EMPTY);
        helper.assertTrue(owner.level() == helper.getLevel(), "nobody enters while the dungeon is still being built");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(owner.level() == helper.getLevel(), "the owner enters through the portal only");
                    use(helper, stranger, portal, ItemStack.EMPTY);
                    helper.assertTrue(stranger.level() == helper.getLevel(), "a stranger cannot enter");

                    use(helper, owner, portal, ItemStack.EMPTY);
                    helper.assertTrue(owner.level() == instances, "the owner entered through the portal");
                    helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state());
                    BlockPos arrival = BlockPos.containing(owner.position());
                    helper.assertTrue(instances.getBlockState(arrival).is(GeneratedBlocks.DUNGEON_EXIT.get()),
                            "the exit lies where players arrive, not " + instances.getBlockState(arrival));

                    use(owner, instances, arrival, ItemStack.EMPTY);
                    helper.assertTrue(owner.level() == helper.getLevel(), "the exit brought the owner back");
                    helper.assertTrue(owner.position().closerThan(start, 0.01), "...to the portal they entered by");
                    helper.assertFalse(owner.hasData(DungeonAttachments.VISIT), "the visit is over");
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "the last player left, so the run closes");
                })
                .thenWaitUntil(() -> helper.assertFalse(isOpen(helper, portal), "the portal closed with its run"))
                .thenExecute(() -> {
                    helper.assertTrue(stranger.gameMode.destroyBlock(portal), "a closed portal can be broken");
                    server.getPlayerList().remove(owner);
                    server.getPlayerList().remove(stranger);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /** Done when of W4d: when the portal's timer runs out, the player inside is sent back and the plot is freed. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void thePortalTimerSendsPlayersBackAndFreesThePlot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerPlayer player = player(helper, "qa-portal-timer");
        Vec3 start = player.position();
        BlockPos portal = placePortal(helper);
        use(helper, player, portal, key(1));
        Plot plot = runOf(helper, player);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    use(helper, player, portal, ItemStack.EMPTY);
                    helper.assertTrue(player.level() == InstanceDimension.level(server), "the player entered");
                    helper.assertTrue(manager.closeIn(plot, 20), "an open run's timer can be set");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.CLOSING
                        || plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertTrue(player.level() == helper.getLevel(), "the timer sent the player back");
                    helper.assertTrue(player.position().closerThan(start, 0.01), "...to the portal");
                    helper.assertFalse(player.hasData(DungeonAttachments.VISIT), "the visit is over");
                    helper.assertTrue(manager.plotOf(player).isEmpty(), "the player has no run");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenWaitUntil(() -> helper.assertFalse(isOpen(helper, portal), "the portal closed with its run"))
                .thenExecute(() -> server.getPlayerList().remove(player))
                .thenSucceed();
    }

    /** Done when of W4d: {@code /instance leave} by the last player inside closes the portal and frees the plot. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void instanceLeaveByTheLastPlayerClosesThePortal(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerPlayer player = player(helper, "qa-portal-leave");
        Vec3 start = player.position();
        BlockPos portal = placePortal(helper);
        use(helper, player, portal, key(1));
        Plot plot = runOf(helper, player);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    use(helper, player, portal, ItemStack.EMPTY);
                    helper.assertTrue(player.level() == InstanceDimension.level(server), "the player entered");
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "instance leave");
                    helper.assertTrue(player.level() == helper.getLevel(), "/instance leave brought the player back");
                    helper.assertTrue(player.position().closerThan(start, 0.01), "...to the portal");
                    helper.assertTrue(plot.state() == PlotState.CLOSING, "the last player left, so the run closes");
                })
                .thenWaitUntil(() -> helper.assertFalse(isOpen(helper, portal), "the portal closed with its run"))
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> server.getPlayerList().remove(player))
                .thenSucceed();
    }

    /**
     * Done when of W4d: completing a run leaves it 5 minutes from then, also when less time was left, and its players
     * stay inside until then.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void completingARunLeavesItFiveMinutes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerPlayer player = player(helper, "qa-portal-complete");
        BlockPos portal = placePortal(helper);
        use(helper, player, portal, key(1));
        Plot plot = runOf(helper, player);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertFalse(manager.complete(plot), "a run nobody entered does not complete");
                    use(helper, player, portal, ItemStack.EMPTY);
                    manager.closeIn(plot, 60 * 20);
                    long now = helper.getLevel().getGameTime();
                    helper.assertTrue(manager.complete(plot), "an active run completes");
                    helper.assertValueEqual(plot.closesAt(), OptionalLong.of(now + PortalTimer.COMPLETED_TICKS),
                            "a run with 1 minute left closes 5 minutes after its completion");
                    helper.assertTrue(plot.state() == PlotState.COMPLETED && plot.state().isPlayable(),
                            "plot is " + plot.state());
                    helper.assertTrue(player.level() == InstanceDimension.level(server), "the player stays inside");
                    helper.assertTrue(isOpen(helper, portal), "the portal stays open");
                    manager.leave(player);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> server.getPlayerList().remove(player))
                .thenSucceed();
    }

    /**
     * W4d: a key whose dungeon cannot be built comes back, here to a player who was offline when the build failed, at
     * their next login.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aKeyComesBackWhenItsDungeonCannotBeBuilt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        StructureTemplateManager templates = helper.getLevel().getStructureManager();
        Identifier gone = RealmforgeQa.id("gametest/portal_gone");
        templates.getOrCreate(gone).fillFromWorld(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                new Vec3i(3, 2, 3), false, List.of());
        InstanceLayout layout = new InstanceLayout(List.of(
                new RoomPlacement(SpikeLayouts.START, BlockPos.ZERO, Rotation.NONE),
                new RoomPlacement(gone, new BlockPos(15, 0, 6), Rotation.NONE)), Optional.empty());
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-portal-refund");
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, profile);
        PortalLink link = new PortalLink(TEST_DUNGEON, GlobalPos.of(helper.getLevel().dimension(),
                helper.absolutePos(PORTAL)));
        Plot plot = manager.open(layout, List.of(profile.id()), link, OPEN_TICKS);
        templates.remove(gone);
        server.getPlayerList().remove(player);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    ServerPlayer again = QaPlayers.login(server, profile);
                    helper.assertValueEqual(again.getInventory().countItem(GeneratedItems.TEST_DUNGEON_KEY.get()), 1,
                            "the key that opened the failed dungeon came back");
                    server.getPlayerList().remove(again);
                    ServerPlayer later = QaPlayers.login(server, profile);
                    helper.assertValueEqual(later.getInventory().countItem(GeneratedItems.TEST_DUNGEON_KEY.get()), 1,
                            "the key comes back once");
                    server.getPlayerList().remove(later);
                })
                .thenSucceed();
    }

    /** W4d: no portal is placed or opened inside a dungeon, not even by a creative player. */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void noPortalOpensInsideADungeon(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = player(helper, "qa-portal-inside");
        ServerPlayer stranger = player(helper, "qa-portal-outside");
        player.setGameMode(GameType.CREATIVE);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    BlockPos above = BlockPos.containing(player.position()).above(2);
                    helper.assertTrue(placement(player, above) == null, "a portal can be placed inside a dungeon");
                    helper.assertTrue(placement(stranger, helper.absolutePos(PORTAL)) != null,
                            "a portal cannot be placed outside one");

                    player.setGameMode(GameType.SURVIVAL);
                    instances.setBlockAndUpdate(above, GeneratedBlocks.DUNGEON_PORTAL.get().defaultBlockState());
                    use(player, instances, above, key(1));
                    helper.assertFalse(instances.getBlockState(above).getValue(DungeonPortalBlock.OPEN),
                            "a portal opened inside a dungeon");
                    helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "the key is kept");
                    helper.assertValueEqual(manager.plotOf(player).map(Plot::id), Optional.of(plot.id()),
                            "the player has only the run they are in");
                    instances.removeBlock(above, false);
                    manager.leave(player);
                    server.getPlayerList().remove(player);
                    server.getPlayerList().remove(stranger);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    // ---- Helpers ----

    /** A new survival player standing in the middle of the platform, facing the portal. */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Plot runOf(GameTestHelper helper, ServerPlayer player) {
        return manager(helper).plotOf(player)
                .orElseThrow(() -> helper.assertionException(Component.literal("the key opened no run")));
    }

    /** The portal block's state when {@code player} places one at {@code pos}, or null when it may not stand there. */
    private static BlockState placement(ServerPlayer player, BlockPos pos) {
        ItemStack stack = new ItemStack(GeneratedBlocks.DUNGEON_PORTAL_ITEM.get());
        return GeneratedBlocks.DUNGEON_PORTAL.get().getStateForPlacement(new BlockPlaceContext(player,
                InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static BlockPos placePortal(GameTestHelper helper) {
        helper.setBlock(PORTAL, GeneratedBlocks.DUNGEON_PORTAL.get());
        return helper.absolutePos(PORTAL);
    }

    private static ItemStack key(int count) {
        return new ItemStack(GeneratedItems.TEST_DUNGEON_KEY.get(), count);
    }

    private static boolean isOpen(GameTestHelper helper, BlockPos portal) {
        BlockState state = helper.getLevel().getBlockState(portal);
        return state.is(GeneratedBlocks.DUNGEON_PORTAL.get()) && state.getValue(DungeonPortalBlock.OPEN);
    }

    /** {@code player} right-clicks the block at {@code pos} of the test's level with {@code stack} in hand. */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack stack) {
        use(player, helper.getLevel(), pos, stack);
    }

    /** {@code player} right-clicks the block at {@code pos} with {@code stack} in their main hand, as a client does. */
    private static void use(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    private static InstanceManager manager(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        return manager;
    }
}
