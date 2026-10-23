package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.InclusiveRange;

/**
 * How the planner lays out a dungeon type (architecture 15.3): the entrance, a main path of rooms a player passes
 * through, the room that ends it, and side branches off the main path's rooms, each ending in a dead end. The planner
 * also closes every connector left over with a dead end or a wall, so a layout holds more rooms than these counts.
 *
 * @param mainPath rooms between the entrance and the ending
 * @param branches side branches off the main path's rooms
 * @param branchRooms rooms in each branch, its dead end included
 * @param ending the role of the room that ends the main path: the boss's lair or a treasury
 * @param specialGap if set, the plain rooms (hallways, stairs) between two special ones (fights, treasure) on the way
 *     from the entrance (RLM-364); unset, any room that fits a slot may take it
 */
public record LayoutRules(InclusiveRange<Integer> mainPath, InclusiveRange<Integer> branches,
        InclusiveRange<Integer> branchRooms, RoomRole ending, Optional<InclusiveRange<Integer>> specialGap) {
    public static final Codec<LayoutRules> CODEC = RecordCodecBuilder.<LayoutRules>create(instance -> instance.group(
                    InclusiveRange.codec(Codec.INT, 1, 32).fieldOf("main_path").forGetter(LayoutRules::mainPath),
                    InclusiveRange.codec(Codec.INT, 0, 16).fieldOf("branches").forGetter(LayoutRules::branches),
                    InclusiveRange.codec(Codec.INT, 1, 8).fieldOf("branch_rooms").forGetter(LayoutRules::branchRooms),
                    RoomRole.CODEC.fieldOf("ending").forGetter(LayoutRules::ending),
                    InclusiveRange.codec(Codec.INT, 1, 16).optionalFieldOf("special_gap")
                            .forGetter(LayoutRules::specialGap))
            .apply(instance, LayoutRules::new)).validate(LayoutRules::validate);

    /** Rules without a {@code special_gap}: any room that fits a slot may take it. */
    public LayoutRules(InclusiveRange<Integer> mainPath, InclusiveRange<Integer> branches,
            InclusiveRange<Integer> branchRooms, RoomRole ending) {
        this(mainPath, branches, branchRooms, ending, Optional.empty());
    }

    private static DataResult<LayoutRules> validate(LayoutRules rules) {
        return rules.ending.endsDungeons() ? DataResult.success(rules)
                : DataResult.error(() -> "a dungeon ends in the boss's lair or a treasury, not a "
                        + rules.ending.getSerializedName() + " room");
    }
}
