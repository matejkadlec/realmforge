package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How a holder of rage gains and loses it (architecture 11.5): a class that uses rage sets these numbers in its data
 * ({@code rage} in {@code content/classes/}), and {@link Rage} applies them.
 *
 * @param perHitDealt rage for a full-strength melee hit dealt; a weaker swing gives its share
 * @param perMaxHealthTaken rage for taking damage equal to the whole max health from an attacker; a hit for a tenth of
 *        it gives a tenth
 * @param decayPerSecond rage lost every second out of combat
 * @param decayAfterTicks how long after the last hit dealt or taken the holder is out of combat
 */
public record RageRules(double perHitDealt, double perMaxHealthTaken, double decayPerSecond, int decayAfterTicks) {
    public static final Codec<RageRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.doubleRange(0, 100).fieldOf("per_hit_dealt").forGetter(RageRules::perHitDealt),
                    Codec.doubleRange(0, 1000).fieldOf("per_max_health_taken")
                            .forGetter(RageRules::perMaxHealthTaken),
                    Codec.doubleRange(0, 100).fieldOf("decay_per_second").forGetter(RageRules::decayPerSecond),
                    Codec.intRange(1, 1200).fieldOf("decay_after_ticks").forGetter(RageRules::decayAfterTicks))
            .apply(instance, RageRules::new));

    /** The rage a melee hit dealt at {@code strength} (vanilla's attack strength, 0 to 1) gives. */
    public double forHitDealt(double strength) {
        return perHitDealt * Math.clamp(strength, 0, 1);
    }

    /** The rage taking {@code damage} gives a holder with {@code maxHealth}; nothing without health to lose. */
    public double forDamageTaken(double damage, double maxHealth) {
        return maxHealth > 0 && damage > 0 ? perMaxHealthTaken * damage / maxHealth : 0;
    }

    /** What the pool regains every second: nothing in combat, minus the decay out of it. */
    public double regeneration(boolean inCombat) {
        return inCombat ? 0 : -decayPerSecond;
    }
}
