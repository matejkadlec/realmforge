package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The targeting types (architecture 5.3): {@code self}, {@code enemy} and {@code ally} (C3a-1), {@code ground_point}
 * and {@code direction} (C3a-3), and the NPC types {@code current_target}, {@code random_player} and
 * {@code farthest_player} (C3a-4), which only {@code npc_only} spells use. The pipeline knows the same ids
 * ({@code tools/realmforge_tools/schema/spell.py}).
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

    /** {@code realmforge:ground_point}: a point in range and sight ({@link GroundPointTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<GroundPointTargeting>> GROUND_POINT =
            TYPES.register("ground_point", () -> GroundPointTargeting.MAP_CODEC);

    /** {@code realmforge:direction}: a direction from the caster's eyes ({@link DirectionTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<DirectionTargeting>> DIRECTION =
            TYPES.register("direction", () -> DirectionTargeting.MAP_CODEC);

    /** {@code realmforge:current_target}: a mob's current target ({@link CurrentTargetTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<CurrentTargetTargeting>>
            CURRENT_TARGET = TYPES.register("current_target", () -> CurrentTargetTargeting.MAP_CODEC);

    /** {@code realmforge:random_player}: a random player a mob may attack ({@link RandomPlayerTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<RandomPlayerTargeting>>
            RANDOM_PLAYER = TYPES.register("random_player", () -> RandomPlayerTargeting.MAP_CODEC);

    /** {@code realmforge:farthest_player}: the farthest player a mob may attack ({@link FarthestPlayerTargeting}). */
    public static final DeferredHolder<MapCodec<? extends SpellTargeting>, MapCodec<FarthestPlayerTargeting>>
            FARTHEST_PLAYER = TYPES.register("farthest_player", () -> FarthestPlayerTargeting.MAP_CODEC);

    private TargetingTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
