package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;

/**
 * {@code realmforge:gain_resource} (RLM-543): gives the caster an amount of a resource, growing per rank, whatever the
 * spell targets (Charge generates 15/20 rage). Players only: mobs and bosses pay no cost, so they gain none, as a
 * swing's or a refund's rage goes only to players too.
 *
 * @param resource the resource the caster gains
 * @param amount the amount at spell level 1
 * @param perLevel added for every spell level above 1
 */
public record GainResourceEffect(Holder<ResourceType> resource, double amount, double perLevel)
        implements SpellEffect {
    public static final MapCodec<GainResourceEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(ResourceType.CODEC.fieldOf("resource").forGetter(GainResourceEffect::resource),
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(GainResourceEffect::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(GainResourceEffect::perLevel))
            .apply(instance, GainResourceEffect::new));

    @Override
    public MapCodec<GainResourceEffect> codec() {
        return MAP_CODEC;
    }

    /** The amount at spell level {@code level}. */
    public double amountAt(int level) {
        return SpellScaling.atLevel(amount, perLevel, level);
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        if (context.caster() instanceof Player player && player.isAlive()) {
            Resources.add(player, resource, amountAt(context.spellLevel()));
        }
    }
}
