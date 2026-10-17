package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * {@code realmforge:damage}: a hit of the spell's school on the target (architecture 9.1, 11). The hit goes through the
 * damage pipeline like any school hit: a magic school's gets spell power and the school's power, every one the
 * target's resistance and a crit roll by the spell's crit rule. An attack damage ability adds its share of the
 * caster's attack damage here (Matěj, 2026-10-15).
 *
 * @param amount the damage at spell level 1
 * @param perLevel added for every spell level above 1
 * @param attackDamage the share of the caster's attack damage added (1 adds all of it), 0 for magic
 */
public record DamageEffect(double amount, double perLevel, double attackDamage) implements EntityEffect {
    public static final MapCodec<DamageEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(DamageEffect::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(DamageEffect::perLevel),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("attack_damage", 0.0)
                            .forGetter(DamageEffect::attackDamage))
            .apply(instance, DamageEffect::new));

    @Override
    public MapCodec<DamageEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        Spell spell = context.spell().value();
        double damage = SpellScaling.base(amount, perLevel, context.spellLevel(), attackDamage,
                context.casterStat(Attributes.ATTACK_DAMAGE));
        LivingEntity caster = context.caster();
        target.hurtServer(context.level(), SpellSchools.damage(context.level().registryAccess(),
                spell.school().unwrapKey().orElseThrow(), context.direct(), caster, spell.crit()), (float) damage);
    }
}
