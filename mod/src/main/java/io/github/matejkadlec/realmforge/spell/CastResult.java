package io.github.matejkadlec.realmforge.spell;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a cast came to (architecture 11.2): cast at a target, begun at a target (a cast time or a channel, whose effects
 * come later, {@link Casting}), or refused with a reason.
 *
 * @param failure why the cast was refused; empty when it was cast or begun
 * @param target what the spell's effects ran on, or will run on; empty when it was refused
 * @param begun whether a cast time or channel began, so the effects have not run yet
 */
public record CastResult(Optional<CastFailure> failure, Optional<SpellTarget> target, boolean begun) {
    public CastResult {
        if (failure.isPresent() == target.isPresent()) {
            throw new IllegalArgumentException("A cast either fails or has a target: " + failure + ", " + target);
        }
        if (begun && failure.isPresent()) {
            throw new IllegalArgumentException("A refused cast never begins: " + failure);
        }
    }

    /** An instant spell, whose effects ran on {@code target}. */
    public static CastResult cast(SpellTarget target) {
        return new CastResult(Optional.empty(), Optional.of(Objects.requireNonNull(target, "target")), false);
    }

    /** A cast time or channel that began at {@code target}; its effects run later. */
    public static CastResult begun(SpellTarget target) {
        return new CastResult(Optional.empty(), Optional.of(Objects.requireNonNull(target, "target")), true);
    }

    public static CastResult refused(CastFailure failure) {
        return new CastResult(Optional.of(failure), Optional.empty(), false);
    }

    /** Whether the spell was cast or its cast began. */
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
