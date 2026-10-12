package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/**
 * One room of an instance: a structure template placed at {@code offset} from the plot's origin, rotated around
 * that corner. The planner (W4) produces these; spike 3 uses a fixed three-room layout from the QA mod.
 */
public record RoomPlacement(ResourceLocation template, BlockPos offset, Rotation rotation) {
    public static final Codec<RoomPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(RoomPlacement::template),
            BlockPos.CODEC.fieldOf("offset").forGetter(RoomPlacement::offset),
            Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(RoomPlacement::rotation)
    ).apply(instance, RoomPlacement::new));
}
