package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * An attribute modifier a race or class gives its player (architecture 8.2, 10.1): {@code amount} at level 1, growing
 * by {@code perLevel} for every level above it. Amounts are in game units (fractions for percentages).
 *
 * @param attribute the attribute it modifies, usually a primary stat
 * @param operation vanilla's operation
 * @param amount the amount at level 1
 * @param perLevel what every level above 1 adds; 0 when the modifier does not grow
 */
public record CharacterModifier(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount,
        double perLevel) {
    public static final Codec<CharacterModifier> CODEC = RecordCodecBuilder.<CharacterModifier>create(instance ->
                    instance.group(
                            Attribute.CODEC.fieldOf("attribute").forGetter(CharacterModifier::attribute),
                            AttributeModifier.Operation.CODEC.fieldOf("operation")
                                    .forGetter(CharacterModifier::operation),
                            Codec.DOUBLE.fieldOf("amount").forGetter(CharacterModifier::amount),
                            Codec.DOUBLE.optionalFieldOf("per_level", 0.0).forGetter(CharacterModifier::perLevel))
                    .apply(instance, CharacterModifier::new))
            .validate(CharacterModifier::validate);

    /** The amount at {@code level}; the pipeline computes the same ({@code CharacterModifierDef.amount_at}). */
    public double amountAt(int level) {
        return amount + perLevel * (level - 1);
    }

    private static DataResult<CharacterModifier> validate(CharacterModifier modifier) {
        if (!Double.isFinite(modifier.amount()) || !Double.isFinite(modifier.perLevel())) {
            return DataResult.error(() -> "A race or class modifier's amounts must be finite: " + modifier);
        }
        return DataResult.success(modifier);
    }
}
