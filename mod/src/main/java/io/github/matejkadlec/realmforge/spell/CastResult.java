package io.github.matejkadlec.realmforge.spell;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a cast came to (architecture 11.2): cast at a target, or refused with a reason.
 *
 * @param failure why the cast was refused; empty when it was cast
 * @param target the entity the spell's effects ran on; empty when it was refused
 */
public record CastResult(Optional<CastFailure> failure, Optional<LivingEntity> target) {
    public CastResult {
        if (failure.isPresent() == target.isPresent()) {
            throw new IllegalArgumentException("A cast either fails or has a target: " + failure + ", " + target);
        }
    }

    public static CastResult cast(LivingEntity target) {
        return new CastResult(Optional.empty(), Optional.of(Objects.requireNonNull(target, "target")));
    }

    public static CastResult refused(CastFailure failure) {
        return new CastResult(Optional.of(failure), Optional.empty());
    }

    /** Whether the spell was cast. */
    public boolean success() {
        return failure.isEmpty();
    }

    /** The reason the cast was refused, or null when it was cast. */
    @Nullable
    public CastFailure failureOrNull() {
        return failure.orElse(null);
    }
}
