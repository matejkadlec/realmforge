package io.github.matejkadlec.realmforge.effect;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Damage or healing over time (architecture 11.4): a hit or a heal of a spell school at the end of every interval
 * while a {@link StatusEffect} lasts. One tick is its amount plus its share of the caster's Spell Power (scaling system
 * C, RLM-534: a damage over time splits its spell's coefficient over its ticks), and each amplifier level adds that
 * again; the school's power raises it as it would the school's hit or heal ({@link EffectTicks}). Pure: JUnit covers
 * it.
 *
 * @param kind whether its ticks hurt or heal
 * @param school the spell school of its ticks
 * @param amount one tick's damage or healing at the first level (amplifier 0), before Spell Power
 * @param spellPower the share of the caster's Spell Power (points) one tick adds at the first level, 0 or more
 * @param intervalTicks the time between two ticks, a tick or more
 */
public record Periodic(Kind kind, ResourceKey<SpellSchool> school, double amount, double spellPower,
        int intervalTicks) {
    public Periodic {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(school, "school");
        if (!(amount > 0) || !Double.isFinite(amount) || intervalTicks < 1) {
            throw new IllegalArgumentException("A tick deals or heals more than 0, a tick or more apart: " + amount
                    + ", " + intervalTicks);
        }
        if (!(spellPower >= 0) || !Double.isFinite(spellPower)) {
            throw new IllegalArgumentException("A tick's share of Spell Power is 0 or more: " + spellPower);
        }
    }

    /** Damage of {@code school} (a full id, {@code realmforge:fire}) every {@code intervalTicks}, without Spell Power. */
    public static Periodic damage(String school, double amount, int intervalTicks) {
        return damage(school, amount, 0, intervalTicks);
    }

    /** Damage of {@code school} every {@code intervalTicks}, adding {@code spellPower} of the caster's Spell Power. */
    public static Periodic damage(String school, double amount, double spellPower, int intervalTicks) {
        return new Periodic(Kind.DAMAGE, key(school), amount, spellPower, intervalTicks);
    }

    /** Healing of {@code school} every {@code intervalTicks}, without Spell Power. */
    public static Periodic heal(String school, double amount, int intervalTicks) {
        return heal(school, amount, 0, intervalTicks);
    }

    /** Healing of {@code school} every {@code intervalTicks}, adding {@code spellPower} of the caster's Spell Power. */
    public static Periodic heal(String school, double amount, double spellPower, int intervalTicks) {
        return new Periodic(Kind.HEAL, key(school), amount, spellPower, intervalTicks);
    }

    /** One tick's amount at {@code amplifier} without a caster: the amount for each level, as vanilla multiplies. */
    public double amountAt(int amplifier) {
        return amountAt(amplifier, 0);
    }

    /**
     * One tick's amount at {@code amplifier} for a caster with {@code casterSpellPower}: (amount + the share of Spell
     * Power) for each level. A negative Spell Power counts as none.
     */
    public double amountAt(int amplifier, double casterSpellPower) {
        return (amount + spellPower * Math.max(0, casterSpellPower)) * (Math.max(0, amplifier) + 1);
    }

    /**
     * Whether an effect with {@code remainingTicks} left ticks now ({@code MobEffect#shouldApplyEffectTickThisTick}
     * gets the remaining duration, which counts down to 1 before the effect ends). It ticks at the end of every
     * interval since it was applied, the last time on its last tick: an effect of 80 ticks with an interval of 20 ticks
     * at 61, 41, 21 and 1 left, 4 times, the first a whole interval after it began.
     */
    public boolean ticksAt(int remainingTicks) {
        return remainingTicks % intervalTicks == 1 % intervalTicks;
    }

    /** How often an effect of {@code durationTicks} ticks: whole intervals only. */
    public int tickCount(int durationTicks) {
        return Math.max(0, durationTicks) / intervalTicks;
    }

    private static ResourceKey<SpellSchool> key(String school) {
        return ResourceKey.create(CombatRegistries.SPELL_SCHOOL, Identifier.parse(school));
    }

    /** Whether the ticks hurt or heal. */
    public enum Kind {
        DAMAGE,
        HEAL
    }
}
