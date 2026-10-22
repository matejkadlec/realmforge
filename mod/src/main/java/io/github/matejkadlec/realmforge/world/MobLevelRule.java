package io.github.matejkadlec.realmforge.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * How the level of a mob spawning in a dimension follows the nearest player, part of the dimension's
 * {@link DimensionRules} (RLM-240, architecture 14.1): a mob is up to {@link #spread()} levels below or above the
 * player, each offset with its weight from {@code weights} (lowest offset first, the middle one for the player's own
 * level), never below its floor nor above {@code maxLevel}. The floor is {@code entryLevel}, the realm's, or the
 * highest of {@code structureFloors} whose structure holds the spot (RLM-342). The floor and the cap drop offsets, and
 * the rest share the chance by their weights. A player below the floor meets every mob at it; a player so far above
 * the cap that no offset is left meets every mob at the cap. The pipeline computes the same
 * ({@code MobLevelRuleDef.chances}), and a GameTest compares the two at sample player levels.
 *
 * @param entryLevel the realm's floor: the level needed to enter it
 * @param weights the weight of each offset from the player's level, an odd number of them, each 1 to
 *     {@link #MAX_WEIGHT}
 * @param structureFloors the structures that raise the floor of the mobs they hold, each above {@code entryLevel}
 *     and at most {@code maxLevel}
 */
public record MobLevelRule(int entryLevel, int maxLevel, List<Integer> weights, List<StructureFloor> structureFloors) {
    /** The most offsets either side of the player's level, far beyond any sensible rule. */
    public static final int MAX_SPREAD = 10;
    /** The largest weight, so a sum of weights always fits an {@code int}. */
    public static final int MAX_WEIGHT = 1000;

    public static final Codec<MobLevelRule> CODEC = RecordCodecBuilder.<MobLevelRule>create(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.fieldOf("entry_level").forGetter(MobLevelRule::entryLevel),
                    ExtraCodecs.POSITIVE_INT.fieldOf("max_level").forGetter(MobLevelRule::maxLevel),
                    Codec.intRange(1, MAX_WEIGHT).listOf(1, 2 * MAX_SPREAD + 1).fieldOf("weights")
                            .forGetter(MobLevelRule::weights),
                    StructureFloor.CODEC.listOf().optionalFieldOf("structure_floors", List.of())
                            .forGetter(MobLevelRule::structureFloors))
            .apply(instance, MobLevelRule::new))
            .validate(MobLevelRule::validate);

    public MobLevelRule {
        weights = List.copyOf(weights);
        structureFloors = List.copyOf(structureFloors);
    }

    /** A rule whose floor is the realm's everywhere. */
    public MobLevelRule(int entryLevel, int maxLevel, List<Integer> weights) {
        this(entryLevel, maxLevel, weights, List.of());
    }

    /**
     * A structure's floor (RLM-342): a mob that gets its level inside one of the structure's pieces is never below
     * {@code level}.
     *
     * @param structure the structure ({@code minecraft:ancient_city}) or a structure tag ({@code #minecraft:village})
     */
    public record StructureFloor(ExtraCodecs.TagOrElementLocation structure, int level) {
        public static final Codec<StructureFloor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("structure").forGetter(StructureFloor::structure),
                        ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(StructureFloor::level))
                .apply(instance, StructureFloor::new));

        /** Whether {@code structure} is this floor's: the structure itself, or one in its tag. */
        public boolean covers(Holder<Structure> structure) {
            return this.structure.tag()
                    ? structure.is(TagKey.create(Registries.STRUCTURE, this.structure.id()))
                    : structure.is(this.structure.id());
        }
    }

    /**
     * The levels a mob spawning near a player of one level may get: from {@code lowest} up, one weight per level, not
     * normalised.
     */
    public record Chances(int lowest, List<Integer> weights) {
        public int highest() {
            return lowest + weights.size() - 1;
        }

        /** The weight of {@code level}, 0 outside the range. */
        public int weight(int level) {
            return level < lowest || level > highest() ? 0 : weights.get(level - lowest);
        }

        /** A level drawn from {@code random} by the weights. */
        public int roll(RandomSource random) {
            int total = weights.stream().mapToInt(Integer::intValue).sum();
            int left = random.nextInt(total);
            for (int i = 0; i < weights.size(); i++) {
                left -= weights.get(i);
                if (left < 0) {
                    return lowest + i;
                }
            }
            throw new IllegalStateException("a roll below the total always lands on a level");
        }
    }

    /** How many levels a mob may be below or above the player. */
    public int spread() {
        return weights.size() / 2;
    }

    /**
     * The highest floor {@code structure} gives the mobs it holds, or 0 when none of {@link #structureFloors} is its.
     */
    public int floorOf(Holder<Structure> structure) {
        int floor = 0;
        for (StructureFloor structureFloor : structureFloors) {
            if (structureFloor.level() > floor && structureFloor.covers(structure)) {
                floor = structureFloor.level();
            }
        }
        return floor;
    }

    /** The levels a mob spawning near a player of {@code playerLevel} may get at the realm's floor. */
    public Chances chances(int playerLevel) {
        return chances(playerLevel, entryLevel);
    }

    /**
     * The levels a mob spawning near a player of {@code playerLevel} may get, with their weights, where its floor is
     * {@code floor}: the realm's or a structure's, {@code entryLevel} to {@code maxLevel}.
     */
    public Chances chances(int playerLevel, int floor) {
        if (floor < entryLevel || floor > maxLevel) {
            throw new IllegalArgumentException("floor " + floor + " is outside " + entryLevel + " to " + maxLevel);
        }
        if (playerLevel < floor) {
            return new Chances(floor, List.of(1));
        }
        int lowest = Math.max(floor, playerLevel - spread());
        int highest = Math.min(maxLevel, playerLevel + spread());
        if (lowest > highest) {
            return new Chances(maxLevel, List.of(1));
        }
        int start = lowest - playerLevel + spread();
        return new Chances(lowest, weights.subList(start, start + highest - lowest + 1));
    }

    /** The level of a mob spawning near a player of {@code playerLevel}, drawn from {@code random}. */
    public int levelFor(int playerLevel, RandomSource random) {
        return chances(playerLevel).roll(random);
    }

    /**
     * The level of a mob spawning near a player of {@code playerLevel} where its floor is {@code floor}, drawn from
     * {@code random}.
     */
    public int levelFor(int playerLevel, int floor, RandomSource random) {
        return chances(playerLevel, floor).roll(random);
    }

    private static DataResult<MobLevelRule> validate(MobLevelRule rule) {
        if (rule.entryLevel() > rule.maxLevel()) {
            return DataResult.error(() -> "entry_level " + rule.entryLevel() + " is above max_level "
                    + rule.maxLevel());
        }
        if (rule.weights().size() % 2 == 0) {
            return DataResult.error(() -> "weights needs an odd number of entries, the middle one for offset 0: "
                    + rule.weights());
        }
        Set<ExtraCodecs.TagOrElementLocation> structures = new HashSet<>();
        for (StructureFloor floor : rule.structureFloors()) {
            if (floor.level() <= rule.entryLevel() || floor.level() > rule.maxLevel()) {
                return DataResult.error(() -> "the floor of " + floor.structure() + ", " + floor.level()
                        + ", is not above entry_level " + rule.entryLevel() + " and at most max_level "
                        + rule.maxLevel());
            }
            if (!structures.add(floor.structure())) {
                return DataResult.error(() -> floor.structure() + " has two structure floors");
            }
        }
        return DataResult.success(rule);
    }
}
