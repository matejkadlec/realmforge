package io.github.matejkadlec.realmforge.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Who applied a damage-over-time effect, so that its ticks show as combat text to that player (architecture 9.1,
 * RLM-186; Matěj's playtest, 2026-10-14). A tick of vanilla's poison or wither has no attacker
 * ({@code DamageSource#getEntity} is null) and {@code MobEffectInstance} does not remember who applied it, so this
 * class remembers the player behind each application: the thrower of a splash or lingering potion, the shooter of a
 * tipped arrow, or a player who applied it directly. The latest application decides; one by anything but a player
 * forgets the player. Kept per entity and effect, transient, with weak keys; an entry goes when its effect ends or is
 * removed. Logical server only.
 *
 * <p>Each damaging effect has one combat text colour, here: our own status effects (C3c, RLM-26) carry their caster
 * and add their kinds to this list.
 */
public final class DamageOverTime {
    /** Poison's ticks: dark green (Matěj, 2026-10-14). */
    public static final int POISON = 0xFF00AA00;
    /** Wither's ticks: ash grey, like the withered hearts. */
    public static final int WITHER = 0xFF9A9A9A;

    /** A damaging effect: the damage type of its ticks and their combat text colour. */
    private record Kind(Holder<MobEffect> effect, ResourceKey<DamageType> damageType, int color) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind(MobEffects.POISON, NeoForgeMod.POISON_DAMAGE, POISON),
            new Kind(MobEffects.WITHER, DamageTypes.WITHER, WITHER));
    private static final Map<LivingEntity, Map<Holder<MobEffect>, UUID>> APPLIERS = new WeakHashMap<>();

    /**
     * One tick of a damaging effect that a player applied.
     *
     * @param applier the player who applied the effect
     * @param color the tick's combat text colour, opaque ARGB
     */
    public record Tick(ServerPlayer applier, int color) {
    }

    private DamageOverTime() {
    }

    /**
     * The player who applied the damaging effect a hit is a tick of, with its colour; null when the hit has an
     * attacker, is no such tick, or no player applied the effect (or that player left).
     */
    @Nullable
    public static Tick tick(LivingEntity defender, DamageSource source) {
        if (source.getEntity() != null) {
            return null;
        }
        for (Kind kind : KINDS) {
            if (source.is(kind.damageType()) && defender.hasEffect(kind.effect())) {
                Map<Holder<MobEffect>, UUID> appliers = APPLIERS.get(defender);
                UUID id = appliers != null ? appliers.get(kind.effect()) : null;
                ServerPlayer applier = id != null && defender.level().getServer() != null
                        ? defender.level().getServer().getPlayerList().getPlayer(id) : null;
                return applier != null ? new Tick(applier, kind.color()) : null;
            }
        }
        return null;
    }

    static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance effect = event.getEffectInstance();
        if (entity.level().isClientSide() || KINDS.stream().noneMatch(kind -> effect.is(kind.effect()))) {
            return;
        }
        ServerPlayer applier = player(event.getEffectSource());
        if (applier != null) {
            APPLIERS.computeIfAbsent(entity, key -> new HashMap<>()).put(effect.getEffect(), applier.getUUID());
        } else {
            forget(entity, effect.getEffect());
        }
    }

    /** Forgets the applier of an effect that is removed; runs last, and only when no listener kept the effect. */
    static void onEffectRemoved(MobEffectEvent.Remove event) {
        forget(event.getEntity(), event.getEffect());
    }

    static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) {
            forget(event.getEntity(), event.getEffectInstance().getEffect());
        }
    }

    /** The player behind an effect's source: the player, or the player who owns the potion, arrow or cloud. */
    @Nullable
    private static ServerPlayer player(@Nullable Entity source) {
        if (source instanceof TraceableEntity traceable && !(source instanceof ServerPlayer)) {
            source = traceable.getOwner();
        }
        return source instanceof ServerPlayer player ? player : null;
    }

    private static void forget(LivingEntity entity, Holder<MobEffect> effect) {
        Map<Holder<MobEffect>, UUID> appliers = APPLIERS.get(entity);
        if (appliers != null && appliers.remove(effect) != null && appliers.isEmpty()) {
            APPLIERS.remove(entity);
        }
    }
}
