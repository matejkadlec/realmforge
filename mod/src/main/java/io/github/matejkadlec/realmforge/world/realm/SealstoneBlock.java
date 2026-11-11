package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The sealstone, the bottom middle of a realm portal's frame (realm portal spec, "The sealstone"): a realm's
 * signet used on it opens the portal onto that realm, or inside that realm back onto Overworld, an empty hand shows
 * where it leads, and sneaking with an empty main hand closes it, or opens it back to Overworld inside a realm. What
 * happens lives in {@link RealmPortals}; the setting lives in its block entity. A frame a trip built has {@link GatewrightBricksBlock#GENERATED} set, and drops nothing.
 *
 * <p>It glows while a player holding a signet that lights it stands near ({@link RealmPortals#tickGlow}, RLM-406): its
 * {@link #GLOW} is its block light, so every player sees it. Like the bricks it has a
 * {@link FrameLook#PROPERTY look}, the open realm's while the portal is lit (RLM-301): its top shows the signet's
 * socket, empty or holding the realm's stone. Breaking it washes the rest of its frame out again.
 */
public final class SealstoneBlock extends BaseEntityBlock {
    public static final MapCodec<SealstoneBlock> CODEC = simpleCodec(SealstoneBlock::new);
    /** The full glow: light level 15, as a beacon's. */
    public static final int MAX_GLOW = 15;
    /** How brightly the sealstone glows, from dark (0) to {@link #MAX_GLOW}: its block light. */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, MAX_GLOW);

    public SealstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GatewrightBricksBlock.GENERATED, false).setValue(GLOW, 0)
                .setValue(FrameLook.PROPERTY, FrameLook.WASHED));
    }

    @Override
    protected MapCodec<SealstoneBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GatewrightBricksBlock.GENERATED, GLOW, FrameLook.PROPERTY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SealstoneBlockEntity(pos, state);
    }

    /**
     * The glow, the frame's look and the storm's moment are the server's: the block states it sets and the block
     * event it sends reach every client. A client only counts a storm blink down ({@link SealstoneBlockEntity}).
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, RealmPortalBlocks.SEALSTONE_ENTITY.get(), SealstoneBlockEntity::clientTick)
                : createTickerHelper(type, RealmPortalBlocks.SEALSTONE_ENTITY.get(), RealmPortals::tickSealstone);
    }

    /** A sealstone that is gone leaves no lit frame: the bricks round it wash out again (RLM-301). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        RealmPortals.wash(level, pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        // The realm registry is synced, so the client knows a signet too.
        Optional<Holder.Reference<Realm>> realm = Realms.bySignet(level.registryAccess(), stack.getItem());
        if (realm.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            RealmPortals.useSignet(serverPlayer, server, pos, realm.get());
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isSecondaryUseActive()) {
                RealmPortals.useSneaking(serverPlayer, server, pos);
            } else {
                RealmPortals.showDestination(serverPlayer, server, pos);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }
}
