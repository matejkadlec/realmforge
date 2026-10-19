package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The portal block that fills a lit realm portal's opening (realm portal spec, "Travel", "Look and sound", "Edge
 * cases"). Like vanilla's nether portal block it has an axis, no collision, no item and no drops, and it checks its
 * frame on every neighbour update: a broken frame block takes the whole opening with it. Unlike it:
 *
 * <ul>
 *   <li>only players travel: a player without access to the destination is refused when they step in, so the
 *       countdown never starts, and every other entity is ignored ({@link #entityInside});
 *   <li>a trip takes 2 seconds, one tick in creative, and shows no vanilla portal overlay ({@link Portal.Transition#NONE});
 *   <li>it refuses every fluid, flowing or from a bucket or a dispenser ({@link LiquidBlockContainer},
 *       {@link #canBeReplaced(BlockState, Fluid)}), so no fluid washes the opening away;
 *   <li>its colour, particles and hum are the destination realm's, read from the frame's selector on the client.
 * </ul>
 */
public final class RealmPortalBlock extends Block implements Portal, LiquidBlockContainer {
    public static final MapCodec<RealmPortalBlock> CODEC = simpleCodec(RealmPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** How long a player stands in the portal before the trip: 2 seconds (the nether portal takes 4). */
    public static final int TRANSITION_TICKS = 40;
    /** The same, in creative mode. */
    public static final int CREATIVE_TRANSITION_TICKS = 1;

    private static final Map<Direction.Axis, VoxelShape> SHAPES =
            Shapes.rotateHorizontalAxis(Block.column(4.0, 16.0, 0.0, 16.0));
    private static final float DUST_SCALE = 1.0F;

    public RealmPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<RealmPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    /** A neighbour changed: the portal stays only while its frame is complete. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (neighbourState.is(this) && neighbourState.getValue(AXIS) == state.getValue(AXIS)) {
            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState,
                    random);
        }
        return RealmPortals.frameOf(level, pos, state.getValue(AXIS)).isPresent()
                ? super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player && player.canUsePortal(false)
                && RealmPortals.mayTravel(player, server, pos, state.getValue(AXIS))) {
            player.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player player && player.getAbilities().invulnerable
                ? CREATIVE_TRANSITION_TICKS
                : TRANSITION_TICKS;
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity,
            BlockPos portalEntryPos) {
        return entity instanceof ServerPlayer player
                ? RealmPortalLinker.destination(currentLevel, player, portalEntryPos).orElse(null)
                : null;
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.NONE;
    }

    /** The destination realm's hum, and a swirl of dust in its colour with its own particle. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Optional<Realm> realm = RealmPortals.destinationOfPortal(level, pos, state.getValue(AXIS));
        if (realm.isEmpty()) {
            return;
        }
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT,
                    SoundSource.BLOCKS, 0.5F, realm.get().soundPitch() * (random.nextFloat() * 0.4F + 0.8F), false);
        }
        DustParticleOptions dust = new DustParticleOptions(realm.get().colour(), DUST_SCALE);
        boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < 3; i++) {
            int side = random.nextInt(2) * 2 - 1;
            double across = random.nextDouble();
            double x = pos.getX() + (alongX ? across : 0.5 + 0.25 * side);
            double z = pos.getZ() + (alongX ? 0.5 + 0.25 * side : across);
            double y = pos.getY() + random.nextDouble();
            double outwards = random.nextFloat() * 0.5 * side;
            level.addParticle(dust, x, y, z, alongX ? 0.0 : outwards, 0.02, alongX ? outwards : 0.0);
        }
        realm.get().particle()
                .flatMap(BuiltInRegistries.PARTICLE_TYPE::getOptional)
                .filter(SimpleParticleType.class::isInstance)
                .map(SimpleParticleType.class::cast)
                .filter(particle -> random.nextInt(3) == 0)
                .ifPresent(particle -> level.addParticle(particle, pos.getX() + random.nextDouble(),
                        pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> state.setValue(AXIS,
                    state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }

    /** A bucket or a dispenser never replaces the portal: it empties in front of it, or not at all. */
    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    /** Flowing water and lava never wash the portal away. */
    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state,
            Fluid type) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
