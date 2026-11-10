package io.github.matejkadlec.realmforge.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a player needs for something: entering a dimension so far, later a dungeon, an item or a talent (architecture
 * 5.3). An entry of the type registry {@code realmforge:requirement_type}, selected by its {@code type}:
 * {@code has_advancement} here in {@code core}, {@code level_at_least} in {@code character} and
 * {@code chapter_completed} in {@code progression}, each registered by the feature that knows the fact. The pipeline
 * knows the same types ({@code tools/realmforge_tools/schema/requirement.py}).
 */
public interface Requirement {
    /** A requirement by its {@code type}, e.g. {@code {"type": "realmforge:level_at_least", "level": 6}}. */
    Codec<Requirement> CODEC = CoreRegistries.REQUIREMENT_TYPES.byNameCodec().dispatch(Requirement::codec, c -> c);

    MapCodec<? extends Requirement> codec();

    /** Whether the player meets it. Logical server only. */
    boolean test(ServerPlayer player);

    /**
     * What to tell a player who does not meet it, when the requirement can say so by itself: a level requirement
     * names its level (RLM-306). Empty by default, where whoever asks has the better hint (a realm's unlock hint for
     * its advancement).
     */
    default Optional<Component> unmetHint() {
        return Optional.empty();
    }

    /** Whether the player meets every one of {@code requirements}; true for none. */
    static boolean all(List<Requirement> requirements, ServerPlayer player) {
        return firstUnmet(requirements, player).isEmpty();
    }

    /** The first of {@code requirements}, in their order, that the player does not meet; empty when they meet all. */
    static Optional<Requirement> firstUnmet(List<Requirement> requirements, ServerPlayer player) {
        for (Requirement requirement : requirements) {
            if (!requirement.test(player)) {
                return Optional.of(requirement);
            }
        }
        return Optional.empty();
    }
}
