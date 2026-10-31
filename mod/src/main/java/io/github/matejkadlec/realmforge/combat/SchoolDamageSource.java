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
 * (Matěj, 2026-10-15) reaches the damage pipeline's crit roll, with what the hit adds to its attacker's armor shred and
 * spell penetration ({@link HitBonus}, C4). It also carries a proc origin when a proc's spell deals it, so the proc
 * engine ignores the hit and procs never trigger procs (architecture 12). {@code combat} knows nothing of spells: the
 * rule, the bonus and the origin are all this source carries. Built by {@link SpellSchools#damage(
 * net.minecraft.core.RegistryAccess, net.minecraft.resources.ResourceKey, Entity, Entity, CritRule, HitBonus,
 * Optional)}; any other source of a school's damage type crits by {@link CritRule#DEFAULT}, adds nothing and has no proc
 * origin.
 */
public class SchoolDamageSource extends DamageSource {
    private final CritRule crit;
    private final HitBonus bonus;
    private final Optional<Identifier> procOrigin;
    private final Optional<Tick> tick;

    public SchoolDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity,
            CritRule crit, HitBonus bonus, Optional<Identifier> procOrigin) {
        this(type, directEntity, causingEntity, crit, bonus, procOrigin, Optional.empty());
    }

    /** A school hit that is {@code tick}, one tick of a damage over time, when present. */
    public SchoolDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity,
            CritRule crit, HitBonus bonus, Optional<Identifier> procOrigin, Optional<Tick> tick) {
        super(type, directEntity, causingEntity);
        this.crit = Objects.requireNonNull(crit, "crit");
        this.bonus = Objects.requireNonNull(bonus, "bonus");
        this.procOrigin = Objects.requireNonNull(procOrigin, "procOrigin");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    /** The damage over time this hit is a tick of, if it is one. */
    public Optional<Tick> tick() {
        return tick;
    }

    /** How the hit crits. */
    public CritRule crit() {
        return crit;
    }

    /** What the hit adds to its attacker's armor shred and spell penetration. */
    public HitBonus bonus() {
        return bonus;
    }

    /** The proc whose spell dealt the hit, if one did. */
    public Optional<Identifier> procOrigin() {
        return procOrigin;
    }

    /** The crit rule of any hit: a school damage source's own, {@link CritRule#DEFAULT} for every other. */
    public static CritRule critOf(DamageSource source) {
        return source instanceof SchoolDamageSource school ? school.crit() : CritRule.DEFAULT;
    }

    /** What any hit adds to its attacker's stats: a school damage source's own bonus, {@link HitBonus#NONE} for every other. */
    public static HitBonus bonusOf(DamageSource source) {
        return source instanceof SchoolDamageSource school ? school.bonus() : HitBonus.NONE;
    }

    /**
     * The damage over time any hit is a tick of, if it is one of ours with a tick (RLM-539, RLM-540): such a hit is never
     * dodged or blocked, and a passive's proc can watch it (Taste for Blood).
     */
    public static Optional<Tick> tickOf(DamageSource source) {
        return source instanceof SchoolDamageSource school ? school.tick() : Optional.empty();
    }

    /** The proc origin of any hit: a school damage source's own, empty for every other. */
    public static Optional<Identifier> procOriginOf(DamageSource source) {
        return source instanceof SchoolDamageSource school ? school.procOrigin() : Optional.empty();
    }

    /**
     * One tick of a damage over time (RLM-539): never dodged or blocked, as the hit that applied it already landed.
     *
     * @param effect the status effect that ticks, e.g. {@code realmforge:bleeding}
     * @param spell the spell that applied it, if a spell did
     * @param ignoresArmor whether the target's armor reduces nothing of it (Bleeding)
     */
    public record Tick(Identifier effect, Optional<Identifier> spell, boolean ignoresArmor) {
        public Tick {
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(spell, "spell");
        }
    }
}
