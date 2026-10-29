package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.RageRules;
import io.github.matejkadlec.realmforge.spell.ResourceType;
import io.github.matejkadlec.realmforge.stats.ClassRole;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;

/**
 * A class, an entry of the datapack registry {@code realmforge:class} from {@code content/classes/} (architecture 5.2,
 * 10.1): its role, its resource (with its rage numbers for a rage class), the modifiers it gives, its starting kit,
 * and the spells it teaches by level (C4). The specialization spell trees come with C5. Named {@code PlayerClass}, as
 * {@code Class} is Java's own.
 *
 * @param name translation key of the class's name
 * @param description translation key of its description on character creation
 * @param role what the class plays, which decides the role-bound bonuses of the stat rules (architecture 8.3)
 * @param resource the resource its spells cost
 * @param rage how a rage class gains and loses rage (architecture 11.5); empty for any other class
 * @param modifiers attribute modifiers it gives its players, the same at every level
 * @param startingKit the items a new character of this class receives once
 * @param spells the spells it teaches by level ({@link ClassSpells})
 */
public record PlayerClass(String name, String description, ClassRole role, Holder<ResourceType> resource,
        Optional<RageRules> rage, List<CharacterModifier> modifiers, List<KitItem> startingKit,
        List<ClassSpell> spells) {
    public static final Codec<PlayerClass> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(PlayerClass::name),
                    Codec.STRING.fieldOf("description").forGetter(PlayerClass::description),
                    ClassRole.CODEC.fieldOf("role").forGetter(PlayerClass::role),
                    ResourceType.CODEC.fieldOf("resource").forGetter(PlayerClass::resource),
                    RageRules.CODEC.optionalFieldOf("rage").forGetter(PlayerClass::rage),
                    CharacterModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of())
                            .forGetter(PlayerClass::modifiers),
                    KitItem.CODEC.listOf().optionalFieldOf("starting_kit", List.of())
                            .forGetter(PlayerClass::startingKit),
                    ClassSpell.CODEC.listOf().optionalFieldOf("spells", List.of()).forGetter(PlayerClass::spells))
            .apply(instance, PlayerClass::new));

    public PlayerClass {
        modifiers = List.copyOf(modifiers);
        startingKit = List.copyOf(startingKit);
        spells = List.copyOf(spells);
    }
}
