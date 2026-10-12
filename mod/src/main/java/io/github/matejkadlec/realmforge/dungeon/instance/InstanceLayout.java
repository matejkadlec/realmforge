package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * What to build in a plot and where players arrive: the rooms in placement order and the entry point, both
 * relative to the plot's origin.
 */
public record InstanceLayout(List<RoomPlacement> rooms, Vec3 spawn, float spawnYaw) {
    public static final Codec<InstanceLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RoomPlacement.CODEC.listOf().fieldOf("rooms").forGetter(InstanceLayout::rooms),
            Vec3.CODEC.fieldOf("spawn").forGetter(InstanceLayout::spawn),
            Codec.FLOAT.optionalFieldOf("spawn_yaw", 0.0F).forGetter(InstanceLayout::spawnYaw)
    ).apply(instance, InstanceLayout::new));

    public InstanceLayout {
        if (rooms.isEmpty()) {
            throw new IllegalArgumentException("an instance layout needs at least one room");
        }
        rooms = List.copyOf(rooms);
    }
}
