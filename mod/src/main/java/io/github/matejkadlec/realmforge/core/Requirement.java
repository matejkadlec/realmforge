package io.github.matejkadlec.realmforge.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
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

    /** Whether the player meets every one of {@code requirements}; true for none. */
    static boolean all(List<Requirement> requirements, ServerPlayer player) {
        for (Requirement requirement : requirements) {
            if (!requirement.test(player)) {
                return false;
            }
        }
        return true;
    }
}
