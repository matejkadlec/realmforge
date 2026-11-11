package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.Realmforge;
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
import io.github.matejkadlec.realmforge.dungeon.plan.RoomRole;
import io.github.matejkadlec.realmforge.loot.LootContainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-290 (ADR 0041, architecture 15.4): when a run is built, every chest and barrel of its rooms becomes a loot
 * container with its dungeon type's container loot table for the room's role, whose contents every player gets for
 * themselves (Lootr). No container of a built dungeon stays vanilla's or without a loot table.
 */
public final class DungeonLootContainerGameTests {
    private static final int TIMEOUT = 1200;
    private static final Identifier TEST_DUNGEON = Identifier.parse("realmforge:test_dungeon");
    /** An imported room with exactly one chest and one barrel, both always kept (Matěj, 2026-10-25). */
    private static final Identifier JAIL =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/pillager_dungeon/rooms/jail");
    private static final ResourceKey<LootTable> NORMAL =
            ResourceKey.create(Registries.LOOT_TABLE, Realmforge.id("containers/normal"));
    private static final ResourceKey<LootTable> TREASURE =
            ResourceKey.create(Registries.LOOT_TABLE, Realmforge.id("containers/treasure"));
    private static final long SEED = 20261111L;

    private DungeonLootContainerGameTests() {
    }

    /**
     * Done when of RLM-290: a room built with an imported chest and barrel, each opened by two players. The room is
     * placed as the test dungeon's entry has it (its container loot table), turned a quarter.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aBuiltRoomsChestAndBarrelGiveTwoPlayersTheirOwnLoot(GameTestHelper helper) {
        DungeonRoom jail = testDungeon(helper).rooms().stream().filter(room -> room.template().equals(JAIL))
                .findFirst().orElse(null);
        helper.assertTrue(jail != null, TEST_DUNGEON + " has no room " + JAIL);
        helper.assertValueEqual(jail.containerLoot(), Optional.of(NORMAL), "the jail's container loot table");
        InstanceLayout layout = new InstanceLayout(List.of(new RoomPlacement(JAIL, BlockPos.ZERO,
                Rotation.CLOCKWISE_90, List.of(), List.of(), jail.containerLoot(), List.of())), Optional.empty());
        InstanceManager manager = manager(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel instances = InstanceDimension.level(server);
        Plot plot = manager.open(layout, List.of());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    List<BlockPos> containers = containers(instances, plot.rooms().getFirst(), helper);
                    helper.assertValueEqual(containers.stream().map(pos -> BuiltInRegistries.BLOCK.getKey(
                            instances.getBlockState(pos).getBlock()).toString()).sorted().toList(),
                            List.of("lootr:barrel", "lootr:chest"), "the jail's containers as built");
                    ServerPlayer first = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-jail-1"));
                    ServerPlayer second = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-jail-2"));
                    try {
                        for (BlockPos pos : containers) {
                            String what = BuiltInRegistries.BLOCK.getKey(instances.getBlockState(pos).getBlock())
                                    .toString();
                            helper.assertValueEqual(lootTable(instances, pos), NORMAL, what + "'s loot table");
                            Container firstLoot = open(helper, instances, first, pos);
                            helper.assertFalse(firstLoot.isEmpty(), "the first player's " + what + " is empty");
                            firstLoot.clearContent();
                            first.closeContainer();
                            Container secondLoot = open(helper, instances, second, pos);
                            helper.assertTrue(secondLoot != firstLoot, what + ": each player has their own contents");
                            helper.assertFalse(secondLoot.isEmpty(),
                                    "the second player's " + what + " is empty after the first took theirs");
                            second.closeContainer();
                            helper.assertTrue(open(helper, instances, first, pos).isEmpty(),
                                    "the first player's " + what + " filled again");
                            first.closeContainer();
                        }
                    } finally {
                        server.getPlayerList().remove(first);
                        server.getPlayerList().remove(second);
                    }
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * A whole built layout: no chest or barrel stays vanilla's, and every loot container names the table of its
     * room, the treasure table in a treasure room and the normal one elsewhere, set pieces included.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void everyContainerOfABuiltDungeonIsALootContainerWithItsRoomsTable(GameTestHelper helper) {
        DungeonPlan plan = testDungeon(helper).plan(SEED);
        InstanceLayout layout = plan.toInstanceLayout();
        InstanceManager manager = manager(helper);
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        Plot plot = manager.open(layout, List.of());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    int normal = 0;
                    int treasure = 0;
                    for (int i = 0; i < plan.rooms().size(); i++) {
                        DungeonRoom room = plan.rooms().get(i).room();
                        ResourceKey<LootTable> expected = room.role() == RoomRole.TREASURE ? TREASURE : NORMAL;
                        if (room.role() != RoomRole.WALL) {
                            helper.assertValueEqual(layout.rooms().get(i).containerLoot(), Optional.of(expected),
                                    room.template() + " container loot table");
                        }
                        for (BlockPos pos : containers(instances, plot.rooms().get(i), helper)) {
                            helper.assertValueEqual(lootTable(instances, pos), expected, room.template() + ": the "
                                    + "loot table of " + instances.getBlockState(pos) + " at " + pos.toShortString());
                            if (expected == TREASURE) {
                                treasure++;
                            } else {
                                normal++;
                            }
                        }
                    }
                    helper.assertTrue(normal > 0, "the layout has no container with the normal table");
                    helper.assertTrue(treasure > 0, "the layout has no container with the treasure table");
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * Where the loot containers stand in {@code box}; fails on a chest, trapped chest or barrel that stayed
     * vanilla's.
     */
    static List<BlockPos> containers(ServerLevel level, BoundingBox box, GameTestHelper helper) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            helper.assertFalse(LootContainers.holdsLoot(state),
                    "a container stayed vanilla's: " + state + " at " + pos.toShortString());
            if (LootContainers.isPerPlayer(state)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /** The loot table the container at {@code pos} names. */
    static ResourceKey<LootTable> lootTable(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RandomizableContainer container ? container.getLootTable() : null;
    }

    /** Right-clicks the container at {@code pos} as {@code player}, from next to it, and returns what they see. */
    private static Container open(GameTestHelper helper, ServerLevel level, ServerPlayer player, BlockPos pos) {
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(player.containerMenu instanceof ChestMenu, level.getBlockState(pos) + " at "
                + pos.toShortString() + " does not open for " + player.getPlainTextName());
        return ((ChestMenu) player.containerMenu).getContainer();
    }

    private static DungeonType testDungeon(GameTestHelper helper) {
        DungeonType type = helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(TEST_DUNGEON);
        helper.assertTrue(type != null, TEST_DUNGEON + " is not loaded");
        return type;
    }

    private static InstanceManager manager(GameTestHelper helper) {
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        return manager;
    }
}
