package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.combat.DamageCalculator;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * {@code realmforge:heal}: heals the target (architecture 11). A spell that scales with spell power heals × (1 + the
 * caster's spell power + the school's power); an attack damage ability adds its share of attack damage instead. Heals
 * crit like hits, by the spell's crit rule (Matěj, 2026-10-15). The heal goes through {@code LivingHealEvent}, so
 * overheal and the combat text see it (architecture 9.1, step 7).
 *
 * @param amount the healing at spell level 1
 * @param perLevel added for every spell level above 1
 * @param attackDamage the share of the caster's attack damage added (1 adds all of it), 0 for magic
 */
public record HealEffect(double amount, double perLevel, double attackDamage) implements EntityEffect {
    public static final MapCodec<HealEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(HealEffect::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(HealEffect::perLevel),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("attack_damage", 0.0)
                            .forGetter(HealEffect::attackDamage))
            .apply(instance, HealEffect::new));

    @Override
    public MapCodec<HealEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        Spell spell = context.spell().value();
        double base = SpellScaling.base(amount, perLevel, context.spellLevel(), attackDamage,
                context.casterStat(Attributes.ATTACK_DAMAGE));
        double schoolPower = spell.school().value().power().map(context::casterStat).orElse(0.0);
        double heal = SpellScaling.heal(base, spell.scalesWith(), context.casterStat(GeneratedStats.SPELL_POWER),
                schoolPower);
        DamageCalculator.Crit crit = DamageCalculator.crit(spell.crit(),
                context.casterStat(GeneratedStats.CRIT_CHANCE), context.casterStat(GeneratedStats.CRIT_DAMAGE),
                context.random());
        target.heal((float) (heal * crit.multiplier()));
    }
}
