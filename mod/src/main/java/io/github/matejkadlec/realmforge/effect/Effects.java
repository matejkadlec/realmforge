package io.github.matejkadlec.realmforge.effect;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * The effect feature's public API (architecture 4.3, 11.4): applying a status effect with its source, and reading the
 * source back for dispels, talents and the ticks of damage and healing over time.
 *
 * <p>The latest application decides: an effect a spell applies through {@link #apply} remembers its caster and spell,
 * and the same effect applied any other way (a potion, a command, vanilla) forgets them, unless that application
 * changes nothing because the effect is already at least as strong and as long (RLM-373). Its removal or expiry
 * forgets them too. Logical server only.
 */
public final class Effects {
    /** The application {@link #apply} is making, so its own {@code MobEffectEvent.Added} keeps the source. */
    @Nullable
    private static Pending pending;

    private Effects() {
    }

    /**
     * Gives {@code target} {@code instance} from {@code caster}, remembering {@code source}. Returns whether the effect
     * was added or changed; when the target already had a stronger or longer one, nothing changes, its source included.
     */
    public static boolean apply(LivingEntity target, MobEffectInstance instance, LivingEntity caster,
            EffectSource source) {
        Objects.requireNonNull(source, "source");
        Pending previous = pending;
        pending = new Pending(target, instance.getEffect());
        boolean changed;
        try {
            changed = target.addEffect(instance, caster);
        } finally {
            pending = previous;
        }
        if (changed) {
            key(instance.getEffect()).ifPresent(key -> target.setData(EffectAttachments.SOURCES,
                    target.getData(EffectAttachments.SOURCES).with(key, source)));
        }
        return changed;
    }

    /** Whether {@code entity} is stunned now ({@link Stuns}). */
    public static boolean isStunned(LivingEntity entity) {
        return Stuns.isStunned(entity);
    }

    /** Whether {@code effect} is a stun ({@link Stuns}). */
    public static boolean isStun(Holder<MobEffect> effect) {
        return Stuns.isStun(effect);
    }

    /** Who applied {@code effect} to {@code entity}, while it has the effect and a spell applied it. */
    public static Optional<EffectSource> source(LivingEntity entity, Holder<MobEffect> effect) {
        if (!entity.hasEffect(effect)) {
            return Optional.empty();
        }
        return key(effect).flatMap(key -> entity.getExistingData(EffectAttachments.SOURCES)
                .flatMap(sources -> sources.get(key)));
    }

    static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        Holder<MobEffect> effect = event.getEffectInstance().getEffect();
        if (entity.level().isClientSide() || (pending != null && pending.matches(entity, effect))
                || !changes(event.getOldEffectInstance(), event.getEffectInstance())) {
            return;
        }
        forget(entity, effect);
    }

    /**
     * Whether applying {@code added} over {@code old} changes the effect, now or later. {@code LivingEntity#addEffect}
     * posts {@code MobEffectEvent.Added} before it updates the old instance, so this asks a copy (which leaves out the
     * old one's hidden effect, as {@code update}'s answer does) what the update will do, leaving the live instance
     * untouched. An application that outlasts it changes it later, though {@code update} says no: a weaker one waits
     * hidden until the stronger one ends and then takes its place, and its ticks are not the spell's.
     */
    private static boolean changes(@Nullable MobEffectInstance old, MobEffectInstance added) {
        return old == null || new MobEffectInstance(old).update(added) || !old.isInfiniteDuration()
                && (added.isInfiniteDuration() || added.getDuration() > old.getDuration());
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

    private static void forget(LivingEntity entity, Holder<MobEffect> effect) {
        Optional<ResourceKey<MobEffect>> key = key(effect);
        Optional<EffectSources> sources = entity.getExistingData(EffectAttachments.SOURCES);
        if (key.isPresent() && sources.isPresent() && sources.get().get(key.get()).isPresent()) {
            entity.setData(EffectAttachments.SOURCES, sources.get().without(key.get()));
        }
    }

    private static Optional<ResourceKey<MobEffect>> key(Holder<MobEffect> effect) {
        return effect.unwrapKey();
    }

    private record Pending(LivingEntity target, Holder<MobEffect> effect) {
        boolean matches(LivingEntity entity, Holder<MobEffect> added) {
            return entity == target && added.is(effect);
        }
    }
}
