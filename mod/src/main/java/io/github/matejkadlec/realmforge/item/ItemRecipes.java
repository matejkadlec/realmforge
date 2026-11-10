package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The recipe types {@code item} adds (architecture 13). */
public final class ItemRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Realmforge.MOD_ID);

    /** {@code realmforge:netherite_upgrade}: a smithing table puts a netherite upgrade on a piece (RLM-566). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<NetheriteUpgradeRecipe>> NETHERITE_UPGRADE =
            SERIALIZERS.register("netherite_upgrade", () -> NetheriteUpgradeRecipe.SERIALIZER);

    private ItemRecipes() {
    }

    static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
