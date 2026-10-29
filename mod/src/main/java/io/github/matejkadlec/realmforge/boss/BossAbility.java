package io.github.matejkadlec.realmforge.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * A spell a boss casts while its phase lasts (architecture 14.3), through the spell engine, whenever the spell is
 * ready and its target in reach.
 *
 * @param spell the spell
 * @param cooldownTicks the ability's own pause between two casts, on top of the spell's cooldown; 0 for the spell's
 *                      cooldown alone
 */
public record BossAbility(ResourceKey<Spell> spell, int cooldownTicks) {
    public static final Codec<BossAbility> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceKey.codec(SpellRegistries.SPELL).fieldOf("spell").forGetter(BossAbility::spell),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("cooldown_ticks", 0)
                            .forGetter(BossAbility::cooldownTicks))
            .apply(instance, BossAbility::new));
}
