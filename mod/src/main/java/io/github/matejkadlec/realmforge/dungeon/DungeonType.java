package io.github.matejkadlec.realmforge.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotGrid;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlanner;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.LayoutRules;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.progression.LevelCaps;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

/**
 * A kind of dungeon, an entry of the datapack registry {@code realmforge:dungeon_type} from {@code content/dungeons/}
 * (architecture 5.2, 15.2): the room pool it is built from, the rules of its layout, the key that opens a dungeon
 * portal to it, how long that portal stays open (W4d, 15.5) and its base level (RLM-459). {@code rf gen} resolves the
 * pool into its rooms, so the entry holds everything the planner needs. Encounters, loot and the theme join with the
 * tasks that use them.
 *
 * @param name translation key of its name
 * @param pool the room pool it is built from, as the content names it
 * @param layout the rules of its layout
 * @param key the item that opens a portal to it; the item tag {@link #KEYS} holds every type's key
 * @param openMinutes how long a portal to it stays open
 * @param level its base level: its boss's level, as the campaign places it; a run of it is at least this level, raised
 *              to its party's highest (RLM-240)
 * @param rooms the pool's rooms, in room id order
 */
public record DungeonType(String name, Identifier pool, LayoutRules layout, Identifier key, int openMinutes, int level,
        List<DungeonRoom> rooms) {
    public static final Codec<DungeonType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(DungeonType::name),
                    Identifier.CODEC.fieldOf("pool").forGetter(DungeonType::pool),
                    LayoutRules.CODEC.fieldOf("layout").forGetter(DungeonType::layout),
                    Identifier.CODEC.fieldOf("key").forGetter(DungeonType::key),
                    ExtraCodecs.POSITIVE_INT.fieldOf("open_minutes").forGetter(DungeonType::openMinutes),
                    Codec.intRange(EntityLevel.MIN, LevelCaps.MAX_LEVEL).fieldOf("level").forGetter(DungeonType::level),
                    DungeonRoom.CODEC.listOf().fieldOf("rooms").forGetter(DungeonType::rooms))
            .apply(instance, DungeonType::new));
    /**
     * Every dungeon type's key ({@code generate/dungeons.py}): the client reads it to know which items a portal takes,
     * as the dungeon types stay on the server.
     */
    public static final TagKey<Item> KEYS = TagKey.create(Registries.ITEM, Realmforge.id("dungeon_key"));
    private static final long TICKS_PER_MINUTE = 60L * 20L;

    public DungeonType {
        rooms = List.copyOf(rooms);
    }

    /** How long a portal to it stays open, in ticks. */
    public long openTicks() {
        return openMinutes * TICKS_PER_MINUTE;
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
