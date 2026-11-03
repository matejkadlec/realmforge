package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.combat.CritRule;
import io.github.matejkadlec.realmforge.combat.HitBonus;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * {@code realmforge:damage}: a hit of the spell's school on the target (architecture 9.1, 11). Its amount at the
 * rank plus its {@link Coefficient} of the caster's power (Attack Damage for a physical ability, Spell Power for a
 * magic spell; system C, RLM-534) is computed here; the hit then goes through the damage pipeline like any school hit:
 * × (1 + the school's power), a crit roll by the spell's crit rule at its rank, the target's resistance. A hit may add
 * armor shred to the caster's own (Slam) or ignore a share of the target's resistance (spell penetration, Arcane
 * Missiles), both growing with the rank (C4). The caster's passive spells (RLM-541) add to the hit's damage (its
 * passives layer, Improved Frostbolt) and to its crit chance against the target (Shatter on a Frozen one). A player's
 * hit that kills its target gives back what the spell's cost refunds for a kill at the rank (RLM-543: Execute).
 *
 * @param amount the damage at spell level 1
 * @param perLevel added for every spell level above 1
 * @param coefficient the share of the caster's power it adds, per rank
 * @param armorShred the armor shred the hit adds at spell level 1, a share
 * @param armorShredPerLevel added to the armor shred for every spell level above 1
 * @param penetration the spell penetration at spell level 1, a share of the target's resistance
 * @param penetrationPerLevel added to the penetration for every spell level above 1
 * @param againstEffect more damage against a target with a status effect (RLM-540: Ice Lance against Frozen), growing
 *                      per rank (RLM-542), if any
 * @param magicShieldDamage how much more of a magic shield the hit drains, a share (RLM-580: Astone's spells)
 */
public record DamageEffect(double amount, double perLevel, Coefficient coefficient, double armorShred,
        double armorShredPerLevel, double penetration, double penetrationPerLevel, Optional<AgainstEffect> againstEffect,
        double magicShieldDamage) implements EntityEffect {
    public static final MapCodec<DamageEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(DamageEffect::amount),
                    Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("per_level", 0.0)
                            .forGetter(DamageEffect::perLevel),
                    Coefficient.MAP_CODEC.forGetter(DamageEffect::coefficient),
                    Codec.doubleRange(0, 1).optionalFieldOf("armor_shred", 0.0).forGetter(DamageEffect::armorShred),
                    Codec.doubleRange(0, 1).optionalFieldOf("armor_shred_per_level", 0.0)
                            .forGetter(DamageEffect::armorShredPerLevel),
                    Codec.doubleRange(0, 1).optionalFieldOf("penetration", 0.0).forGetter(DamageEffect::penetration),
                    Codec.doubleRange(0, 1).optionalFieldOf("penetration_per_level", 0.0)
                            .forGetter(DamageEffect::penetrationPerLevel),
                    AgainstEffect.CODEC.optionalFieldOf("against_effect").forGetter(DamageEffect::againstEffect),
                    Codec.doubleRange(0, 10).optionalFieldOf("magic_shield_damage", 0.0)
                            .forGetter(DamageEffect::magicShieldDamage))
            .apply(instance, DamageEffect::new));

    /** A hit without armor shred or spell penetration. */
    public DamageEffect(double amount, double perLevel, Coefficient coefficient) {
        this(amount, perLevel, coefficient, 0, 0, 0, 0, Optional.empty(), 0);
    }

    /** A hit with armor shred and spell penetration, and no bonus against an effect. */
    public DamageEffect(double amount, double perLevel, Coefficient coefficient, double armorShred,
            double armorShredPerLevel, double penetration, double penetrationPerLevel) {
        this(amount, perLevel, coefficient, armorShred, armorShredPerLevel, penetration, penetrationPerLevel,
                Optional.empty(), 0);
    }

    /** A hit with a bonus against an effect, and no magic shield bonus. */
    public DamageEffect(double amount, double perLevel, Coefficient coefficient, double armorShred,
            double armorShredPerLevel, double penetration, double penetrationPerLevel,
            Optional<AgainstEffect> againstEffect) {
        this(amount, perLevel, coefficient, armorShred, armorShredPerLevel, penetration, penetrationPerLevel,
                againstEffect, 0);
    }

    /**
     * More damage against a target that has {@code effect}: × (1 + the bonus at the rank), {@code damage} at rank 1
     * and {@code damagePerLevel} more for every rank above it (RLM-540, RLM-542: Ice Lance's 100 % to 200 % against a
     * Frozen target, × 2 to × 3).
     */
    public record AgainstEffect(Holder<MobEffect> effect, double damage, double damagePerLevel) {
        public static final Codec<AgainstEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        MobEffect.CODEC.fieldOf("effect").forGetter(AgainstEffect::effect),
                        Codec.doubleRange(0, 100).fieldOf("damage").forGetter(AgainstEffect::damage),
                        Codec.doubleRange(0, 100).optionalFieldOf("damage_per_level", 0.0)
                                .forGetter(AgainstEffect::damagePerLevel))
                .apply(instance, AgainstEffect::new));

        /** A bonus that does not grow with the rank. */
        public AgainstEffect(Holder<MobEffect> effect, double damage) {
            this(effect, damage, 0);
        }

        /** What a hit at rank {@code level} is multiplied by against a target with the effect. */
        public double factorAt(int level) {
            return 1 + SpellScaling.atLevel(damage, damagePerLevel, level);
        }
    }

    /**
     * What the hit's base at rank {@code level} is multiplied by against {@code target}: 1 + the bonus at the rank when
     * it has the effect, else 1.
     */
    public double againstFactor(LivingEntity target, int level) {
        return againstEffect.filter(against -> target.hasEffect(against.effect()))
                .map(against -> against.factorAt(level)).orElse(1.0);
    }

    /** The hit's damage at spell level {@code level} for a caster with {@code attackDamage} and {@code spellPower}. */
    public double baseAt(int level, double attackDamage, double spellPower) {
        return SpellScaling.base(amount, perLevel, level, coefficient, attackDamage, spellPower);
    }

    @Override
    public MapCodec<DamageEffect> codec() {
        return MAP_CODEC;
    }

    /** What the hit adds to the caster's armor shred and spell penetration at spell level {@code level}, at most 1. */
    public HitBonus bonusAt(int level) {
        return new HitBonus(Math.min(1, armorShred + armorShredPerLevel * (level - 1)),
                Math.min(1, penetration + penetrationPerLevel * (level - 1)), 0, magicShieldDamage);
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        Spell spell = context.spell().value();
        // A splash's share or extra rage's bonus (the context's scale), and a bonus against the target's effect.
        double damage = baseAt(context.spellLevel(), context.casterStat(Attributes.ATTACK_DAMAGE),
                context.casterStat(GeneratedStats.SPELL_POWER)) * context.damageScale()
                * againstFactor(target, context.spellLevel());
        LivingEntity caster = context.caster();
        // The caster's passive spells (RLM-541): more damage for this spell, more crit chance against this target.
        HitBonus bonus = bonusAt(context.spellLevel()).withDamage(context.spell().unwrapKey()
                .map(key -> Passives.damageBonus(caster, key)).orElse(0.0));
        CritRule crit = spell.crit().atLevel(context.spellLevel())
                .withBonusChance(Passives.critChanceAgainst(caster, target));
        boolean alive = !target.isDeadOrDying();
        target.hurtServer(context.level(), SpellSchools.damage(context.level().registryAccess(),
                // The pipeline makes every spell that deals damage name its school.
                spell.school().flatMap(Holder::unwrapKey).orElseThrow(), context.direct(), caster, crit, bonus,
                context.procOrigin()), (float) damage);
        // A player's kill gives back what the cost refunds at the rank (RLM-543: Execute's 10 rage at rank 3); a
        // proc's spell cost nothing, so it refunds nothing.
        if (alive && target.isDeadOrDying() && caster instanceof Player && context.procOrigin().isEmpty()) {
            spell.cost().filter(cost -> cost.refundAt(context.spellLevel()) > 0).ifPresent(cost -> Resources.add(
                    caster, cost.resource(), cost.refundAt(context.spellLevel())));
        }
    }
}
