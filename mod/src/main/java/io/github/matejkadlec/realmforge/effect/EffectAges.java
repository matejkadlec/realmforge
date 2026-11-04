package io.github.matejkadlec.realmforge.effect;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * The value of the {@code realmforge:effect_ages} attachment (RLM-487; architecture 7, 11.4): how many server ticks
 * each of the holder's periodic effects has run since it was first applied, by effect. A {@link Periodic} ticks by it
 * ({@link Periodic#ticksAtAge}), not by the remaining duration, which every application resets: an area or a channel
 * that applies the effect again sooner than it ticks (a channel that Cast Time Reduction hastens, a channel's areas that
 * pulse out of step) only lengthens it, and its ticks keep the pace of the first application. A new application after
 * the effect ended starts again at 0.
 *
 * <p>Mutable, as it counts every tick; saved with the holder, as its effects are, so a reload keeps the pace; never
 * synced, as only the server reads it; not copied on death, which clears the effects.
 */
public final class EffectAges {
    public static final Codec<EffectAges> CODEC = Codec.unboundedMap(ResourceKey.codec(Registries.MOB_EFFECT),
            Codec.INT).xmap(EffectAges::new, EffectAges::ages);

    /** What {@link #advance(LivingEntity, Holder)} answers for an effect without a registry key: no tick is due. */
    private static final int NO_AGE = -1;

    private final Map<ResourceKey<MobEffect>, Integer> ages = new HashMap<>();

    public EffectAges() {
    }

    private EffectAges(Map<ResourceKey<MobEffect>, Integer> saved) {
        ages.putAll(saved);
    }

    /** Effect to its age in server ticks; a copy. */
    public Map<ResourceKey<MobEffect>, Integer> ages() {
        return Map.copyOf(ages);
    }

    /**
     * Counts one more server tick of {@code effect} and returns its age now: 1 on the first tick after it was first
     * applied. An effect without end that reaches the largest int starts counting again at 1.
     */
    public int advance(ResourceKey<MobEffect> effect) {
        int age = ages.getOrDefault(effect, 0);
        int next = age == Integer.MAX_VALUE ? 1 : age + 1;
        ages.put(effect, next);
        return next;
    }

    /** How many server ticks {@code effect} has run since it was first applied; 0 before its first tick. */
    public int age(ResourceKey<MobEffect> effect) {
        return ages.getOrDefault(effect, 0);
    }

    /** Forgets {@code effect}'s age, so its next application starts again at 0; whether it had one. */
    public boolean forget(ResourceKey<MobEffect> effect) {
        return ages.remove(effect) != null;
    }

    public boolean isEmpty() {
        return ages.isEmpty();
    }

    /**
     * Counts one more server tick of {@code effect} on {@code holder} and returns its age now ({@link #advance}); an
     * effect without a registry key, which never happens for ours, answers -1, so it never ticks.
     */
    static int advance(LivingEntity holder, Holder<MobEffect> effect) {
        return effect.unwrapKey().map(key -> holder.getData(EffectAttachments.AGES).advance(key)).orElse(NO_AGE);
    }

    /**
     * A new application of an effect the holder does not have starts its age again; an application over the effect it
     * has keeps the age, so it only lengthens it.
     */
    static void onEffectAdded(MobEffectEvent.Added event) {
        if (!event.getEntity().level().isClientSide() && event.getOldEffectInstance() == null) {
            forget(event.getEntity(), event.getEffectInstance().getEffect());
        }
    }

    /** Runs last, and only when no listener kept the effect. */
    static void onEffectRemoved(MobEffectEvent.Remove event) {
        forget(event.getEntity(), event.getEffect());
    }

    static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) {
            forget(event.getEntity(), event.getEffectInstance().getEffect());
        }
    }

    private static void forget(LivingEntity holder, Holder<MobEffect> effect) {
        effect.unwrapKey().ifPresent(key -> holder.getExistingData(EffectAttachments.AGES)
                .ifPresent(ages -> ages.forget(key)));
    }
}
