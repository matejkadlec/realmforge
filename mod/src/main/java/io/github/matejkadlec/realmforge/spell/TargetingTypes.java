package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The targeting types (architecture 5.3): {@code self}, {@code enemy} and {@code ally} so far. The pipeline knows the
 * same ids ({@code tools/realmforge_tools/schema/spell.py}).
 */
public final class TargetingTypes {
    private static final DeferredRegister<MapCodec<? extends SpellTargeting>> TYPES =
            DeferredRegister.create(SpellRegistries.TARGETING_TYPES, Realmforge.MOD_ID);

    /** {@code realmforge:self}: the caster ({@link SelfTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<SelfTargeting>> SELF =
            TYPES.register("self", () -> SelfTargeting.MAP_CODEC);

    /** {@code realmforge:enemy}: an enemy in range and sight ({@link EnemyTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<EnemyTargeting>> ENEMY =
            TYPES.register("enemy", () -> EnemyTargeting.MAP_CODEC);

    /** {@code realmforge:ally}: the caster or an ally in range and sight ({@link AllyTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<AllyTargeting>> ALLY =
            TYPES.register("ally", () -> AllyTargeting.MAP_CODEC);

    private TargetingTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
