package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Codecs;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * The value of a player's {@code realmforge:character} attachment (architecture 7 and 10): race, classes (a list:
 * one in M1, room for dual class), total character XP and the cached level cap.
 *
 * <p>Races and classes are saved as their ids, which is how F3 saved them before C1 added their registries, so
 * those saves stay readable. A key whose entry no longer exists (content removed) stays saved and gives nothing.
 * {@link #NONE} is a player without a character: no race, which is what makes the client show character creation.
 *
 * @param race     the chosen race, empty until the player creates a character
 * @param classes  the player's classes in the order they were taken, without repeats
 * @param totalXp  every point of character XP earned, also beyond the level cap (architecture 10.2)
 * @param levelCap the cap last computed from completed chapters, empty until {@code character} computes it
 */
public record CharacterData(Optional<ResourceKey<Race>> race, List<ResourceKey<PlayerClass>> classes, long totalXp,
        Optional<Integer> levelCap) {
    public static final CharacterData NONE = new CharacterData(Optional.empty(), List.of(), 0, Optional.empty());

    public static final Codec<CharacterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(CharacterRegistries.RACE).optionalFieldOf("race").forGetter(CharacterData::race),
            Codecs.distinctList(ResourceKey.codec(CharacterRegistries.CLASS)).optionalFieldOf("classes", List.of())
                    .forGetter(CharacterData::classes),
            Codecs.NON_NEGATIVE_LONG.optionalFieldOf("total_xp", 0L).forGetter(CharacterData::totalXp),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("level_cap").forGetter(CharacterData::levelCap)
    ).apply(instance, CharacterData::new));

    public static final StreamCodec<ByteBuf, CharacterData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceKey.streamCodec(CharacterRegistries.RACE)), CharacterData::race,
            ResourceKey.streamCodec(CharacterRegistries.CLASS).apply(ByteBufCodecs.list()), CharacterData::classes,
            ByteBufCodecs.VAR_LONG, CharacterData::totalXp,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), CharacterData::levelCap,
            CharacterData::new);

    public CharacterData {
        Objects.requireNonNull(race, "race");
        Objects.requireNonNull(levelCap, "levelCap");
        classes = List.copyOf(classes);
        if (new HashSet<>(classes).size() != classes.size()) {
            throw new IllegalArgumentException("A class is listed twice: " + classes);
        }
        if (totalXp < 0) {
            throw new IllegalArgumentException("Character XP is never negative: " + totalXp);
        }
        if (levelCap.isPresent() && levelCap.get() < 1) {
            throw new IllegalArgumentException("A level cap is at least 1: " + levelCap.get());
        }
    }

    /** Whether the player has created a character; until then the server refuses their gameplay (architecture 10.1). */
    public boolean exists() {
        return race.isPresent();
    }

    /** The same character with {@code xp} as its total character XP. */
    public CharacterData withTotalXp(long xp) {
        return new CharacterData(race, classes, xp, levelCap);
    }

    /** The same character with {@code cap} as its cached level cap. */
    public CharacterData withLevelCap(int cap) {
        return new CharacterData(race, classes, totalXp, Optional.of(cap));
    }

    /** A new character of the race and class: everything else as before (no XP is lost by creating one). */
    public CharacterData create(ResourceKey<Race> newRace, ResourceKey<PlayerClass> playerClass) {
        return new CharacterData(Optional.of(newRace), List.of(playerClass), totalXp, levelCap);
    }
}
