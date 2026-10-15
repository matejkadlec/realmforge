package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/**
 * How much kill XP the mobs of one entity type give, as a multiple of a normal mob's (architecture 10.2): an entry of
 * the server-only datapack registry {@code realmforge:kill_xp_multiplier} from {@code content/kill_xp_multipliers/}.
 * The XP model (RLM-192) calls it the mob's rank: 0.1 for a swarm mob, 1.5 for a strong one, 10 for a boss. A hostile
 * mob whose type has no entry gives 1. Slimes and magma cubes may give by size ({@code by_size}). The pipeline computes
 * the same ({@code multiplier_at} in {@code tools/realmforge_tools/schema/kill_xp_multiplier.py}), and a GameTest
 * compares the two.
 *
 * @param entity the entity type it applies to; validation allows one entry per type
 * @param multiplier the multiplier, 0 for a mob that gives nothing
 * @param bySize multipliers by size, in increasing {@code maxSize} order: the first one whose {@code maxSize} the mob
 *               does not exceed applies, and larger mobs take {@code multiplier}
 */
public record KillXpMultiplier(Holder<EntityType<?>> entity, double multiplier, List<SizeMultiplier> bySize) {
    /** {@code Slime#setSize} clamps a size to 1 .. 127. */
    public static final int MAX_SIZE = 127;
    /** A guard against typos, not a balance number: a realm guardian's rank is 20 (RLM-192). */
    public static final double MAX_MULTIPLIER = 1000;
    /** What a hostile mob gives when its type has no entry. */
    public static final double DEFAULT = 1.0;

    public static final Codec<KillXpMultiplier> CODEC = RecordCodecBuilder.<KillXpMultiplier>create(instance ->
                    instance.group(
                            BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().fieldOf("entity")
                                    .forGetter(KillXpMultiplier::entity),
                            Codec.doubleRange(0, MAX_MULTIPLIER).fieldOf("multiplier")
                                    .forGetter(KillXpMultiplier::multiplier),
                            SizeMultiplier.CODEC.listOf().optionalFieldOf("by_size", List.of())
                                    .forGetter(KillXpMultiplier::bySize))
                    .apply(instance, KillXpMultiplier::new))
            .validate(KillXpMultiplier::validate);

    public KillXpMultiplier {
        bySize = List.copyOf(bySize);
    }

    /** The multiplier of a mob of {@link #entity} with {@code size}, empty for a mob without a size. */
    public double at(OptionalInt size) {
        if (size.isPresent()) {
            for (SizeMultiplier entry : bySize) {
                if (size.getAsInt() <= entry.maxSize()) {
                    return entry.multiplier();
                }
            }
        }
        return multiplier;
    }

    private static DataResult<KillXpMultiplier> validate(KillXpMultiplier value) {
        List<SizeMultiplier> sizes = value.bySize();
        for (int i = 1; i < sizes.size(); i++) {
            int before = sizes.get(i - 1).maxSize();
            int after = sizes.get(i).maxSize();
            if (after <= before) {
                return DataResult.error(() -> "by_size max_size must strictly increase, but " + after + " follows "
                        + before);
            }
        }
        return DataResult.success(value);
    }

    /** The multiplier for mobs up to a size. */
    public record SizeMultiplier(int maxSize, double multiplier) {
        public static final Codec<SizeMultiplier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.intRange(1, MAX_SIZE).fieldOf("max_size").forGetter(SizeMultiplier::maxSize),
                        Codec.doubleRange(0, MAX_MULTIPLIER).fieldOf("multiplier").forGetter(SizeMultiplier::multiplier))
                .apply(instance, SizeMultiplier::new));
    }
}
