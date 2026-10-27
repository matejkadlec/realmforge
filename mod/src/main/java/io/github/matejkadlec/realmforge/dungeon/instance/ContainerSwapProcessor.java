package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Places a room's dropped containers as the blocks they leave behind (RLM-365, {@link RoomPlacement#swaps}): a block
 * of the template at a swapped position comes out as the swap's state, without the container's data. It works on the
 * template's own positions, so a room cut into chunk slices swaps each block in the slice that places it.
 */
public final class ContainerSwapProcessor extends StructureProcessor {
    private static final DeferredRegister<StructureProcessorType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Realmforge.MOD_ID);
    private static final MapCodec<ContainerSwapProcessor> CODEC = RoomPlacement.Swap.CODEC.listOf().fieldOf("swaps")
            .xmap(ContainerSwapProcessor::new, processor -> processor.swaps.entrySet().stream()
                    .map(entry -> new RoomPlacement.Swap(entry.getKey(), entry.getValue())).toList());
    private static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<ContainerSwapProcessor>>
            TYPE = TYPES.register("container_swap", () -> () -> CODEC);

    private final Map<BlockPos, BlockState> swaps = new HashMap<>();

    ContainerSwapProcessor(List<RoomPlacement.Swap> swaps) {
        swaps.forEach(swap -> this.swaps.put(swap.pos(), swap.state()));
    }

    /** Registers its processor type; called once from {@code DungeonFeature}. */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition,
            BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
        BlockState state = swaps.get(originalBlockInfo.pos());
        return state == null ? processedBlockInfo
                : new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), state, null);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE.get();
    }
}
