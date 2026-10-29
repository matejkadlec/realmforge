package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A race's racial passive (Matěj, 2026-10-15, RLM-219; C4, architecture 10.1): modifiers its players have while the
 * main hand holds an item of {@code weapons}, such as the Human's Sword Specialization, +10 % attack damage with a
 * sword-like weapon. {@link CharacterBonuses} gives the modifiers, and {@link RacialPassives} the tooltip lines.
 *
 * @param name translation key of the passive's name
 * @param description translation key of its tooltip line, with {@code {bonus}} for its first modifier
 * @param weapons the item tag the main hand must hold
 * @param modifiers the attribute modifiers it gives meanwhile
 */
public record RacialPassive(String name, String description, TagKey<Item> weapons,
        List<CharacterModifier> modifiers) {
    public static final Codec<RacialPassive> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(RacialPassive::name),
                    Codec.STRING.fieldOf("description").forGetter(RacialPassive::description),
                    TagKey.codec(Registries.ITEM).fieldOf("weapons").forGetter(RacialPassive::weapons),
                    ExtraCodecs.nonEmptyList(CharacterModifier.CODEC.listOf()).fieldOf("modifiers")
                            .forGetter(RacialPassive::modifiers))
            .apply(instance, RacialPassive::new));

    public RacialPassive {
        modifiers = List.copyOf(modifiers);
    }

    /** Whether the passive applies with {@code mainHand} in the main hand. */
    public boolean appliesWith(ItemStack mainHand) {
        return mainHand.is(weapons);
    }
}
