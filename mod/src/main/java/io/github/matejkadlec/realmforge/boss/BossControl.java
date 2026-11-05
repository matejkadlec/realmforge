package io.github.matejkadlec.realmforge.boss;

import io.github.matejkadlec.realmforge.effect.StatusEffect;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;

/**
 * Crowd control on a boss (RLM-615; Matěj, 2026-11-04): an effect with {@code on_bosses} (Stunned, Frozen) lasts a
 * share of its duration on every boss, and when it ends, however it ends (it runs out, damage breaks it), the boss gets
 * that effect's immunity for its time (Stun Immunity, Frozen Immunity: 10 s), which refuses a new one
 * ({@code effect/Immunities}). Each effect has its own immunity, so a stun never blocks a freeze. A boss is never
 * stun-locked or kept frozen, and its crowd control still counts. This works on top of a boss's own
 * {@code immune_to} (RLM-539), which refuses the effect for good.
 *
 * <p>A {@link BossMob} shortens what it takes in {@code addEffect} and asks {@link #follow} every server tick, after its
 * effects ticked, for the effects that ended; its reset clears its effects and {@link #forget}s them, so a reset never
 * leaves an immunity. Its bar shows each immunity with its time left ({@link #withImmunities}).
 */
final class BossControl {
    /** The effects with {@code on_bosses} the boss had at the last check. */
    private final Set<Holder<MobEffect>> held = new HashSet<>();

    /** {@code effect} as it lands on a boss: shorter when it has {@code on_bosses}, unchanged otherwise. */
    static MobEffectInstance shortened(MobEffectInstance effect) {
        return onBosses(effect.getEffect())
                .map(rule -> effect.withScaledDuration((float) rule.durationFraction()))
                .orElse(effect);
    }

    /**
     * Notes the boss's effects with {@code on_bosses}, and gives it the immunity of each one it had at the last check
     * and has no longer.
     */
    void follow(LivingEntity boss) {
        Iterator<Holder<MobEffect>> had = held.iterator();
        while (had.hasNext()) {
            Holder<MobEffect> effect = had.next();
            if (!boss.hasEffect(effect)) {
                had.remove();
                onBosses(effect).ifPresent(rule -> boss.addEffect(
                        new MobEffectInstance(rule.immunityEffect(), rule.immunityTicks())));
            }
        }
        for (MobEffectInstance active : boss.getActiveEffects()) {
            if (onBosses(active.getEffect()).isPresent()) {
                held.add(active.getEffect());
            }
        }
    }

    /** Forgets every effect it noted, so the boss gets no immunity for them: its reset. */
    void forget() {
        held.clear();
    }

    /**
     * {@code title} with each immunity the boss has and its time left, as the tick rate counts it: "Astone, the
     * Unbroken — The Weighing · Stun Immunity (00:10)".
     */
    static Component withImmunities(LivingEntity boss, Component title) {
        List<MobEffectInstance> immunities = new ArrayList<>();
        for (MobEffectInstance active : boss.getActiveEffects()) {
            if (active.getEffect().value() instanceof StatusEffect status && status.immuneTo().isPresent()) {
                immunities.add(active);
            }
        }
        immunities.sort((a, b) -> a.getEffect().getRegisteredName().compareTo(b.getEffect().getRegisteredName()));
        float tickRate = boss.level().tickRateManager().tickrate();
        Component shown = title;
        for (MobEffectInstance immunity : immunities) {
            shown = Component.translatable(GeneratedTexts.GUI_BOSS_BAR_IMMUNITY, shown,
                    immunity.getEffect().value().getDisplayName(),
                    MobEffectUtil.formatDuration(immunity, 1.0F, tickRate));
        }
        return shown;
    }

    private static Optional<StatusEffect.OnBosses> onBosses(Holder<MobEffect> effect) {
        return effect.value() instanceof StatusEffect status ? status.onBosses() : Optional.empty();
    }
}
