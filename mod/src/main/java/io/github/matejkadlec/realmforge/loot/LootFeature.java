package io.github.matejkadlec.realmforge.loot;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Loot entries of our own, container loot by loot points, mob coin drops (architecture 13). */
public final class LootFeature {
    private static final DeferredRegister<MapCodec<? extends LootPoolEntryContainer>> ENTRY_TYPES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, Realmforge.MOD_ID);

    static {
        // Container loot by loot points (RLM-289): the budget entry and what only our code can give.
        ENTRY_TYPES.register("budget", () -> BudgetEntry.CODEC);
        ENTRY_TYPES.register("money", () -> MoneyEntry.CODEC);
        ENTRY_TYPES.register("tier_gem", () -> TierLootEntry.GEM);
        ENTRY_TYPES.register("tier_affix_item", () -> TierLootEntry.AFFIX_ITEM);
    }

    private LootFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        ENTRY_TYPES.register(modBus);
        // A hostile mob a player killed drops coins by its level and rank (RLM-286).
        NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, CoinDrops::onLivingDrops);
    }
}
