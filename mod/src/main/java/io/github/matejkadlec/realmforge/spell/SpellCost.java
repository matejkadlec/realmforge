package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;

/**
 * What a cast costs (architecture 11.1): an amount of a resource, growing per spell level like the effects
 * (research SP-9: MnS's Fireball costs more at higher ranks).
 *
 * @param resource the resource the cast takes
 * @param amount the amount at spell level 1
 * @param perLevel added for every spell level above 1
 * @param extra more of the resource a player's cast spends when they have it, for more damage (RLM-540: Execute)
 */
public record SpellCost(Holder<ResourceType> resource, double amount, double perLevel, Optional<Extra> extra) {
    public static final Codec<SpellCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceType.CODEC.fieldOf("resource").forGetter(SpellCost::resource),
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(SpellCost::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(SpellCost::perLevel),
                    Extra.CODEC.optionalFieldOf("extra").forGetter(SpellCost::extra))
            .apply(instance, SpellCost::new));

    /** A cost without extra spending. */
    public SpellCost(Holder<ResourceType> resource, double amount, double perLevel) {
        this(resource, amount, perLevel, Optional.empty());
    }

    /**
     * More of the resource a cast spends beyond its cost, up to {@code max}, each point raising the spell's damage by
     * {@code damagePerPoint} (Execute: up to 30 more rage, 3 % a point).
     */
    public record Extra(double max, double damagePerPoint) {
        public static final Codec<Extra> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("max").forGetter(Extra::max),
                        Codec.doubleRange(0, 1).fieldOf("damage_per_point").forGetter(Extra::damagePerPoint))
                .apply(instance, Extra::new));

        /** What a caster with {@code left} of the resource after the cost spends beyond it: at most {@link #max}. */
        public double spentFrom(double left) {
            return Math.max(0, Math.min(max, left));
        }

        /** The damage factor {@code spent} extra points give: 1 + {@code spent} × the share a point. */
        public double damageFactor(double spent) {
            return 1 + Math.max(0, spent) * damagePerPoint;
        }
    }

    /** The amount at spell level {@code level}. */
    public double amountAt(int level) {
        return SpellScaling.atLevel(amount, perLevel, level);
    }
}
