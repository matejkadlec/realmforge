package io.github.matejkadlec.realmforge.world.realm;

import io.github.matejkadlec.realmforge.world.WorldRegistries;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Where a realm portal leads (realm portal spec, "Linking", "Building a portal", "Arriving"): portals link like the
 * Nether's, in both directions, at the realm's coordinate scale. A trip divides (into a realm) or multiplies (back to
 * Overworld) the x and z of the source opening's centre, clamps them into the target's world border, and looks for a
 * lit portal leading back where the player came from within the search radius; when there is none, it builds one
 * within the build radius. There is no saved pair: breaking or switching a portal needs no bookkeeping.
 *
 * <p>The radii follow vanilla's arithmetic: Overworld searches 128 blocks and builds within 16; a realm of scale s
 * searches ⌊128 ÷ s⌋ blocks and builds within one block less, because {@code WorldBorder#clampToBounds} rounds the
 * scaled position down, so a pair a trip built always links back both ways.
 */
public final class RealmPortalLinker {
    /** Overworld's search radius, as vanilla's for nether portals. */
    public static final int OVERWORLD_SEARCH_RADIUS = 128;
    /** Overworld's build radius, as vanilla's spiral. */
    public static final int OVERWORLD_BUILD_RADIUS = 16;
    /** The volume of the travel sound, as vanilla's. */
    private static final float TRAVEL_VOLUME = 0.25F;

    private RealmPortalLinker() {
    }

    /** The search radius in {@code target}: 128 in Overworld, ⌊128 ÷ scale⌋ in a realm (8 at 1:16). */
    public static int searchRadius(ServerLevel target) {
        return target.dimension() == Level.OVERWORLD ? OVERWORLD_SEARCH_RADIUS : realmSearchRadius(target.dimensionType());
    }

    /** The build radius in {@code target}: 16 in Overworld, ⌊128 ÷ scale⌋ − 1 in a realm (7 at 1:16). */
    public static int buildRadius(ServerLevel target) {
        return target.dimension() == Level.OVERWORLD ? OVERWORLD_BUILD_RADIUS : realmSearchRadius(target.dimensionType()) - 1;
    }

    static int realmSearchRadius(DimensionType realm) {
        return realmSearchRadius(realm.coordinateScale());
    }

    /** ⌊128 ÷ scale⌋, at least 2 so a realm always has room to build. */
    static int realmSearchRadius(double scale) {
        return Math.max(2, (int) Math.floor(OVERWORLD_SEARCH_RADIUS / scale));
    }

    /**
     * Where {@code target} aims from the opening centred at {@code centre} in {@code source}: its x and z times the
     * teleportation scale, clamped into the target's world border and rounded down, as the nether portal does.
     */
    public static BlockPos scaledPosition(DimensionType source, DimensionType target, WorldBorder border, Vec3 centre) {
        double scale = DimensionType.getTeleportationScale(source, target);
        return border.clampToBounds(centre.x * scale, centre.y, centre.z * scale);
    }

    /** The centre of a frame's opening, at the height of its bottom row of portal blocks. */
    public static Vec3 centre(PortalFrame.Frame frame) {
        return Vec3.atBottomCenterOf(frame.at(1, 0));
    }

    /**
     * The trip of {@code player} through the portal block at {@code entry}: the linked or newly built portal in the
     * destination, and where the player arrives there. Empty when the portal leads nowhere it can open (a broken frame,
     * an unknown realm, a dimension that is not loaded, or the dimension the player is in).
     */
    public static Optional<TeleportTransition> destination(ServerLevel source, ServerPlayer player, BlockPos entry) {
        BlockState state = source.getBlockState(entry);
        if (!(state.getBlock() instanceof RealmPortalBlock)) {
            return Optional.empty();
        }
        Optional<PortalFrame.Frame> sourceFrame = RealmPortals.frameOf(source, entry, state.getValue(RealmPortalBlock.AXIS));
        Registry<Realm> realms = source.registryAccess().lookupOrThrow(WorldRegistries.REALM);
        Optional<Holder.Reference<Realm>> target = sourceFrame
                .flatMap(frame -> RealmPortals.destination(source, frame.selector()))
                .flatMap(realms::get);
        Optional<Holder.Reference<Realm>> origin = Realms.entry(source.registryAccess(), source.dimension());
        if (sourceFrame.isEmpty() || target.isEmpty() || origin.isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = source.getServer().getLevel(target.get().value().dimension());
        if (level == null || level == source) {
            return Optional.empty();
        }
        Optional<PortalFrame.Frame> exit = exitPortal(source, sourceFrame.get(), level, target.get().value(),
                origin.get().key().identifier());
        if (exit.isEmpty()) {
            return Optional.empty();
        }
        // The realm's pitch: the one entered, or on the way back, the one left.
        float pitch = (level.dimension() == Level.OVERWORLD ? origin : target).get().value().soundPitch();
        return Optional.of(arrive(level, exit.get(), pitch));
    }

    /**
     * The portal the trip from {@code sourceFrame} arrives at in {@code level}: the nearest lit one leading back to
     * {@code back} within the search radius, or one built within the build radius.
     */
    static Optional<PortalFrame.Frame> exitPortal(ServerLevel source, PortalFrame.Frame sourceFrame, ServerLevel level,
            Realm target, Identifier back) {
        BlockPos scaled = scaledPosition(source.dimensionType(), level.dimensionType(), level.getWorldBorder(),
                centre(sourceFrame));
        Optional<PortalFrame.Frame> linked = findPortal(level, scaled, searchRadius(level), back);
        if (linked.isPresent()) {
            return linked;
        }
        Realm.Arrival arrival = target.arrival();
        int preferredY = arrival.clampY(sourceFrame.selector().getY());
        PortalFrame.Frame frame = PortalSpots.find(terrain(level), scaled, buildRadius(level), arrival.minY(),
                        arrival.maxY(), preferredY)
                .orElseGet(() -> carve(level, scaled.atY(preferredY), sourceFrame.axis(), arrival));
        RealmPortals.build(level, frame, back);
        return Optional.of(frame);
    }

    /**
     * The nearest lit portal within {@code radius} of {@code centre} (a square, as vanilla's) whose selector leads to
     * {@code back}; the lowest first among equally near ones. Reads point-of-interest data, as vanilla's search does.
     */
    public static Optional<PortalFrame.Frame> findPortal(ServerLevel level, BlockPos centre, int radius, Identifier back) {
        PoiManager pois = level.getPoiManager();
        pois.ensureLoadedAndValid(level, centre, radius);
        WorldBorder border = level.getWorldBorder();
        return pois.getInSquare(type -> type.is(RealmPortalBlocks.SELECTOR_POI), centre, radius,
                        PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .filter(border::isWithinBounds)
                .filter(pos -> RealmPortals.destination(level, pos).filter(back::equals).isPresent())
                .filter(pos -> PortalFrame.around(RealmPortals.lookup(level), pos).complete())
                .min(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(centre)).thenComparingInt(Vec3i::getY))
                .map(pos -> PortalFrame.around(RealmPortals.lookup(level), pos).frame());
    }

    /**
     * Where nothing fits (a realm with a ceiling, a void, water, Overworld's sea): the frame is carved out at
     * {@code at}, on a floor of the arrival block under and in front of it, with free space around it; fluids and
     * falling blocks at the pocket's edge become floor blocks too, so none runs or falls in (realm portal spec,
     * "Building a portal").
     */
    static PortalFrame.Frame carve(ServerLevel level, BlockPos at, Direction.Axis axis, Realm.Arrival arrival) {
        PortalFrame.Frame frame = new PortalFrame.Frame(at, axis);
        BlockState floor = BuiltInRegistries.BLOCK.getOptional(arrival.floor()).orElse(Blocks.STONE_BRICKS)
                .defaultBlockState();
        WorldBorder border = level.getWorldBorder();
        int radius = arrival.pocketRadius();
        Direction front = frame.front();
        for (int across = -2 - radius; across <= PortalFrame.WIDTH + 1 + radius; across++) {
            for (int depth = -radius - 1; depth <= radius + 1; depth++) {
                boolean edge = across < -1 - radius || across > PortalFrame.WIDTH + radius || Math.abs(depth) > radius;
                for (int up = -1; up <= PortalFrame.HEIGHT + 1; up++) {
                    BlockPos pos = frame.at(across, up).relative(front, depth);
                    if (!border.isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    if (edge || up == PortalFrame.HEIGHT + 1) {
                        if (unsafe(level.getBlockState(pos))) {
                            level.setBlock(pos, floor, Block.UPDATE_ALL);
                        }
                    } else {
                        level.setBlock(pos, up == -1 ? floor : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        return frame;
    }

    /** A fluid or a falling block, which would run or fall into a carved pocket. */
    private static boolean unsafe(BlockState state) {
        return !state.getFluidState().isEmpty() || state.getBlock() instanceof Fallable;
    }

    /**
     * The player arrives in front of {@code exit}, facing away from it, when one side of the opening has floor and two
     * free blocks; otherwise in the opening, as a nether portal places them, where vanilla's portal cooldown keeps them
     * from bouncing straight back. The trip plays the vanilla travel sound at the realm's pitch and loads the chunks
     * around the exit for a short time, through vanilla's portal ticket.
     */
    static TeleportTransition arrive(ServerLevel level, PortalFrame.Frame exit, float pitch) {
        Optional<Direction> side = PortalSpots.arrival(terrain(level), exit);
        BlockPos foot = side.map(direction -> exit.at(1, 0).relative(direction)).orElse(exit.at(1, 0));
        float yaw = side.orElse(exit.front()).toYRot();
        BlockPos ticket = exit.at(1, 0);
        TeleportTransition.PostTeleportTransition post = entity -> {
            playTravelSound(entity, pitch);
            entity.placePortalTicket(ticket);
        };
        return new TeleportTransition(level, Vec3.atBottomCenterOf(foot), Vec3.ZERO, yaw, 0.0F, Set.of(Relative.X_ROT),
                post);
    }

    private static void playTravelSound(Entity entity, float pitch) {
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PORTAL_TRAVEL), SoundSource.AMBIENT,
                    player.getX(), player.getY(), player.getZ(), TRAVEL_VOLUME,
                    pitch * (player.getRandom().nextFloat() * 0.4F + 0.8F), player.getRandom().nextLong()));
        }
    }

    /** The level as {@link PortalSpots.Terrain}. */
    static PortalSpots.Terrain terrain(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        return new PortalSpots.Terrain() {
            @Override
            public boolean solid(BlockPos pos) {
                if (level.isOutsideBuildHeight(pos)) {
                    return false;
                }
                BlockState state = level.getBlockState(pos);
                return !state.canBeReplaced() && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                        && state.isFaceSturdy(level, pos, Direction.UP) && !(state.getBlock() instanceof Fallable);
            }

            @Override
            public boolean free(BlockPos pos) {
                if (level.isOutsideBuildHeight(pos)) {
                    return false;
                }
                BlockState state = level.getBlockState(pos);
                return state.canBeReplaced() && state.getFluidState().isEmpty();
            }

            @Override
            public boolean inside(BlockPos pos) {
                return border.isWithinBounds(pos);
            }

            @Override
            public int top(int x, int z) {
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            }
        };
    }
}
