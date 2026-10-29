package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * A playable race, an entry of the datapack registry {@code realmforge:race} from {@code content/races/} (architecture
 * 5.2, 10.1): the classes it may take, the modifiers it gives, and its racial active and passive (C4, RLM-219). A size
 * comes later.
 *
 * @param name translation key of the race's name
 * @param description translation key of its description on character creation
 * @param classes the classes it may take, in the order character creation lists them; a class that does not exist
 *     fails the world load
 * @param modifiers attribute modifiers it gives its players
 * @param racialSpell its racial active: a free spell every player of the race knows, in the racial slot beside the
 *     spell bar ({@link ClassSpells}); a spell that does not exist fails the world load
 * @param racialPassive its racial passive, if it has one
 */
public record Race(String name, String description, List<Holder<PlayerClass>> classes,
        List<CharacterModifier> modifiers, Optional<Holder<Spell>> racialSpell, Optional<RacialPassive> racialPassive) {
    public static final Codec<Race> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(Race::name),
                    Codec.STRING.fieldOf("description").forGetter(Race::description),
                    ExtraCodecs.nonEmptyList(RegistryFixedCodec.create(CharacterRegistries.CLASS).listOf())
                            .fieldOf("classes").forGetter(Race::classes),
                    CharacterModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of())
                            .forGetter(Race::modifiers),
                    RegistryFixedCodec.create(SpellRegistries.SPELL).optionalFieldOf("racial_spell")
                            .forGetter(Race::racialSpell),
                    RacialPassive.CODEC.optionalFieldOf("racial_passive").forGetter(Race::racialPassive))
            .apply(instance, Race::new));

    public Race {
        classes = List.copyOf(classes);
        modifiers = List.copyOf(modifiers);
    }

    /** Whether a player of this race may take the class. */
    public boolean allows(ResourceKey<PlayerClass> playerClass) {
        return classes.stream().anyMatch(allowed -> allowed.is(playerClass));
    }
}
