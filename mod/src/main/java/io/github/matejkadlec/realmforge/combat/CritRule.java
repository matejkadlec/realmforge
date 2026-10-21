package io.github.matejkadlec.realmforge.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * How a school hit or a heal crits (Matěj, 2026-10-15; architecture 9.1, 11.1): every spell can crit, heals too, on
 * the caster's crit chance plus a bonus, for 1 + the caster's crit damage unless the spell sets its own multiplier.
 * Crit damage is shared with melee, so a crit damage bonus raises spell crits as well. A school hit without a rule of
 * its own ({@code /damage}, a trap) crits by {@link #DEFAULT}.
 *
 * @param canCrit whether the hit or heal can crit at all
 * @param multiplier a crit's own multiplier (2 doubles), instead of 1 + the caster's crit damage
 * @param bonusChance crit chance added to the caster's own, a share
 */
public record CritRule(boolean canCrit, Optional<Double> multiplier, double bonusChance) {
    /** Crits on the caster's crit chance, for 1 + the caster's crit damage: 150 % with the base 50 %. */
    public static final CritRule DEFAULT = new CritRule(true, Optional.empty(), 0);
    /** Never crits: the ticks of damage and healing over time (architecture 11.4). */
    public static final CritRule NONE = new CritRule(false, Optional.empty(), 0);

    public static final Codec<CritRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("can_crit", true).forGetter(CritRule::canCrit),
                    Codec.doubleRange(1, Double.MAX_VALUE).optionalFieldOf("multiplier")
                            .forGetter(CritRule::multiplier),
                    Codec.doubleRange(0, 1).optionalFieldOf("bonus_chance", 0.0).forGetter(CritRule::bonusChance))
            .apply(instance, CritRule::new));

    public CritRule {
        if (multiplier.isPresent() && !(multiplier.get() >= 1)) {
            throw new IllegalArgumentException("A crit multiplies by 1 or more: " + multiplier.get());
        }
        if (!(bonusChance >= 0 && bonusChance <= 1)) {
            throw new IllegalArgumentException("A bonus crit chance is a share from 0 to 1: " + bonusChance);
        }
    }

    /** The chance to crit for a caster with {@code critChance}; 0 when the rule cannot crit. */
    public double chance(double critChance) {
        return canCrit ? critChance + bonusChance : 0;
    }

    /** What a crit multiplies by for a caster with {@code critDamage}: the rule's multiplier, or 1 + crit damage. */
    public double multiplier(double critDamage) {
        return multiplier.orElseGet(() -> DamageCalculator.critMultiplier(critDamage));
    }
}
