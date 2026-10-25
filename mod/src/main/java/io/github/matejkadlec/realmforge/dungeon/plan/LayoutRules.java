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
 * @param pacing if set, the rhythm of corridors, fights and other rooms along the way (RLM-364); unset, any room that
 *     fits a slot may take it
 */
public record LayoutRules(InclusiveRange<Integer> mainPath, InclusiveRange<Integer> branches,
        InclusiveRange<Integer> branchRooms, RoomRole ending, Optional<Pacing> pacing) {
    public static final Codec<LayoutRules> CODEC = RecordCodecBuilder.<LayoutRules>create(instance -> instance.group(
                    InclusiveRange.codec(Codec.INT, 1, 96).fieldOf("main_path").forGetter(LayoutRules::mainPath),
                    InclusiveRange.codec(Codec.INT, 0, 16).fieldOf("branches").forGetter(LayoutRules::branches),
                    InclusiveRange.codec(Codec.INT, 1, 8).fieldOf("branch_rooms").forGetter(LayoutRules::branchRooms),
                    RoomRole.CODEC.fieldOf("ending").forGetter(LayoutRules::ending),
                    Pacing.CODEC.optionalFieldOf("pacing").forGetter(LayoutRules::pacing))
            .apply(instance, LayoutRules::new)).validate(LayoutRules::validate);

    /** Rules without pacing: any room that fits a slot may take it. */
    public LayoutRules(InclusiveRange<Integer> mainPath, InclusiveRange<Integer> branches,
            InclusiveRange<Integer> branchRooms, RoomRole ending) {
        this(mainPath, branches, branchRooms, ending, Optional.empty());
    }

    private static DataResult<LayoutRules> validate(LayoutRules rules) {
        return rules.ending.endsDungeons() ? DataResult.success(rules)
                : DataResult.error(() -> "a dungeon ends in the boss's lair or a treasury, not a "
                        + rules.ending.getSerializedName() + " room");
    }

    /**
     * The rhythm of a layout (RLM-364, Matěj's playtest of 2026-10-24): the main path is runs of corridors, each
     * followed by a room that is none, a fight once enough rooms lie behind the last one, else a feature (a lock, a
     * trap, a room with something to see or take). Each range is drawn binomially, so its middle comes most often
     * ({@code [2, 4]}: 2 a quarter of the time, 3 half, 4 a quarter).
     *
     * @param corridors corridor rooms in a row before a room that is none
     * @param fights rooms between two fights on the way from the entrance, corridors and features alike
     * @param repeat rooms on the way from the entrance between two copies of the same room that is no corridor
     */
    public record Pacing(InclusiveRange<Integer> corridors, InclusiveRange<Integer> fights, int repeat) {
        public static final Codec<Pacing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        InclusiveRange.codec(Codec.INT, 1, 8).fieldOf("corridors").forGetter(Pacing::corridors),
                        InclusiveRange.codec(Codec.INT, 1, 16).fieldOf("fights").forGetter(Pacing::fights),
                        Codec.intRange(0, 64).optionalFieldOf("repeat", 0).forGetter(Pacing::repeat))
                .apply(instance, Pacing::new));
    }
}
