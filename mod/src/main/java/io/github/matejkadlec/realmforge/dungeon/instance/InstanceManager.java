package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.dungeon.DungeonAttachments;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import org.slf4j.Logger;

/**
 * Allocates plots in the instance dimension, builds and clears them over ticks, holds their chunks loaded, and
 * moves players in and out (architecture 15.5). Its state is a {@link SavedData} in the instance level, so plots
 * that were building or closing when the server stopped resume where they were.
 */
public final class InstanceManager extends SavedData {
    /** Time the builder may spend per server tick, over all plots together; one unit always runs. */
    public static final long TICK_BUDGET_NANOS = 5_000_000L;
    /** Holds every chunk of a plot that is building, playable or closing, also across restarts. */
    public static final TicketController TICKETS =
            new TicketController(Realmforge.id("instance_plots"), InstanceManager::validateTickets);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "realmforge_instances";
    private static final SavedData.Factory<InstanceManager> FACTORY =
            new SavedData.Factory<>(InstanceManager::new, InstanceManager::load);
    private static final Codec<Saved> SAVED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("next_id").forGetter(Saved::nextId),
            Codec.INT.listOf().optionalFieldOf("free", List.of()).forGetter(Saved::free),
            Plot.CODEC.listOf().optionalFieldOf("plots", List.of()).forGetter(Saved::plots)
    ).apply(instance, Saved::new));

    private int nextId;
    /** Freed plots, reused lowest id first so the world stays compact. */
    private final IntSortedSet free = new IntAVLTreeSet();
    private final Int2ObjectSortedMap<Plot> plots = new Int2ObjectAVLTreeMap<>();
    private final Int2ObjectMap<BuildReport> reports = new Int2ObjectOpenHashMap<>();
    private PlotCleanup cleanup = PlotCleanup.CLEAR;
    @Nullable
    private ServerLevel level;

    private InstanceManager() {
    }

    /** The manager of this server's instance dimension, or {@code null} when the dimension is missing. */
    @Nullable
    public static InstanceManager get(MinecraftServer server) {
        ServerLevel level = InstanceDimension.level(server);
        return level == null ? null : get(level);
    }

    private static InstanceManager get(ServerLevel level) {
        InstanceManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        return manager;
    }

    // ---- Queries ----

    public Optional<Plot> plot(int id) {
        return Optional.ofNullable(plots.get(id));
    }

    public Collection<Plot> plots() {
        return Collections.unmodifiableCollection(plots.values());
    }

    /** The plot the player's run belongs to, if they are in one: entered, or still being built for them. */
    public Optional<Plot> plotOf(ServerPlayer player) {
        Optional<Plot> entered = player.getExistingData(DungeonAttachments.VISIT).flatMap(visit -> plot(visit.plot()));
        if (entered.isPresent()) {
            return entered;
        }
        return plots.values().stream().filter(plot -> plot.party().contains(player.getUUID())).findFirst();
    }

    /** Timing of the last finished build or clearing of a plot, since the server started. */
    public Optional<BuildReport> lastReport(int plotId) {
        return Optional.ofNullable(reports.get(plotId));
    }

    public PlotCleanup cleanup() {
        return cleanup;
    }

    /** Changes the cleanup strategy for plots released from now on (not saved; measurement only). */
    public void setCleanup(PlotCleanup cleanup) {
        this.cleanup = cleanup;
    }

    // ---- Lifecycle ----

    /**
     * Takes a plot and starts building {@code layout} in it over the next ticks. Online party members enter
     * when it is built.
     *
     * @throws IllegalArgumentException when a room template is unknown or a room reaches outside the plot
     */
    public Plot open(InstanceLayout layout, List<UUID> party) {
        ServerLevel level = level();
        List<BoundingBox> relativeRooms = new ArrayList<>();
        for (RoomPlacement room : layout.rooms()) {
            BoundingBox box = InstanceBuilder.template(level, room)
                    .getBoundingBox(InstanceBuilder.settings(room), room.offset());
            if (!PlotGrid.fits(box)) {
                throw new IllegalArgumentException("room " + room.template() + " reaches outside its plot: " + box);
            }
            relativeRooms.add(box);
        }
        Plot plot;
        if (free.isEmpty()) {
            plot = new Plot(nextId++);
            plots.put(plot.id(), plot);
        } else {
            int id = free.firstInt();
            free.remove(id);
            plot = plots.get(id);
        }
        BlockPos origin = plot.origin();
        List<BoundingBox> rooms = relativeRooms.stream()
                .map(box -> box.moved(origin.getX(), origin.getY(), origin.getZ()))
                .toList();
        plot.startBuilding(layout, rooms, party);
        List<BoundingBox> touched = new ArrayList<>(plot.dirty());
        touched.addAll(rooms);
        holdChunks(plot, PlotWork.chunks(touched));
        setDirty();
        LOGGER.debug("Opened instance plot {} at {} for {}", plot.id(), origin, party);
        return plot;
    }

    /**
     * Does up to {@link #TICK_BUDGET_NANOS} of building and clearing; called once per tick of the instance level.
     * A plot whose next unit's chunks are still loading waits for a later tick.
     */
    public void tick() {
        ServerLevel level = this.level;
        if (level == null) {
            return;
        }
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        boolean changed = false;
        boolean first = true;
        for (Plot plot : List.copyOf(plots.values())) {
            if (!plot.state().hasWork()) {
                continue;
            }
            List<PlotWork.Unit> units = plot.units();
            while (plot.step() < units.size()) {
                long start = System.nanoTime();
                if (!first && start >= deadline) {
                    setDirty();
                    return;
                }
                PlotWork.Unit unit = units.get(plot.step());
                if (!InstanceBuilder.ready(level, unit)) {
                    break;
                }
                first = false;
                long cleared = InstanceBuilder.run(level, plot, unit);
                int room = unit instanceof PlotWork.Place place ? place.room() : -1;
                plot.recorder().unit(level.getGameTime(), room, System.nanoTime() - start, cleared);
                plot.advance();
                changed = true;
            }
            if (plot.step() >= units.size()) {
                finishWork(plot);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * Moves a party member into a playable plot and remembers where they came from. A player already inside the
     * plot keeps their first return point.
     */
    public boolean enter(ServerPlayer player, Plot plot) {
        if (!plot.state().isPlayable() || !plot.party().contains(player.getUUID())) {
            return false;
        }
        Optional<InstanceVisit> visit = player.getExistingData(DungeonAttachments.VISIT);
        if (visit.isPresent() && visit.get().plot() != plot.id()) {
            return false;
        }
        if (visit.isEmpty()) {
            player.setData(DungeonAttachments.VISIT, new InstanceVisit(plot.id(), player.level().dimension(),
                    player.position(), player.getYRot(), player.getXRot()));
        }
        InstanceLayout layout = plot.layout().orElseThrow();
        Vec3 spawn = Vec3.atLowerCornerOf(plot.origin()).add(layout.spawn());
        player.teleportTo(level(), spawn.x, spawn.y, spawn.z, layout.spawnYaw(), 0.0F);
        plot.activate();
        setDirty();
        return true;
    }

    /** Sends the player back to where they entered from and drops them from their run. */
    public boolean leave(ServerPlayer player) {
        InstanceVisit visit = player.getExistingDataOrNull(DungeonAttachments.VISIT);
        if (visit == null) {
            return false;
        }
        sendBack(player, visit);
        leftRun(player.getUUID(), visit.plot());
        return true;
    }

    /**
     * Ends a plot's run: players still inside are sent back, then the plot is cleaned up by the current
     * {@link PlotCleanup}.
     */
    public void release(Plot plot) {
        if (plot.state() == PlotState.CLOSING || plot.state() == PlotState.FREE) {
            return;
        }
        MinecraftServer server = level().getServer();
        for (UUID member : plot.party()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            InstanceVisit visit = player == null ? null : player.getExistingDataOrNull(DungeonAttachments.VISIT);
            if (visit != null && visit.plot() == plot.id()) {
                sendBack(player, visit);
            }
        }
        switch (cleanup) {
            case CLEAR -> plot.startClosing();
            case OVERWRITE -> {
                releaseChunks(plot);
                plot.free(false);
                free.add(plot.id());
            }
            case NEVER_REUSE -> {
                releaseChunks(plot);
                plots.remove(plot.id());
            }
        }
        setDirty();
    }

    // ---- Players outside the normal flow ----

    /**
     * After a login: a player inside the instance dimension stays only if their run still exists; anyone else is
     * sent back. A player outside it leaves the run they belong to: one they left while offline (they still hold
     * a visit), or one they never entered because they disconnected while it was being built.
     */
    public void onLogin(ServerPlayer player) {
        InstanceVisit visit = player.getExistingDataOrNull(DungeonAttachments.VISIT);
        if (!InstanceDimension.isInstance(player.level())) {
            if (visit != null) {
                player.removeData(DungeonAttachments.VISIT);
                leftRun(player.getUUID(), visit.plot());
            }
            for (Plot plot : List.copyOf(plots.values())) {
                if (plot.party().contains(player.getUUID())) {
                    leftRun(player.getUUID(), plot.id());
                }
            }
            return;
        }
        Plot plot = visit == null ? null : plots.get(visit.plot());
        if (plot != null && plot.state().isPlayable() && plot.party().contains(player.getUUID())) {
            return;
        }
        LOGGER.info("Sending {} out of the instance dimension: their run is gone",
                player.getGameProfile().getName());
        if (visit == null) {
            sendToSpawn(player);
        } else {
            sendBack(player, visit);
            leftRun(player.getUUID(), visit.plot());
        }
    }

    /** A player who holds a visit but is now outside the instance dimension (respawned, teleported) left the run. */
    public void onLeftDimension(ServerPlayer player) {
        InstanceVisit visit = player.getExistingDataOrNull(DungeonAttachments.VISIT);
        if (visit != null && !InstanceDimension.isInstance(player.level())) {
            player.removeData(DungeonAttachments.VISIT);
            leftRun(player.getUUID(), visit.plot());
        }
    }

    // ---- Internals ----

    private void finishWork(Plot plot) {
        BuildReport report = plot.recorder().finish();
        reports.put(plot.id(), report);
        LOGGER.info("Instance {}", report);
        if (plot.state() == PlotState.BUILDING) {
            plot.finishBuilding();
            MinecraftServer server = level().getServer();
            for (UUID member : plot.party()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                if (player != null) {
                    enter(player, plot);
                }
            }
        } else {
            releaseChunks(plot);
            plot.free(true);
            free.add(plot.id());
        }
    }

    /** Drops the player from the plot's party; a run left with nobody, built or still building, is released. */
    private void leftRun(UUID player, int plotId) {
        Plot plot = plots.get(plotId);
        if (plot != null && plot.removeFromParty(player) && plot.party().isEmpty()) {
            release(plot);
        }
        setDirty();
    }

    private void sendBack(ServerPlayer player, InstanceVisit visit) {
        player.removeData(DungeonAttachments.VISIT);
        ServerLevel target = player.server.getLevel(visit.returnLevel());
        if (target == null) {
            sendToSpawn(player);
            return;
        }
        Vec3 pos = visit.returnPos();
        player.teleportTo(target, pos.x, pos.y, pos.z, visit.returnYaw(), visit.returnPitch());
    }

    private static void sendToSpawn(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                overworld.getSharedSpawnAngle(), 0.0F);
    }

    private void holdChunks(Plot plot, LongSet chunks) {
        releaseChunks(plot);
        for (long chunk : chunks) {
            TICKETS.forceChunk(level(), plot.origin(), ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, false);
            plot.chunks().add(chunk);
        }
    }

    private void releaseChunks(Plot plot) {
        for (long chunk : plot.chunks()) {
            TICKETS.forceChunk(level(), plot.origin(), ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, false);
        }
        plot.chunks().clear();
    }

    private ServerLevel level() {
        if (level == null) {
            throw new IllegalStateException("instance manager used before its level was known");
        }
        return level;
    }

    /** Drops the saved tickets of plots that no longer need their chunks, before NeoForge reinstates the rest. */
    private static void validateTickets(ServerLevel level, TicketHelper tickets) {
        InstanceManager manager = InstanceDimension.isInstance(level) ? get(level) : null;
        for (BlockPos owner : tickets.getBlockTickets().keySet()) {
            Plot plot = manager == null ? null : manager.plots.get(PlotGrid.plotAt(owner.getX(), owner.getZ()));
            if (plot == null || !plot.origin().equals(owner) || plot.state() == PlotState.FREE) {
                LOGGER.info("Dropping stale instance chunk tickets of {} in {}", owner, level.dimension().location());
                tickets.removeAllTickets(owner);
            }
        }
        for (UUID owner : tickets.getEntityTickets().keySet()) {
            tickets.removeAllTickets(owner);
        }
    }

    // ---- Saving ----

    private record Saved(int nextId, List<Integer> free, List<Plot> plots) {
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Saved saved = new Saved(nextId, List.copyOf(free), List.copyOf(plots.values()));
        SAVED_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), saved)
                .resultOrPartial(error -> LOGGER.error("Could not save instance plots: {}", error))
                .ifPresent(encoded -> tag.put("instances", encoded));
        return tag;
    }

    private static InstanceManager load(CompoundTag tag, HolderLookup.Provider registries) {
        InstanceManager manager = new InstanceManager();
        if (!tag.contains("instances")) {
            return manager;
        }
        SAVED_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("instances"))
                .resultOrPartial(error -> LOGGER.error("Could not load instance plots: {}", error))
                .ifPresent(saved -> {
                    manager.nextId = saved.nextId();
                    manager.free.addAll(saved.free());
                    saved.plots().forEach(plot -> manager.plots.put(plot.id(), plot));
                });
        return manager;
    }
}
