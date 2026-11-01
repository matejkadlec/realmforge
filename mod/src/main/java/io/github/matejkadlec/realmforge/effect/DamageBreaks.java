package io.github.matejkadlec.realmforge.effect;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Effects that damage can end (RLM-542; architecture 11.4): every hit that deals its holder damage ends each of our
 * effects marked {@code breaks_on_damage} at its chance, rolled apart for each effect, on the logical server. Frozen
 * breaks at 50 % (Matěj, 2026-11-01: any damage, from any source, and so for every Frost freeze to come). It rolls
 * after the hit ({@code LivingDamageEvent.Post}), so the hit itself still sees the effect: Ice Lance's bonus and
 * Shatter's crit chance against a Frozen target count, and a spell that damages and then freezes (Frost Nova) freezes
 * after its own damage.
 */
public final class DamageBreaks {
    private DamageBreaks() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, DamageBreaks::onDamagePost);
    }

    static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || event.getInflictedDamage() <= 0) {
            return;
        }
        breakOn(entity);
    }

    /** Ends each of {@code entity}'s effects that damage breaks, at its chance: what a hit that dealt damage does. */
    public static void breakOn(LivingEntity entity) {
        List<Holder<MobEffect>> broken = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().value() instanceof StatusEffect status && status.breakChance() > 0
                    && entity.getRandom().nextDouble() < status.breakChance()) {
                broken.add(instance.getEffect());
            }
        }
        broken.forEach(entity::removeEffect);
    }
}
