package io.github.matejkadlec.realmforge.world.realm;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.world.BlockKinds;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The realm portal's block kinds (architecture 6.2, 15.1; realm portal spec): Realmforge Bricks, the sealstone and the
 * portal block. Their blocks come from {@code content/blocks/} ({@code GeneratedBlocks}), one of each; this provides
 * their classes, the sealstone's block entity and its point-of-interest type, which the trip's search reads.
 */
public final class RealmPortalBlocks {
    /** {@code kind: realmforge_bricks} in {@code content/blocks/}. */
    public static final String BRICKS_KIND = "realmforge_bricks";
    /** {@code kind: realmforge_sealstone} in {@code content/blocks/}. */
    public static final String SEALSTONE_KIND = "realmforge_sealstone";
    /** {@code kind: realm_portal} in {@code content/blocks/}. */
    public static final String PORTAL_KIND = "realm_portal";
    /** The portal's light, as the nether portal's. */
    private static final int PORTAL_LIGHT = 11;

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Realmforge.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Realmforge.MOD_ID);

    /** {@code realmforge:realmforge_sealstone}: where the sealstone's portal leads. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SealstoneBlockEntity>> SEALSTONE_ENTITY =
            BLOCK_ENTITIES.register(SEALSTONE_KIND, () -> new BlockEntityType<>(SealstoneBlockEntity::new,
                    Set.copyOf(BlockKinds.blocks(SEALSTONE_KIND))));
    /**
     * {@code realmforge:realmforge_sealstone}: every state of every sealstone, as vanilla's {@code nether_portal} is every
     * portal block. Holds no tickets, as vanilla's.
     */
    private static final DeferredHolder<PoiType, PoiType> SEALSTONE_POI_TYPE =
            POI_TYPES.register(SEALSTONE_KIND, () -> new PoiType(BlockKinds.blocks(SEALSTONE_KIND).stream()
                    .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                    .collect(Collectors.<BlockState>toUnmodifiableSet()), 0, 1));
    /** The key of the sealstone's point-of-interest type, which the trip's search asks for. */
    public static final ResourceKey<PoiType> SEALSTONE_POI = SEALSTONE_POI_TYPE.getKey();

    private RealmPortalBlocks() {
    }

    /** Provides the kinds' blocks and registers the sealstone's block entity and point of interest. Called once. */
    public static void register(IEventBus modBus) {
        // Frame blocks cannot be pushed, so no contraption moves a lit frame apart.
        BlockKinds.provide(BRICKS_KIND,
                properties -> new RealmforgeBricksBlock(properties.pushReaction(PushReaction.BLOCK)));
        BlockKinds.provide(SEALSTONE_KIND, properties -> new SealstoneBlock(properties.pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> state.getValue(SealstoneBlock.GLOW))));
        BlockKinds.provide(PORTAL_KIND, properties -> new RealmPortalBlock(properties.noCollision()
                .lightLevel(state -> PORTAL_LIGHT).pushReaction(PushReaction.BLOCK)));
        BLOCK_ENTITIES.register(modBus);
        POI_TYPES.register(modBus);
    }

    /** The Realmforge Bricks block. */
    public static Block bricks() {
        return single(BRICKS_KIND);
    }

    /** The sealstone block. */
    public static Block sealstone() {
        return single(SEALSTONE_KIND);
    }

    /** The realm portal's portal block. */
    public static Block portal() {
        return single(PORTAL_KIND);
    }

    /** The one block of {@code kind}: the pipeline allows one, and needs all three kinds together. */
    private static Block single(String kind) {
        List<Block> blocks = BlockKinds.blocks(kind);
        if (blocks.isEmpty()) {
            throw new IllegalStateException("content/blocks/ has no block of kind " + kind);
        }
        return blocks.getFirst();
    }
}
