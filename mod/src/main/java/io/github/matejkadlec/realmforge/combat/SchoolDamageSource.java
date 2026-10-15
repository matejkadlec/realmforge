package io.github.matejkadlec.realmforge.combat;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * A school hit that brings its own crit rule (architecture 9.1): the spell engine's damage, so a spell's crit data
 * (Matěj, 2026-10-15) reaches the damage pipeline's crit roll. {@code combat} knows nothing of spells: the rule is all
 * this source carries. Built by {@link SpellSchools#damage(net.minecraft.core.RegistryAccess,
 * net.minecraft.resources.ResourceKey, Entity, Entity, CritRule)}; any other source of a school's damage type crits by
 * {@link CritRule#DEFAULT}.
 */
public class SchoolDamageSource extends DamageSource {
    private final CritRule crit;

    public SchoolDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity,
            CritRule crit) {
        super(type, directEntity, causingEntity);
        this.crit = Objects.requireNonNull(crit, "crit");
    }

    /** How the hit crits. */
    public CritRule crit() {
        return crit;
    }

    /** The crit rule of any hit: a school damage source's own, {@link CritRule#DEFAULT} for every other. */
    public static CritRule critOf(DamageSource source) {
        return source instanceof SchoolDamageSource school ? school.crit() : CritRule.DEFAULT;
    }
}
