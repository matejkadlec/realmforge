package io.github.matejkadlec.realmforge.spell;

import net.minecraft.util.Mth;

/**
 * The spell engine's numbers, without world access, so JUnit covers them (architecture 4.3, 11):
 * <ul>
 *   <li><b>Per spell level:</b> an amount at level 1 grows by its {@code per_level} for every level above it (research
 *   SP-9).</li>
 *   <li><b>Scaling</b> (system C, Matěj, 2026-10-30, RLM-534): each amount adds its {@link Coefficient}, a share of
 *   the caster's power growing per rank, Attack Damage for a physical ability and Spell Power (points) for a magic
 *   spell. The damage pipeline then multiplies a hit by 1 + the school's power + passives (architecture 9.1); a magic
 *   spell's heals get × (1 + the school's power) here.</li>
 *   <li><b>Cooldowns:</b> Cooldown Reduction shortens a spell's own cooldown, never the global cooldown.</li>
 *   <li><b>Cast times:</b> Cast Time Reduction shortens a cast time, and a channel's interval, so the channel keeps its
 *   number of ticks.</li>
 *   <li><b>Resource pools:</b> every second a pool regains its regeneration, within 0 and its maximum.</li>
 * </ul>
 */
public final class SpellScaling {
    private SpellScaling() {
    }

    /** {@code amount} at spell level {@code level}: amount + perLevel × (level − 1); level 1 or more. */
    public static double atLevel(double amount, double perLevel, int level) {
        if (level < 1) {
            throw new IllegalArgumentException("Spell levels start at 1: " + level);
        }
        return amount + perLevel * (level - 1);
    }

    /**
     * An effect's base amount for its caster: the amount at the spell level plus its {@code coefficient} of the
     * caster's {@code attackDamage} and {@code spellPower} at that level, (Base(rank) + Coefficient(rank) × Power) of
     * system C. Never negative.
     */
    public static double base(double amount, double perLevel, int level, Coefficient coefficient,
            double attackDamage, double spellPower) {
        return Math.max(0, atLevel(amount, perLevel, level) + coefficient.addedAt(level, attackDamage, spellPower));
    }

    /**
     * A heal before its crit: {@code base} × (1 + the school's power) for a spell that scales with spell power,
     * {@code base} alone for one that scales with attack damage. The caster's power is already in the base (its
     * {@link Coefficient}). Never negative.
     */
    public static double heal(double base, Scaling scaling, double schoolPower) {
        double factor = scaling == Scaling.SPELL_POWER ? 1 + schoolPower : 1;
        return Math.max(0, base * Math.max(0, factor));
    }

    /**
     * A spell's cooldown after Cooldown Reduction, in whole ticks: {@code ticks} × (1 − reduction), rounded to the
     * nearest tick. The reduction counts between 0 and 1; its stat caps it at 75 % ({@code content/stats/}).
     */
    public static int cooldownTicks(int ticks, double cooldownReduction) {
        if (ticks <= 0) {
            return 0;
        }
        return (int) Math.round(ticks * (1 - Mth.clamp(cooldownReduction, 0, 1)));
    }

    /**
     * A cast time or a channel's interval after Cast Time Reduction, in whole ticks: {@code ticks} × (1 − reduction),
     * rounded to the nearest tick and at least one. The reduction counts between 0 and 1; its stat caps it at 75 %
     * ({@code content/stats/}). A channel's duration is its shortened interval times its tick count.
     */
    public static int castTicks(int ticks, double castTimeReduction) {
        if (ticks < 1) {
            throw new IllegalArgumentException("A cast time lasts a tick or more: " + ticks);
        }
        return Math.max(1, (int) Math.round(ticks * (1 - Mth.clamp(castTimeReduction, 0, 1))));
    }

    /**
     * A resource pool one second later: {@code current} + {@code perSecond}, kept between 0 and {@code maximum}, so a
     * pool above a lowered maximum drops to it. A maximum of 0 or less leaves the pool as it is: the holder's stats
     * may not be applied yet (a respawned player before its first recompute, architecture 8.2).
     */
    public static double regenerate(double current, double perSecond, double maximum) {
        if (maximum <= 0) {
            return current;
        }
        return Mth.clamp(current + perSecond, 0, maximum);
    }
}
