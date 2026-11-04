package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Codecs;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * The value of a player's {@code realmforge:character} attachment (architecture 7 and 10): race, class, specialization,
 * total character XP, the cached level cap and the stat points spent.
 *
 * <p>Races and classes are saved as their ids, which is how F3 saved them before C1 added their registries, so
 * those saves stay readable. A key whose entry no longer exists (content removed) stays saved and gives nothing.
 * {@link #NONE} is a player without a character: no race, which is what makes the client show character creation.
 * Unspent stat points are not saved: they are the points the level has earned less the spent ones
 * ({@link StatPoints}), so a save from before stat points existed has every point of its levels unspent. Spell points
 * are not saved at all: the spellbook's ranks are their record ({@link SpellPoints}).
 *
 * <p>A player has one class, never two (RLM-218), so C5 (RLM-333) stores it as one value and the specialization beside
 * it, which is what the old list of classes from when dual class was planned became. A save with that list ({@code
 * classes}) still reads as its first class.
 *
 * @param race           the chosen race, empty until the player creates a character
 * @param playerClass    the chosen class, empty until the player creates a character
 * @param specialization the id of the specialization picked in the spell trees window, within the class; empty before
 * @param totalXp        character XP held, at most the XP of the level cap's level (XP stops at the cap, architecture
 *                       10.2)
 * @param levelCap       the cap last computed from completed chapters, empty until {@code character} computes it
 * @param statPoints     the stat points spent on each primary stat, each at least 1 (RLM-196)
 */
public record CharacterData(Optional<ResourceKey<Race>> race, Optional<ResourceKey<PlayerClass>> playerClass,
        Optional<String> specialization, long totalXp, Optional<Integer> levelCap,
        Map<ResourceKey<Attribute>, Integer> statPoints) {
    public static final CharacterData NONE = new CharacterData(Optional.empty(), Optional.empty(), 0, Optional.empty());

    public static final Codec<CharacterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(CharacterRegistries.RACE).optionalFieldOf("race").forGetter(CharacterData::race),
            ResourceKey.codec(CharacterRegistries.CLASS).optionalFieldOf("class").forGetter(CharacterData::playerClass),
            // Read only: a save from before C5 holds its class in this list, and nothing writes it any more.
            Codecs.distinctList(ResourceKey.codec(CharacterRegistries.CLASS)).optionalFieldOf("classes", List.of())
                    .forGetter(character -> List.of()),
            Codec.STRING.optionalFieldOf("specialization").forGetter(CharacterData::specialization),
            Codecs.NON_NEGATIVE_LONG.optionalFieldOf("total_xp", 0L).forGetter(CharacterData::totalXp),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("level_cap").forGetter(CharacterData::levelCap),
            Codec.unboundedMap(ResourceKey.codec(Registries.ATTRIBUTE), ExtraCodecs.POSITIVE_INT)
                    .optionalFieldOf("stat_points", Map.of()).forGetter(CharacterData::statPoints)
    ).apply(instance, (race, playerClass, oldClasses, specialization, totalXp, levelCap, statPoints) ->
            new CharacterData(race, playerClass.or(() -> oldClasses.stream().findFirst()), specialization, totalXp,
                    levelCap, statPoints)));

    public static final StreamCodec<ByteBuf, CharacterData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceKey.streamCodec(CharacterRegistries.RACE)), CharacterData::race,
            ByteBufCodecs.optional(ResourceKey.streamCodec(CharacterRegistries.CLASS)), CharacterData::playerClass,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), CharacterData::specialization,
            ByteBufCodecs.VAR_LONG, CharacterData::totalXp,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), CharacterData::levelCap,
            ByteBufCodecs.map(HashMap::new, ResourceKey.streamCodec(Registries.ATTRIBUTE), ByteBufCodecs.VAR_INT),
            CharacterData::statPoints,
            CharacterData::new);

    public CharacterData {
        Objects.requireNonNull(race, "race");
        Objects.requireNonNull(playerClass, "playerClass");
        Objects.requireNonNull(specialization, "specialization");
        Objects.requireNonNull(levelCap, "levelCap");
        if (totalXp < 0) {
            throw new IllegalArgumentException("Character XP is never negative: " + totalXp);
        }
        if (levelCap.isPresent() && levelCap.get() < 1) {
            throw new IllegalArgumentException("A level cap is at least 1: " + levelCap.get());
        }
        statPoints = Map.copyOf(statPoints);
        if (statPoints.values().stream().anyMatch(points -> points < 1)) {
            throw new IllegalArgumentException("A stat holds at least 1 spent point: " + statPoints);
        }
    }

    /** A character who has picked no specialization and spent no stat point. */
    public CharacterData(Optional<ResourceKey<Race>> race, Optional<ResourceKey<PlayerClass>> playerClass, long totalXp,
            Optional<Integer> levelCap) {
        this(race, playerClass, Optional.empty(), totalXp, levelCap, Map.of());
    }

    /** A character who has picked no specialization. */
    public CharacterData(Optional<ResourceKey<Race>> race, Optional<ResourceKey<PlayerClass>> playerClass, long totalXp,
            Optional<Integer> levelCap, Map<ResourceKey<Attribute>, Integer> statPoints) {
        this(race, playerClass, Optional.empty(), totalXp, levelCap, statPoints);
    }

    /** Whether the player has created a character; until then the server refuses their gameplay (architecture 10.1). */
    public boolean exists() {
        return race.isPresent();
    }

    /** The same character with {@code xp} as its total character XP. */
    public CharacterData withTotalXp(long xp) {
        return new CharacterData(race, playerClass, specialization, xp, levelCap, statPoints);
    }

    /** The same character with {@code cap} as its cached level cap. */
    public CharacterData withLevelCap(int cap) {
        return new CharacterData(race, playerClass, specialization, totalXp, Optional.of(cap), statPoints);
    }

    /** The same character with {@code points} as the stat points spent on each stat. */
    public CharacterData withStatPoints(Map<ResourceKey<Attribute>, Integer> points) {
        return new CharacterData(race, playerClass, specialization, totalXp, levelCap, points);
    }

    /** The same character with {@code id} as its specialization. */
    public CharacterData withSpecialization(String id) {
        return new CharacterData(race, playerClass, Optional.of(id), totalXp, levelCap, statPoints);
    }

    /** The same character with one more point spent on {@code stat}. */
    public CharacterData plusStatPoint(ResourceKey<Attribute> stat) {
        Map<ResourceKey<Attribute>, Integer> points = new HashMap<>(statPoints);
        points.merge(stat, 1, Integer::sum);
        return withStatPoints(points);
    }

    /** Every stat point spent, on any stat. */
    public int spentStatPoints() {
        return statPoints.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * A new character of the race and class, who has yet to pick a specialization: everything else as before (no XP is
     * lost by creating one).
     */
    public CharacterData create(ResourceKey<Race> newRace, ResourceKey<PlayerClass> newClass) {
        return new CharacterData(Optional.of(newRace), Optional.of(newClass), Optional.empty(), totalXp, levelCap,
                statPoints);
    }
}
