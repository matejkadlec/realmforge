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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The public API of {@code world} for realms (architecture 4.3, 15.1): which dimension is a realm, its unlock, and the
 * entries the realm portal reads. Overworld's entry, which gives return portals their colour, is never a realm.
 */
public final class Realms {
    /** The path prefix of every realm's unlock advancement. */
    public static final String ADVANCEMENT_PREFIX = "realm/";

    /** The index of the last registry asked about: one per loaded world, rebuilt when the world changes. */
    private static volatile Index index;

    private record Index(Registry<Realm> registry, Map<ResourceKey<Level>, Holder.Reference<Realm>> byDimension,
            Map<Item, Holder.Reference<Realm>> bySigil) {
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
        return dimension == Level.OVERWORLD ? Optional.empty() : entry(registries, dimension);
    }

    /**
     * The entry whose dimension is {@code dimension}: a realm's, Overworld's, or empty for any other dimension (the
     * instances, the Nether, other mods' dimensions), where the realm portal does not open.
     */
    public static Optional<Holder.Reference<Realm>> entry(RegistryAccess registries, ResourceKey<Level> dimension) {
        return Optional.ofNullable(index(registries).byDimension().get(dimension));
    }

    /** Overworld's entry, the destination of return portals; empty only when no data pack gives it one. */
    public static Optional<Holder.Reference<Realm>> overworld(RegistryAccess registries) {
        return entry(registries, Level.OVERWORLD);
    }

    /** The realm whose sigil is {@code item}, or empty when it is no sigil. */
    public static Optional<Holder.Reference<Realm>> bySigil(RegistryAccess registries, Item item) {
        return Optional.ofNullable(index(registries).bySigil().get(item));
    }

    /** Whether {@code dimension} is a realm's. */
    public static boolean isRealm(RegistryAccess registries, ResourceKey<Level> dimension) {
        return of(registries, dimension).isPresent();
    }

    private static Index index(RegistryAccess registries) {
        Registry<Realm> registry = registries.lookupOrThrow(WorldRegistries.REALM);
        Index current = index;
        if (current == null || current.registry() != registry) {
            current = new Index(registry, byDimension(registry), bySigil(registry));
            index = current;
        }
        return current;
    }

    /**
     * The entries by their dimension. Our pipeline allows one per dimension; when another data pack adds a second, the
     * entry with the lowest id wins and the log says so.
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

    /** The realms by their sigil, the lowest id first when two share one; sigils that are no item are left out. */
    private static Map<Item, Holder.Reference<Realm>> bySigil(Registry<Realm> registry) {
        Map<Item, Holder.Reference<Realm>> bySigil = new HashMap<>();
        registry.listElements()
                .sorted(Comparator.comparing(holder -> holder.key().identifier()))
                .forEach(holder -> holder.value().sigil()
                        .flatMap(BuiltInRegistries.ITEM::getOptional)
                        .ifPresent(item -> bySigil.putIfAbsent(item, holder)));
        return Map.copyOf(bySigil);
    }
}
