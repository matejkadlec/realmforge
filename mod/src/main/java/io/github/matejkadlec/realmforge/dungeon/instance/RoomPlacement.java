package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * One room of an instance: a structure template placed at {@code offset} from the plot's origin, rotated around
 * that corner, and the blocks an explosion inside it may break (its room's {@code explosion_breaks}, RLM-257). The
 * planner (W4) produces these; spike 3 uses a fixed three-room layout from the QA mod.
 */
public record RoomPlacement(Identifier template, BlockPos offset, Rotation rotation, List<Identifier> explosionBreaks) {
    public static final Codec<RoomPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("template").forGetter(RoomPlacement::template),
            BlockPos.CODEC.fieldOf("offset").forGetter(RoomPlacement::offset),
            Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(RoomPlacement::rotation),
            Identifier.CODEC.listOf().optionalFieldOf("explosion_breaks", List.of())
                    .forGetter(RoomPlacement::explosionBreaks)
    ).apply(instance, RoomPlacement::new));

    public RoomPlacement {
        explosionBreaks = List.copyOf(explosionBreaks);
    }

    /** A room whose explosions break nothing. */
    public RoomPlacement(Identifier template, BlockPos offset, Rotation rotation) {
        this(template, offset, rotation, List.of());
    }
}
