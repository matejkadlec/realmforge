package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;

/**
 * A kind of resource a class's spells cost, an entry of the static registry {@code realmforge:resource_type}
 * (architecture 5.3, 11.5): mana or rage. C1 gives them ids and names only, so class entries can name their resource;
 * C3a gives them behaviour (mana regenerates, rage is gained in combat and decays).
 *
 * @param name translation key of the resource's name
 */
public record ResourceType(String name) {
    /** A resource type by its id, e.g. {@code realmforge:rage}. */
    public static final Codec<Holder<ResourceType>> CODEC = SpellRegistries.RESOURCE_TYPES.holderByNameCodec();
}
