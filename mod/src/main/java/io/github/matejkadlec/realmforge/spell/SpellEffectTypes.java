package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The spell effect types (architecture 5.3): {@code damage} and {@code heal} so far. The pipeline knows the same ids
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

    private SpellEffectTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
