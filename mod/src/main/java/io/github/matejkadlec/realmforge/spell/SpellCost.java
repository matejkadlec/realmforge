package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * What a cast costs (architecture 11.1): an amount of a resource, growing per spell level like the effects
 * (research SP-9: MnS's Fireball costs more at higher ranks).
 *
 * @param resource the resource the cast takes
 * @param amount the amount at spell level 1
 * @param perLevel added for every spell level above 1
 */
public record SpellCost(Holder<ResourceType> resource, double amount, double perLevel) {
    public static final Codec<SpellCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceType.CODEC.fieldOf("resource").forGetter(SpellCost::resource),
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(SpellCost::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(SpellCost::perLevel))
            .apply(instance, SpellCost::new));

    /** The amount at spell level {@code level}. */
    public double amountAt(int level) {
        return SpellScaling.atLevel(amount, perLevel, level);
    }
}
