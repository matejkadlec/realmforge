package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * What to build in a plot: the rooms in placement order, the entrance first, and the block an arriving player looks
 * at (the way on), relative to the plot's origin. Where players arrive comes from the entrance room's entrance marker,
 * or the builder finds a spot there ({@link SpawnFinder}).
 */
public record InstanceLayout(List<RoomPlacement> rooms, Optional<BlockPos> lookAt) {
    public static final Codec<InstanceLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RoomPlacement.CODEC.listOf().fieldOf("rooms").forGetter(InstanceLayout::rooms),
            BlockPos.CODEC.optionalFieldOf("look_at").forGetter(InstanceLayout::lookAt)
    ).apply(instance, InstanceLayout::new));

    public InstanceLayout {
        if (rooms.isEmpty()) {
            throw new IllegalArgumentException("an instance layout needs at least one room");
        }
        rooms = List.copyOf(rooms);
    }

    /**
     * The yaw of a player standing at {@code spawn} (relative to the plot's origin) who looks at the middle of
     * {@link #lookAt}; 0 (south) without one, or when it is straight above or below.
     */
    public float yawFrom(Vec3 spawn) {
        return lookAt.map(target -> {
            double dx = target.getX() + 0.5 - spawn.x;
            double dz = target.getZ() + 0.5 - spawn.z;
            if (Math.abs(dx) < 1.0E-6 && Math.abs(dz) < 1.0E-6) {
                return 0.0F;
            }
            // Minecraft's yaw is 0 facing south (+z) and grows clockwise seen from above: west is 90, east -90.
            return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        }).orElse(0.0F);
    }
}
