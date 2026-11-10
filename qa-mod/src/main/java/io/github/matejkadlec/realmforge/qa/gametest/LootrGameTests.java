package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.loot.LootContainers;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-288 (ADR 0041, architecture 15.5): Lootr gives every player their own contents of a loot container.
 * <ul>
 *   <li>The dungeon builder's way, {@link LootContainers#place}: Lootr's own chest or barrel with the loot table, in
 *   the vanilla block's place and facing, which rolls the table once per player from the moment it stands.</li>
 *   <li>Lootr's own takeover of a vanilla container with a loot table happens only once the 5 × 5 chunks around it
 *   are fully loaded, which is why the builder does not rely on it.</li>
 *   <li>A container without a loot table stays vanilla's.</li>
 * </ul>
 * The tests name Lootr's blocks by id, so the QA mod needs no class of Lootr's: the GameTest server loads it as a pack
 * mod.
 */
public final class LootrGameTests {
    /** {@code data/realmforge_qa/loot_table/test/three_diamonds.json}: always three diamonds, so every roll shows. */
    private static final ResourceKey<LootTable> THREE_DIAMONDS =
            ResourceKey.create(Registries.LOOT_TABLE, RealmforgeQa.id("test/three_diamonds"));
    private static final BlockPos SPOT = new BlockPos(2, 1, 3);
    private static final int DIAMONDS = 3;
    /** Lootr converts a container once every chunk within this many chunks of its own is fully loaded. */
    private static final int LOOTR_CHUNK_RADIUS = 2;

    private LootrGameTests() {
    }

    @QaGameTest
    public static void aPlacedLootChestGivesEveryPlayerTheirOwnLoot(GameTestHelper helper) {
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        place(helper, chest);
        BlockState placed = helper.getBlockState(SPOT);
        helper.assertValueEqual(blockId(helper), "lootr:chest", "the chest the builder places");
        helper.assertValueEqual(placed.getValue(ChestBlock.FACING), Direction.WEST, "its facing");
        helper.assertValueEqual(placed.getValue(ChestBlock.TYPE), ChestType.SINGLE, "Lootr's chests never join");
        everyPlayerGetsTheirOwnLoot(helper, "qa-lootr-c");
    }

    @QaGameTest
    public static void aPlacedLootBarrelGivesEveryPlayerTheirOwnLoot(GameTestHelper helper) {
        place(helper, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        helper.assertValueEqual(blockId(helper), "lootr:barrel", "the barrel the builder places");
        helper.assertValueEqual(helper.getBlockState(SPOT).getValue(BarrelBlock.FACING), Direction.UP, "its facing");
        everyPlayerGetsTheirOwnLoot(helper, "qa-lootr-b");
    }

    /** A block that is no container of Lootr's is placed as it is, and the caller learns that it took no table. */
    @QaGameTest
    public static void onlyChestsAndBarrelsBecomeLootContainers(GameTestHelper helper) {
        helper.assertValueEqual(LootContainers.perPlayer(Blocks.STONE.defaultBlockState()),
                Blocks.STONE.defaultBlockState(), "a block without a twin of Lootr's");
        helper.assertFalse(LootContainers.place(helper.getLevel(), helper.absolutePos(SPOT),
                Blocks.STONE.defaultBlockState(), THREE_DIAMONDS, 0L), "stone takes no loot table");
        helper.assertValueEqual(blockId(helper), "minecraft:stone", "the block placed");
        helper.assertTrue(LootContainers.isPerPlayer(LootContainers.perPlayer(Blocks.TRAPPED_CHEST.defaultBlockState())),
                "a trapped chest has a twin");
        helper.succeed();
    }

    /**
     * What Lootr does by itself with a vanilla chest that has a loot table (a world structure's, an imported room's):
     * it leaves it alone while the chunks around it are not loaded, and takes it over once all 25 are.
     */
    @QaGameTest(timeoutTicks = 600)
    public static void lootrTakesAVanillaLootChestOverOnceItsSurroundingsAreLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(SPOT, Blocks.CHEST);
        helper.getBlockEntity(SPOT, RandomizableContainerBlockEntity.class).setLootTable(THREE_DIAMONDS, 42L);
        loadSurroundings(level, helper.absolutePos(SPOT));
        helper.succeedWhen(
                () -> helper.assertValueEqual(blockId(helper), "lootr:chest", "the chest with a loot table"));
    }

    /** Lootr converts only what has loot to roll: a chest a player filled, or a decoration, stays vanilla's. */
    @QaGameTest(timeoutTicks = 600)
    public static void aContainerWithoutALootTableStaysVanillas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(SPOT, Blocks.CHEST);
        helper.getBlockEntity(SPOT, RandomizableContainerBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND));
        loadSurroundings(level, helper.absolutePos(SPOT));
        helper.runAfterDelay(300, () -> {
            helper.assertValueEqual(blockId(helper), "minecraft:chest", "the chest without a loot table");
            helper.succeed();
        });
    }

    private static void place(GameTestHelper helper, BlockState state) {
        helper.assertTrue(LootContainers.place(helper.getLevel(), helper.absolutePos(SPOT), state, THREE_DIAMONDS, 42L),
                "the container takes its loot table");
    }

    /** Opens the container at the spot as two players: each finds the three diamonds, whatever the other took. */
    private static void everyPlayerGetsTheirOwnLoot(GameTestHelper helper, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer first = QaPlayers.loginOnPlatform(helper, name + "1", new Vec3(1.5, 1, 1.5));
        ServerPlayer second = QaPlayers.loginOnPlatform(helper, name + "2", new Vec3(3.5, 1, 1.5));

        Container firstLoot = open(helper, first);
        helper.assertValueEqual(firstLoot.countItem(Items.DIAMOND), DIAMONDS, "the first player's diamonds");
        firstLoot.clearContent();
        first.closeContainer();

        Container secondLoot = open(helper, second);
        helper.assertTrue(secondLoot != firstLoot, "each player has an inventory of their own");
        helper.assertValueEqual(secondLoot.countItem(Items.DIAMOND), DIAMONDS,
                "the second player's diamonds, after the first took theirs");
        second.closeContainer();

        // The first player's own contents are kept: what they took does not come back.
        helper.assertValueEqual(open(helper, first).countItem(Items.DIAMOND), 0,
                "the first player's diamonds when they open it again");
        first.closeContainer();
        server.getPlayerList().remove(first);
        server.getPlayerList().remove(second);
        helper.succeed();
    }

    /** Right-clicks the container as {@code player} and returns the contents its menu shows them. */
    private static Container open(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(SPOT);
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(player.containerMenu instanceof ChestMenu,
                "the container does not open for " + player.getPlainTextName());
        return ((ChestMenu) player.containerMenu).getContainer();
    }

    /**
     * Loads every chunk Lootr wants loaded around {@code pos} before it converts a container there. They stay loaded
     * for the rest of the batch, as the tests' own chunks do: the GameTest runner lets go of every forced chunk when
     * a batch ends ({@code GameTestRunner}), and a chunk let go earlier could be another running test's.
     */
    private static void loadSurroundings(ServerLevel level, BlockPos pos) {
        ChunkPos middle = ChunkPos.containing(pos);
        for (int x = middle.x() - LOOTR_CHUNK_RADIUS; x <= middle.x() + LOOTR_CHUNK_RADIUS; x++) {
            for (int z = middle.z() - LOOTR_CHUNK_RADIUS; z <= middle.z() + LOOTR_CHUNK_RADIUS; z++) {
                level.setChunkForced(x, z, true);
            }
        }
    }

    private static String blockId(GameTestHelper helper) {
        return BuiltInRegistries.BLOCK.getKey(helper.getBlockState(SPOT).getBlock()).toString();
    }
}
