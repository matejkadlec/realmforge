package io.github.matejkadlec.realmforge.stats;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * A stat that lowers the hits of one damage type tag by its share (architecture 9.1, RLM-233): Physical, Projectile,
 * Explosion and Fall Resistance, from {@code resists} in {@code content/stats/} through
 * {@link GeneratedStats#DAMAGE_RESISTANCES}. The damage pipeline applies every one whose tag the hit's damage type is in;
 * several on one hit multiply ({@code combat/DamageCalculator#damageTypeResistance}).
 *
 * @param stat the stat, a share of at most 75 %
 * @param damage the damage type tag it resists, e.g. {@code #minecraft:is_projectile}
 */
public record DamageTypeResistance(Holder<Attribute> stat, TagKey<DamageType> damage) {
}
