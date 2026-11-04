package io.github.matejkadlec.realmforge.spell;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a cast came to (architecture 11.2): cast at a target, begun at a target (a cast time or a channel, whose effects
 * come later, {@link Casting}), queued to follow the cast in progress (RLM-517: it had less than the config's quarter of
 * a second left), or refused with a reason.
 *
 * @param failure why the cast was refused; empty when it was cast, begun or queued
 * @param target what the spell's effects ran on, or will run on, as aimed now; empty when it was refused
 * @param begun whether a cast time or channel began, so the effects have not run yet
 * @param queued whether the spell waits for the cast in progress to end, and is cast then ({@link Casting})
 */
public record CastResult(Optional<CastFailure> failure, Optional<SpellTarget> target, boolean begun,
        boolean queued) {
    public CastResult {
        if (failure.isPresent() == target.isPresent()) {
            throw new IllegalArgumentException("A cast either fails or has a target: " + failure + ", " + target);
        }
        if ((begun || queued) && failure.isPresent()) {
            throw new IllegalArgumentException("A refused cast never begins: " + failure);
        }
        if (begun && queued) {
            throw new IllegalArgumentException("A queued cast has not begun yet: " + target);
        }
    }

    /** A result that was not queued. */
    public CastResult(Optional<CastFailure> failure, Optional<SpellTarget> target, boolean begun) {
        this(failure, target, begun, false);
    }

    /** An instant spell, whose effects ran on {@code target}. */
    public static CastResult cast(SpellTarget target) {
        return new CastResult(Optional.empty(), Optional.of(Objects.requireNonNull(target, "target")), false);
    }

    /** A cast time or channel that began at {@code target}; its effects run later. */
    public static CastResult begun(SpellTarget target) {
        return new CastResult(Optional.empty(), Optional.of(Objects.requireNonNull(target, "target")), true);
    }

    /**
     * A cast that waits for the cast in progress to end and is cast then (RLM-517), aimed at {@code target} for now; its
     * checks run again when it goes.
     */
    public static CastResult queued(SpellTarget target) {
        return new CastResult(Optional.empty(), Optional.of(Objects.requireNonNull(target, "target")), false, true);
    }

    public static CastResult refused(CastFailure failure) {
        return new CastResult(Optional.of(failure), Optional.empty(), false);
    }

    /** Whether the spell was cast, its cast began, or it was queued to follow the cast in progress. */
    public boolean success() {
        return failure.isEmpty();
    }

    /** The entity the cast acts on, if its target is one. */
    public Optional<LivingEntity> entity() {
        return target.flatMap(SpellTarget::entity);
    }

    /** The reason the cast was refused, or null when it was cast. */
    @Nullable
    public CastFailure failureOrNull() {
        return failure.orElse(null);
    }
}
