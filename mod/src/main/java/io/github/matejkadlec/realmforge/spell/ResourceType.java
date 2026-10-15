package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

/**
 * A kind of resource spells cost, an entry of the static registry {@code realmforge:resource_type} (architecture 5.3,
 * 11.5): mana or rage ({@link ResourceTypes}). A class names the one its spells use (C1); the type itself says how big
 * a holder's pool is and how fast it refills, so {@link Resources} needs no knowledge of the holder's class.
 *
 * @param name translation key of the resource's name
 * @param maximumOf the size of a holder's pool; 0 means it has none
 * @param regenerationOf what a holder's pool regains every second (negative drains it)
 * @param startsFull whether a new pool starts full (mana) or empty (rage)
 */
public record ResourceType(String name, ToDoubleFunction<LivingEntity> maximumOf,
        ToDoubleFunction<LivingEntity> regenerationOf, boolean startsFull) {
    /** A resource type by its id, e.g. {@code realmforge:rage}. */
    public static final Codec<Holder<ResourceType>> CODEC = SpellRegistries.RESOURCE_TYPES.holderByNameCodec();

    /** The size of {@code holder}'s pool, never negative; 0 when it has none. */
    public double maximum(LivingEntity holder) {
        return Math.max(0, maximumOf.applyAsDouble(holder));
    }

    /** What {@code holder}'s pool regains every second. */
    public double regeneration(LivingEntity holder) {
        return regenerationOf.applyAsDouble(holder);
    }
}
