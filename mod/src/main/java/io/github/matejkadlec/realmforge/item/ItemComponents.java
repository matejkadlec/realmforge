package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The item stack data components {@code item} owns (architecture 13). */
public final class ItemComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Realmforge.MOD_ID);

    /**
     * {@code realmforge:netherite_upgrade}: the netherite upgrade a piece carries (RLM-566), set by
     * {@link NetheriteUpgradeRecipe}. It names the registry entry rather than copying its bonus, so a rebalanced
     * upgrade reaches the pieces that already carry it; saved with the stack and sent to clients for the tooltip.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<NetheriteUpgrade>>>
            NETHERITE_UPGRADE = COMPONENTS.registerComponentType("netherite_upgrade", builder -> builder
                    .persistent(ResourceKey.codec(ItemRegistries.NETHERITE_UPGRADE))
                    .networkSynchronized(ResourceKey.streamCodec(ItemRegistries.NETHERITE_UPGRADE)));

    private ItemComponents() {
    }

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
