package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Moves the block a placed item frame or painting hangs at to where the room puts it (RLM-362). A template keeps
 * each such entity's {@code block_pos} as it was where the template was saved, so the entity, loaded far from there,
 * logs "Block-attached entity at invalid position" before vanilla moves it into place
 * ({@code BlockAttachedEntity#readAdditionalSaveData}, {@code StructureTemplate#addEntitiesToWorld}); this processor
 * writes the placed block into the entity's data first, through NeoForge's entity hook of a structure processor.
 */
final class AttachedEntityProcessor extends StructureProcessor {
    static final AttachedEntityProcessor INSTANCE = new AttachedEntityProcessor();
    private static final String BLOCK_POS = "block_pos";
    private static final DeferredRegister<StructureProcessorType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Realmforge.MOD_ID);
    private static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<AttachedEntityProcessor>>
            TYPE = TYPES.register("attached_entity", () -> () -> MapCodec.unit(INSTANCE));

    private AttachedEntityProcessor() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    public StructureTemplate.StructureEntityInfo processEntity(LevelReader level, BlockPos seedPos,
            StructureTemplate.StructureEntityInfo rawEntityInfo, StructureTemplate.StructureEntityInfo entityInfo,
            StructurePlaceSettings settings, StructureTemplate template) {
        if (!entityInfo.nbt.contains(BLOCK_POS)) {
            return entityInfo;
        }
        CompoundTag nbt = entityInfo.nbt.copy();
        BlockPos pos = entityInfo.blockPos;
        nbt.putIntArray(BLOCK_POS, new int[] {pos.getX(), pos.getY(), pos.getZ()});
        return new StructureTemplate.StructureEntityInfo(entityInfo.pos, pos, nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE.get();
    }
}
