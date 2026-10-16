package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * An attribute modifier a race or class gives its player (architecture 8.2, 10.1), the same at every level: levels give
 * stat points to spend instead ({@link StatPoints}; Matěj, 2026-10-15, RLM-196). Amounts are in game units (fractions
 * for percentages).
 *
 * @param attribute the attribute it modifies, usually a primary stat
 * @param operation vanilla's operation
 * @param amount the amount
 */
public record CharacterModifier(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
    public static final Codec<CharacterModifier> CODEC = RecordCodecBuilder.<CharacterModifier>create(instance ->
                    instance.group(
                            Attribute.CODEC.fieldOf("attribute").forGetter(CharacterModifier::attribute),
                            AttributeModifier.Operation.CODEC.fieldOf("operation")
                                    .forGetter(CharacterModifier::operation),
                            Codec.DOUBLE.fieldOf("amount").forGetter(CharacterModifier::amount))
                    .apply(instance, CharacterModifier::new))
            .validate(CharacterModifier::validate);

    private static DataResult<CharacterModifier> validate(CharacterModifier modifier) {
        if (!Double.isFinite(modifier.amount())) {
            return DataResult.error(() -> "A race or class modifier's amount must be finite: " + modifier);
        }
        return DataResult.success(modifier);
    }
}
