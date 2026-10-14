package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The resource types (architecture 11.5): ids and names only until C3a. The pipeline knows the same ids
 * ({@code RESOURCE_TYPES} in {@code tools/realmforge_tools/schema/character.py}), so a class can name only these.
 */
public final class ResourceTypes {
    private static final DeferredRegister<ResourceType> TYPES =
            DeferredRegister.create(SpellRegistries.RESOURCE_TYPES, Realmforge.MOD_ID);

    /** {@code realmforge:mana}: the caster's resource; it will regenerate. */
    public static final DeferredHolder<ResourceType, ResourceType> MANA =
            TYPES.register("mana", () -> new ResourceType(GeneratedTexts.RESOURCE_MANA));

    /** {@code realmforge:rage}: the melee resource; it will be gained in combat and decay. */
    public static final DeferredHolder<ResourceType, ResourceType> RAGE =
            TYPES.register("rage", () -> new ResourceType(GeneratedTexts.RESOURCE_RAGE));

    private ResourceTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
