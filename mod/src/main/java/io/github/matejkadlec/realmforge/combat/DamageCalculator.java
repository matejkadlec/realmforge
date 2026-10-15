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
 *   × (1 − the defender's resistance): the resistance to the school, plus spell resistance for a magic school, at
 *   most 75 % together ({@link #RESISTANCE_CAP}).</li>
 *   <li><b>Armor:</b> with A = armor + armor toughness, less the attacker's armor pierce (the flat points first, then
 *   the percent of what is left), armor blocks A / (A + K) of a hit, at most the cap, where K and the cap come from the
 *   curves {@code realmforge:armor_halving} and {@code realmforge:armor_reduction_cap} at the attacker's level: K
 *   armor halves a hit.</li>
 *   <li><b>Lifesteal:</b> a melee or ranged hit heals the attacker by its lifesteal share of the damage it dealt.</li>
 *   <li><b>Overheal:</b> healing beyond max health becomes absorption, up to the overheal share of max health.</li>
 *   <li><b>Health regeneration:</b> heals its points every second, never beyond max health.</li>
 *   <li><b>Magic shield:</b> takes what is left of a hit after absorption hearts, up to the points in the pool.</li>
 * </ul>
 */
public final class DamageCalculator {
    /**
     * The most a school hit's resistances block together, a share: a school's resistance and spell resistance are each
     * capped at 75 % ({@code content/stats/}), and so is their sum, so nothing becomes immune without a tell (research
     * BM-18).
     */
    public static final double RESISTANCE_CAP = 0.75;

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

    /**
     * The defender's resistance to a school hit: its resistance to the school, plus its spell resistance when the
     * school is magic, at most {@link #RESISTANCE_CAP}. A negative value counts as 0.
     */
    public static double resistance(double schoolResistance, double spellResistance, boolean magic) {
        double sum = Math.max(0, schoolResistance) + (magic ? Math.max(0, spellResistance) : 0);
        return Math.min(RESISTANCE_CAP, sum);
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

    /**
     * The armor a hit meets after the attacker's armor pierce: armor + armor toughness, less the flat {@code pierce},
     * then less {@code piercePercent} of what is left; never below 0. Negative values count as 0, and a percent
     * above 1 as 1.
     */
    public static double piercedArmor(double armor, double toughness, double pierce, double piercePercent) {
        double effective = Math.max(0, armor) + Math.max(0, toughness);
        return Math.max(0, effective - Math.max(0, pierce)) * (1 - Mth.clamp(piercePercent, 0, 1));
    }

    /**
     * The share of a hit armor blocks against an attacker with armor pierce: {@link #armorReduction} of the
     * {@link #piercedArmor} the hit meets.
     */
    public static double armorReduction(double armor, double toughness, double pierce, double piercePercent,
            double halving, double cap) {
        return armorReduction(piercedArmor(armor, toughness, pierce, piercePercent), 0, halving, cap);
    }

    /** What lifesteal heals: its {@code share} of the damage a hit dealt, the share kept between 0 and 1. */
    public static double lifesteal(double dealt, double share) {
        return Math.max(0, dealt) * Mth.clamp(share, 0, 1);
    }

    /**
     * The overheal absorption after a heal of {@code heal} at {@code health}: the part of the heal beyond max health
     * adds to the {@code current} overheal absorption, up to {@code share} × max health. It never lowers
     * {@code current}, even when max health or the share dropped below it.
     */
    public static double overheal(double heal, double health, double maxHealth, double current, double share) {
        double overflow = heal - Math.max(0, maxHealth - health);
        double cap = Math.max(0, maxHealth) * Mth.clamp(share, 0, 1);
        if (overflow <= 0 || current >= cap) {
            return current;
        }
        return Math.min(cap, current + overflow);
    }

    /**
     * What health regeneration heals in one second: {@code perSecond} points, never beyond max health, so it never
     * overheals. Nothing for a negative rate or a full health bar.
     */
    public static double regeneration(double perSecond, double health, double maxHealth) {
        return Math.max(0, Math.min(perSecond, maxHealth - health));
    }

    /**
     * What a magic shield takes of a hit: the damage left after absorption hearts, up to the {@code shield} points in
     * the pool; never negative.
     */
    public static double shieldAbsorbs(double damageLeft, double shield) {
        return Math.max(0, Math.min(damageLeft, shield));
    }

    /** A roll that succeeds with the given chance; a chance of 0 or less never rolls, 1 or more always succeeds. */
    private static boolean rolls(double chance, RandomSource random) {
        return chance > 0 && random.nextDouble() < chance;
    }
}
