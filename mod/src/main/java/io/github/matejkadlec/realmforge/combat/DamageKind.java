package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * A kind of hit the character sheet's Resistances column names (RLM-235; Matěj, 2026-10-31), each with a damage type
 * that stands for it: what a player's resistance stats ({@code GeneratedStats#DAMAGE_RESISTANCES}, by the type's tags)
 * and protection enchantments ({@link EnchantmentProtection}, by the game's own rules for that type) block of such a
 * hit. Physical stands for melee hits (vanilla's mob attack), Projectile for arrows, Magic for our magic schools
 * (Arcane: magic in no tag of fire or physical hits).
 *
 * @param example the damage type a hit of this kind has
 */
public enum DamageKind {
    PHYSICAL(DamageTypes.MOB_ATTACK),
    PROJECTILE(DamageTypes.ARROW),
    EXPLOSION(DamageTypes.EXPLOSION),
    FALL(DamageTypes.FALL),
    FIRE(DamageTypes.IN_FIRE),
    MAGIC(ResourceKey.create(Registries.DAMAGE_TYPE, Realmforge.id("arcane")));

    private final ResourceKey<DamageType> example;

    DamageKind(ResourceKey<DamageType> example) {
        this.example = example;
    }

    /** The damage type a hit of this kind has. */
    public ResourceKey<DamageType> example() {
        return example;
    }
}
