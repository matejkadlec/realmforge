package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * RLM-559: Apotheosis's area mining (Radial, Super-Massive) breaks no block a player could not break by hand. Each
 * extra block goes through {@code ServerPlayerGameMode#destroyBlock}, so an instance's protection
 * ({@code dungeon/instance/InstanceRules}, Adventure mode) refuses it, and {@code world/BlockBreakGuard} adds the spawn
 * protection and world border check vanilla makes only for the first block. A Mythic pickaxe with Radial at its lowest
 * roll breaks 3 by 3, through Apotheosis's own commands as a player would get it.
 */
public final class AreaMiningGameTests {
    private static final int TIMEOUT = 600;
    /** The wall's middle in the test platform: x 1 to 3, y 1 to 3, z 4. */
    private static final BlockPos WALL_MIDDLE = new BlockPos(2, 2, 4);
    /** In an instance plot, where the spike layout's room has air (beside {@code InstanceRulesGameTests}' blocks). */
    private static final BlockPos PLOT_WALL_MIDDLE = new BlockPos(6, 2, 8);

    private AreaMiningGameTests() {
    }

    /** The control: outside an instance, a Radial pickaxe breaks the 3 by 3 around the stone a player breaks. */
    @QaGameTest
    public static void aRadialPickaxeBreaksThreeByThree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-radial-control");
        BlockPos middle = helper.absolutePos(WALL_MIDDLE);
        wall(level, middle, Blocks.STONE, Blocks.STONE);
        radialPickaxe(player);
        stand(player, level, middle);

        helper.assertTrue(player.gameMode.destroyBlock(middle), "the player broke the wall's middle");
        for (BlockPos pos : around(middle)) {
            helper.assertTrue(level.getBlockState(pos).isAir(), "Radial broke " + pos);
        }
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-559: inside an instance, the player breaks a cobweb (on the allowlist) with a Radial pickaxe,
     * and none of the room's blocks around it breaks.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aRadialPickaxeBreaksNoProtectedBlockOfAnInstance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = InstanceManager.get(server);
        ServerLevel instances = InstanceDimension.level(server);
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-radial-instance"));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
        radialPickaxe(player);
        Plot plot = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.ACTIVE, "plot is " + plot.state()))
                .thenExecute(() -> {
                    helper.assertValueEqual(player.gameMode(), GameType.ADVENTURE, "game mode inside");
                    BlockPos middle = plot.origin().offset(PLOT_WALL_MIDDLE);
                    wall(instances, middle, Blocks.STONE_BRICKS, Blocks.COBWEB);
                    stand(player, instances, middle);

                    helper.assertTrue(player.gameMode.destroyBlock(middle), "the player broke the cobweb");
                    for (BlockPos pos : around(middle)) {
                        helper.assertTrue(instances.getBlockState(pos).is(Blocks.STONE_BRICKS),
                                "the room's block at " + pos + " stayed");
                    }
                    helper.assertTrue(manager.leave(player), "the player can leave");
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenExecute(() -> server.getPlayerList().remove(player))
                .thenSucceed();
    }

    /**
     * RLM-559: a block a player may not touch there (here, past the world border; spawn protection the same way) is not
     * broken, whoever's code breaks it: the guard cancels the break event, which an area tool's extra blocks post too.
     */
    @QaGameTest
    public static void noBlockBreaksPastTheWorldBorder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-break-border");
        BlockPos outside = new BlockPos(level.getWorldBorder().getAbsoluteMaxSize(), 64, 0);
        helper.assertFalse(level.mayInteract(player, outside), "the position is past the world border");
        BreakBlockEvent past = NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, outside,
                Blocks.STONE.defaultBlockState(), player));
        helper.assertTrue(past.isCanceled(), "a break past the world border is cancelled");
        BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 2));
        BreakBlockEvent within = NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, inside,
                Blocks.STONE.defaultBlockState(), player));
        helper.assertFalse(within.isCanceled(), "a break inside the border goes ahead");
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /** A Mythic diamond pickaxe with Radial at its lowest roll (3 by 3) in the player's main hand. */
    private static void radialPickaxe(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        ApotheosisGameTests.run(player, "apoth set_rarity apotheosis:mythic");
        ApotheosisGameTests.run(player, "apoth affix apply apotheosis:breaker/effect/radial 0.0");
    }

    /** A 3 by 3 wall facing north, of {@code block} around {@code middle}, with the air in front of it cleared. */
    private static void wall(ServerLevel level, BlockPos middle, Block block, Block centre) {
        for (BlockPos pos : around(middle)) {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
        level.setBlockAndUpdate(middle, centre.defaultBlockState());
        for (int dz = 1; dz <= 3; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                level.setBlockAndUpdate(middle.offset(0, dy, -dz), Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Stands the player two blocks north of the wall, looking at its middle. */
    private static void stand(ServerPlayer player, ServerLevel level, BlockPos middle) {
        Vec3 feet = Vec3.atBottomCenterOf(middle.offset(0, -1, -2));
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0.0F, 0.0F, true);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(middle));
    }

    /** The eight blocks of the 3 by 3 around {@code middle} in its x-y plane. */
    private static List<BlockPos> around(BlockPos middle) {
        return BlockPos.betweenClosedStream(middle.offset(-1, -1, 0), middle.offset(1, 1, 0))
                .map(BlockPos::immutable)
                .filter(pos -> !pos.equals(middle))
                .toList();
    }
}
