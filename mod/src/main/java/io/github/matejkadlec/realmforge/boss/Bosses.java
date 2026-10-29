package io.github.matejkadlec.realmforge.boss;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

/**
 * The bosses' public API (architecture 4.3, 14.3): the fight of a boss's body, by its entity type. The registry stays
 * on the server; {@link #of} finds an entry through an index built once per registry, as {@code DimensionRules} does.
 */
public final class Bosses {
    /** The index of the last registry asked about: one per loaded world, rebuilt when the world changes. */
    private static volatile Index index;

    private record Index(Registry<Boss> registry, Map<EntityType<?>, Holder.Reference<Boss>> byEntity) {
    }

    private Bosses() {
    }

    /** The boss whose body is {@code entity}, or empty when no entry names it. Logical server only. */
    public static Optional<Holder.Reference<Boss>> of(RegistryAccess registries, EntityType<?> entity) {
        Registry<Boss> registry = registries.lookupOrThrow(BossRegistries.BOSS);
        Index current = index;
        if (current == null || current.registry() != registry) {
            current = new Index(registry, byEntity(registry));
            index = current;
        }
        return Optional.ofNullable(current.byEntity().get(entity));
    }

    /**
     * The entries by the entity type they name. Our pipeline allows one per body; when another data pack adds a
     * second, the entry with the lowest id wins and the log says so.
     */
    private static Map<EntityType<?>, Holder.Reference<Boss>> byEntity(Registry<Boss> registry) {
        Map<EntityType<?>, Holder.Reference<Boss>> byEntity = new HashMap<>();
        registry.listElements()
                .sorted(Comparator.comparing(holder -> holder.key().identifier()))
                .forEach(holder -> {
                    Holder.Reference<Boss> winner = byEntity.putIfAbsent(holder.value().entity(), holder);
                    if (winner != null) {
                        ResourceKey<Boss> key = holder.key();
                        Realmforge.LOGGER.warn("Bosses {} and {} both run on {}; using {}", winner.key().identifier(),
                                key.identifier(), BuiltInRegistries.ENTITY_TYPE.getKey(holder.value().entity()),
                                winner.key().identifier());
                    }
                });
        return Map.copyOf(byEntity);
    }
}
