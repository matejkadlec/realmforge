package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * How a spell projectile flies, an entry of the datapack registry {@code realmforge:spell_trajectory} from
 * {@code content/spell_trajectories/} (architecture 11.3, ADR 0073). A trajectory changes where a projectile is, so it
 * is gameplay: the server moves the projectile along it, and its clients compute the same path from what its spawn
 * carries (the trajectory, the aim and a seed), so they draw what the server flies.
 *
 * <p>A path is a pure function of the launch point, the aim, the ticks flown and the seed: {@link #velocity} gives the
 * step of one tick and {@link #position} adds the steps up, exactly as the projectile does tick by tick. The aim is
 * the velocity of the line it flies along; aim assistance's homing turns that line on the server (RLM-345), and a
 * later kind adds its own offset to it, so the two never fight.
 *
 * @param kind which path it is
 */
public record SpellTrajectory(Kind kind) {
    public static final Codec<SpellTrajectory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Kind.CODEC.fieldOf("kind").forGetter(SpellTrajectory::kind))
            .apply(instance, SpellTrajectory::new));

    /**
     * The projectile's movement during its tick number {@code tick} (0 for its first), in blocks, for a flight along
     * {@code aim} (blocks per tick).
     */
    public Vec3 velocity(Vec3 aim, int tick, long seed) {
        return kind.velocity(aim, tick, seed);
    }

    /**
     * Where a projectile launched at {@code launch} along {@code aim} (blocks per tick) is after {@code ticks} ticks:
     * its steps added up one by one, as the projectile itself adds them.
     */
    public Vec3 position(Vec3 launch, Vec3 aim, int ticks, long seed) {
        double x = launch.x;
        double y = launch.y;
        double z = launch.z;
        for (int tick = 0; tick < ticks; tick++) {
            Vec3 step = velocity(aim, tick, seed);
            x += step.x;
            y += step.y;
            z += step.z;
        }
        return new Vec3(x, y, z);
    }

    /** The paths a projectile can fly. A new one is a decision of its own (ADR 0052 says straight). */
    public enum Kind implements StringRepresentable {
        /** Along the aim at its speed, without gravity or drag: every tick's step is the aim, whatever the seed. */
        STRAIGHT("straight") {
            @Override
            Vec3 velocity(Vec3 aim, int tick, long seed) {
                return aim;
            }
        };

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        abstract Vec3 velocity(Vec3 aim, int tick, long seed);

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
