package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.combat.HitBonus;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * {@code realmforge:damage}: a hit of the spell's school on the target (architecture 9.1, 11). The hit goes through the
 * damage pipeline like any school hit: a magic school's gets spell power and the school's power, every one the
 * target's resistance and a crit roll by the spell's crit rule at its rank. An attack damage ability adds its share of
 * the caster's attack damage here (Matěj, 2026-10-15). A hit may add armor shred to the caster's own (Slam) or ignore a
 * share of the target's resistance (spell penetration, Arcane Missiles), both growing with the rank (C4).
 *
 * @param amount the damage at spell level 1
 * @param perLevel added for every spell level above 1
 * @param attackDamage the share of the caster's attack damage added (1 adds all of it), 0 for magic
 * @param armorShred the armor shred the hit adds at spell level 1, a share
 * @param armorShredPerLevel added to the armor shred for every spell level above 1
 * @param penetration the spell penetration at spell level 1, a share of the target's resistance
 * @param penetrationPerLevel added to the penetration for every spell level above 1
 */
public record DamageEffect(double amount, double perLevel, double attackDamage, double armorShred,
        double armorShredPerLevel, double penetration, double penetrationPerLevel) implements EntityEffect {
    public static final MapCodec<DamageEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(DamageEffect::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(DamageEffect::perLevel),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("attack_damage", 0.0)
                            .forGetter(DamageEffect::attackDamage),
                    Codec.doubleRange(0, 1).optionalFieldOf("armor_shred", 0.0).forGetter(DamageEffect::armorShred),
                    Codec.doubleRange(0, 1).optionalFieldOf("armor_shred_per_level", 0.0)
                            .forGetter(DamageEffect::armorShredPerLevel),
                    Codec.doubleRange(0, 1).optionalFieldOf("penetration", 0.0).forGetter(DamageEffect::penetration),
                    Codec.doubleRange(0, 1).optionalFieldOf("penetration_per_level", 0.0)
                            .forGetter(DamageEffect::penetrationPerLevel))
            .apply(instance, DamageEffect::new));

    /** A hit without armor shred or spell penetration. */
    public DamageEffect(double amount, double perLevel, double attackDamage) {
        this(amount, perLevel, attackDamage, 0, 0, 0, 0);
    }

    @Override
    public MapCodec<DamageEffect> codec() {
        return MAP_CODEC;
    }

    /** What the hit adds to the caster's armor shred and spell penetration at spell level {@code level}, at most 1. */
    public HitBonus bonusAt(int level) {
        return new HitBonus(Math.min(1, armorShred + armorShredPerLevel * (level - 1)),
                Math.min(1, penetration + penetrationPerLevel * (level - 1)));
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        Spell spell = context.spell().value();
        double damage = SpellScaling.base(amount, perLevel, context.spellLevel(), attackDamage,
                context.casterStat(Attributes.ATTACK_DAMAGE));
        LivingEntity caster = context.caster();
        target.hurtServer(context.level(), SpellSchools.damage(context.level().registryAccess(),
                spell.school().unwrapKey().orElseThrow(), context.direct(), caster,
                spell.crit().atLevel(context.spellLevel()), bonusAt(context.spellLevel()), context.procOrigin()),
                (float) damage);
    }
}
