package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * What a spell's numbers grow with (Matěj, 2026-10-15; architecture 11.1). Off-class stats do nothing for a class's
 * abilities: spell power raises magic, attack damage raises physical abilities, and the pipeline refuses a spell whose
 * school's kind does not fit (later: summon damage).
 */
public enum Scaling implements StringRepresentable {
    /**
     * Magic: the damage pipeline multiplies the school's hits by 1 + spell power + the school's power; heals get the
     * same factor from the spell engine.
     */
    SPELL_POWER("spell_power"),
    /** Physical abilities: each effect adds its share of the caster's attack damage. */
    ATTACK_DAMAGE("attack_damage");

    public static final Codec<Scaling> CODEC = StringRepresentable.fromEnum(Scaling::values);

    private final String serializedName;

    Scaling(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
