package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The spell effect types (architecture 5.3), the first set: {@code damage} and {@code heal} (C3a-1), the status
 * effects, the spell entities, the movements and {@code knockback} (C3a-3), and {@code cleanse} (C4). The pipeline knows the same ids
 * ({@code tools/realmforge_tools/schema/spell.py}).
 */
public final class SpellEffectTypes {
    private static final DeferredRegister<MapCodec<? extends SpellEffect>> TYPES =
            DeferredRegister.create(SpellRegistries.EFFECT_TYPES, Realmforge.MOD_ID);

    /** {@code realmforge:damage}: a hit of the spell's school ({@link DamageEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<DamageEffect>> DAMAGE =
            TYPES.register("damage", () -> DamageEffect.MAP_CODEC);

    /** {@code realmforge:heal}: healing ({@link HealEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<HealEffect>> HEAL =
            TYPES.register("heal", () -> HealEffect.MAP_CODEC);

    /** {@code realmforge:damage_over_time}: the caster's stack of a damage over time ({@link DamageOverTimeEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<DamageOverTimeEffect>>
            DAMAGE_OVER_TIME = TYPES.register("damage_over_time", () -> DamageOverTimeEffect.MAP_CODEC);

    /** {@code realmforge:apply_effect}: a status effect on the target ({@link ApplyEffectEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<ApplyEffectEffect>> APPLY_EFFECT =
            TYPES.register("apply_effect", () -> ApplyEffectEffect.MAP_CODEC);

    /** {@code realmforge:remove_effect}: takes a status effect off the target ({@link RemoveEffectEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<RemoveEffectEffect>> REMOVE_EFFECT =
            TYPES.register("remove_effect", () -> RemoveEffectEffect.MAP_CODEC);

    /** {@code realmforge:cleanse}: takes every harmful status effect off the target ({@link CleanseEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<CleanseEffect>> CLEANSE =
            TYPES.register("cleanse", () -> CleanseEffect.MAP_CODEC);

    /** {@code realmforge:projectile}: a spell projectile ({@link ProjectileEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<ProjectileEffect>> PROJECTILE =
            TYPES.register("projectile", () -> ProjectileEffect.MAP_CODEC);

    /** {@code realmforge:area}: a spell area ({@link AreaEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<AreaEffect>> AREA =
            TYPES.register("area", () -> AreaEffect.MAP_CODEC);

    /** {@code realmforge:melee}: a melee spell's hit shape in front of the caster ({@link MeleeEffect}, RLM-399). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<MeleeEffect>> MELEE =
            TYPES.register("melee", () -> MeleeEffect.MAP_CODEC);

    /** {@code realmforge:teleport}: moves the caster to the target at once ({@link TeleportEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<TeleportEffect>> TELEPORT =
            TYPES.register("teleport", () -> TeleportEffect.MAP_CODEC);

    /** {@code realmforge:dash}: throws the caster toward the target ({@link DashEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<DashEffect>> DASH =
            TYPES.register("dash", () -> DashEffect.MAP_CODEC);

    /** {@code realmforge:knockback}: pushes the target away ({@link KnockbackEffect}). */
    public static final DeferredHolder<MapCodec<? extends SpellEffect>, MapCodec<KnockbackEffect>> KNOCKBACK =
            TYPES.register("knockback", () -> KnockbackEffect.MAP_CODEC);

    private SpellEffectTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
