package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/**
 * How a spell is cast (architecture 11.1, 11.2): {@code instant}, its effects running when it is cast; with a cast
 * time, its effects running when the cast completes; or as a channel, its effects running at every interval for its
 * duration ({@link Casting}).
 *
 * @param type                the cast type
 * @param durationTicks       the cast time or the channel's duration, before Cast Time Reduction; 0 for an instant
 *                            cast
 * @param intervalTicks       a channel's time between two ticks of its effects, which divides its duration; 0 for
 *                            the other types
 * @param castableWhileMoving whether the caster may move during a cast-time or channelled spell; otherwise moving
 *                            cancels it
 * @param lockOn              whether a cast-time or channelled spell keeps its target: range and line of sight are not
 *                            checked again before its effects run, only that the target lives in the caster's
 *                            dimension
 */
public record SpellCast(Type type, int durationTicks, int intervalTicks, boolean castableWhileMoving,
        boolean lockOn) {
    public static final SpellCast INSTANT = new SpellCast(Type.INSTANT, 0, 0, false, false);

    public static final Codec<SpellCast> CODEC = RecordCodecBuilder.<SpellCast>create(instance -> instance.group(
                    Type.CODEC.fieldOf("type").forGetter(SpellCast::type),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("duration_ticks", 0)
                            .forGetter(SpellCast::durationTicks),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("interval_ticks", 0)
                            .forGetter(SpellCast::intervalTicks),
                    Codec.BOOL.optionalFieldOf("castable_while_moving", false)
                            .forGetter(SpellCast::castableWhileMoving),
                    Codec.BOOL.optionalFieldOf("lock_on", false).forGetter(SpellCast::lockOn))
            .apply(instance, SpellCast::new)).validate(SpellCast::validate);

    /** A cast time of {@code ticks} (1 or more). */
    public static SpellCast castTime(int ticks, boolean castableWhileMoving, boolean lockOn) {
        return new SpellCast(Type.CAST_TIME, ticks, 0, castableWhileMoving, lockOn);
    }

    /** A channel of {@code durationTicks} that ticks every {@code intervalTicks}, which divides the duration. */
    public static SpellCast channel(int durationTicks, int intervalTicks, boolean castableWhileMoving,
            boolean lockOn) {
        return new SpellCast(Type.CHANNEL, durationTicks, intervalTicks, castableWhileMoving, lockOn);
    }

    /** Whether the effects run at once, without a current cast. */
    public boolean instant() {
        return type == Type.INSTANT;
    }

    /** How often a channel runs its effects: its duration over its interval; 1 for the other types. */
    public int tickCount() {
        return type == Type.CHANNEL ? durationTicks / intervalTicks : 1;
    }

    private static DataResult<SpellCast> validate(SpellCast cast) {
        return switch (cast.type) {
            case INSTANT -> cast.durationTicks == 0 && cast.intervalTicks == 0 && !cast.castableWhileMoving
                    && !cast.lockOn
                    ? DataResult.success(cast)
                    : DataResult.error(() -> "An instant cast has no duration, interval, movement or lock-on: " + cast);
            case CAST_TIME -> cast.durationTicks > 0 && cast.intervalTicks == 0
                    ? DataResult.success(cast)
                    : DataResult.error(() -> "A cast time lasts a tick or more and has no interval: " + cast);
            case CHANNEL -> cast.intervalTicks > 0 && cast.durationTicks > 0
                    && cast.durationTicks % cast.intervalTicks == 0
                    ? DataResult.success(cast)
                    : DataResult.error(() -> "A channel lasts a whole number of intervals of a tick or more: " + cast);
        };
    }

    /** The cast types. */
    public enum Type implements StringRepresentable {
        /** The effects run when the spell is cast. */
        INSTANT("instant"),
        /** The effects run when the cast time is over. */
        CAST_TIME("cast_time"),
        /** The effects run at every interval of the channel. */
        CHANNEL("channel");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
