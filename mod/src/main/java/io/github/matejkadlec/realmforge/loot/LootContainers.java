package io.github.matejkadlec.realmforge.loot;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Places a container whose loot every player gets for themselves (ADR 0041, architecture 15.5; RLM-288): a chest, a
 * trapped chest or a barrel with a loot table, as Lootr's own block where Lootr is loaded.
 * <p>
 * Lootr (1.23.38.123, read in its jar) also takes over a vanilla container that has a loot table, but only once the
 * container's chunk and the 24 chunks around it are fully loaded; until then a player who opens it gets vanilla's one
 * roll for everybody. A dungeon is built before anyone stands in it, so the builder does not wait for that: it places
 * Lootr's block itself. Lootr's blocks are named by id, so the core mod needs no class of Lootr's, and without Lootr
 * (the lean dev client) the container stays vanilla's, with vanilla's shared roll.
 */
public final class LootContainers {
    /** Lootr's mod id, the namespace of its blocks. */
    public static final String LOOTR = "lootr";

    private LootContainers() {
    }

    /**
     * Sets {@code state}, a chest, a trapped chest or a barrel, at {@code pos} as a loot container that rolls
     * {@code table} once per player: Lootr's twin of the block with the same facing where Lootr is loaded
     * ({@link #perPlayer}). False, with the block placed all the same, if what stands there afterwards takes no loot
     * table.
     *
     * @param seed the table's seed, 0 for a random one; Lootr rolls its own per player whatever it is
     */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, ResourceKey<LootTable> table,
            long seed) {
        level.setBlock(pos, perPlayer(state), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(table, seed);
            return true;
        }
        return false;
    }

    /**
     * Lootr's block for a vanilla chest, trapped chest or barrel, with every property the two share (facing, open,
     * waterlogged), as a single chest: Lootr's chests never join. {@code state} itself for any other block, and for
     * these three where Lootr is not loaded.
     */
    public static BlockState perPlayer(BlockState state) {
        Optional<Block> twin = twin(state.getBlock());
        if (twin.isEmpty()) {
            return state;
        }
        BlockState result = twin.get().defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (result.hasProperty(property)) {
                result = copy(state, result, property);
            }
        }
        if (result.hasProperty(BlockStateProperties.CHEST_TYPE)) {
            result = result.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE);
        }
        return result;
    }

    /** Whether {@code state} is one of Lootr's containers. */
    public static boolean isPerPlayer(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(LOOTR);
    }

    private static Optional<Block> twin(Block block) {
        String path;
        if (block == Blocks.CHEST) {
            path = "chest";
        } else if (block == Blocks.TRAPPED_CHEST) {
            path = "trapped_chest";
        } else if (block == Blocks.BARREL) {
            path = "barrel";
        } else {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(LOOTR, path));
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
}
