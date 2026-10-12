package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The value of a player's {@code realmforge:instance} attachment while they belong to a run: the plot and where
 * to send them back to. W4 adds the game mode to restore (architecture 7 and 15.5).
 */
public record InstanceVisit(int plot, ResourceKey<Level> returnLevel, Vec3 returnPos, float returnYaw,
        float returnPitch) {
    public static final Codec<InstanceVisit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("plot").forGetter(InstanceVisit::plot),
            Level.RESOURCE_KEY_CODEC.fieldOf("return_level").forGetter(InstanceVisit::returnLevel),
            Vec3.CODEC.fieldOf("return_pos").forGetter(InstanceVisit::returnPos),
            Codec.FLOAT.optionalFieldOf("return_yaw", 0.0F).forGetter(InstanceVisit::returnYaw),
            Codec.FLOAT.optionalFieldOf("return_pitch", 0.0F).forGetter(InstanceVisit::returnPitch)
    ).apply(instance, InstanceVisit::new));
}
