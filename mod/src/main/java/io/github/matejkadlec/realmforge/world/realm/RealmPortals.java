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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What the realm portal does (realm portal spec, "The frame", "The sealstone", "Access and unlocks"): the
 * sealstone's gestures, lighting and closing a frame, the check when a player steps in, and the frames a trip builds
 * ({@link RealmPortalLinker} finds where).
 *
 * <p>The sealstone's setting counts only while its opening is lit: a frame that went dark leads nowhere, so a closed or
 * rebuilt frame lights again with a signet (RLM-133).
 */
public final class RealmPortals {
    /** How long particles mark what an incomplete frame lacks: 3 seconds. */
    static final int MARK_TICKS = 60;
    private static final int MARK_INTERVAL = 10;
    /** Red dust over each missing or wrong block. */
    private static final DustParticleOptions MARK = new DustParticleOptions(0xFF3030, 1.5F);

    /** How far from the sealstone's centre a player holding a signet lights it: 5 blocks (RLM-406). */
    public static final double GLOW_RADIUS = 5.0;

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
        if (state.getBlock() instanceof RealmforgeBricksBlock) {
            return PortalFrame.Part.BRICKS;
        }
        if (state.getBlock() instanceof SealstoneBlock) {
            return PortalFrame.Part.SEALSTONE;
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

    /** Whether the opening above the sealstone at {@code sealstone} holds portal blocks. */
    public static boolean isLit(BlockGetter level, BlockPos sealstone) {
        return level.getBlockState(sealstone.above()).getBlock() instanceof RealmPortalBlock;
    }

    /**
     * Where the sealstone at {@code sealstone} leads: the id of a {@code realmforge:realm} entry, a realm's or (a return
     * portal) Overworld's. Empty while the opening is dark.
     */
    public static Optional<Identifier> destination(BlockGetter level, BlockPos sealstone) {
        if (!isLit(level, sealstone) || !(level.getBlockEntity(sealstone) instanceof SealstoneBlockEntity entity)) {
            return Optional.empty();
        }
        return entity.storedDestination();
    }

    /** The entry the portal block at {@code portal} leads to, on either side; empty for a broken frame or an unknown realm. */
    public static Optional<Realm> destinationOfPortal(Level level, BlockPos portal, Direction.Axis axis) {
        return frameOf(level, portal, axis)
                .flatMap(frame -> destination(level, frame.sealstone()))
                .flatMap(id -> level.registryAccess().lookupOrThrow(WorldRegistries.REALM).getOptional(id));
    }

    /**
     * Whether the entity in the portal block at {@code portal} may start the trip: the portal leads somewhere, and the
     * entity, when it is a player, and every player riding it may enter the destination. Each player without access is
     * told, at most once every 3 seconds, and the entity stays where it is, so the portal never starts its countdown.
     */
    static boolean mayTravel(Entity entity, ServerLevel level, BlockPos portal, Direction.Axis axis) {
        Optional<Realm> destination = destinationOfPortal(level, portal, axis);
        if (destination.isEmpty()) {
            return false;
        }
        ResourceKey<Level> dimension = destination.get().dimension();
        if (dimension == level.dimension()) {
            return true;
        }
        List<ServerPlayer> refused = entity.getSelfAndPassengers()
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .filter(player -> !RealmAccess.mayEnter(player, dimension))
                .toList();
        refused.forEach(player -> RealmAccess.refuse(player, dimension));
        return refused.isEmpty();
    }

    /** A signet used on the sealstone at {@code sealstone} (realm portal spec, "The sealstone"). */
    static void useSignet(ServerPlayer player, ServerLevel level, BlockPos sealstone, Holder.Reference<Realm> realm) {
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
                    Component.translatable(realm.value().description()),
                    Component.translatable(realm.value().lockedHint())));
            return;
        }
        open(player, level, sealstone, realm);
    }

    /**
     * The sneak gesture with an empty main hand: in Overworld it closes the portal; inside a realm it opens the portal
     * onto Overworld, or switches it back there.
     */
    static void useSneaking(ServerPlayer player, ServerLevel level, BlockPos sealstone) {
        RegistryAccess registries = level.registryAccess();
        Optional<Holder.Reference<Realm>> overworld = Realms.overworld(registries);
        if (Realms.entry(registries, level.dimension()).isEmpty() || overworld.isEmpty()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_CANNOT_OPEN_HERE));
            return;
        }
        if (Realms.isRealm(registries, level.dimension())) {
            open(player, level, sealstone, overworld.get());
            return;
        }
        if (isLit(level, sealstone)) {
            close(level, sealstone);
        }
        tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_CLOSED));
    }

    /** An empty hand on the sealstone: where its portal leads, or that it is closed. */
    static void showDestination(ServerPlayer player, ServerLevel level, BlockPos sealstone) {
        Optional<Identifier> destination = destination(level, sealstone);
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
     * Opens the frame around {@code sealstone} onto {@code entry}, or switches it there, once the frame is complete and
     * its opening free; otherwise particles mark what is missing or in the way.
     */
    private static void open(ServerPlayer player, ServerLevel level, BlockPos sealstone, Holder.Reference<Realm> entry) {
        Component name = Component.translatable(entry.value().name());
        PortalFrame.Check check = PortalFrame.around(lookup(level), sealstone);
        if (!check.canLight()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_FRAME_INCOMPLETE));
            mark(level, check.problems());
            return;
        }
        if (destination(level, sealstone).filter(entry.key().identifier()::equals).isPresent()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_ALREADY_OPENS_ONTO, name));
            return;
        }
        light(level, check.frame(), entry.key().identifier());
        tell(player, Component.translatable(GeneratedTexts.MESSAGE_REALM_PORTAL_OPENS_ONTO, name));
    }

    /**
     * Lights a complete frame onto {@code destination}: the sealstone's setting, then the opening's portal blocks along
     * the frame's axis. Placed without neighbour updates, as vanilla places nether portal blocks, so the half-filled
     * opening does not take itself apart.
     */
    public static void light(ServerLevel level, PortalFrame.Frame frame, Identifier destination) {
        if (level.getBlockEntity(frame.sealstone()) instanceof SealstoneBlockEntity entity) {
            entity.setDestination(destination);
        }
        BlockState portal = RealmPortalBlocks.portal().defaultBlockState().setValue(RealmPortalBlock.AXIS, frame.axis());
        for (BlockPos pos : frame.opening()) {
            level.setBlock(pos, portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Closes the portal over {@code sealstone}: the opening goes dark and the setting becomes none. */
    public static void close(ServerLevel level, BlockPos sealstone) {
        BlockState above = level.getBlockState(sealstone.above());
        if (above.getBlock() instanceof RealmPortalBlock) {
            PortalFrame.Frame frame = new PortalFrame.Frame(sealstone, above.getValue(RealmPortalBlock.AXIS));
            for (BlockPos pos : frame.opening()) {
                if (level.getBlockState(pos).getBlock() instanceof RealmPortalBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (level.getBlockEntity(sealstone) instanceof SealstoneBlockEntity entity) {
            entity.setDestination(null);
        }
    }

    /**
     * Builds a frame a trip needs, lit onto {@code destination}: 13 Realmforge Bricks and a sealstone, and Realmforge
     * Bricks in the four corners too, which no frame needs but every built one has (Matěj, 2026-10-20: "without it it
     * looks bad"); all {@link RealmforgeBricksBlock#GENERATED}, so they drop nothing, then the opening (realm portal
     * spec, "Building a portal").
     */
    public static void build(ServerLevel level, PortalFrame.Frame frame, Identifier destination) {
        BlockState stone = RealmPortalBlocks.bricks().defaultBlockState().setValue(RealmforgeBricksBlock.GENERATED, true);
        for (BlockPos pos : frame.bricks()) {
            level.setBlock(pos, stone, Block.UPDATE_ALL);
        }
        for (BlockPos pos : frame.corners()) {
            level.setBlock(pos, stone, Block.UPDATE_ALL);
        }
        level.setBlock(frame.sealstone(),
                RealmPortalBlocks.sealstone().defaultBlockState().setValue(RealmforgeBricksBlock.GENERATED, true),
                Block.UPDATE_ALL);
        light(level, frame, destination);
    }

    /**
     * The sealstone's glow, every server tick (RLM-406, Matěj: "the Sealstone block would smoothly light up if player
     * with Signet item in hand"): one light level a tick towards {@link SealstoneBlock#MAX_GLOW} while a player holding
     * a signet that lights it ({@link #lightsSealstone}) in either hand stands within {@link #GLOW_RADIUS} of its centre,
     * and towards dark otherwise, so it rises or fades over 15 ticks, about a second. It stays dark where no signet
     * opens a portal ({@link #signetsOpenPortalsIn}, RLM-458). Spectators count for nothing. The new state goes to
     * clients without shape updates, so nothing next to it reacts to the glow.
     */
    static void tickGlow(Level level, BlockPos pos, BlockState state, SealstoneBlockEntity entity) {
        boolean near = false;
        if (signetsOpenPortalsIn(level)) {
            Optional<Identifier> destination = isLit(level, pos) ? entity.storedDestination() : Optional.empty();
            Vec3 centre = Vec3.atCenterOf(pos);
            for (Player player : level.players()) {
                if (!player.isSpectator() && player.distanceToSqr(centre) <= GLOW_RADIUS * GLOW_RADIUS
                        && (lightsSealstone(level, player.getMainHandItem(), destination)
                                || lightsSealstone(level, player.getOffhandItem(), destination))) {
                    near = true;
                    break;
                }
            }
        }
        int glow = state.getValue(SealstoneBlock.GLOW);
        int target = near ? SealstoneBlock.MAX_GLOW : 0;
        if (glow != target) {
            level.setBlock(pos, state.setValue(SealstoneBlock.GLOW, glow + Integer.signum(target - glow)),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /**
     * Whether a signet opens a portal in {@code level}'s dimension, the rule {@link #useSignet} applies first: only in a
     * dimension with a realm entry that is no realm, so in Overworld. Inside a realm a frame opens only back to
     * Overworld, and the instances, the Nether and other mods' dimensions have no entry (RLM-458).
     */
    static boolean signetsOpenPortalsIn(Level level) {
        RegistryAccess registries = level.registryAccess();
        return Realms.entry(registries, level.dimension()).isPresent()
                && !Realms.isRealm(registries, level.dimension());
    }

    /**
     * Whether {@code stack} is a signet that lights a sealstone whose portal opens onto {@code destination}: the signet
     * of that realm, or any signet while the portal is dark, as a frame that was never opened is (RLM-406). A return
     * portal, open onto Overworld, which has no signet, stays dark.
     */
    public static boolean lightsSealstone(Level level, ItemStack stack, Optional<Identifier> destination) {
        return Realms.bySignet(level.registryAccess(), stack.getItem())
                .filter(realm -> destination.isEmpty() || destination.get().equals(realm.key().identifier()))
                .isPresent();
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
     * Lets the sneak gesture reach the sealstone whatever the off hand holds (RLM-133): vanilla skips a block's use while
     * the player sneaks with anything in either hand, so a shield, torch or food in the off hand would block it. Common
     * code, run on both sides; the sealstone's {@code useWithoutItem} then handles the gesture.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity().isSecondaryUseActive()
                && event.getEntity().getMainHandItem().isEmpty()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SealstoneBlock) {
            event.setUseBlock(TriState.TRUE);
        }
    }

    private static void tell(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }
}
