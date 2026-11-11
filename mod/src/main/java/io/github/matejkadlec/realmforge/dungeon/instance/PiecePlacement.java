package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One set piece of a room of an instance (RLM-282, architecture 15.4): a structure template placed at {@code offset}
 * from the plot's origin, mirrored and then turned about that corner as vanilla places a template, with its swaps
 * (the variants the run drew: a block that is not there this time becomes air, a barrel a chest) and the loot table
 * its chests and barrels get. The planner draws these with the layout ({@code SetPieces#draw}); the builder places
 * each with the room it stands in.
 */
public record PiecePlacement(Identifier template, BlockPos offset, Rotation rotation, Mirror mirror,
        List<RoomPlacement.Swap> swaps, Optional<ResourceKey<LootTable>> containerLoot) {
    public static final Codec<PiecePlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("template").forGetter(PiecePlacement::template),
            BlockPos.CODEC.fieldOf("offset").forGetter(PiecePlacement::offset),
            Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(PiecePlacement::rotation),
            Mirror.CODEC.optionalFieldOf("mirror", Mirror.NONE).forGetter(PiecePlacement::mirror),
            RoomPlacement.Swap.CODEC.listOf().optionalFieldOf("swaps", List.of()).forGetter(PiecePlacement::swaps),
            LootTable.KEY_CODEC.optionalFieldOf("container_loot").forGetter(PiecePlacement::containerLoot)
    ).apply(instance, PiecePlacement::new));

    public PiecePlacement {
        swaps = List.copyOf(swaps);
    }
}
