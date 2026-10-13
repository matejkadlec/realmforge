package io.github.matejkadlec.realmforge.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * What one primary stat gives, an entry of the datapack registry {@code realmforge:stat_rule} from
 * {@code content/stat_rules/} (architecture 8.3): attribute modifiers per point of the stat, and bonuses once the stat
 * reaches a milestone. Amounts are in game units (fractions for percentages). {@link StatRules} turns a stat value
 * into modifiers; {@link CharacterStats} applies them to players.
 *
 * @param stat the primary stat it reads
 * @param perPoint what every point gives
 * @param milestones bonuses at thresholds, in strictly increasing points order
 */
public record StatRule(Holder<Attribute> stat, List<PerPoint> perPoint, List<Milestone> milestones) {
    public static final Codec<StatRule> CODEC = RecordCodecBuilder.<StatRule>create(instance -> instance.group(
                    Attribute.CODEC.fieldOf("stat").forGetter(StatRule::stat),
                    PerPoint.CODEC.listOf().optionalFieldOf("per_point", List.of()).forGetter(StatRule::perPoint),
                    Milestone.CODEC.listOf().optionalFieldOf("milestones", List.of())
                            .forGetter(StatRule::milestones))
            .apply(instance, StatRule::new))
            .validate(StatRule::validate);

    public StatRule {
        perPoint = List.copyOf(perPoint);
        milestones = List.copyOf(milestones);
    }

    /** One attribute modifier a rule writes: the attribute, vanilla's operation and the amount. */
    public record Modifier(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
        public static final MapCodec<Modifier> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Attribute.CODEC.fieldOf("attribute").forGetter(Modifier::attribute),
                        AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(Modifier::operation),
                        Codec.DOUBLE.fieldOf("amount").forGetter(Modifier::amount))
                .apply(instance, Modifier::new));
        public static final Codec<Modifier> CODEC = MAP_CODEC.codec();
    }

    /**
     * A modifier every point of the stat gives: {@code points * amount}, capped at {@code max} when there is one.
     *
     * @param max cap on the total from all points, in the amount's unit; positive
     */
    public record PerPoint(Modifier modifier, Optional<Double> max) {
        public static final Codec<PerPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Modifier.MAP_CODEC.forGetter(PerPoint::modifier),
                        Codec.DOUBLE.optionalFieldOf("max").forGetter(PerPoint::max))
                .apply(instance, PerPoint::new));

        /** The amount {@code value} points give. */
        public double totalAt(double value) {
            double total = value * modifier.amount();
            return max.map(cap -> Math.min(total, cap)).orElse(total);
        }
    }

    /** Bonuses the stat gives from {@code points} on, on top of the per-point modifiers. */
    public record Milestone(int points, List<Modifier> modifiers) {
        public static final Codec<Milestone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("points").forGetter(Milestone::points),
                        ExtraCodecs.nonEmptyList(Modifier.CODEC.listOf()).fieldOf("modifiers")
                                .forGetter(Milestone::modifiers))
                .apply(instance, Milestone::new));

        public Milestone {
            modifiers = List.copyOf(modifiers);
        }
    }

    private static DataResult<StatRule> validate(StatRule rule) {
        if (rule.perPoint().isEmpty() && rule.milestones().isEmpty()) {
            return DataResult.error(() -> "A stat rule gives nothing: it has neither per_point nor milestones");
        }
        for (PerPoint entry : rule.perPoint()) {
            if (!Double.isFinite(entry.modifier().amount())) {
                return DataResult.error(() -> "A per-point amount is not finite: " + entry.modifier().amount());
            }
            if (entry.max().isPresent() && !(entry.max().get() > 0 && entry.modifier().amount() > 0)) {
                return DataResult.error(() -> "A per-point cap needs a positive cap and amount: " + entry);
            }
        }
        for (int i = 1; i < rule.milestones().size(); i++) {
            int before = rule.milestones().get(i - 1).points();
            int after = rule.milestones().get(i).points();
            if (after <= before) {
                return DataResult.error(() -> "Milestone points must strictly increase, but " + after + " follows "
                        + before);
            }
        }
        for (Milestone milestone : rule.milestones()) {
            for (Modifier modifier : milestone.modifiers()) {
                if (!Double.isFinite(modifier.amount())) {
                    return DataResult.error(() -> "A milestone amount is not finite: " + modifier.amount());
                }
            }
        }
        return DataResult.success(rule);
    }
}
