package io.github.matejkadlec.realmforge.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotGrid;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlanner;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.LayoutRules;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * A kind of dungeon, an entry of the datapack registry {@code realmforge:dungeon_type} from {@code content/dungeons/}
 * (architecture 5.2, 15.2): the room pool it is built from and the rules of its layout. {@code rf gen} resolves the
 * pool into its rooms, so the entry holds everything the planner needs. Encounters, loot, the level range, the theme
 * and the key join with the tasks that use them.
 *
 * @param name translation key of its name
 * @param pool the room pool it is built from, as the content names it
 * @param layout the rules of its layout
 * @param rooms the pool's rooms, in room id order
 */
public record DungeonType(String name, Identifier pool, LayoutRules layout, List<DungeonRoom> rooms) {
    public static final Codec<DungeonType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(DungeonType::name),
                    Identifier.CODEC.fieldOf("pool").forGetter(DungeonType::pool),
                    LayoutRules.CODEC.fieldOf("layout").forGetter(DungeonType::layout),
                    DungeonRoom.CODEC.listOf().fieldOf("rooms").forGetter(DungeonType::rooms))
            .apply(instance, DungeonType::new));

    public DungeonType {
        rooms = List.copyOf(rooms);
    }

    /**
     * A layout for a plot, the same for the same seed.
     *
     * @throws io.github.matejkadlec.realmforge.dungeon.plan.PlanningException when no layout fits
     */
    public DungeonPlan plan(long seed) {
        return DungeonPlanner.plan(rooms, layout, seed, PlotGrid.limits());
    }
}
