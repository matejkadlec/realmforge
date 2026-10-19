package io.github.matejkadlec.realmforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The block classes of the block kinds in {@code content/blocks/} other than {@code simple} (architecture 6.2). A
 * kind's behavior is game code in the feature that owns it, above {@code world}, so that feature provides the kind's
 * block here when the mod is constructed, and {@code GeneratedBlocks} creates its blocks through {@link #create} when
 * blocks register.
 */
public final class BlockKinds {
    private static final Map<String, Function<BlockBehaviour.Properties, Block>> FACTORIES = new ConcurrentHashMap<>();
    private static final Map<String, List<Block>> BLOCKS = new ConcurrentHashMap<>();

    private BlockKinds() {
    }

    /** Provides the block of {@code kind}; called once per kind, before blocks register. */
    public static void provide(String kind, Function<BlockBehaviour.Properties, Block> factory) {
        if (FACTORIES.putIfAbsent(kind, factory) != null) {
            throw new IllegalStateException("block kind " + kind + " is provided twice");
        }
    }

    /**
     * A new block of {@code kind} with the content's properties.
     *
     * @throws IllegalStateException when no feature provides the kind
     */
    public static Block create(String kind, BlockBehaviour.Properties properties) {
        Function<BlockBehaviour.Properties, Block> factory = FACTORIES.get(kind);
        if (factory == null) {
            throw new IllegalStateException("no feature provides block kind " + kind);
        }
        Block block = factory.apply(properties);
        BLOCKS.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(block);
        return block;
    }

    /** Every block of {@code kind}, once blocks have registered; content order. */
    public static List<Block> blocks(String kind) {
        return List.copyOf(BLOCKS.getOrDefault(kind, List.of()));
    }
}
