package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One room of an instance: a structure template placed at {@code offset} from the plot's origin, rotated around
 * that corner, the blocks an explosion inside it may break (its room's {@code explosion_breaks}, RLM-257), and its
 * swaps: the containers its dungeon's chance tables dropped, each swapped for the block it leaves behind (RLM-365),
 * and the blocks of the side it opens, swapped for air (RLM-412). The planner (W4) produces these; spike 3 uses a
 * fixed three-room layout from the QA mod.
 */
public record RoomPlacement(Identifier template, BlockPos offset, Rotation rotation, List<Identifier> explosionBreaks,
        List<Swap> swaps) {
    public static final Codec<RoomPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("template").forGetter(RoomPlacement::template),
            BlockPos.CODEC.fieldOf("offset").forGetter(RoomPlacement::offset),
            Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(RoomPlacement::rotation),
            Identifier.CODEC.listOf().optionalFieldOf("explosion_breaks", List.of())
                    .forGetter(RoomPlacement::explosionBreaks),
            Swap.CODEC.listOf().optionalFieldOf("swaps", List.of()).forGetter(RoomPlacement::swaps)
    ).apply(instance, RoomPlacement::new));

    public RoomPlacement {
        explosionBreaks = List.copyOf(explosionBreaks);
        swaps = List.copyOf(swaps);
    }

    /** A room whose explosions break nothing and that places every block of its template. */
    public RoomPlacement(Identifier template, BlockPos offset, Rotation rotation) {
        this(template, offset, rotation, List.of(), List.of());
    }

    /** A room that places every block of its template. */
    public RoomPlacement(Identifier template, BlockPos offset, Rotation rotation, List<Identifier> explosionBreaks) {
        this(template, offset, rotation, explosionBreaks, List.of());
    }

    /**
     * A block of the template placed as {@code state} instead: a dropped container's floor, wall or air (RLM-365), or
     * the air of an opening (RLM-412).
     */
    public record Swap(BlockPos pos, BlockState state) {
        public static final Codec<Swap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Swap::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Swap::state)
        ).apply(instance, Swap::new));
    }
}
