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
 * @param animationTicks      an instant spell that plays out over its animation (RLM-399, a Warrior's swing): how
 *                            long, 0 for one that lands at once
 * @param hitAt               when an animated instant spell's effects run: a share of its animation, the middle by
 *                            default
 * @param castTimeReduction   what Cast Time Reduction does to a channel (RLM-572): shortens it, so it keeps its ticks,
 *                            or adds ticks to it, so it keeps its duration; {@link Haste#SHORTENS} for every other cast
 */
public record SpellCast(Type type, int durationTicks, int intervalTicks, boolean castableWhileMoving,
        boolean lockOn, boolean keepSight, Optional<Double> keepRange, Optional<Identifier> sound,
        boolean formsProjectile, Animation animation, int animationTicks, double hitAt, Haste castTimeReduction) {
    /** When an animated instant spell lands by default: the middle of its swing (Matěj, 2026-10-24). */
    public static final double DEFAULT_HIT_AT = 0.5;
    public static final SpellCast INSTANT = new SpellCast(Type.INSTANT, 0, 0, false, false, false, Optional.empty(),
            Optional.empty(), false, Animation.RAISE, 0, DEFAULT_HIT_AT, Haste.SHORTENS);

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
                    Animation.CODEC.optionalFieldOf("animation", Animation.RAISE).forGetter(SpellCast::animation),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("animation_ticks", 0)
                            .forGetter(SpellCast::animationTicks),
                    Codec.doubleRange(0, 1).optionalFieldOf("hit_at", DEFAULT_HIT_AT).forGetter(SpellCast::hitAt),
                    Haste.CODEC.optionalFieldOf("cast_time_reduction", Haste.SHORTENS)
                            .forGetter(SpellCast::castTimeReduction))
            .apply(instance, SpellCast::new)).validate(SpellCast::validate);

    /** A cast time of {@code ticks} (1 or more). */
    public static SpellCast castTime(int ticks, boolean castableWhileMoving, boolean lockOn) {
        return new SpellCast(Type.CAST_TIME, ticks, 0, castableWhileMoving, lockOn, false, Optional.empty(),
                Optional.empty(), false, Animation.RAISE, 0, DEFAULT_HIT_AT, Haste.SHORTENS);
    }

    /** An instant spell that plays out over {@code animationTicks}, its effects at the {@code hitAt} share of it. */
    public static SpellCast swing(int animationTicks, double hitAt) {
        return new SpellCast(Type.INSTANT, 0, 0, false, false, false, Optional.empty(), Optional.empty(), false,
                Animation.RAISE, animationTicks, hitAt, Haste.SHORTENS);
    }

    /** A channel of {@code durationTicks} that ticks every {@code intervalTicks}, which divides the duration. */
    public static SpellCast channel(int durationTicks, int intervalTicks, boolean castableWhileMoving,
            boolean lockOn) {
        return channel(durationTicks, intervalTicks, castableWhileMoving, lockOn, Haste.SHORTENS);
    }

    /** A channel as above whose ticks Cast Time Reduction treats as {@code castTimeReduction} says (RLM-572). */
    public static SpellCast channel(int durationTicks, int intervalTicks, boolean castableWhileMoving,
            boolean lockOn, Haste castTimeReduction) {
        return new SpellCast(Type.CHANNEL, durationTicks, intervalTicks, castableWhileMoving, lockOn, false,
                Optional.empty(), Optional.empty(), false, Animation.RAISE, 0, DEFAULT_HIT_AT, castTimeReduction);
    }

    /** Whether the effects run at once, without a current cast. */
    public boolean instant() {
        return type == Type.INSTANT && animationTicks == 0;
    }

    /** Whether it is an instant spell that plays out over its animation (RLM-399): a swing. */
    public boolean swing() {
        return type == Type.INSTANT && animationTicks > 0;
    }

    /** The tick of a swing's animation its effects run on: its hit moment, at least 1 and at most the animation's last. */
    public int hitTick() {
        return Math.clamp(Math.round(animationTicks * hitAt), 1, Math.max(1, animationTicks));
    }

    /** How often a channel runs its effects: its duration over its interval; 1 for the other types. */
    public int tickCount() {
        return type == Type.CHANNEL ? durationTicks / intervalTicks : 1;
    }

    /**
     * How often a channel runs its effects for a caster with {@code castTimeReduction} (0 to 1): its {@link #tickCount}
     * when the reduction shortens it, round(tickCount × (1 + reduction)), halves up, when it adds ticks (RLM-572:
     * Blizzard's 8 waves, 10 with 25 % and with 20 %); 1 for the other types.
     */
    public int tickCountFor(double castTimeReduction) {
        return type == Type.CHANNEL && this.castTimeReduction == Haste.ADDS_TICKS
                ? SpellScaling.moreTicks(tickCount(), castTimeReduction) : tickCount();
    }

    /**
     * How long a channel lasts for a caster with {@code castTimeReduction}, in ticks: its interval shortened ({@link
     * SpellScaling#castTicks}) times its tick count, or its whole duration when the reduction adds ticks instead
     * (RLM-572). A cast time's shortened duration; 0 for an instant spell.
     */
    public int durationFor(double castTimeReduction) {
        return switch (type) {
            case INSTANT -> 0;
            case CAST_TIME -> SpellScaling.castTicks(durationTicks, castTimeReduction);
            case CHANNEL -> this.castTimeReduction == Haste.ADDS_TICKS ? durationTicks
                    : SpellScaling.castTicks(intervalTicks, castTimeReduction) * tickCount();
        };
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
        if (cast.castTimeReduction == Haste.ADDS_TICKS && cast.type != Type.CHANNEL) {
            return DataResult.error(() -> "Only a channel gets more ticks from Cast Time Reduction: " + cast);
        }
        if (cast.animationTicks > 0 && cast.type != Type.INSTANT || !(cast.hitAt > 0)) {
            return DataResult.error(() -> "Only an instant spell plays out over an animation, its hit moment within "
                    + "it: " + cast);
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
     * What Cast Time Reduction does to a channel (RLM-572). Every other cast ignores it: a cast time is always
     * shortened.
     */
    public enum Haste implements StringRepresentable {
        /** Its interval, and so its duration, are shortened: it keeps its number of ticks (Arcane Missiles). */
        SHORTENS("shortens"),
        /**
         * Its duration stays and it runs more ticks in it, round(ticks × (1 + reduction)), halves up, spread evenly
         * (Matěj, 2026-11-01: Blizzard's waves).
         */
        ADDS_TICKS("adds_ticks");

        public static final Codec<Haste> CODEC = StringRepresentable.fromEnum(Haste::values);

        private final String serializedName;

        Haste(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
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
