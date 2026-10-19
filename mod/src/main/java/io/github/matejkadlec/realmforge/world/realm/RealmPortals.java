package io.github.matejkadlec.realmforge.world.realm;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.world.WorldRegistries;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What the realm portal does (realm portal spec, "The frame", "The realm selector", "Access and unlocks"): the
 * selector's gestures, lighting and closing a frame, the check when a player steps in, and the frames a trip builds
 * ({@link RealmPortalLinker} finds where).
 *
 * <p>The selector's setting counts only while its opening is lit: a frame that went dark leads nowhere, so a closed or
 * rebuilt frame lights again with a sigil (RLM-133).
 */
public final class RealmPortals {
    /** How long particles mark what an incomplete frame lacks: 3 seconds. */
    static final int MARK_TICKS = 60;
    private static final int MARK_INTERVAL = 10;
    /** Red dust over each missing or wrong block. */
    private static final DustParticleOptions MARK = new DustParticleOptions(0xFF3030, 1.5F);

    /** The incomplete frames being marked; touched only on the server thread. */
    private static final List<Mark> MARKS = new ArrayList<>();

    private record Mark(ServerLevel level, List<BlockPos> positions, long until) {
    }

    private RealmPortals() {
    }

    /** What each position holds for the frame check. */
    public static PortalFrame.Lookup lookup(BlockGetter level) {
        return pos -> part(level.getBlockState(pos));
    }

    static PortalFrame.Part part(BlockState state) {
        if (state.getBlock() instanceof PortalStoneBlock) {
            return PortalFrame.Part.PORTAL_STONE;
        }
        if (state.getBlock() instanceof RealmSelectorBlock) {
            return PortalFrame.Part.SELECTOR;
        }
        if (state.getBlock() instanceof RealmPortalBlock) {
            return PortalFrame.Part.PORTAL;
        }
        return state.canBeReplaced() && state.getFluidState().isEmpty() ? PortalFrame.Part.OPEN : PortalFrame.Part.OTHER;
    }

    /** The complete frame around the portal block at {@code portal}, or empty when it is broken. */
    public static Optional<PortalFrame.Frame> frameOf(BlockGetter level, BlockPos portal, Direction.Axis axis) {
        return PortalFrame.ofPortal(lookup(level), portal, axis);
    }

    /** Whether the opening above the selector at {@code selector} holds portal blocks. */
    public static boolean isLit(BlockGetter level, BlockPos selector) {
        return level.getBlockState(selector.above()).getBlock() instanceof RealmPortalBlock;
    }

    /**
     * Where the selector at {@code selector} leads: the id of a {@code realmforge:realm} entry, a realm's or (a return
     * portal) Overworld's. Empty while the opening is dark.
     */
    public static Optional<Identifier> destination(BlockGetter level, BlockPos selector) {
        if (!isLit(level, selector) || !(level.getBlockEntity(selector) instanceof RealmSelectorBlockEntity entity)) {
            return Optional.empty();
        }
        return entity.storedDestination();
    }

    /** The entry the portal block at {@code portal} leads to, on either side; empty for a broken frame or an unknown realm. */
    public static Optional<Realm> destinationOfPortal(Level level, BlockPos portal, Direction.Axis axis) {
        return frameOf(level, portal, axis)
                .flatMap(frame -> destination(level, frame.selector()))
                .flatMap(id -> level.registryAccess().lookupOrThrow(WorldRegistries.REALM).getOptional(id));
    }

    /**
     * Whether the player in the portal block at {@code portal} may start the trip: the portal leads somewhere, and the
     * player may enter the destination. A player without access is told, at most once every 3 seconds, and stays where
     * they are, so the portal never starts its countdown for them.
     */
    static boolean mayTravel(ServerPlayer player, ServerLevel level, BlockPos portal, Direction.Axis axis) {
        Optional<Realm> destination = destinationOfPortal(level, portal, axis);
        if (destination.isEmpty()) {
            return false;
        }
        ResourceKey<Level> dimension = destination.get().dimension();
        if (dimension != level.dimension() && !RealmAccess.mayEnter(player, dimension)) {
            RealmAccess.refuse(player, dimension);
            return false;
        }
        return true;
    }

    /** A sigil used on the selector at {@code selector} (realm portal spec, "The realm selector"). */
    static void useSigil(ServerPlayer player, ServerLevel level, BlockPos selector, Holder.Reference<Realm> realm) {
        RegistryAccess registries = level.registryAccess();
        if (Realms.entry(registries, level.dimension()).isEmpty()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_CANNOT_OPEN_HERE));
            return;
        }
        if (Realms.isRealm(registries, level.dimension())) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_ONLY_BACK));
            return;
        }
        if (!RealmAccess.mayEnter(player, realm.value().dimension())) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_NOT_ELIGIBLE,
                    Component.translatable(realm.value().name()), Component.translatable(realm.value().lockedHint())));
            return;
        }
        open(player, level, selector, realm);
    }

    /**
     * The sneak gesture with an empty main hand: in Overworld it closes the portal; inside a realm it opens the portal
     * onto Overworld, or switches it back there.
     */
    static void useSneaking(ServerPlayer player, ServerLevel level, BlockPos selector) {
        RegistryAccess registries = level.registryAccess();
        Optional<Holder.Reference<Realm>> overworld = Realms.overworld(registries);
        if (Realms.entry(registries, level.dimension()).isEmpty() || overworld.isEmpty()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_CANNOT_OPEN_HERE));
            return;
        }
        if (Realms.isRealm(registries, level.dimension())) {
            open(player, level, selector, overworld.get());
            return;
        }
        if (isLit(level, selector)) {
            close(level, selector);
        }
        tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_CLOSED));
    }

    /** An empty hand on the selector: where its portal leads, or that it is closed. */
    static void showDestination(ServerPlayer player, ServerLevel level, BlockPos selector) {
        Optional<Identifier> destination = destination(level, selector);
        if (destination.isEmpty()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_CLOSED));
            return;
        }
        Optional<Realm> realm = level.registryAccess().lookupOrThrow(WorldRegistries.REALM).getOptional(destination.get());
        tell(player, realm.isPresent()
                ? Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_OPENS_ONTO,
                        Component.translatable(realm.get().name()))
                : Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_UNKNOWN_REALM));
    }

    /**
     * Opens the frame around {@code selector} onto {@code entry}, or switches it there, once the frame is complete and
     * its opening free; otherwise particles mark what is missing or in the way.
     */
    private static void open(ServerPlayer player, ServerLevel level, BlockPos selector, Holder.Reference<Realm> entry) {
        Component name = Component.translatable(entry.value().name());
        PortalFrame.Check check = PortalFrame.around(lookup(level), selector);
        if (!check.canLight()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_FRAME_INCOMPLETE));
            mark(level, check.problems());
            return;
        }
        if (destination(level, selector).filter(entry.key().identifier()::equals).isPresent()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_ALREADY_OPENS_ONTO, name));
            return;
        }
        light(level, check.frame(), entry.key().identifier());
        tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_OPENS_ONTO, name));
    }

    /**
     * Lights a complete frame onto {@code destination}: the selector's setting, then the opening's portal blocks along
     * the frame's axis. Placed without neighbour updates, as vanilla places nether portal blocks, so the half-filled
     * opening does not take itself apart.
     */
    public static void light(ServerLevel level, PortalFrame.Frame frame, Identifier destination) {
        if (level.getBlockEntity(frame.selector()) instanceof RealmSelectorBlockEntity entity) {
            entity.setDestination(destination);
        }
        BlockState portal = RealmPortalBlocks.portal().defaultBlockState().setValue(RealmPortalBlock.AXIS, frame.axis());
        for (BlockPos pos : frame.opening()) {
            level.setBlock(pos, portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Closes the portal over {@code selector}: the opening goes dark and the setting becomes none. */
    public static void close(ServerLevel level, BlockPos selector) {
        BlockState above = level.getBlockState(selector.above());
        if (above.getBlock() instanceof RealmPortalBlock) {
            PortalFrame.Frame frame = new PortalFrame.Frame(selector, above.getValue(RealmPortalBlock.AXIS));
            for (BlockPos pos : frame.opening()) {
                if (level.getBlockState(pos).getBlock() instanceof RealmPortalBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (level.getBlockEntity(selector) instanceof RealmSelectorBlockEntity entity) {
            entity.setDestination(null);
        }
    }

    /**
     * Builds a frame a trip needs, lit onto {@code destination}: 13 Portal Stone and a selector, all
     * {@link PortalStoneBlock#GENERATED}, so they drop nothing, then the opening (realm portal spec, "Building a
     * portal"). The corners stay as they are.
     */
    public static void build(ServerLevel level, PortalFrame.Frame frame, Identifier destination) {
        BlockState stone = RealmPortalBlocks.portalStone().defaultBlockState().setValue(PortalStoneBlock.GENERATED, true);
        for (BlockPos pos : frame.portalStones()) {
            level.setBlock(pos, stone, Block.UPDATE_ALL);
        }
        level.setBlock(frame.selector(),
                RealmPortalBlocks.selector().defaultBlockState().setValue(PortalStoneBlock.GENERATED, true),
                Block.UPDATE_ALL);
        light(level, frame, destination);
    }

    /** Marks {@code positions} with particles for {@link #MARK_TICKS} ticks. */
    private static void mark(ServerLevel level, List<BlockPos> positions) {
        MARKS.add(new Mark(level, List.copyOf(positions), level.getGameTime() + MARK_TICKS));
        sendMarks(level, positions);
    }

    /** Renews the marks every half second until they expire. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || MARKS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Mark> it = MARKS.iterator(); it.hasNext(); ) {
            Mark mark = it.next();
            if (mark.level().getServer() != level.getServer()) {
                // A server that stopped while its frame was marked.
                it.remove();
                continue;
            }
            if (mark.level() != level) {
                continue;
            }
            if (now >= mark.until()) {
                it.remove();
            } else if (now % MARK_INTERVAL == 0) {
                sendMarks(level, mark.positions());
            }
        }
    }

    private static void sendMarks(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            level.sendParticles(MARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /**
     * Lets the sneak gesture reach the selector whatever the off hand holds (RLM-133): vanilla skips a block's use while
     * the player sneaks with anything in either hand, so a shield, torch or food in the off hand would block it. Common
     * code, run on both sides; the selector's {@code useWithoutItem} then handles the gesture.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity().isSecondaryUseActive()
                && event.getEntity().getMainHandItem().isEmpty()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof RealmSelectorBlock) {
            event.setUseBlock(TriState.TRUE);
        }
    }

    private static void tell(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }
}
