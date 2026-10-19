package io.github.matejkadlec.realmforge.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Requirement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The gameplay rules of one dimension, an entry of the datapack registry {@code realmforge:dimension_rules} from
 * {@code content/dimensions/} (architecture 5.2, 15.1). The entry names its dimension in a field, so its own id stays
 * ours ({@code realmforge:overworld} holds the rules of {@code minecraft:overworld}): the rule for mob levels, and what
 * a player needs to enter the dimension, which {@link RealmAccess} checks on every way in (W2a).
 *
 * <p>The registry stays on the server. {@link #of} finds a dimension's rules through an index built once per
 * registry, so a spawn costs one map lookup.
 *
 * @param mobLevels the level of mobs spawning there; without it, mobs there have no level
 * @param access what a player needs to enter the dimension, every one of them; none: open to everyone
 */
public record DimensionRules(ResourceKey<Level> dimension, Optional<MobLevelRule> mobLevels, List<Requirement> access) {
    public static final Codec<DimensionRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(DimensionRules::dimension),
                    MobLevelRule.CODEC.optionalFieldOf("mob_levels").forGetter(DimensionRules::mobLevels),
                    Requirement.CODEC.listOf().optionalFieldOf("access", List.of()).forGetter(DimensionRules::access))
            .apply(instance, DimensionRules::new));

    /** The index of the last registry asked about: one per loaded world, rebuilt when the world changes. */
    private static volatile Index index;

    private record Index(Registry<DimensionRules> registry, Map<ResourceKey<Level>, DimensionRules> byDimension) {
    }

    /** The rules of {@code dimension}, or empty when no entry names it. */
    public static Optional<DimensionRules> of(RegistryAccess registries, ResourceKey<Level> dimension) {
        Registry<DimensionRules> registry = registries.lookupOrThrow(WorldRegistries.DIMENSION_RULES);
        Index current = index;
        if (current == null || current.registry() != registry) {
            current = new Index(registry, byDimension(registry));
            index = current;
        }
        return Optional.ofNullable(current.byDimension().get(dimension));
    }

    /** The rule for mob levels in {@code dimension}, or empty when it has none. */
    public static Optional<MobLevelRule> mobLevels(RegistryAccess registries, ResourceKey<Level> dimension) {
        return of(registries, dimension).flatMap(DimensionRules::mobLevels);
    }

    /**
     * The entries by the dimension they name. Our pipeline allows one per dimension; when another data pack adds a
     * second, the entry with the lowest id wins and the log says so.
     */
    private static Map<ResourceKey<Level>, DimensionRules> byDimension(Registry<DimensionRules> registry) {
        Map<ResourceKey<Level>, DimensionRules> byDimension = new HashMap<>();
        Map<ResourceKey<Level>, ResourceKey<DimensionRules>> winners = new HashMap<>();
        registry.listElements()
                .sorted(Comparator.comparing(holder -> holder.key().identifier()))
                .forEach(holder -> add(byDimension, winners, holder));
        return Map.copyOf(byDimension);
    }

    private static void add(Map<ResourceKey<Level>, DimensionRules> byDimension,
            Map<ResourceKey<Level>, ResourceKey<DimensionRules>> winners, Holder.Reference<DimensionRules> holder) {
        ResourceKey<Level> dimension = holder.value().dimension();
        ResourceKey<DimensionRules> winner = winners.putIfAbsent(dimension, holder.key());
        if (winner != null) {
            Realmforge.LOGGER.warn("Dimension rules {} and {} both name {}; using {}", winner.identifier(),
                    holder.key().identifier(), dimension.identifier(), winner.identifier());
            return;
        }
        byDimension.put(dimension, holder.value());
    }
}
