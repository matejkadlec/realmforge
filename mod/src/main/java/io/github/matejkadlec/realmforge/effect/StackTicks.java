package io.github.matejkadlec.realmforge.effect;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SchoolDamageSource;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The stacks of damage over time (RLM-539, Bleeding; architecture 11.4), on the logical server: adding a caster's
 * stack, and dealing every stack's ticks while its status effect lasts.
 *
 * <ul>
 *   <li>A stack's damage was fixed when it was applied: the spell's amount plus its coefficient of the caster's power,
 *   split evenly over its ticks ({@code spell/DamageOverTimeEffect}).</li>
 *   <li>A tick is a hit of the effect's school from the stack's caster (from nobody once the caster is gone), which
 *   the damage pipeline raises by the caster's school power as it lands; it never crits, is never dodged or blocked,
 *   ignores armor when the effect says so, and carries the stack's proc origin.</li>
 *   <li>The status effect shows while any stack lasts: each application makes it last at least as long as the new
 *   stack. Its removal (milk, a cleanse) or expiry forgets every stack.</li>
 * </ul>
 */
public final class StackTicks {
    private StackTicks() {
    }

    /**
     * Gives {@code target} {@code caster}'s stack of {@code effect} (one with {@link StatusEffect#stacks()}), dealing
     * {@code total} over {@code durationTicks}, from {@code source}. Returns whether it took hold: a target that cannot
     * have the effect (an immune boss, RLM-539) takes none.
     */
    public static boolean apply(LivingEntity target, Holder<MobEffect> effect, LivingEntity caster, EffectSource source,
            double total, int durationTicks) {
        Optional<StackedDamage> damage = stacked(effect);
        Optional<ResourceKey<MobEffect>> key = effect.unwrapKey();
        if (damage.isEmpty() || key.isEmpty() || durationTicks < 1) {
            return false;
        }
        Effects.apply(target, new MobEffectInstance(effect, durationTicks), caster, source);
        if (!target.hasEffect(effect)) {
            return false;
        }
        double perTick = Math.max(0, total) / damage.get().tickCount(durationTicks);
        target.getData(EffectAttachments.STACKS).add(key.get(), new EffectStacks.Stack(source.caster(),
                source.spell(), source.procOrigin(), perTick, durationTicks));
        return true;
    }

    /** The stacks of {@code effect} on {@code entity}; empty without any. */
    public static List<EffectStacks.Stack> of(LivingEntity entity, Holder<MobEffect> effect) {
        return effect.unwrapKey().flatMap(key -> entity.getExistingData(EffectAttachments.STACKS)
                .map(stacks -> stacks.of(key))).orElse(List.of());
    }

    /** Every tick while the effect lasts: deals the ticks that are due and counts every stack down. */
    static void tick(ServerLevel level, LivingEntity target, Holder<MobEffect> effect, StackedDamage damage) {
        Optional<ResourceKey<MobEffect>> key = effect.unwrapKey();
        Optional<EffectStacks> stacks = target.getExistingData(EffectAttachments.STACKS);
        if (key.isEmpty() || stacks.isEmpty()) {
            return;
        }
        List<EffectStacks.Stack> remaining = new ArrayList<>();
        List<EffectStacks.Stack> due = new ArrayList<>();
        for (EffectStacks.Stack stack : stacks.get().of(key.get())) {
            if (damage.ticksAt(stack.remainingTicks())) {
                due.add(stack);
            }
            EffectStacks.Stack counted = stack.counted();
            if (counted.remainingTicks() > 0) {
                remaining.add(counted);
            }
        }
        stacks.get().set(key.get(), remaining);
        boolean known = level.registryAccess().lookupOrThrow(CombatRegistries.SPELL_SCHOOL).get(damage.school())
                .isPresent();
        for (EffectStacks.Stack stack : due) {
            if (!known || !target.isAlive()) {
                // A data pack took the school away, or an earlier tick killed the target.
                break;
            }
            Entity entity = level.getEntity(stack.caster());
            LivingEntity caster = entity instanceof LivingEntity living && living.isAlive() ? living : null;
            target.hurtServer(level, SpellSchools.tick(level.registryAccess(), damage.school(), caster,
                    stack.procOrigin(), new SchoolDamageSource.Tick(key.get().identifier(), stack.spell(),
                            damage.ignoresArmor())), (float) stack.perTick());
        }
    }

    private static Optional<StackedDamage> stacked(Holder<MobEffect> effect) {
        return effect.value() instanceof StatusEffect status ? status.stacks() : Optional.empty();
    }
}
