package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The share of its caster's power a damage or heal effect adds to its amount, growing with the spell's rank like the
 * amount: scaling system C (Matěj, 2026-10-30, RLM-534; architecture 11.1). An effect's base is its amount plus this
 * share of the caster's Attack Damage (a physical ability) or Spell Power (a magic spell, in points), and the damage
 * pipeline then multiplies the hit by 1 + the school's power + passives. The pipeline lets an effect name only the
 * share of its spell's own power ({@code scales_with}), so the other is 0. Shares are fractions (1 adds all of it).
 *
 * @param attackDamage the share of attack damage at spell level 1
 * @param attackDamagePerLevel added to it for every spell level above 1
 * @param spellPower the share of Spell Power at spell level 1
 * @param spellPowerPerLevel added to it for every spell level above 1
 */
public record Coefficient(double attackDamage, double attackDamagePerLevel, double spellPower,
        double spellPowerPerLevel) {
    /** No share of either power: a mob's spell, a test spell. */
    public static final Coefficient NONE = new Coefficient(0, 0, 0, 0);

    /** The fields an effect's JSON carries beside its amount; a missing share is 0. */
    public static final MapCodec<Coefficient> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("attack_damage", 0.0)
                            .forGetter(Coefficient::attackDamage),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("attack_damage_per_level", 0.0)
                            .forGetter(Coefficient::attackDamagePerLevel),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("spell_power", 0.0)
                            .forGetter(Coefficient::spellPower),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("spell_power_per_level", 0.0)
                            .forGetter(Coefficient::spellPowerPerLevel))
            .apply(instance, Coefficient::new));

    /** A share of attack damage only, the same at every level. */
    public static Coefficient attackDamage(double share) {
        return new Coefficient(share, 0, 0, 0);
    }

    /** A share of Spell Power only, growing by {@code perLevel} for every level above 1. */
    public static Coefficient spellPower(double share, double perLevel) {
        return new Coefficient(0, 0, share, perLevel);
    }

    /** The share of attack damage at spell level {@code level}. */
    public double attackDamageAt(int level) {
        return SpellScaling.atLevel(attackDamage, attackDamagePerLevel, level);
    }

    /** The share of Spell Power at spell level {@code level}. */
    public double spellPowerAt(int level) {
        return SpellScaling.atLevel(spellPower, spellPowerPerLevel, level);
    }

    /**
     * What it adds to an amount at spell level {@code level} for a caster with {@code attackDamage} and
     * {@code spellPower}: each share times its power, a negative power counting as none.
     */
    public double addedAt(int level, double attackDamage, double spellPower) {
        return attackDamageAt(level) * Math.max(0, attackDamage) + spellPowerAt(level) * Math.max(0, spellPower);
    }
}
