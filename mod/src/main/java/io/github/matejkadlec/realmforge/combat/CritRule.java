package io.github.matejkadlec.realmforge.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * How a school hit or a heal crits (Matěj, 2026-10-15; architecture 9.1, 11.1): every spell can crit, heals too, on
 * the caster's crit chance plus a bonus, for 1 + the caster's crit damage unless the spell sets its own multiplier.
 * Crit damage is shared with melee, so a crit damage bonus raises spell crits as well. A school hit without a rule of
 * its own ({@code /damage}, a trap) crits by {@link #DEFAULT}. The bonus may grow with the spell's rank (C4: Reckless
 * Strike's 10 to 30 %); {@link #atLevel} gives the rule a cast of that rank rolls by.
 *
 * @param canCrit whether the hit or heal can crit at all
 * @param multiplier a crit's own multiplier (2 doubles), instead of 1 + the caster's crit damage
 * @param bonusChance crit chance added to the caster's own at spell level 1, a share
 * @param bonusChancePerLevel added to the bonus for every spell level above 1, a share
 */
public record CritRule(boolean canCrit, Optional<Double> multiplier, double bonusChance, double bonusChancePerLevel) {
    /** Crits on the caster's crit chance, for 1 + the caster's crit damage: 150 % with the base 50 %. */
    public static final CritRule DEFAULT = new CritRule(true, Optional.empty(), 0, 0);
    /** Never crits: the ticks of damage and healing over time (architecture 11.4). */
    public static final CritRule NONE = new CritRule(false, Optional.empty(), 0, 0);

    public static final Codec<CritRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("can_crit", true).forGetter(CritRule::canCrit),
                    Codec.doubleRange(1, Double.MAX_VALUE).optionalFieldOf("multiplier")
                            .forGetter(CritRule::multiplier),
                    Codec.doubleRange(0, 1).optionalFieldOf("bonus_chance", 0.0).forGetter(CritRule::bonusChance),
                    Codec.doubleRange(0, 1).optionalFieldOf("bonus_chance_per_level", 0.0)
                            .forGetter(CritRule::bonusChancePerLevel))
            .apply(instance, CritRule::new));

    public CritRule {
        if (multiplier.isPresent() && !(multiplier.get() >= 1)) {
            throw new IllegalArgumentException("A crit multiplies by 1 or more: " + multiplier.get());
        }
        if (!(bonusChance >= 0 && bonusChance <= 1)) {
            throw new IllegalArgumentException("A bonus crit chance is a share from 0 to 1: " + bonusChance);
        }
        if (!(bonusChancePerLevel >= 0 && bonusChancePerLevel <= 1)) {
            throw new IllegalArgumentException("A bonus crit chance per level is a share from 0 to 1: "
                    + bonusChancePerLevel);
        }
    }

    /** A rule of the spell's own multiplier and bonus chance, the bonus the same at every level. */
    public CritRule(boolean canCrit, Optional<Double> multiplier, double bonusChance) {
        this(canCrit, multiplier, bonusChance, 0);
    }

    /**
     * The rule a cast at spell level {@code level} rolls by: the bonus chance with its growth for every level above 1,
     * at most 1 (the pipeline keeps it there at the spell's top rank), and no growth left.
     */
    public CritRule atLevel(int level) {
        if (bonusChancePerLevel == 0) {
            return this;
        }
        return new CritRule(canCrit, multiplier, Math.min(1, bonusChance + bonusChancePerLevel * (level - 1)), 0);
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
