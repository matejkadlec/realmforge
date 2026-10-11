package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One plot of the instance dimension and its saved state. Owned and mutated only by {@link InstanceManager},
 * which marks its saved data dirty after every change.
 */
public final class Plot {
    public static final Codec<Plot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(Plot::id),
            PlotState.CODEC.fieldOf("state").forGetter(Plot::state),
            InstanceLayout.CODEC.optionalFieldOf("layout").forGetter(plot -> Optional.ofNullable(plot.layout)),
            BoundingBox.CODEC.listOf().optionalFieldOf("rooms", List.of()).forGetter(plot -> plot.rooms),
            BoundingBox.CODEC.listOf().optionalFieldOf("dirty", List.of()).forGetter(plot -> plot.dirty),
            Codec.INT.optionalFieldOf("step", 0).forGetter(Plot::step),
            UUIDUtil.CODEC.listOf().optionalFieldOf("party", List.of()).forGetter(plot -> plot.party),
            Codec.LONG.listOf().optionalFieldOf("chunks", List.of()).forGetter(plot -> plot.chunks)
    ).apply(instance, Plot::new));

    private final int id;
    private PlotState state;
    @Nullable
    private InstanceLayout layout;
    /** Boxes of the current layout's rooms, in world coordinates. */
    private List<BoundingBox> rooms;
    /** Boxes that may still hold blocks from an earlier run and must be cleared before or instead of reuse. */
    private List<BoundingBox> dirty;
    /** Index of the next {@link PlotWork.Unit} while the plot has work. */
    private int step;
    private final List<UUID> party;
    /** Chunks this plot holds loaded through the ticket controller. */
    private final LongList chunks;
    /** Derived from the saved state; rebuilt after a restart. */
    @Nullable
    private List<PlotWork.Unit> units;
    @Nullable
    private BuildReport.Recorder recorder;

    Plot(int id) {
        this(id, PlotState.FREE, Optional.empty(), List.of(), List.of(), 0, List.of(), List.of());
    }

    private Plot(int id, PlotState state, Optional<InstanceLayout> layout, List<BoundingBox> rooms,
            List<BoundingBox> dirty, int step, List<UUID> party, List<Long> chunks) {
        this.id = id;
        this.state = state;
        this.layout = layout.orElse(null);
        this.rooms = List.copyOf(rooms);
        this.dirty = List.copyOf(dirty);
        this.step = step;
        this.party = new ArrayList<>(party);
        this.chunks = new LongArrayList(chunks);
    }

    public int id() {
        return id;
    }

    public BlockPos origin() {
        return PlotGrid.origin(id);
    }

    public PlotState state() {
        return state;
    }

    public Optional<InstanceLayout> layout() {
        return Optional.ofNullable(layout);
    }

    public List<BoundingBox> rooms() {
        return rooms;
    }

    public List<BoundingBox> dirty() {
        return dirty;
    }

    public int step() {
        return step;
    }

    /** The players this run belongs to (a list so parties can come later, RLM-68; solo in M1, decision D8). */
    public List<UUID> party() {
        return List.copyOf(party);
    }

    public LongList chunks() {
        return chunks;
    }

    /** The work left for this plot, rebuilt from the saved state when needed. */
    List<PlotWork.Unit> units() {
        if (units == null) {
            List<BoundingBox> toPlace = state == PlotState.BUILDING ? rooms : List.of();
            units = state.hasWork() ? PlotWork.units(dirty, toPlace) : List.of();
        }
        return units;
    }

    /** The boxes a {@link PlotWork.Clear} unit must leave alone: the rooms about to be placed over them. */
    List<BoundingBox> keepWhileClearing() {
        return state == PlotState.BUILDING ? rooms : List.of();
    }

    BuildReport.Recorder recorder() {
        if (recorder == null) {
            int roomCount = state == PlotState.BUILDING ? rooms.size() : 0;
            recorder = new BuildReport.Recorder(id, state, units().size(), roomCount);
        }
        return recorder;
    }

    void advance() {
        step++;
    }

    void startBuilding(InstanceLayout layout, List<BoundingBox> rooms, List<UUID> party) {
        this.layout = layout;
        this.rooms = List.copyOf(rooms);
        this.party.clear();
        this.party.addAll(party);
        enter(PlotState.BUILDING);
    }

    /** Placing finished: the rooms are now what a later run must clear. */
    void finishBuilding() {
        dirty = rooms;
        enter(PlotState.READY);
    }

    void activate() {
        state = PlotState.ACTIVE;
    }

    /** Start clearing everything this plot may hold, including rooms of an unfinished build. */
    void startClosing() {
        dirty = merged(dirty, state == PlotState.BUILDING ? rooms : List.of());
        clearRun();
        enter(PlotState.CLOSING);
    }

    /** Free the plot; {@code clean} says whether its blocks are gone (after closing) or stay for the next run. */
    void free(boolean clean) {
        if (clean) {
            dirty = List.of();
        } else if (state == PlotState.BUILDING) {
            dirty = merged(dirty, rooms);
        }
        clearRun();
        enter(PlotState.FREE);
    }

    boolean removeFromParty(UUID player) {
        return party.remove(player);
    }

    private void clearRun() {
        layout = null;
        rooms = List.of();
        party.clear();
    }

    private void enter(PlotState next) {
        state = next;
        step = 0;
        units = null;
        recorder = null;
    }

    private static List<BoundingBox> merged(List<BoundingBox> first, List<BoundingBox> second) {
        List<BoundingBox> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }
}
