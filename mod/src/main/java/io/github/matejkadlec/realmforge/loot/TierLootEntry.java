package io.github.matejkadlec.realmforge.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * The loot entries {@code realmforge:tier_gem} and {@code realmforge:tier_affix_item} (architecture 13; RLM-289): a
 * gem, or an item with affixes, rolled for the opener by the mod that owns the world tiers ({@link WorldTiers}:
 * Apotheosis, whose purities and rarities follow the tier). Our tables name these instead of Apotheosis's own entries,
 * so a table still loads where Apotheosis is not (the lean dev client), where the two give nothing. A gem may be the
 * purest of several {@code rolls}, a treasure room's better gem for the same tier.
 */
public final class TierLootEntry extends LootPoolSingletonContainer {
    /** The most rolls a gem may be the best of. */
    public static final int MAX_ROLLS = 10;

    public static final MapCodec<TierLootEntry> GEM = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(1, MAX_ROLLS).optionalFieldOf("rolls", 1).forGetter(entry -> entry.rolls)
    ).and(singletonFields(instance)).apply(instance, (rolls, weight, quality, conditions, functions) ->
            new TierLootEntry(true, rolls, weight, quality, conditions, functions)));
    public static final MapCodec<TierLootEntry> AFFIX_ITEM = RecordCodecBuilder.mapCodec(instance ->
            singletonFields(instance).apply(instance, (weight, quality, conditions, functions) ->
                    new TierLootEntry(false, 1, weight, quality, conditions, functions)));

    private final boolean gem;
    private final int rolls;

    private TierLootEntry(boolean gem, int rolls, int weight, int quality, List<LootItemCondition> conditions,
            List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.gem = gem;
        this.rolls = rolls;
    }

    @Override
    public MapCodec<TierLootEntry> codec() {
        return gem ? GEM : AFFIX_ITEM;
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        if (gem) {
            WorldTiers.gem(context, rolls, output);
        } else {
            WorldTiers.affixItem(context, output);
        }
    }
}
