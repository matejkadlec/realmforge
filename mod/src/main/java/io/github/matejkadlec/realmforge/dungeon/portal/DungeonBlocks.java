package io.github.matejkadlec.realmforge.dungeon.portal;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.world.BlockKinds;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The dungeon feature's block kinds (architecture 6.2, 15.5): the dungeon portal and the dungeon exit. Their blocks
 * come from {@code content/blocks/} ({@code GeneratedBlocks}); this provides their classes and the portal's block
 * entity.
 */
public final class DungeonBlocks {
    /** {@code kind: dungeon_portal} in {@code content/blocks/}. */
    public static final String PORTAL_KIND = "dungeon_portal";
    /** {@code kind: dungeon_exit} in {@code content/blocks/}; one block at most. */
    public static final String EXIT_KIND = "dungeon_exit";

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Realmforge.MOD_ID);
    /** {@code realmforge:dungeon_portal}: which run an open portal leads to. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DungeonPortalBlockEntity>> PORTAL_ENTITY =
            BLOCK_ENTITIES.register("dungeon_portal", () -> new BlockEntityType<>(DungeonPortalBlockEntity::new,
                    Set.copyOf(BlockKinds.blocks(PORTAL_KIND))));

    private DungeonBlocks() {
    }

    /** Provides the kinds' blocks and registers the portal's block entity. Called once from the dungeon feature. */
    public static void register(IEventBus modBus) {
        // The portal glows while open; the exit is a plate you walk over.
        BlockKinds.provide(PORTAL_KIND, properties -> new DungeonPortalBlock(properties
                .lightLevel(state -> state.getValue(DungeonPortalBlock.OPEN) ? 10 : 0)));
        BlockKinds.provide(EXIT_KIND, properties -> new DungeonExitBlock(properties.noCollision()));
        BLOCK_ENTITIES.register(modBus);
        DungeonPortals.register();
    }
}
