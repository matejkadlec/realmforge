package io.github.matejkadlec.realmforge.effect;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Damage over time that each caster's spell keeps as a stack of its own on a target (RLM-539, Bleeding; architecture
 * 11.4): the spell that applies it names the damage, fixed from the caster's stats when it is applied
 * ({@link EffectStacks}), and its ticks split it evenly. Pure: JUnit covers it.
 *
 * @param school the spell school of its ticks
 * @param intervalTicks the time between two ticks, a tick or more
 * @param ignoresArmor whether its ticks ignore the target's armor, as bleeds do in WoW
 */
public record StackedDamage(ResourceKey<SpellSchool> school, int intervalTicks, boolean ignoresArmor) {
    public StackedDamage {
        Objects.requireNonNull(school, "school");
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("Ticks are a tick or more apart: " + intervalTicks);
        }
    }

    /** Ticks of {@code school} (a full id, {@code realmforge:physical}) every {@code intervalTicks}. */
    public static StackedDamage of(String school, int intervalTicks, boolean ignoresArmor) {
        return new StackedDamage(ResourceKey.create(CombatRegistries.SPELL_SCHOOL, Identifier.parse(school)),
                intervalTicks, ignoresArmor);
    }

    /** How often a stack of {@code durationTicks} ticks: whole intervals only, at least once. */
    public int tickCount(int durationTicks) {
        return Math.max(1, durationTicks / intervalTicks);
    }

    /**
     * Whether a stack with {@code remainingTicks} left ticks now: at the end of every interval since it was applied,
     * the last time on its last tick, as {@link Periodic#ticksAt} counts an effect's remaining duration.
     */
    public boolean ticksAt(int remainingTicks) {
        return remainingTicks > 0 && remainingTicks % intervalTicks == 1 % intervalTicks;
    }
}
