package io.github.matejkadlec.realmforge.combat;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * A school hit that brings its own crit rule (architecture 9.1): the spell engine's damage, so a spell's crit data
 * (Matěj, 2026-10-15) reaches the damage pipeline's crit roll. It also carries a proc origin when a proc's spell deals
 * it, so the proc engine ignores the hit and procs never trigger procs (architecture 12). {@code combat} knows nothing
 * of spells: the rule and the origin are all this source carries. Built by {@link SpellSchools#damage(
 * net.minecraft.core.RegistryAccess, net.minecraft.resources.ResourceKey, Entity, Entity, CritRule, Optional)}; any
 * other source of a school's damage type crits by {@link CritRule#DEFAULT} and has no proc origin.
 */
public class SchoolDamageSource extends DamageSource {
    private final CritRule crit;
    private final Optional<Identifier> procOrigin;

    public SchoolDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity,
            CritRule crit, Optional<Identifier> procOrigin) {
        super(type, directEntity, causingEntity);
        this.crit = Objects.requireNonNull(crit, "crit");
        this.procOrigin = Objects.requireNonNull(procOrigin, "procOrigin");
    }

    /** How the hit crits. */
    public CritRule crit() {
        return crit;
    }

    /** The proc whose spell dealt the hit, if one did. */
    public Optional<Identifier> procOrigin() {
        return procOrigin;
    }

    /** The crit rule of any hit: a school damage source's own, {@link CritRule#DEFAULT} for every other. */
    public static CritRule critOf(DamageSource source) {
        return source instanceof SchoolDamageSource school ? school.crit() : CritRule.DEFAULT;
    }

    /** The proc origin of any hit: a school damage source's own, empty for every other. */
    public static Optional<Identifier> procOriginOf(DamageSource source) {
        return source instanceof SchoolDamageSource school ? school.procOrigin() : Optional.empty();
    }
}
