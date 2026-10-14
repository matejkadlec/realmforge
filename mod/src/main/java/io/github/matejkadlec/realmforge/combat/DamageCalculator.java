package io.github.matejkadlec.realmforge.combat;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Every formula of the damage pipeline (architecture 9.1), without world access, so JUnit covers them in
 * milliseconds. {@link DamagePipeline} reads the attacker's and the defender's stats and applies the results. Shares
 * are fractions (5 % is 0.05), as our percentage attributes hold them; rolls take a random source.
 *
 * <p>The formulas, kept short on purpose (research BM-20):
 * <ul>
 *   <li><b>Crit:</b> a roll against the attacker's crit chance; a crit multiplies the damage by 1 + crit damage.</li>
 *   <li><b>Dodge:</b> a roll against the defender's dodge chance; a dodged hit deals nothing.</li>
 *   <li><b>School hit:</b> damage × (1 + spell power, for magic only, + the school's power), then the crit roll, then
 *   × (1 − the defender's resistance to the school).</li>
 *   <li><b>Armor:</b> with A = armor + armor toughness, armor blocks A / (A + K) of a hit, at most the cap, where K and
 *   the cap come from the curves {@code realmforge:armor_halving} and {@code realmforge:armor_reduction_cap} at the
 *   attacker's level: K armor halves a hit.</li>
 * </ul>
 */
public final class DamageCalculator {
    private DamageCalculator() {
    }

    /** What an attacker brings to a school hit; an attribute it lacks counts as 0. */
    public record Attacker(double critChance, double critDamage, double spellPower, double schoolPower) {
        /** A hit nobody caused, or an attacker without our stats. */
        public static final Attacker NONE = new Attacker(0, 0, 0, 0);
    }

    /** A school hit's damage before armor and vanilla's reductions, and whether it was a critical hit. */
    public record SchoolHit(double damage, boolean crit) {
    }

    /** Whether a hit is dodged: a roll against the defender's dodge chance. */
    public static boolean dodges(double dodgeChance, RandomSource random) {
        return rolls(dodgeChance, random);
    }

    /** Whether a hit is a critical hit: a roll against the attacker's crit chance. */
    public static boolean crits(double critChance, RandomSource random) {
        return rolls(critChance, random);
    }

    /** The damage multiplier of a critical hit: 1 + crit damage, so the base 50 % gives 1.5. */
    public static double critMultiplier(double critDamage) {
        return 1 + Math.max(0, critDamage);
    }

    /**
     * A school hit: {@code damage} × (1 + spell power, for a magic school only, + the school's power), rolled for a
     * crit, then reduced by the defender's resistance to the school. Never negative.
     */
    public static SchoolHit schoolHit(double damage, boolean magic, Attacker attacker, double resistance,
            RandomSource random) {
        double bonus = (magic ? attacker.spellPower() : 0) + attacker.schoolPower();
        double dealt = damage * Math.max(0, 1 + bonus);
        boolean crit = crits(attacker.critChance(), random);
        if (crit) {
            dealt *= critMultiplier(attacker.critDamage());
        }
        return new SchoolHit(afterResistance(dealt, resistance), crit);
    }

    /** Damage after a resistance: × (1 − resistance), the resistance kept between 0 and 1. */
    public static double afterResistance(double damage, double resistance) {
        return damage * (1 - Mth.clamp(resistance, 0, 1));
    }

    /**
     * The share of a hit armor blocks: with A = armor + armor toughness, A / (A + halving), at most {@code cap}.
     * Without armor, or with a halving value that is not positive, armor blocks nothing.
     *
     * @param halving the armor that halves a hit at the attacker's level (curve {@code realmforge:armor_halving})
     * @param cap the most armor may block, a share (curve {@code realmforge:armor_reduction_cap})
     */
    public static double armorReduction(double armor, double toughness, double halving, double cap) {
        double effective = Math.max(0, armor) + Math.max(0, toughness);
        if (effective <= 0 || halving <= 0) {
            return 0;
        }
        return Math.min(Mth.clamp(cap, 0, 1), effective / (effective + halving));
    }

    /** A roll that succeeds with the given chance; a chance of 0 or less never rolls, 1 or more always succeeds. */
    private static boolean rolls(double chance, RandomSource random) {
        return chance > 0 && random.nextDouble() < chance;
    }
}
