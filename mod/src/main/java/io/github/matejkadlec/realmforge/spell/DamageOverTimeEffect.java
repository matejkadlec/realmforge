package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.effect.EffectSource;
import io.github.matejkadlec.realmforge.effect.StackTicks;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * {@code realmforge:damage_over_time} (RLM-539; architecture 11.4): the caster's own stack of a status effect with
 * stacks (Bleeding) on the target. Its amount at the rank plus its {@link Coefficient} of the caster's power is the
 * damage over the whole duration, fixed now, and the effect's ticks split it evenly ({@code effect/StackTicks}).
 * Another spell or caster stacks; the same spell from the same caster refreshes its stack. A splash gives it at full
 * strength, as it does every effect but damage.
 *
 * @param effect the status effect with stacks
 * @param amount the damage over the duration at spell level 1
 * @param perLevel added for every spell level above 1
 * @param coefficient the share of the caster's power it adds, per rank
 * @param durationTicks how long it lasts at spell level 1
 * @param durationPerLevelTicks added to the duration for every spell level above 1
 */
public record DamageOverTimeEffect(Holder<MobEffect> effect, double amount, double perLevel, Coefficient coefficient,
        int durationTicks, int durationPerLevelTicks) implements EntityEffect {
    public static final MapCodec<DamageOverTimeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    MobEffect.CODEC.fieldOf("effect").forGetter(DamageOverTimeEffect::effect),
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(DamageOverTimeEffect::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(DamageOverTimeEffect::perLevel),
                    Coefficient.MAP_CODEC.forGetter(DamageOverTimeEffect::coefficient),
                    Codec.intRange(1, ApplyEffectEffect.MAX_DURATION_TICKS).fieldOf("duration_ticks")
                            .forGetter(DamageOverTimeEffect::durationTicks),
                    Codec.intRange(0, ApplyEffectEffect.MAX_DURATION_TICKS).optionalFieldOf("duration_per_level_ticks", 0)
                            .forGetter(DamageOverTimeEffect::durationPerLevelTicks))
            .apply(instance, DamageOverTimeEffect::new));

    @Override
    public MapCodec<DamageOverTimeEffect> codec() {
        return MAP_CODEC;
    }

    /** How long it lasts at spell level {@code level}, in ticks, at most a minute. */
    public int durationAt(int level) {
        return Math.min(ApplyEffectEffect.MAX_DURATION_TICKS, durationTicks + durationPerLevelTicks * (level - 1));
    }

    /** The damage over the whole duration at spell level {@code level} for a caster with these powers. */
    public double totalAt(int level, double attackDamage, double spellPower) {
        return SpellScaling.base(amount, perLevel, level, coefficient, attackDamage, spellPower);
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        int level = context.spellLevel();
        double total = totalAt(level, context.casterStat(Attributes.ATTACK_DAMAGE),
                context.casterStat(GeneratedStats.SPELL_POWER));
        EffectSource source = new EffectSource(context.caster().getUUID(),
                context.spell().unwrapKey().map(ResourceKey::identifier), context.procOrigin());
        StackTicks.apply(target, effect, context.caster(), source, total, durationAt(level));
    }
}
