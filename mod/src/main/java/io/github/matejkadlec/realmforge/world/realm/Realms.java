package io.github.matejkadlec.realmforge.world.realm;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.world.WorldRegistries;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The public API of {@code world} for realms (architecture 4.3, 15.1): which dimension is a realm, and its unlock.
 * Overworld is never a realm, even once W2b gives it an entry for its return portals.
 */
public final class Realms {
    /** The path prefix of every realm's unlock advancement. */
    public static final String ADVANCEMENT_PREFIX = "realm/";

    /** The index of the last registry asked about: one per loaded world, rebuilt when the world changes. */
    private static volatile Index index;

    private record Index(Registry<Realm> registry, Map<ResourceKey<Level>, Holder.Reference<Realm>> byDimension) {
    }

    private Realms() {
    }

    /** The advancement that unlocks the realm: {@code <namespace>:realm/<path>}. */
    public static Identifier unlockAdvancement(ResourceKey<Realm> realm) {
        Identifier id = realm.identifier();
        return Identifier.fromNamespaceAndPath(id.getNamespace(), ADVANCEMENT_PREFIX + id.getPath());
    }

    /** The realm whose dimension is {@code dimension}, or empty when it is not a realm's. */
    public static Optional<Holder.Reference<Realm>> of(RegistryAccess registries, ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return Optional.empty();
        }
        Registry<Realm> registry = registries.lookupOrThrow(WorldRegistries.REALM);
        Index current = index;
        if (current == null || current.registry() != registry) {
            current = new Index(registry, byDimension(registry));
            index = current;
        }
        return Optional.ofNullable(current.byDimension().get(dimension));
    }

    /** Whether {@code dimension} is a realm's. */
    public static boolean isRealm(RegistryAccess registries, ResourceKey<Level> dimension) {
        return of(registries, dimension).isPresent();
    }

    /**
     * The realms by their dimension. Our pipeline allows one per dimension; when another data pack adds a second, the
     * realm with the lowest id wins and the log says so.
     */
    private static Map<ResourceKey<Level>, Holder.Reference<Realm>> byDimension(Registry<Realm> registry) {
        Map<ResourceKey<Level>, Holder.Reference<Realm>> byDimension = new HashMap<>();
        registry.listElements()
                .sorted(Comparator.comparing(holder -> holder.key().identifier()))
                .forEach(holder -> {
                    Holder.Reference<Realm> winner = byDimension.putIfAbsent(holder.value().dimension(), holder);
                    if (winner != null) {
                        Realmforge.LOGGER.warn("Realms {} and {} both name {}; using {}", winner.key().identifier(),
                                holder.key().identifier(), holder.value().dimension().identifier(),
                                winner.key().identifier());
                    }
                });
        return Map.copyOf(byDimension);
    }
}
