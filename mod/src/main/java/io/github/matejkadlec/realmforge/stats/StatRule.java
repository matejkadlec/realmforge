package io.github.matejkadlec.realmforge.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * What one primary stat gives, an entry of the datapack registry {@code realmforge:stat_rule} from
 * {@code content/stat_rules/} (architecture 8.3): attribute modifiers per point of the stat, and bonuses once the stat
 * reaches a milestone, each for every player or for some class roles only ({@link ClassRole}). Amounts are in game
 * units (fractions for percentages). {@link StatRules} turns a stat value into modifiers; {@link CharacterStats}
 * applies them to players.
 *
 * <p>A rule reads a primary stat and writes no primary stat, so rules never feed each other: a rule whose bonuses
 * raised a primary stat would read them back as a changed input on the next check and escalate them. The pipeline
 * validates our content; the codec also refuses such a rule from any other data pack.
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

    /**
     * One attribute modifier a rule writes: the attribute, vanilla's operation, the amount, and the class roles it
     * applies to.
     *
     * @param roles the roles whose players get it; empty: every player, one without a character included
     */
    public record Modifier(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount,
            List<ClassRole> roles) {
        public static final MapCodec<Modifier> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Attribute.CODEC.fieldOf("attribute").forGetter(Modifier::attribute),
                        AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(Modifier::operation),
                        Codec.DOUBLE.fieldOf("amount").forGetter(Modifier::amount),
                        ClassRole.CODEC.listOf().optionalFieldOf("roles", List.of()).forGetter(Modifier::roles))
                .apply(instance, Modifier::new));
        public static final Codec<Modifier> CODEC = MAP_CODEC.codec();

        public Modifier {
            roles = List.copyOf(roles);
        }

        /** A modifier for every player. */
        public Modifier(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
            this(attribute, operation, amount, List.of());
        }

        /**
         * Whether a player of {@code role} gets it: one without roles applies to everyone, one with roles only to
         * those, never to a player without a role (no character).
         */
        public boolean appliesTo(Optional<ClassRole> role) {
            return roles.isEmpty() || role.isPresent() && roles.contains(role.get());
        }
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
        if (!isPrimary(rule.stat())) {
            return DataResult.error(() -> "A stat rule reads a primary stat, not " + rule.stat().getRegisteredName());
        }
        List<Modifier> written = new ArrayList<>();
        rule.perPoint().forEach(entry -> written.add(entry.modifier()));
        rule.milestones().forEach(milestone -> written.addAll(milestone.modifiers()));
        for (Modifier modifier : written) {
            if (isPrimary(modifier.attribute())) {
                return DataResult.error(() -> "A stat rule must not write the primary stat "
                        + modifier.attribute().getRegisteredName());
            }
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

    private static boolean isPrimary(Holder<Attribute> attribute) {
        return GeneratedStats.PRIMARY.stream().anyMatch(primary -> attribute.is(primary.getKey()));
    }
}
