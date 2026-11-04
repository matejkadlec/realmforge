package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;

/**
 * What a cast costs (architecture 11.1): an amount of a resource, growing per spell level like the effects
 * (research SP-9: MnS's Fireball costs more at higher ranks); a channel's for each second, and optionally an amount up
 * front when it begins (RLM-572).
 *
 * @param resource the resource the cast takes
 * @param amount the amount at spell level 1: a channel's for each second
 * @param perLevel added for every spell level above 1
 * @param extra more of the resource a player's cast spends when they have it, for more damage (RLM-540: Execute)
 * @param refundOnKill resource a player's cast gets back for each enemy its hits kill, from a rank on (RLM-543:
 *                     Execute), if any
 * @param upFront what a channel costs when it begins, on top of its first second's (RLM-572: Blizzard), if anything
 */
public record SpellCost(Holder<ResourceType> resource, double amount, double perLevel, Optional<Extra> extra,
        Optional<KillRefund> refundOnKill, Optional<UpFront> upFront) {
    public static final Codec<SpellCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceType.CODEC.fieldOf("resource").forGetter(SpellCost::resource),
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(SpellCost::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(SpellCost::perLevel),
                    Extra.CODEC.optionalFieldOf("extra").forGetter(SpellCost::extra),
                    KillRefund.CODEC.optionalFieldOf("refund_on_kill").forGetter(SpellCost::refundOnKill),
                    UpFront.CODEC.optionalFieldOf("up_front").forGetter(SpellCost::upFront))
            .apply(instance, SpellCost::new));

    /** A cost without extra spending. */
    public SpellCost(Holder<ResourceType> resource, double amount, double perLevel) {
        this(resource, amount, perLevel, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** A cost that may spend extra, without a refund. */
    public SpellCost(Holder<ResourceType> resource, double amount, double perLevel, Optional<Extra> extra) {
        this(resource, amount, perLevel, extra, Optional.empty(), Optional.empty());
    }

    /** A cost that may spend extra and refund a kill, without an amount up front. */
    public SpellCost(Holder<ResourceType> resource, double amount, double perLevel, Optional<Extra> extra,
            Optional<KillRefund> refundOnKill) {
        this(resource, amount, perLevel, extra, refundOnKill, Optional.empty());
    }

    /**
     * What a channel costs when it begins, on top of its first second's: {@code amount} at rank 1 and {@code perLevel}
     * more for every rank above it (RLM-572: Blizzard's 40/60/80 mana, then 15/20/25 a second).
     */
    public record UpFront(double amount, double perLevel) {
        public static final Codec<UpFront> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(UpFront::amount),
                        Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                                .forGetter(UpFront::perLevel))
                .apply(instance, UpFront::new));

        /** The amount at rank {@code level}. */
        public double amountAt(int level) {
            return SpellScaling.atLevel(amount, perLevel, level);
        }
    }

    /** What a channel costs up front at rank {@code level}, on top of its first second's: 0 without an amount up front. */
    public double upFrontAt(int level) {
        return upFront.map(more -> more.amountAt(level)).orElse(0.0);
    }

    /**
     * The resource back for each kill: {@code amount} at rank 1 and {@code perLevel} more for every rank above it, from
     * rank {@code fromLevel} on (Execute: 5/10/15 rage, Matěj, 2026-11-03).
     */
    public record KillRefund(double amount, double perLevel, int fromLevel) {
        public static final Codec<KillRefund> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(KillRefund::amount),
                        Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                                .forGetter(KillRefund::perLevel),
                        ExtraCodecs.POSITIVE_INT.optionalFieldOf("from_level", 1).forGetter(KillRefund::fromLevel))
                .apply(instance, KillRefund::new));

        /** What a kill refunds at rank {@code level}: nothing below {@link #fromLevel}. */
        public double amountAt(int level) {
            return level >= fromLevel ? SpellScaling.atLevel(amount, perLevel, level) : 0;
        }
    }

    /** What a kill at rank {@code level} gives back: its refund at the rank, 0 before its rank or without one. */
    public double refundAt(int level) {
        return refundOnKill.map(refund -> refund.amountAt(level)).orElse(0.0);
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
