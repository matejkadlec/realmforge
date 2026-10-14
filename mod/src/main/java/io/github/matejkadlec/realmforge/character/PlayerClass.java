package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.ResourceType;
import java.util.List;
import net.minecraft.core.Holder;

/**
 * A class, an entry of the datapack registry {@code realmforge:class} from {@code content/classes/} (architecture 5.2,
 * 10.1): its resource, the modifiers it gives and how they grow per level, and its starting kit. Spells by level and
 * talent trees come with C4 and C5. Named {@code PlayerClass}, as {@code Class} is Java's own.
 *
 * @param name translation key of the class's name
 * @param description translation key of its description on character creation
 * @param resource the resource its spells cost
 * @param modifiers attribute modifiers it gives its players, growing with their level
 * @param startingKit the items a new character of this class receives once
 */
public record PlayerClass(String name, String description, Holder<ResourceType> resource,
        List<CharacterModifier> modifiers, List<KitItem> startingKit) {
    public static final Codec<PlayerClass> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(PlayerClass::name),
                    Codec.STRING.fieldOf("description").forGetter(PlayerClass::description),
                    ResourceType.CODEC.fieldOf("resource").forGetter(PlayerClass::resource),
                    CharacterModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of())
                            .forGetter(PlayerClass::modifiers),
                    KitItem.CODEC.listOf().optionalFieldOf("starting_kit", List.of())
                            .forGetter(PlayerClass::startingKit))
            .apply(instance, PlayerClass::new));

    public PlayerClass {
        modifiers = List.copyOf(modifiers);
        startingKit = List.copyOf(startingKit);
    }
}
