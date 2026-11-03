package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.combat.CritCounters;
import io.github.matejkadlec.realmforge.combat.DamageCalculator;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * {@code realmforge:heal}: heals the target (architecture 11): its amount at the rank plus its {@link Coefficient} of
 * the caster's power (system C, RLM-534) and its share of the healed entity's max health (RLM-543: Victory Rush), × (1 +
 * the school's power) for a spell that scales with spell power. Heals crit like hits, by the spell's crit rule (Matěj,
 * 2026-10-15). The heal goes through {@code LivingHealEvent}, so overheal and the combat text see it (architecture 9.1,
 * step 7). With {@code onCaster} it heals the caster instead of the target, whatever the spell targets (Victory Rush
 * strikes an enemy and heals the Warrior).
 *
 * @param amount the healing at spell level 1
 * @param perLevel added for every spell level above 1
 * @param coefficient the share of the caster's power it adds, per rank
 * @param maxHealth the share of the healed entity's max health it adds at spell level 1
 * @param maxHealthPerLevel added to that share for every spell level above 1
 * @param onCaster whether it heals the caster rather than the target
 */
public record HealEffect(double amount, double perLevel, Coefficient coefficient, double maxHealth,
        double maxHealthPerLevel, boolean onCaster) implements EntityEffect {
    public static final MapCodec<HealEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(HealEffect::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(HealEffect::perLevel),
                    Coefficient.MAP_CODEC.forGetter(HealEffect::coefficient),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("max_health", 0.0)
                            .forGetter(HealEffect::maxHealth),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("max_health_per_level", 0.0)
                            .forGetter(HealEffect::maxHealthPerLevel),
                    Codec.BOOL.optionalFieldOf("on_caster", false).forGetter(HealEffect::onCaster))
            .apply(instance, HealEffect::new));

    /** A heal of the target without a share of max health. */
    public HealEffect(double amount, double perLevel, Coefficient coefficient) {
        this(amount, perLevel, coefficient, 0, 0, false);
    }

    @Override
    public MapCodec<HealEffect> codec() {
        return MAP_CODEC;
    }

    /** The share of the healed entity's max health it adds at spell level {@code level}. */
    public double maxHealthAt(int level) {
        return SpellScaling.atLevel(maxHealth, maxHealthPerLevel, level);
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        if (onCaster) {
            applyTo(context, context.caster(), target);
        } else {
            EntityEffect.super.apply(context, target);
        }
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        LivingEntity healed = onCaster ? context.caster() : target;
        Spell spell = context.spell().value();
        double base = SpellScaling.base(amount, perLevel, context.spellLevel(), coefficient,
                context.casterStat(Attributes.ATTACK_DAMAGE), context.casterStat(GeneratedStats.SPELL_POWER))
                + maxHealthAt(context.spellLevel()) * healed.getMaxHealth();
        double schoolPower = spell.school().flatMap(school -> school.value().power()).map(context::casterStat)
                .orElse(0.0);
        double heal = SpellScaling.heal(base, spell.scalesWith(), schoolPower);
        DamageCalculator.Crit crit = DamageCalculator.crit(spell.crit().atLevel(context.spellLevel()),
                context.casterStat(GeneratedStats.CRIT_CHANCE), context.casterStat(GeneratedStats.CRIT_DAMAGE),
                CritCounters.roller(context.caster(), CritCounters.Kind.SPELL, context.random()));
        healed.heal((float) (heal * crit.multiplier()));
    }
}
