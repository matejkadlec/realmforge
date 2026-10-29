package io.github.matejkadlec.realmforge.stats;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The role a class plays (architecture 8.3, 10.1), which decides the role-bound bonuses of stat rules its players get:
 * Dexterity gives melee and ranged classes attack speed and casters cooldown reduction, for instance. A class names
 * its role in {@code content/classes/}; assassins count as ranged. The pipeline's {@code ROLES} has the same names.
 */
public enum ClassRole implements StringRepresentable {
    MELEE("melee"),
    CASTER("caster"),
    RANGED("ranged");

    public static final Codec<ClassRole> CODEC = StringRepresentable.fromEnum(ClassRole::values);

    private final String name;

    ClassRole(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
