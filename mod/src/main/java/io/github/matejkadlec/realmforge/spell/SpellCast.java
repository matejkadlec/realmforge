package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.Identifier;
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
 * @param castableWhileMoving whether the caster moves at full speed during a cast-time or channelled spell;
 *                            otherwise the cast halves its speed and stops its sprint (ADR 0039, ADR 0047,
 *                            ADR 0057)
 * @param lockOn              whether a cast-time or channelled spell keeps its target: range and line of sight are not
 *                            checked again before its effects run, only that the target lives in the caster's
 *                            dimension
 * @param keepSight           whether the caster must keep its target entity in sight for the whole cast: losing sight
 *                            ends the cast at once ({@link Casting})
 * @param keepRange           for this share of the cast (more than 0, at most 1) the target entity must stay in range,
 *                            or the cast ends at once; after it, range no longer matters, not even before the effects.
 *                            Empty: range is checked only before the effects
 * @param sound               the sound event the caster makes when the cast begins, if any
 * @param formsProjectile     whether the spell's projectile (its first effect) forms in front of the caster during a
 *                            cast time, growing to its size, and flies from there ({@link SpellVisuals})
 * @param animation           how a custom mob or boss moves its arms while a cast-time or channelled spell runs (its
 *                            model's animation); players' casts ignore it
 */
public record SpellCast(Type type, int durationTicks, int intervalTicks, boolean castableWhileMoving,
        boolean lockOn, boolean keepSight, Optional<Double> keepRange, Optional<Identifier> sound,
        boolean formsProjectile, Animation animation) {
    public static final SpellCast INSTANT = new SpellCast(Type.INSTANT, 0, 0, false, false, false, Optional.empty(),
            Optional.empty(), false, Animation.RAISE);

    public static final Codec<SpellCast> CODEC = RecordCodecBuilder.<SpellCast>create(instance -> instance.group(
                    Type.CODEC.fieldOf("type").forGetter(SpellCast::type),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("duration_ticks", 0)
                            .forGetter(SpellCast::durationTicks),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("interval_ticks", 0)
                            .forGetter(SpellCast::intervalTicks),
                    Codec.BOOL.optionalFieldOf("castable_while_moving", false)
                            .forGetter(SpellCast::castableWhileMoving),
                    Codec.BOOL.optionalFieldOf("lock_on", false).forGetter(SpellCast::lockOn),
                    Codec.BOOL.optionalFieldOf("keep_sight", false).forGetter(SpellCast::keepSight),
                    Codec.doubleRange(0, 1).optionalFieldOf("keep_range").forGetter(SpellCast::keepRange),
                    Identifier.CODEC.optionalFieldOf("sound").forGetter(SpellCast::sound),
                    Codec.BOOL.optionalFieldOf("forms_projectile", false).forGetter(SpellCast::formsProjectile),
                    Animation.CODEC.optionalFieldOf("animation", Animation.RAISE).forGetter(SpellCast::animation))
            .apply(instance, SpellCast::new)).validate(SpellCast::validate);

    /** A cast time of {@code ticks} (1 or more). */
    public static SpellCast castTime(int ticks, boolean castableWhileMoving, boolean lockOn) {
        return new SpellCast(Type.CAST_TIME, ticks, 0, castableWhileMoving, lockOn, false, Optional.empty(),
                Optional.empty(), false, Animation.RAISE);
    }

    /** A channel of {@code durationTicks} that ticks every {@code intervalTicks}, which divides the duration. */
    public static SpellCast channel(int durationTicks, int intervalTicks, boolean castableWhileMoving,
            boolean lockOn) {
        return new SpellCast(Type.CHANNEL, durationTicks, intervalTicks, castableWhileMoving, lockOn, false,
                Optional.empty(), Optional.empty(), false, Animation.RAISE);
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
        if (cast.lockOn && (cast.keepSight || cast.keepRange.isPresent())) {
            return DataResult.error(() -> "A spell that locks on keeps neither sight nor range: " + cast);
        }
        if (cast.keepRange.filter(share -> share <= 0).isPresent()) {
            return DataResult.error(() -> "keep_range is a share of the cast above 0: " + cast);
        }
        if (cast.formsProjectile && cast.type != Type.CAST_TIME) {
            return DataResult.error(() -> "Only a cast time forms a projectile: " + cast);
        }
        return switch (cast.type) {
            case INSTANT -> cast.durationTicks == 0 && cast.intervalTicks == 0 && !cast.castableWhileMoving
                    && !cast.lockOn && !cast.keepSight && cast.keepRange.isEmpty() && cast.sound.isEmpty()
                    && cast.animation == Animation.RAISE
                    ? DataResult.success(cast)
                    : DataResult.error(() -> "An instant cast has no duration, interval, movement, lock-on, kept "
                            + "sight or range, sound or animation: " + cast);
            case CAST_TIME -> cast.durationTicks > 0 && cast.intervalTicks == 0
                    ? DataResult.success(cast)
                    : DataResult.error(() -> "A cast time lasts a tick or more and has no interval: " + cast);
            case CHANNEL -> cast.intervalTicks > 0 && cast.durationTicks > 0
                    && cast.durationTicks % cast.intervalTicks == 0
                    ? DataResult.success(cast)
                    : DataResult.error(() -> "A channel lasts a whole number of intervals of a tick or more: " + cast);
        };
    }

    /**
     * How a custom mob or boss moves its arms while it casts, its model's animation ({@code generate/mobs.py} writes
     * both into every custom mob's animations).
     */
    public enum Animation implements StringRepresentable {
        /** Its arms raised forward and up, the hands before its forehead, where a projectile it forms grows. */
        RAISE("raise", "attack.cast"),
        /** Its fists beating up and down, a slam every 0.75 s, the interval a quake ticks at. */
        SLAM("slam", "attack.slam");

        public static final Codec<Animation> CODEC = StringRepresentable.fromEnum(Animation::values);

        private final String serializedName;
        private final String modelAnimation;

        Animation(String serializedName, String modelAnimation) {
            this.serializedName = serializedName;
            this.modelAnimation = modelAnimation;
        }

        /** The model's animation it plays, e.g. {@code attack.cast}. */
        public String modelAnimation() {
            return modelAnimation;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
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
