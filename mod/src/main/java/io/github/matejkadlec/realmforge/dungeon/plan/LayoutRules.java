package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.InclusiveRange;

/**
 * How the planner lays out a dungeon type (architecture 15.3): the entrance, a main path of rooms a player passes
 * through, the room that ends it, and side branches off the main path's rooms, each ending in a dead end. The planner
 * also closes every connector left over with a dead end, so a layout holds more rooms than these counts.
 *
 * @param mainPath rooms between the entrance and the ending
 * @param branches side branches off the main path's rooms
 * @param branchRooms rooms in each branch, its dead end included
 * @param ending the role of the room that ends the main path: the boss's lair or a treasury
 */
public record LayoutRules(InclusiveRange<Integer> mainPath, InclusiveRange<Integer> branches,
        InclusiveRange<Integer> branchRooms, RoomRole ending) {
    public static final Codec<LayoutRules> CODEC = RecordCodecBuilder.<LayoutRules>create(instance -> instance.group(
                    InclusiveRange.codec(Codec.INT, 1, 32).fieldOf("main_path").forGetter(LayoutRules::mainPath),
                    InclusiveRange.codec(Codec.INT, 0, 16).fieldOf("branches").forGetter(LayoutRules::branches),
                    InclusiveRange.codec(Codec.INT, 1, 8).fieldOf("branch_rooms").forGetter(LayoutRules::branchRooms),
                    RoomRole.CODEC.fieldOf("ending").forGetter(LayoutRules::ending))
            .apply(instance, LayoutRules::new)).validate(LayoutRules::validate);

    private static DataResult<LayoutRules> validate(LayoutRules rules) {
        return rules.ending.endsDungeons() ? DataResult.success(rules)
                : DataResult.error(() -> "a dungeon ends in the boss's lair or a treasury, not a "
                        + rules.ending.getSerializedName() + " room");
    }
}
