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
 *   <li><b>Crit:</b> a roll against the attacker's crit chance; a crit multiplies the damage by 1 + crit damage. A
 *   spell's hit or heal crits by its {@link CritRule}: it may not crit at all, add to the chance, or bring its own
 *   multiplier (Matěj, 2026-10-15). A chance above 100 % always crits, and what lies above 100 % adds to the
 *   multiplier 1:1, 1 % of chance for 1 % of crit damage (crit overflow, RLM-392; C4).</li>
 *   <li><b>Dodge:</b> a roll against the defender's dodge chance; a dodged hit deals nothing.</li>
 *   <li><b>School hit:</b> damage × (1 + spell power, for magic only, + the school's power), then the crit roll, then
 *   × (1 − the defender's resistance): the resistance to the school, plus spell resistance for a magic school, at
 *   most 75 % together ({@link #RESISTANCE_CAP}), less the hit's spell penetration share of it
 *   ({@link #penetrated}; C4).</li>
 *   <li><b>Armor:</b> vanilla's formula ({@code CombatRules#getDamageAfterAbsorb}; Matěj, 2026-10-16), on the armor
 *   the attacker's armor pierce leaves (the flat points first, then the armor shred share of what is left). With D the
 *   hit's damage, effective armor = clamp(armor − D / (2 + toughness / 4), armor / 5, 20) blocks effective / 25 of
 *   it: armor counts most against small hits, toughness against big ones, and nothing blocks more than 80 %.
 *   Toughness is never pierced.</li>
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
    /** Vanilla's armor formula: the most effective armor counts for, in points ({@code CombatRules.MAX_ARMOR}). */
    public static final double MAX_EFFECTIVE_ARMOR = 20;
    /** Vanilla's armor formula: an effective armor point blocks 1/25 of a hit ({@code ARMOR_PROTECTION_DIVIDER}). */
    public static final double ARMOR_DIVIDER = 25;
    /** Vanilla's armor formula: the least effective armor, as a share of the armor ({@code MIN_ARMOR_RATIO}). */
    private static final double MIN_ARMOR_RATIO = 0.2;

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

    /** A crit roll's outcome: whether it crit, and what the hit or heal is multiplied by (1 without a crit). */
    public record Crit(boolean crit, double multiplier) {
        /** No crit: × 1. */
        public static final Crit NONE = new Crit(false, 1);
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
     * The damage multiplier of a critical hit rolled on {@code critChance}: 1 + crit damage + the chance above 100 %
     * (crit overflow, RLM-392: 1 % of chance for 1 % of crit damage).
     */
    public static double critMultiplier(double critDamage, double critChance) {
        return critMultiplier(critDamage) + critOverflow(critChance);
    }

    /** The crit chance above 100 %, which a crit adds to its multiplier; 0 at 100 % or less. */
    public static double critOverflow(double critChance) {
        return Math.max(0, critChance - 1);
    }

    /**
     * A crit roll by {@code rule} for a caster with {@code critChance} and {@code critDamage}: a rule that cannot crit
     * never rolls; otherwise the chance is the caster's plus the rule's bonus, and a crit multiplies by the rule's own
     * multiplier or 1 + crit damage, plus the chance above 100 % (crit overflow).
     */
    public static Crit crit(CritRule rule, double critChance, double critDamage, RandomSource random) {
        double chance = rule.chance(critChance);
        return rule.canCrit() && crits(chance, random)
                ? new Crit(true, rule.multiplier(critDamage) + critOverflow(chance))
                : Crit.NONE;
    }

    /**
     * A school hit: {@code damage} × (1 + spell power, for a magic school only, + the school's power), rolled for a
     * crit by {@link CritRule#DEFAULT}, then reduced by the defender's resistance to the school. Never negative.
     */
    public static SchoolHit schoolHit(double damage, boolean magic, Attacker attacker, double resistance,
            RandomSource random) {
        return schoolHit(damage, magic, attacker, resistance, CritRule.DEFAULT, random);
    }

    /**
     * A school hit as {@link #schoolHit(double, boolean, Attacker, double, RandomSource)}, rolled for a crit by
     * {@code rule}: a spell's crit data.
     */
    public static SchoolHit schoolHit(double damage, boolean magic, Attacker attacker, double resistance,
            CritRule rule, RandomSource random) {
        double bonus = (magic ? attacker.spellPower() : 0) + attacker.schoolPower();
        Crit crit = crit(rule, attacker.critChance(), attacker.critDamage(), random);
        double dealt = damage * Math.max(0, 1 + bonus) * crit.multiplier();
        return new SchoolHit(afterResistance(dealt, resistance), crit.crit());
    }

    /**
     * The defender's resistance to a school hit: its resistance to the school, plus its spell resistance when the
     * school is magic, at most {@link #RESISTANCE_CAP}. A negative value counts as 0.
     */
    public static double resistance(double schoolResistance, double spellResistance, boolean magic) {
        double sum = Math.max(0, schoolResistance) + (magic ? Math.max(0, spellResistance) : 0);
        return Math.min(RESISTANCE_CAP, sum);
    }

    /**
     * The resistance a hit with spell penetration meets: {@code resistance} less the {@code penetration} share of it
     * (25 % penetration leaves three quarters of a 40 % resistance, 30 %). Shares outside 0 to 1 are kept there.
     */
    public static double penetrated(double resistance, double penetration) {
        return Math.max(0, resistance) * (1 - Mth.clamp(penetration, 0, 1));
    }

    /** Damage after a resistance: × (1 − resistance), the resistance kept between 0 and 1. */
    public static double afterResistance(double damage, double resistance) {
        return damage * (1 - Mth.clamp(resistance, 0, 1));
    }

    /**
     * The share of a {@code damage} hit that {@code armor} and {@code toughness} block, by vanilla's formula
     * ({@code CombatRules#getDamageAfterAbsorb}): effective armor = clamp(armor − damage / (2 + toughness / 4),
     * armor / 5, {@value #MAX_EFFECTIVE_ARMOR}), and each effective point blocks 1 / {@value #ARMOR_DIVIDER} of the
     * hit. Negative armor and toughness count as 0. Vanilla's armor-effectiveness enchantments (Breach) adjust the
     * share afterwards, in {@link DamagePipeline}.
     */
    public static double armorReduction(double damage, double armor, double toughness) {
        double points = Math.max(0, armor);
        double effective = Mth.clamp(points - damage / (2 + Math.max(0, toughness) / 4), points * MIN_ARMOR_RATIO,
                MAX_EFFECTIVE_ARMOR);
        return effective / ARMOR_DIVIDER;
    }

    /**
     * The armor a hit meets after the attacker's armor pierce: armor less the flat {@code pierce}, then less the
     * {@code shred} share of what is left; never below 0. Negative values count as 0, and a share above 1 as 1.
     * Armor toughness is never pierced.
     */
    public static double piercedArmor(double armor, double pierce, double shred) {
        return Math.max(0, Math.max(0, armor) - Math.max(0, pierce)) * (1 - Mth.clamp(shred, 0, 1));
    }

    /**
     * The share of a {@code damage} hit armor blocks against an attacker with armor pierce and shred:
     * {@link #armorReduction(double, double, double)} of the {@link #piercedArmor} the hit meets, with the defender's
     * whole toughness.
     */
    public static double armorReduction(double damage, double armor, double toughness, double pierce, double shred) {
        return armorReduction(damage, piercedArmor(armor, pierce, shred), toughness);
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
