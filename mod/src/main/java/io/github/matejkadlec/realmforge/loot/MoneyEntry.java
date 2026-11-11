package io.github.matejkadlec.realmforge.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.item.Coins;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * The loot entry {@code realmforge:money} (architecture 13; RLM-289): an amount of money between {@code min} and
 * {@code max}, in the smallest coin, each amount as likely, given in the largest coins ({@link Coins#stacks}) as a
 * mob's coin drop is. Vanilla's item entry gives one item type; money is up to four.
 */
public final class MoneyEntry extends LootPoolSingletonContainer {
    public static final MapCodec<MoneyEntry> CODEC = RecordCodecBuilder.<MoneyEntry>mapCodec(instance -> instance.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("min").forGetter(entry -> entry.min),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max").forGetter(entry -> entry.max)
    ).and(singletonFields(instance)).apply(instance, MoneyEntry::new)).validate(entry -> entry.min <= entry.max
            ? DataResult.success(entry)
            : DataResult.error(() -> "Money's min is at most its max: " + entry.min + ", " + entry.max));

    private final int min;
    private final int max;

    private MoneyEntry(int min, int max, int weight, int quality, List<LootItemCondition> conditions,
            List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.min = min;
        this.max = max;
    }

    @Override
    public MapCodec<MoneyEntry> codec() {
        return CODEC;
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        Coins.stacks(context.getRandom().nextIntBetweenInclusive(min, max)).forEach(output);
    }
}
