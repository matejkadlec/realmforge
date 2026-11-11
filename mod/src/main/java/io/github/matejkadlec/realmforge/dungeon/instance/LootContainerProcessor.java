package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.loot.LootContainers;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Makes every chest, trapped chest and barrel of a room or a set piece a loot container as it is placed (RLM-290, ADR
 * 0041): the block becomes the one whose loot every player gets for themselves ({@link LootContainers#perPlayer},
 * Lootr's twin where Lootr is loaded), and its data names the loot table, which {@code StructureTemplate#placeInWorld}
 * loads into the block entity with a seed. A container that names a loot table of its own keeps it; no container of
 * a run is left without one. It runs after the swaps, so it sees a container a swap put there and none a swap took
 * away.
 */
public final class LootContainerProcessor extends StructureProcessor {
    private static final DeferredRegister<StructureProcessorType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Realmforge.MOD_ID);
    private static final MapCodec<LootContainerProcessor> CODEC = LootTable.KEY_CODEC.fieldOf("loot_table")
            .xmap(LootContainerProcessor::new, processor -> processor.table);
    private static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<LootContainerProcessor>>
            TYPE = TYPES.register("loot_container", () -> () -> CODEC);

    private final ResourceKey<LootTable> table;

    LootContainerProcessor(ResourceKey<LootTable> table) {
        this.table = table;
    }

    /** Registers its processor type; called once from {@code DungeonFeature}. */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** NeoForge's overload, as in {@link BlockSwapProcessor}. */
    @Override
    public StructureTemplate.StructureBlockInfo process(LevelReader level, BlockPos targetPosition,
            BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        if (!LootContainers.holdsLoot(processedBlockInfo.state())) {
            return processedBlockInfo;
        }
        // A copy: placing writes the seed into this data, and a template's own data is shared by every placement.
        CompoundTag data = processedBlockInfo.nbt() == null ? new CompoundTag() : processedBlockInfo.nbt().copy();
        if (!data.contains(RandomizableContainer.LOOT_TABLE_TAG)) {
            data.putString(RandomizableContainer.LOOT_TABLE_TAG, table.identifier().toString());
        }
        return new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(),
                LootContainers.perPlayer(processedBlockInfo.state()), data);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE.get();
    }
}
