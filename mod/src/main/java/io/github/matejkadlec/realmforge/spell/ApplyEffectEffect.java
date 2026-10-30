package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.effect.EffectSource;
import io.github.matejkadlec.realmforge.effect.Effects;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:apply_effect}: gives the target a status effect, from the caster (architecture 11.4): one of ours
 * from {@code content/effects/} (C3c, RLM-26) or a vanilla one. It remembers the caster, the spell and the proc that
 * cast it, if any ({@code effect/EffectSource}), for dispels, talents and the ticks of damage and healing over time.
 * Combat effects keep their own short time scale (research SP-8), so at most a minute. A rank may make it last longer
 * and grow stronger (C4: Frostbolt's Slowness I becomes Slowness II at rank 5).
 *
 * @param effect the status effect
 * @param durationTicks how long it lasts at spell level 1
 * @param durationPerLevelTicks added to the duration for every spell level above 1
 * @param amplifier its strength above the first at spell level 1 (0 is Slowness I)
 * @param strongerAtLevels the spell levels from which the amplifier is one higher, each
 */
public record ApplyEffectEffect(Holder<MobEffect> effect, int durationTicks, int durationPerLevelTicks, int amplifier,
        List<Integer> strongerAtLevels) implements EntityEffect {
    /** The longest a spell's status effect lasts: a minute. */
    public static final int MAX_DURATION_TICKS = 1200;
    public static final int MAX_AMPLIFIER = 9;

    public static final MapCodec<ApplyEffectEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    MobEffect.CODEC.fieldOf("effect").forGetter(ApplyEffectEffect::effect),
                    Codec.intRange(1, MAX_DURATION_TICKS).fieldOf("duration_ticks")
                            .forGetter(ApplyEffectEffect::durationTicks),
                    Codec.intRange(0, MAX_DURATION_TICKS).optionalFieldOf("duration_per_level_ticks", 0)
                            .forGetter(ApplyEffectEffect::durationPerLevelTicks),
                    Codec.intRange(0, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0)
                            .forGetter(ApplyEffectEffect::amplifier),
                    Codec.intRange(2, Integer.MAX_VALUE).listOf().optionalFieldOf("stronger_at_levels", List.of())
                            .forGetter(ApplyEffectEffect::strongerAtLevels))
            .apply(instance, ApplyEffectEffect::new));

    public ApplyEffectEffect {
        strongerAtLevels = List.copyOf(strongerAtLevels);
    }

    /** A status effect of the same duration and strength at every spell level. */
    public ApplyEffectEffect(Holder<MobEffect> effect, int durationTicks, int amplifier) {
        this(effect, durationTicks, 0, amplifier, List.of());
    }

    @Override
    public MapCodec<ApplyEffectEffect> codec() {
        return MAP_CODEC;
    }

    /** How long it lasts at spell level {@code level}, in ticks, at most a minute. */
    public int durationAt(int level) {
        return Math.min(MAX_DURATION_TICKS, durationTicks + durationPerLevelTicks * (level - 1));
    }

    /** Its amplifier at spell level {@code level}: one higher from each level of {@link #strongerAtLevels}. */
    public int amplifierAt(int level) {
        int stronger = (int) strongerAtLevels.stream().filter(from -> level >= from).count();
        return Math.min(MAX_AMPLIFIER, amplifier + stronger);
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        EffectSource source = new EffectSource(context.caster().getUUID(),
                context.spell().unwrapKey().map(ResourceKey::identifier), context.procOrigin());
        int level = context.spellLevel();
        Effects.apply(target, new MobEffectInstance(effect, durationAt(level), amplifierAt(level)), context.caster(),
                source);
    }
}
