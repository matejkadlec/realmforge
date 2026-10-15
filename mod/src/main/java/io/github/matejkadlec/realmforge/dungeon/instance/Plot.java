package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

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
            DataMarker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(plot -> plot.markers),
            Vec3.CODEC.optionalFieldOf("spawn").forGetter(Plot::spawn),
            BoundingBox.CODEC.listOf().optionalFieldOf("dirty", List.of()).forGetter(plot -> plot.dirty),
            Codec.INT.optionalFieldOf("step", 0).forGetter(Plot::step),
            UUIDUtil.CODEC.listOf().optionalFieldOf("party", List.of()).forGetter(plot -> plot.party),
            Codec.LONG.listOf().optionalFieldOf("chunks", List.of()).forGetter(plot -> List.copyOf(plot.chunks))
    ).apply(instance, Plot::new));

    private final int id;
    private PlotState state;
    @Nullable
    private InstanceLayout layout;
    /** Boxes of the current layout's rooms, in world coordinates. */
    private List<BoundingBox> rooms;
    /** The current layout's data markers, read from its room templates when the plot opened. */
    private List<DataMarker> markers;
    /** Where players arrive, relative to the origin; found when the rooms are built. */
    @Nullable
    private Vec3 spawn;
    /** Boxes that may still hold blocks from an earlier run and must be cleared: rooms and their margin. */
    private List<BoundingBox> dirty;
    /** Index of the next {@link PlotWork.Unit} while the plot has work. */
    private int step;
    private final List<UUID> party;
    /** Chunks this plot holds loaded through the ticket controller, in the order it took them. */
    private final LongSet chunks;
    /** Derived from the saved state; rebuilt after a restart. */
    @Nullable
    private List<PlotWork.Unit> units;
    @Nullable
    private LongSet wantedChunks;
    @Nullable
    private BuildReport.Recorder recorder;

    Plot(int id) {
        this(id, PlotState.FREE, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), 0, List.of(),
                List.of());
    }

    private Plot(int id, PlotState state, Optional<InstanceLayout> layout, List<BoundingBox> rooms,
            List<DataMarker> markers, Optional<Vec3> spawn, List<BoundingBox> dirty, int step, List<UUID> party,
            List<Long> chunks) {
        this.id = id;
        this.state = state;
        this.layout = layout.orElse(null);
        this.rooms = List.copyOf(rooms);
        this.markers = List.copyOf(markers);
        this.spawn = spawn.orElse(null);
        this.dirty = List.copyOf(dirty);
        this.step = step;
        this.party = new ArrayList<>(party);
        this.chunks = new LongLinkedOpenHashSet(chunks);
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

    /** The current run's data markers, positions relative to the origin (architecture 15.3, step 3). */
    public List<DataMarker> markers() {
        return markers;
    }

    /** The first marker of a kind in a room, in template order. */
    public Optional<DataMarker> marker(DataMarker.Kind kind, int room) {
        return markers.stream().filter(marker -> marker.kind() == kind && marker.room() == room).findFirst();
    }

    /** Where players arrive, relative to the origin; known once the rooms stand. */
    public Optional<Vec3> spawn() {
        return Optional.ofNullable(spawn);
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

    /** The chunks it holds loaded, as {@code ChunkPos#pack} values. */
    public LongSet chunks() {
        return chunks;
    }

    /**
     * Every chunk it must hold while it is not free: those of the boxes to clear, and while building also those of
     * its rooms with their margin, which it clears when it closes.
     */
    public LongSet wantedChunks() {
        if (wantedChunks == null) {
            wantedChunks = PlotWork.chunks(state == PlotState.BUILDING ? merged(dirty, PlotWork.withMargin(rooms))
                    : dirty);
        }
        return wantedChunks;
    }

    /** The work left for this plot, rebuilt from the saved state when needed. */
    List<PlotWork.Unit> units() {
        if (units == null) {
            List<BoundingBox> toPlace = state == PlotState.BUILDING ? rooms : List.of();
            units = state.hasWork() ? PlotWork.units(dirty, toPlace) : List.of();
        }
        return units;
    }

    BuildReport.Recorder recorder() {
        if (recorder == null) {
            int roomCount = state == PlotState.BUILDING ? rooms.size() : 0;
            // A job resumed after a restart reports only the units it runs from here on.
            recorder = new BuildReport.Recorder(id, state, units().size() - step, roomCount);
        }
        return recorder;
    }

    void advance() {
        step++;
    }

    void startBuilding(InstanceLayout layout, List<BoundingBox> rooms, List<DataMarker> markers, List<UUID> party) {
        this.layout = layout;
        this.rooms = List.copyOf(rooms);
        this.markers = List.copyOf(markers);
        this.spawn = null;
        this.party.clear();
        this.party.addAll(party);
        enter(PlotState.BUILDING);
    }

    /** Placing finished: players arrive at {@code spawn}, and the rooms with their margin are what closing clears. */
    void finishBuilding(Vec3 spawn) {
        this.spawn = spawn;
        dirty = merged(dirty, PlotWork.withMargin(rooms));
        enter(PlotState.READY);
    }

    /** A plot saved before spawns were kept gets one when a player enters. */
    void setSpawn(Vec3 spawn) {
        this.spawn = spawn;
    }

    /** A party member entered; a completed run stays completed when another one comes back. */
    void activate() {
        if (state == PlotState.READY) {
            state = PlotState.ACTIVE;
        }
    }

    void complete() {
        state = PlotState.COMPLETED;
    }

    /**
     * Start clearing everything this plot may hold, including the rooms of an unfinished build once a unit of it ran
     * (a build released before that placed nothing, so it has nothing to clear and no chunk to load for it).
     */
    void startClosing() {
        if (state == PlotState.BUILDING && step > 0) {
            dirty = merged(dirty, PlotWork.withMargin(rooms));
        }
        clearRun();
        enter(PlotState.CLOSING);
    }

    /** Clearing finished: the plot is empty and free for the next run. */
    void free() {
        dirty = List.of();
        clearRun();
        enter(PlotState.FREE);
    }

    boolean removeFromParty(UUID player) {
        return party.remove(player);
    }

    private void clearRun() {
        layout = null;
        rooms = List.of();
        markers = List.of();
        spawn = null;
        party.clear();
    }

    private void enter(PlotState next) {
        state = next;
        step = 0;
        units = null;
        wantedChunks = null;
        recorder = null;
    }

    private static List<BoundingBox> merged(List<BoundingBox> first, List<BoundingBox> second) {
        List<BoundingBox> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }
}
