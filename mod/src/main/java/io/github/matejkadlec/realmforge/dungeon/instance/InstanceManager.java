package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.dungeon.DungeonAttachments;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.PlanningException;
import io.github.matejkadlec.realmforge.dungeon.portal.DungeonBlocks;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.progression.ProgressionStats;
import io.github.matejkadlec.realmforge.progression.ProgressionTriggers;
import io.github.matejkadlec.realmforge.world.BlockKinds;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Allocates plots in the instance dimension, builds and clears them over ticks, holds their chunks loaded, and
 * moves players in and out (architecture 15.5). Its state is a {@link SavedData} in the instance level, so plots
 * that were building or closing when the server stopped resume where they were.
 */
public final class InstanceManager extends SavedData {
    /**
     * Time the builder may spend per server tick, over all plots together. A plot being built gets one unit every
     * tick even past it, and some unit always runs.
     */
    public static final long TICK_BUDGET_NANOS = 5_000_000L;
    /** Holds every chunk of a plot that is building, playable or closing, also across restarts. */
    public static final TicketController TICKETS =
            new TicketController(Realmforge.id("instance_plots"), InstanceManager::validateTickets);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, Realmforge.MOD_ID);
    /**
     * Loads a chunk a plot needs in the background until {@link #TICKETS} holds it: holding a chunk that is not
     * loaded yet ({@code TicketController#forceChunk}) loads it on the main thread. Renewed every tick until then, and
     * gone a second after the chunk loaded; not saved, as a restart asks again.
     */
    private static final DeferredHolder<TicketType, TicketType> LOADING = TICKET_TYPES.register("instance_loading",
            () -> new TicketType(20L, TicketType.FLAG_LOADING));
    private static final Codec<InstanceManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("next_id").forGetter(manager -> manager.nextId),
            Codec.INT.listOf().optionalFieldOf("free", List.of()).forGetter(manager -> List.copyOf(manager.free)),
            Plot.CODEC.listOf().optionalFieldOf("plots", List.of())
                    .forGetter(manager -> List.copyOf(manager.plots.values())),
            Codec.LONG.optionalFieldOf("next_run", 1L).forGetter(manager -> manager.nextRun),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Identifier.CODEC.listOf()).optionalFieldOf("refunds", Map.of())
                    .forGetter(manager -> Map.copyOf(manager.refunds))
    ).apply(instance, InstanceManager::new));
    /**
     * Saved as {@code data/realmforge/instances.dat} in the instance dimension's folder. Data that fails to parse is
     * logged and replaced by an empty manager (vanilla's {@code SavedDataStorage}).
     */
    private static final SavedDataType<InstanceManager> TYPE =
            new SavedDataType<>(Realmforge.id("instances"), InstanceManager::new, CODEC);

    private int nextId;
    /** The id the next run gets; ids are never reused, so a portal can tell its run from a later one in its plot. */
    private long nextRun = 1L;
    /** Keys to give back to players who were offline when their dungeon could not be built, given at their login. */
    private final Map<UUID, List<Identifier>> refunds = new HashMap<>();
    /** Freed plots, reused lowest id first so the world stays compact. */
    private final IntSortedSet free = new IntAVLTreeSet();
    private final Int2ObjectSortedMap<Plot> plots = new Int2ObjectAVLTreeMap<>();
    private final Int2ObjectMap<BuildReport> reports = new Int2ObjectOpenHashMap<>();
    /** The plot that ran the last unit; the next round of the tick budget starts after it. */
    private int lastServed = -1;
    @Nullable
    private ServerLevel level;

    private InstanceManager() {
    }

    private InstanceManager(int nextId, List<Integer> free, List<Plot> plots, long nextRun,
            Map<UUID, List<Identifier>> refunds) {
        this.nextId = nextId;
        this.free.addAll(free);
        plots.forEach(plot -> this.plots.put(plot.id(), plot));
        this.nextRun = Math.max(1L, nextRun);
        refunds.forEach((player, keys) -> this.refunds.put(player, new ArrayList<>(keys)));
    }

    /** Registers the chunk ticket type and the ticket controller plots load and hold their chunks with. */
    public static void register(IEventBus modBus) {
        TICKET_TYPES.register(modBus);
        AttachedEntityProcessor.register(modBus);
        modBus.addListener(RegisterTicketControllersEvent.class, event -> event.register(TICKETS));
    }

    /** The manager of this server's instance dimension, or {@code null} when the dimension is missing. */
    @Nullable
    public static InstanceManager get(MinecraftServer server) {
        ServerLevel level = InstanceDimension.level(server);
        return level == null ? null : get(level);
    }

    private static InstanceManager get(ServerLevel level) {
        InstanceManager manager = level.getDataStorage().computeIfAbsent(TYPE);
        manager.level = level;
        return manager;
    }

    /**
     * Loads the room templates of every dungeon type, so no run waits for one to load and upgrade from its data
     * version (spike 3: up to 54 ms for a room). Called when the server starts, and after a reload, which empties the
     * structure manager's cache ({@link #preloadAfterReload}).
     */
    public static void preloadTemplates(MinecraftServer server) {
        long start = System.nanoTime();
        Set<Identifier> templates = new LinkedHashSet<>();
        int types = 0;
        for (DungeonType type : server.registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)) {
            types++;
            type.rooms().stream().map(DungeonRoom::template).forEach(templates::add);
        }
        StructureTemplateManager structures = server.getStructureManager();
        for (Identifier template : templates) {
            if (structures.get(template).isEmpty()) {
                LOGGER.warn("Room template {} of a dungeon type does not load", template);
            }
        }
        LOGGER.info("Loaded the {} room templates of {} dungeon types in {} ms", templates.size(), types,
                (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * Loads the room templates again after a reload. The reload asks for this from inside its server task, before it
     * empties the structure manager's cache ({@code MinecraftServer#reloadResources} posts the sync event first), so
     * the loading is queued behind that task; outside a server task it runs at once.
     */
    public static void preloadAfterReload(MinecraftServer server) {
        server.execute(() -> preloadTemplates(server));
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

    // ---- Lifecycle ----

    /**
     * Takes a plot and starts building {@code layout} in it over the next ticks, after its chunks have loaded in the
     * background. Online party members enter when it is built. The run has no timer and no level.
     *
     * @throws IllegalArgumentException when a room template is unknown or a room reaches outside the plot
     */
    public Plot open(InstanceLayout layout, List<UUID> party) {
        return open(layout, party, null, 0L, OptionalInt.empty());
    }

    /**
     * Opens a run of {@code type} for {@code player} through the dungeon portal at {@code portal} (W4d): plans a layout
     * from a new seed and starts building it. The run closes after the type's open time, and its level is the type's
     * base raised to the player's (RLM-459); the player enters through the portal once it is built, not by themselves.
     *
     * @throws PlanningException when no layout of the type fits a plot
     * @throws IllegalArgumentException when a room template is unknown or a room reaches outside the plot
     */
    public Plot openPortal(ServerPlayer player, Holder.Reference<DungeonType> type, GlobalPos portal) {
        DungeonType dungeon = type.value();
        DungeonPlan plan = dungeon.plan(level().getRandom().nextLong());
        return open(plan.toInstanceLayout(), List.of(player.getUUID()),
                new PortalLink(type.key().identifier(), portal), dungeon.openTicks(), OptionalInt.of(dungeon.level()));
    }

    /**
     * {@link #open(InstanceLayout, List)} for a run opened through a dungeon portal, which closes {@code openTicks}
     * after now; public for the GameTests, which open portal runs of layouts of their own.
     */
    public Plot open(InstanceLayout layout, List<UUID> party, @Nullable PortalLink portal, long openTicks) {
        return open(layout, party, portal, openTicks, OptionalInt.empty());
    }

    /**
     * {@link #open(InstanceLayout, List, PortalLink, long)} for a run of a dungeon type whose base level is
     * {@code baseLevel}: the run's level is that base raised to the highest level of the party members online now,
     * and holds for the whole run (RLM-459). No portal ({@code null}) means no timer. Public for the QA commands and
     * GameTests, which open runs of a type's or of their own layouts. A run through a portal stands in its portal's
     * dungeon type's biome.
     */
    public Plot open(InstanceLayout layout, List<UUID> party, @Nullable PortalLink portal, long openTicks,
            OptionalInt baseLevel) {
        return open(layout, party, portal, openTicks, baseLevel, portal == null ? null : portal.type());
    }

    /**
     * {@link #open(InstanceLayout, List, PortalLink, long, OptionalInt)} for a run planned from the dungeon type
     * {@code dungeonType}, whose rooms stand in that type's biome (RLM-409, {@link InstanceBiomes}); {@code null}
     * leaves them in the void. Public for the QA commands and GameTests, which open runs of a type without a portal.
     */
    public Plot open(InstanceLayout layout, List<UUID> party, @Nullable PortalLink portal, long openTicks,
            OptionalInt baseLevel, @Nullable Identifier dungeonType) {
        ServerLevel level = level();
        List<BoundingBox> relativeRooms = new ArrayList<>();
        List<DataMarker> markers = new ArrayList<>();
        for (int index = 0; index < layout.rooms().size(); index++) {
            RoomPlacement room = layout.rooms().get(index);
            StructureTemplate template = InstanceBuilder.template(level, room);
            BoundingBox box = template.getBoundingBox(InstanceBuilder.settings(room), room.offset());
            if (!PlotGrid.fits(box)) {
                throw new IllegalArgumentException("room " + room.template() + " reaches outside its plot: " + box);
            }
            relativeRooms.add(box);
            markers.addAll(InstanceBuilder.markers(template, index, room));
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
        Long closesAt = portal == null ? null : level.getGameTime() + openTicks;
        Integer runLevel = null;
        if (baseLevel.isPresent()) {
            runLevel = RunLevels.runLevel(baseLevel.getAsInt(), party.stream()
                    .map(level.getServer().getPlayerList()::getPlayer).filter(Objects::nonNull)
                    .mapToInt(Characters::level));
        }
        plot.startBuilding(layout, rooms, markers, party, nextRun++, portal, closesAt, runLevel, dungeonType);
        holdChunks(plot);
        setDirty();
        LOGGER.debug("Opened instance plot {} at {} for {} at level {}", plot.id(), origin, party, runLevel);
        return plot;
    }

    /**
     * Holds the chunks that have loaded, then does up to {@link #TICK_BUDGET_NANOS} of building and clearing; called
     * once per tick of the instance level. The budget goes round the plots one unit at a time, starting after the
     * plot served last, but every plot being built runs a unit each tick first, so a run's build never waits for
     * another plot's. A plot whose next unit's chunks are still loading waits for a later tick.
     */
    public void tick() {
        ServerLevel level = this.level;
        if (level == null) {
            return;
        }
        boolean changed = runTimers(level);
        List<Plot> busy = new ArrayList<>();
        List<Plot> later = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.state() != PlotState.FREE) {
                changed |= holdChunks(plot);
            }
            if (plot.state().hasWork()) {
                (plot.id() > lastServed ? busy : later).add(plot);
            }
        }
        busy.addAll(later);
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        boolean ran = false;
        for (Plot plot : busy) {
            if (plot.state() == PlotState.BUILDING && runUnit(level, plot)) {
                ran = true;
            }
        }
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Plot plot : busy) {
                if (ran && System.nanoTime() >= deadline) {
                    setDirty();
                    return;
                }
                if (plot.state().hasWork() && runUnit(level, plot)) {
                    ran = true;
                    progress = true;
                }
            }
        }
        if (ran || changed) {
            setDirty();
        }
    }

    /**
     * Moves a party member into a playable plot, remembers where they came from and switches a survival player to
     * Adventure mode. A player already inside the plot keeps their first return point.
     */
    public boolean enter(ServerPlayer player, Plot plot) {
        if (!plot.state().isPlayable() || !plot.party().contains(player.getUUID())) {
            return false;
        }
        Optional<InstanceVisit> existing = player.getExistingData(DungeonAttachments.VISIT);
        if (existing.isPresent() && existing.get().plot() != plot.id()) {
            return false;
        }
        InstanceVisit visit = existing.orElseGet(() -> new InstanceVisit(plot.id(), player.level().dimension(),
                player.position(), player.getYRot(), player.getXRot(), Optional.empty()));
        player.setData(DungeonAttachments.VISIT, visit);
        TeleportTransition arrival = arrival(plot);
        Vec3 spawn = arrival.position();
        player.teleportTo(level(), spawn.x, spawn.y, spawn.z, Set.of(), arrival.yRot(), arrival.xRot(), true);
        player.setData(DungeonAttachments.VISIT, InstanceRules.enterAdventure(player, visit));
        plot.activate();
        setDirty();
        return true;
    }

    /**
     * Drops the player from their run: one they entered sends them back to where they came from, and one still
     * being built (or built and not entered yet) is left without moving them. False when they belong to no run.
     */
    public boolean leave(ServerPlayer player) {
        InstanceVisit visit = player.getExistingDataOrNull(DungeonAttachments.VISIT);
        if (visit != null) {
            sendBack(player, visit);
            leftRun(player.getUUID(), visit.plot());
            return true;
        }
        Optional<Plot> pending = plotOf(player);
        pending.ifPresent(plot -> leftRun(player.getUUID(), plot.id()));
        return pending.isPresent();
    }

    /**
     * Marks an active run's goal as done: the entry point for its final boss's death and its objectives (W5, W6), and
     * {@code /qa instance complete}. Its players stay until they leave or the plot closes, which is now 5 minutes from
     * now, however much time the run had left ({@link PortalTimer#COMPLETED_TICKS}); the party is told so. Each party
     * member online completed it: their {@code realmforge:dungeons_completed} statistic counts it, and
     * {@code realmforge:dungeon_completed} fires with the run's dungeon type (architecture 16). False for a plot that is
     * not active.
     */
    public boolean complete(Plot plot) {
        if (plot.state() != PlotState.ACTIVE) {
            return false;
        }
        plot.complete();
        plot.setClosesAt(level().getGameTime() + PortalTimer.COMPLETED_TICKS);
        tell(plot, Component.translatable(GeneratedTexts.MESSAGE_DUNGEON_COMPLETED));
        Optional<Identifier> type = plot.portal().map(PortalLink::type);
        for (UUID member : plot.party()) {
            ServerPlayer player = level().getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                ProgressionStats.dungeonCompleted(player);
                ProgressionTriggers.DUNGEON_COMPLETED.get().trigger(player, type);
            }
        }
        setDirty();
        return true;
    }

    /**
     * Sets a run's timer to {@code ticks} from now, for {@code /qa instance timer} and the GameTests; the warnings still
     * come at their times. False for a plot without a run.
     */
    public boolean closeIn(Plot plot, long ticks) {
        if (!plot.state().isPlayable() && plot.state() != PlotState.BUILDING) {
            return false;
        }
        plot.setClosesAt(level().getGameTime() + ticks);
        setDirty();
        return true;
    }

    // ---- Dungeon portals (W4d) ----

    /** Whether run {@code run} of plot {@code plotId} is still open: being built or playable. */
    public boolean isOpen(int plotId, long run) {
        Plot plot = plots.get(plotId);
        return plot != null && plot.run() == run
                && (plot.state() == PlotState.BUILDING || plot.state().isPlayable());
    }

    /** What happened when a player used an open portal. */
    public enum PortalUse {
        /** The player entered the run. */
        ENTERED,
        /** The run is still being built. */
        STILL_OPENING,
        /** The run belongs to another player. */
        NOT_YOURS,
        /** The run is gone: the portal closed. */
        CLOSED
    }

    /** A player used the portal of run {@code run} of plot {@code plotId}: its party member enters once it is built. */
    public PortalUse usePortal(ServerPlayer player, int plotId, long run) {
        if (!isOpen(plotId, run)) {
            return PortalUse.CLOSED;
        }
        Plot plot = plots.get(plotId);
        if (!plot.party().contains(player.getUUID())) {
            return PortalUse.NOT_YOURS;
        }
        if (plot.state() == PlotState.BUILDING) {
            return PortalUse.STILL_OPENING;
        }
        return enter(player, plot) ? PortalUse.ENTERED : PortalUse.NOT_YOURS;
    }

    /**
     * Ends a plot's run: players still inside are sent back (a dead one when they respawn), then its blocks, its rooms'
     * margin included, are cleared over the next ticks before the plot is free for the next run. Nobody waits for that
     * (spike 3, ADR 0019). Its dungeon portal, if any, closes with it ({@code DungeonPortalBlockEntity}).
     */
    public void release(Plot plot) {
        if (plot.state() == PlotState.CLOSING || plot.state() == PlotState.FREE) {
            return;
        }
        MinecraftServer server = level().getServer();
        for (UUID member : plot.party()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            InstanceVisit visit = player == null ? null : player.getExistingDataOrNull(DungeonAttachments.VISIT);
            // A dead player stays where they lie: their respawn takes them to their return point.
            if (visit != null && visit.plot() == plot.id() && !player.isDeadOrDying()) {
                sendBack(player, visit);
            }
        }
        plot.startClosing();
        setDirty();
    }

    // ---- Players outside the normal flow ----

    /**
     * After a login: a player inside the instance dimension stays only if their run still exists; anyone else is
     * sent back, but a dead one is left to their respawn, which takes them to their return point. A player outside it
     * leaves the run they belong to: one they left while offline (they still hold a visit), or one they never entered
     * because they disconnected while it was being built, unless a portal opened it, which keeps it until its timer
     * runs out. Whoever leaves gets back the game mode entering took from them, also after a crash. Keys owed to the
     * player are given back.
     */
    public void onLogin(ServerPlayer player) {
        giveRefunds(player);
        InstanceVisit visit = player.getExistingDataOrNull(DungeonAttachments.VISIT);
        if (!InstanceDimension.isInstance(player.level())) {
            if (visit != null) {
                endVisit(player, visit);
                leftRun(player.getUUID(), visit.plot());
            }
            for (Plot plot : List.copyOf(plots.values())) {
                // A run opened through a portal waits for its player until its timer runs out (W4d).
                if (plot.party().contains(player.getUUID()) && plot.portal().isEmpty()) {
                    leftRun(player.getUUID(), plot.id());
                }
            }
            return;
        }
        Plot plot = visit == null ? null : plots.get(visit.plot());
        if (plot != null && plot.state().isPlayable() && plot.party().contains(player.getUUID())) {
            // The server may force its default game mode at login.
            player.setData(DungeonAttachments.VISIT, InstanceRules.enterAdventure(player, visit));
            return;
        }
        if (visit != null && player.isDeadOrDying()) {
            // Released while they lay dead, as release leaves them (RLM-258): their respawn takes them to their
            // return point, ends the visit and gives their game mode back.
            return;
        }
        LOGGER.info("Sending {} out of the instance dimension: their run is gone",
                player.getGameProfile().name());
        if (visit == null) {
            sendToSpawn(player);
        } else {
            sendBack(player, visit);
            leftRun(player.getUUID(), visit.plot());
        }
    }

    /**
     * A player who holds a visit but is now outside the instance dimension (respawned, teleported) left the run, and
     * gets back the game mode entering took from them.
     */
    public void onLeftDimension(ServerPlayer player) {
        InstanceVisit visit = player.getExistingDataOrNull(DungeonAttachments.VISIT);
        if (visit != null && !InstanceDimension.isInstance(player.level())) {
            endVisit(player, visit);
            leftRun(player.getUUID(), visit.plot());
        }
    }

    /**
     * Where a player who died in their run respawns: at its entrance, still inside, while the run is playable, and at
     * their return point once it is gone (it was released while they lay dead). Empty for anyone else, who respawns
     * as vanilla decides.
     */
    public Optional<TeleportTransition> respawnPoint(ServerPlayer player) {
        InstanceVisit visit = player.getExistingDataOrNull(DungeonAttachments.VISIT);
        if (visit == null || !InstanceDimension.isInstance(player.level())) {
            return Optional.empty();
        }
        Plot plot = plots.get(visit.plot());
        if (plot != null && plot.state().isPlayable() && plot.party().contains(player.getUUID())) {
            return Optional.of(arrival(plot));
        }
        ServerLevel target = level().getServer().getLevel(visit.returnLevel());
        return target == null ? Optional.empty() : Optional.of(new TeleportTransition(target, visit.returnPos(),
                Vec3.ZERO, visit.returnYaw(), visit.returnPitch(), TeleportTransition.DO_NOTHING));
    }

    // ---- Internals ----

    /**
     * Runs the plot's next unit, unless its chunks are still loading, and finishes the job after its last one.
     * Returns whether the plot moved on. A unit that fails (a room template that no longer resolves after a restart,
     * say) releases a plot being built and is skipped by a closing one, instead of failing every tick.
     */
    private boolean runUnit(ServerLevel level, Plot plot) {
        List<PlotWork.Unit> units = plot.units();
        if (plot.step() < units.size()) {
            PlotWork.Unit unit = units.get(plot.step());
            if (!InstanceBuilder.ready(level, unit)) {
                return false;
            }
            long start = System.nanoTime();
            try {
                long cleared = InstanceBuilder.run(level, plot, unit);
                InstanceBiomes.apply(level, plot, unit);
                int room = unit instanceof PlotWork.Place place ? place.room() : -1;
                plot.recorder().unit(level.getGameTime(), room, System.nanoTime() - start, cleared);
            } catch (RuntimeException e) {
                lastServed = plot.id();
                if (plot.state() == PlotState.BUILDING) {
                    LOGGER.error("Instance plot {} failed to build ({}): {}; releasing it", plot.id(), unit, e.getMessage(), e);
                    // The failed unit counts as run: it may have placed blocks, which closing must clear.
                    plot.advance();
                    refundKey(plot);
                    release(plot);
                    return true;
                }
                LOGGER.error("Instance plot {} failed to clear ({}): {}; skipping it", plot.id(), unit, e.getMessage(), e);
            }
            plot.advance();
        }
        lastServed = plot.id();
        if (plot.step() >= units.size()) {
            finishWork(plot);
        }
        return true;
    }

    private void finishWork(Plot plot) {
        BuildReport report = plot.recorder().finish();
        reports.put(plot.id(), report);
        LOGGER.info("Instance {}", report);
        if (plot.state() == PlotState.BUILDING) {
            plot.finishBuilding(findSpawn(plot));
            placeExits(plot);
            Optional<PortalLink> portal = plot.portal();
            if (portal.isPresent()) {
                // The party enters through the portal, which stands where they come back to.
                tell(plot, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_READY, typeName(portal.get())));
                return;
            }
            MinecraftServer server = level().getServer();
            for (UUID member : plot.party()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                if (player != null) {
                    enter(player, plot);
                }
            }
        } else {
            releaseChunks(plot);
            plot.free();
            free.add(plot.id());
        }
    }

    /**
     * Where players arrive in a built plot, relative to its origin: the entrance room's entrance marker, else the
     * spot {@link SpawnFinder} picks in that room, else the middle of its roof.
     */
    private Vec3 findSpawn(Plot plot) {
        BlockPos origin = plot.origin();
        Optional<DataMarker> marker = plot.marker(DataMarker.Kind.ENTRANCE, 0);
        if (marker.isPresent()) {
            return Vec3.atBottomCenterOf(marker.get().pos());
        }
        ServerLevel level = level();
        BoundingBox entrance = plot.rooms().getFirst();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Optional<BlockPos> spot = SpawnFinder.find(entrance, new SpawnFinder.Blocks() {
            @Override
            public boolean floor(int x, int y, int z) {
                pos.set(x, y, z);
                return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
            }

            @Override
            public boolean open(int x, int y, int z) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
            }
        });
        if (spot.isPresent()) {
            return Vec3.atBottomCenterOf(spot.get().subtract(origin));
        }
        BlockPos roof = new BlockPos(entrance.getCenter().getX(), entrance.maxY() + 1, entrance.getCenter().getZ());
        LOGGER.warn("Instance plot {}: its entrance room has no entrance marker and no floor to stand on; players "
                + "arrive on its roof at {}", plot.id(), roof.toShortString());
        return Vec3.atBottomCenterOf(roof.subtract(origin));
    }

    /**
     * Sends the timer warnings and releases every run whose timer ran out, telling its party the portal closed.
     * Returns whether any closed.
     */
    private boolean runTimers(ServerLevel level) {
        long now = level.getGameTime();
        boolean closed = false;
        for (Plot plot : List.copyOf(plots.values())) {
            OptionalLong closesAt = plot.closesAt();
            if (closesAt.isEmpty() || (plot.state() != PlotState.BUILDING && !plot.state().isPlayable())) {
                continue;
            }
            long left = closesAt.getAsLong() - now;
            if (left <= 0) {
                LOGGER.info("Instance plot {}: its portal closed", plot.id());
                tell(plot, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_CLOSED));
                release(plot);
                closed = true;
            } else {
                PortalTimer.warning(left).ifPresent(key -> tell(plot, Component.translatable(key)));
            }
        }
        return closed;
    }

    /**
     * Lays the dungeon exit where players arrive and at every exit marker, where nothing stands (W4d). Closing clears it
     * with the rooms.
     */
    private void placeExits(Plot plot) {
        List<Block> exits = BlockKinds.blocks(DungeonBlocks.EXIT_KIND);
        if (exits.isEmpty()) {
            return;
        }
        BlockState exit = exits.getFirst().defaultBlockState();
        ServerLevel level = level();
        BlockPos origin = plot.origin();
        List<BlockPos> spots = new ArrayList<>();
        plot.spawn().ifPresent(spawn -> spots.add(BlockPos.containing(spawn).offset(origin)));
        plot.markers().stream().filter(marker -> marker.kind() == DataMarker.Kind.EXIT)
                .forEach(marker -> spots.add(origin.offset(marker.pos())));
        for (BlockPos spot : spots) {
            if (level.getBlockState(spot).isAir()) {
                level.setBlock(spot, exit, Block.UPDATE_ALL);
            }
        }
    }

    /** Gives back the key that opened a portal run that could not be built, now or at the player's next login. */
    private void refundKey(Plot plot) {
        Optional<PortalLink> portal = plot.portal();
        if (portal.isEmpty()) {
            return;
        }
        DungeonType type = level().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(portal.get().type());
        if (type == null) {
            return;
        }
        for (UUID member : plot.party()) {
            refunds.computeIfAbsent(member, ignored -> new ArrayList<>()).add(type.key());
            ServerPlayer player = level().getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                giveRefunds(player);
            }
        }
        setDirty();
    }

    private void giveRefunds(ServerPlayer player) {
        List<Identifier> keys = refunds.remove(player.getUUID());
        if (keys == null) {
            return;
        }
        for (Identifier key : keys) {
            BuiltInRegistries.ITEM.getOptional(key)
                    .ifPresent(item -> player.getInventory().placeItemBackInInventory(new ItemStack(item)));
        }
        player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_PORTAL_FAILED));
        setDirty();
    }

    /** Tells the plot's party members who are online. */
    private void tell(Plot plot, Component message) {
        MinecraftServer server = level().getServer();
        for (UUID member : plot.party()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** The name of the portal's dungeon type, or its id when the type is gone. */
    private Component typeName(PortalLink portal) {
        DungeonType type = level().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(portal.type());
        return type == null ? Component.literal(portal.type().toString()) : Component.translatable(type.name());
    }

    /** Drops the player from the plot's party; a run left with nobody, built or still building, is released. */
    private void leftRun(UUID player, int plotId) {
        Plot plot = plots.get(plotId);
        if (plot != null && plot.removeFromParty(player) && plot.party().isEmpty()) {
            release(plot);
        }
        setDirty();
    }

    /**
     * Where players arrive in a playable plot, in the instance level: its spawn (found once), looking along the
     * layout's way on.
     */
    private TeleportTransition arrival(Plot plot) {
        Vec3 relative = plot.spawn().orElseGet(() -> {
            Vec3 found = findSpawn(plot);
            plot.setSpawn(found);
            return found;
        });
        Vec3 spawn = Vec3.atLowerCornerOf(plot.origin()).add(relative);
        float yaw = plot.layout().orElseThrow().yawFrom(relative);
        return new TeleportTransition(level(), spawn, Vec3.ZERO, yaw, 0.0F, TeleportTransition.DO_NOTHING);
    }

    /** Forgets the player's visit and gives back the game mode entering took from them. */
    private static void endVisit(ServerPlayer player, InstanceVisit visit) {
        player.removeData(DungeonAttachments.VISIT);
        InstanceRules.restoreGameMode(player, visit);
    }

    private void sendBack(ServerPlayer player, InstanceVisit visit) {
        endVisit(player, visit);
        ServerLevel target = player.level().getServer().getLevel(visit.returnLevel());
        if (target == null) {
            sendToSpawn(player);
            return;
        }
        Vec3 pos = visit.returnPos();
        player.teleportTo(target, pos.x, pos.y, pos.z, Set.of(), visit.returnYaw(), visit.returnPitch(), true);
    }

    /** To the world spawn, whose respawn data names its dimension (not always the overworld). */
    private static void sendToSpawn(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel spawnLevel = server.findRespawnDimension();
        LevelData.RespawnData spawn = server.getRespawnData();
        BlockPos pos = spawn.pos();
        player.teleportTo(spawnLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(),
                spawn.yaw(), spawn.pitch(), true);
    }

    /**
     * Holds the plot's wanted chunks that have loaded, and asks for the others to load in the background. Holding a
     * chunk loads it on the main thread when it is not loaded yet (spike 3: up to a second for a new plot cell), so a
     * chunk is held only once it has loaded, its entities included: the entity manager makes a chunk's entity
     * sections accessible after its blocks, and a room's item frames placed before that would stay hidden (RLM-483).
     * Returns whether it took any.
     */
    private boolean holdChunks(Plot plot) {
        LongSet wanted = plot.wantedChunks();
        if (plot.chunks().containsAll(wanted)) {
            return false;
        }
        ServerLevel level = level();
        boolean held = false;
        for (long chunk : wanted) {
            if (plot.chunks().contains(chunk)) {
                continue;
            }
            int chunkX = ChunkPos.getX(chunk);
            int chunkZ = ChunkPos.getZ(chunk);
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null && level.areEntitiesLoaded(chunk)) {
                TICKETS.forceChunk(level, plot.origin(), chunkX, chunkZ, true, false);
                plot.chunks().add(chunk);
                held = true;
            } else {
                level.getChunkSource().addTicketWithRadius(LOADING.value(), new ChunkPos(chunkX, chunkZ), 0);
            }
        }
        return held;
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
                LOGGER.info("Dropping stale instance chunk tickets of {} in {}", owner, level.dimension().identifier());
                tickets.removeAllTickets(owner);
            }
        }
        for (UUID owner : tickets.getEntityTickets().keySet()) {
            tickets.removeAllTickets(owner);
        }
    }

}
