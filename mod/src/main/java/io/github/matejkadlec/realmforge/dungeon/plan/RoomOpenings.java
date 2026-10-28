package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The sides of a room a run may open (RLM-412), resolved by {@code rf gen} from its sidecar: per side of the template
 * (as saved, before any turn), the blocks the run turns to air there, such as the block in front of a spawner the room
 * hides. A run opens one side, at random among those that are not the room's way in, so a player meets the spawner
 * from the left, the right or across the room, never straight from the doorway (Matěj, 2026-10-28).
 *
 * @param sides the blocks each side's opening clears, in template positions
 */
public record RoomOpenings(Map<Direction, List<BlockPos>> sides) {
    /** A room that opens nothing. */
    public static final RoomOpenings NONE = new RoomOpenings(Map.of());

    public static final Codec<RoomOpenings> CODEC = Codec.unboundedMap(Direction.CODEC, BlockPos.CODEC.listOf())
            .xmap(RoomOpenings::new, RoomOpenings::sides).validate(RoomOpenings::validate);

    public RoomOpenings {
        Map<Direction, List<BlockPos>> copy = new EnumMap<>(Direction.class);
        sides.forEach((side, positions) -> copy.put(side, List.copyOf(positions)));
        sides = Collections.unmodifiableMap(copy);
    }

    /**
     * The blocks of the side a run opens: one side drawn at random among those that are not {@code wayIn}, the side
     * the run enters the room from (all of them when it enters from above or below, or when no other side is left).
     * Positions are the template's; none when the room opens nothing.
     */
    public List<RoomPlacement.Swap> draw(Optional<Direction> wayIn, RandomSource random) {
        if (sides.isEmpty()) {
            return List.of();
        }
        List<Direction> choices = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (sides.containsKey(side) && wayIn.map(entered -> entered != side).orElse(true)) {
                choices.add(side);
            }
        }
        if (choices.isEmpty()) {
            choices.addAll(sides.keySet());
        }
        Direction opened = choices.get(random.nextInt(choices.size()));
        return sides.get(opened).stream()
                .map(pos -> new RoomPlacement.Swap(pos, Blocks.AIR.defaultBlockState()))
                .toList();
    }

    private static DataResult<RoomOpenings> validate(RoomOpenings openings) {
        for (Map.Entry<Direction, List<BlockPos>> side : openings.sides.entrySet()) {
            if (!side.getKey().getAxis().isHorizontal()) {
                return DataResult.error(() -> "an opening faces " + side.getKey().getSerializedName()
                        + ", not a side");
            }
            if (side.getValue().isEmpty()) {
                return DataResult.error(() -> "the " + side.getKey().getSerializedName() + " opening clears nothing");
            }
        }
        return DataResult.success(openings);
    }
}
