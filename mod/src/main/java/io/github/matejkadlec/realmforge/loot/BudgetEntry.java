package io.github.matejkadlec.realmforge.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * The loot entry {@code realmforge:budget} (architecture 13, ADR 0041; RLM-289): a container's loot by loot points.
 * Vanilla's pools roll each on their own and cannot share a budget between rolls, so this one entry gives the whole
 * container:
 * <ol>
 *   <li>it draws the roll's points from its {@link LootBudget}, times the percent of the opener's world tier
 *   ({@link WorldTiers}, the loot context's {@code THIS_ENTITY}) and their luck ({@code luck_percent} more per point
 *   of luck);</li>
 *   <li>{@link BudgetRoll} spends them on the options of that tier, by weight, until none is affordable;</li>
 *   <li>each pick rolls its option's own loot entry, any entry a pool may hold, and equal stacks join, so a container
 *   holds as few stacks as its loot allows.</li>
 * </ol>
 * The pipeline writes the tables from {@code content/container_loot/} and simulates the same rule in pytest.
 */
public final class BudgetEntry extends LootPoolSingletonContainer {
    /** The world tiers' names in the data, in order; Apotheosis's own names for them. */
    public static final List<String> TIERS = List.of("haven", "frontier", "ascent", "summit", "pinnacle");

    private static final Codec<String> TIER = Codec.STRING.validate(name -> TIERS.contains(name)
            ? DataResult.success(name) : DataResult.error(() -> "Unknown world tier " + name + ": one of " + TIERS));

    /**
     * One thing the budget may buy.
     *
     * @param weight how likely it is against the other affordable options
     * @param cost its points
     * @param limit how often one container may hold it, 0 for any number of times
     * @param tiers the world tiers it is for, every tier when empty
     * @param entry what a pick rolls
     */
    public record Option(int weight, int cost, int limit, List<String> tiers, LootPoolEntryContainer entry) {
        public static final Codec<Option> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(Option::weight),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("cost").forGetter(Option::cost),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("limit", 0).forGetter(Option::limit),
                TIER.listOf().optionalFieldOf("tiers", List.of()).forGetter(Option::tiers),
                LootPoolEntries.CODEC.fieldOf("entry").forGetter(Option::entry)
        ).apply(instance, Option::new));

        boolean isFor(int tier) {
            return tiers.isEmpty() || tiers.contains(TIERS.get(tier));
        }
    }

    public static final MapCodec<BudgetEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            LootBudget.CODEC.fieldOf("budget").forGetter(entry -> entry.budget),
            Codec.unboundedMap(TIER, Codec.doubleRange(0, 1_000_000)).optionalFieldOf("tier_percent", Map.of())
                    .forGetter(entry -> entry.tierPercent),
            Codec.doubleRange(0, 1_000).optionalFieldOf("luck_percent", 0.0).forGetter(entry -> entry.luckPercent),
            Option.CODEC.listOf().fieldOf("options").forGetter(entry -> entry.options)
    ).and(singletonFields(instance)).apply(instance, BudgetEntry::new));

    private final LootBudget budget;
    private final Map<String, Double> tierPercent;
    private final double luckPercent;
    private final List<Option> options;

    private BudgetEntry(LootBudget budget, Map<String, Double> tierPercent, double luckPercent, List<Option> options,
            int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.budget = budget;
        this.tierPercent = Map.copyOf(tierPercent);
        this.luckPercent = luckPercent;
        this.options = List.copyOf(options);
    }

    @Override
    public MapCodec<BudgetEntry> codec() {
        return CODEC;
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        for (int i = 0; i < options.size(); i++) {
            options.get(i).entry().validate(context.forIndexedField("options", i).forField("entry"));
        }
    }

    /** The points a roll of {@code roll} (0 to 1) has at the world tier {@code tier} with {@code luck}. */
    public int points(double roll, int tier, double luck) {
        return budget.points(roll, tierPercent.getOrDefault(TIERS.get(tier), 100.0), luck, luckPercent);
    }

    /** The options a container opened at the world tier {@code tier} may hold. */
    public List<Option> optionsAt(int tier) {
        return options.stream().filter(option -> option.isFor(tier)).toList();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        RandomSource random = context.getRandom();
        int tier = WorldTiers.of(context);
        List<Option> available = optionsAt(tier);
        List<BudgetRoll.Choice> choices = available.stream()
                .map(option -> new BudgetRoll.Choice(option.weight(), option.cost(), option.limit())).toList();
        List<ItemStack> stacks = new ArrayList<>();
        int points = points(random.nextDouble(), tier, context.getLuck());
        for (int pick : BudgetRoll.roll(points, choices, random::nextInt)) {
            roll(available.get(pick).entry(), context, stack -> join(stacks, stack));
        }
        stacks.forEach(output);
    }

    /** Rolls one entry as a pool does: of the entries it expands to, one by weight. */
    private static void roll(LootPoolEntryContainer container, LootContext context, Consumer<ItemStack> output) {
        List<LootPoolEntry> entries = new ArrayList<>();
        container.expand(context, entries::add);
        int total = 0;
        for (LootPoolEntry entry : entries) {
            total += entry.getWeight(context.getLuck());
        }
        if (total <= 0) {
            return;
        }
        int roll = context.getRandom().nextInt(total);
        for (LootPoolEntry entry : entries) {
            roll -= entry.getWeight(context.getLuck());
            if (roll < 0) {
                entry.createItemStack(output, context);
                return;
            }
        }
    }

    /** Adds {@code stack} to {@code stacks}, filling stacks of the same item and components first. */
    static void join(List<ItemStack> stacks, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack held : stacks) {
            if (rest.isEmpty()) {
                return;
            }
            if (held.getCount() < held.getMaxStackSize() && ItemStack.isSameItemSameComponents(held, rest)) {
                int moved = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                rest.shrink(moved);
            }
        }
        while (!rest.isEmpty()) {
            stacks.add(rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
        }
    }
}
