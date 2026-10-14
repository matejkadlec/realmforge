package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One item of a class's starting kit (architecture 10.1).
 *
 * @param item the item
 * @param count how many, 1 to 64; more than the item stacks to are given as several stacks
 */
public record KitItem(Holder<Item> item, int count) {
    public static final int MAX_COUNT = 64;

    public static final Codec<KitItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Item.CODEC.fieldOf("item").forGetter(KitItem::item),
                    ExtraCodecs.intRange(1, MAX_COUNT).optionalFieldOf("count", 1).forGetter(KitItem::count))
            .apply(instance, KitItem::new));

    /** The kit item as stacks: full stacks of the item, then the rest (three stacks for 3 swords). */
    public List<ItemStack> stacks() {
        List<ItemStack> stacks = new ArrayList<>();
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        for (int left = count; left > 0; left -= maxStackSize) {
            stacks.add(new ItemStack(item, Math.min(left, maxStackSize)));
        }
        return stacks;
    }
}
