package io.github.matejkadlec.realmforge.effect;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.CritRule;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * The ticks of damage and healing over time (architecture 11.4), on the logical server.
 *
 * <ul>
 *   <li><b>Damage</b> is a hit of the effect's school from its caster ({@link EffectSource}): its amount plus its share
 *   of the caster's Spell Power as it is now ({@link Periodic#amountAt(int, double)}; system C, RLM-534), which the
 *   damage pipeline raises by the caster's school power; the target's resistance lowers it, its kill counts for the
 *   caster and the caster sees its numbers in the school's colour. It never crits ({@link CritRule#NONE}) and never
 *   knocks back (school damage). Once the caster is gone (dead, logged out, in another dimension) the ticks go on
 *   without an attacker, at the bare amount.</li>
 *   <li><b>Healing</b> is the same tick × (1 + the school's power), as a spell's heal
 *   ({@code spell/SpellScaling#heal}); without the caster, the bare amount.</li>
 *   <li>A proc's effect carries its proc origin to its ticks, so they trigger no proc (architecture 12).</li>
 * </ul>
 */
public final class EffectTicks {
    private EffectTicks() {
    }

    static void tick(ServerLevel level, LivingEntity target, Holder<MobEffect> effect, Periodic periodic,
            int amplifier) {
        Optional<Holder.Reference<SpellSchool>> school = level.registryAccess()
                .lookupOrThrow(CombatRegistries.SPELL_SCHOOL).get(periodic.school());
        if (school.isEmpty()) {
            // A data pack took the school away: the effect stays, without its ticks.
            return;
        }
        Optional<EffectSource> source = Effects.source(target, effect);
        LivingEntity caster = source.map(value -> value.casterIn(level)).orElse(null);
        Optional<Identifier> procOrigin = source.flatMap(EffectSource::procOrigin);
        double amount = caster == null ? periodic.amountAt(amplifier)
                : periodic.amountAt(amplifier, DamagePipeline.stat(caster, GeneratedStats.SPELL_POWER));
        switch (periodic.kind()) {
            case DAMAGE -> target.hurtServer(level, SpellSchools.damage(level.registryAccess(), periodic.school(),
                    caster, caster, CritRule.NONE, procOrigin), (float) amount);
            case HEAL -> target.heal((float) (amount * (caster == null ? 1
                    : factor(school.get().value(), stat -> DamagePipeline.stat(caster, stat)))));
        }
    }

    /**
     * What the caster's stats multiply a tick by, damage or healing alike: 1 + the school's power, never below 0, as
     * the damage pipeline raises a school hit. The caster's Spell Power is in the tick's amount already
     * ({@link Periodic#amountAt(int, double)}). {@code stats} reads the caster's.
     */
    public static double factor(SpellSchool school, ToDoubleFunction<Holder<Attribute>> stats) {
        double schoolPower = school.power().map(stats::applyAsDouble).orElse(0.0);
        return Math.max(0, 1 + schoolPower);
    }

    /**
     * One tick of {@code periodic} at {@code amplifier} as the caster's stats ({@code stats}) make it, before the
     * target's resistance: its amount with the caster's share of Spell Power, × {@link #factor}. What a tooltip shows.
     */
    public static double tickFor(Periodic periodic, SpellSchool school, int amplifier,
            ToDoubleFunction<Holder<Attribute>> stats) {
        return periodic.amountAt(amplifier, stats.applyAsDouble(GeneratedStats.SPELL_POWER)) * factor(school, stats);
    }
}
