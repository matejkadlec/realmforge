package io.github.matejkadlec.realmforge.compat.apotheosis;

import dev.shadowsoffire.apotheosis.loot.entry.AffixLootPoolEntry;
import dev.shadowsoffire.apotheosis.loot.entry.GemLootPoolEntry;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import io.github.matejkadlec.realmforge.loot.WorldTiers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;

/**
 * Apotheosis as the owner of the world tiers for container loot (RLM-289, architecture 13 and 19.4): the opener's
 * tier is Apotheosis's own ({@code WorldTier#getTier}; Haven to Pinnacle, in our order), and a gem or an affix item is
 * rolled by Apotheosis's own loot entries without a filter, so its purities, rarities and loot pools follow the
 * tier and the luck of the loot context's player ({@code GenContext#forLoot}) as in its chest loot. A gem of several
 * rolls is the purest of them ({@code GemItem#getPurity}), which is how a treasure room's gems are better for the
 * same tier.
 */
public final class ApotheosisTiers implements WorldTiers.Provider {
    // Built at the first roll, not at mod construction, when Apotheosis's own classes are not ready to be used.
    private LootPoolEntryContainer gems;
    private LootPoolEntryContainer affixItems;

    private ApotheosisTiers() {
    }

    /** Names Apotheosis as the provider. Called once from {@code CompatFeature} when Apotheosis is loaded. */
    public static void register() {
        WorldTiers.provide(new ApotheosisTiers());
    }

    @Override
    public int tier(Player player) {
        return WorldTier.getTier(player).ordinal();
    }

    @Override
    public void gem(LootContext context, int rolls, Consumer<ItemStack> output) {
        if (gems == null) {
            gems = GemLootPoolEntry.builder(Set.of(), Set.of()).build();
        }
        List<ItemStack> rolled = new ArrayList<>();
        for (int roll = 0; roll < Math.max(1, rolls); roll++) {
            gems.expand(context, entry -> entry.createItemStack(rolled::add, context));
        }
        rolled.stream().max(Comparator.comparing(GemItem::getPurity)).ifPresent(output);
    }

    @Override
    public void affixItem(LootContext context, Consumer<ItemStack> output) {
        if (affixItems == null) {
            affixItems = AffixLootPoolEntry.builder(Set.of(), Set.of()).build();
        }
        affixItems.expand(context, entry -> entry.createItemStack(output, context));
    }
}
