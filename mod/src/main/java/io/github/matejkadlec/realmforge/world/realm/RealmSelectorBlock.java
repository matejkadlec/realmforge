package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The realm selector, the bottom middle of a realm portal's frame (realm portal spec, "The realm selector"): a realm's
 * sigil used on it opens the portal onto that realm, an empty hand shows where it leads, and sneaking with an empty
 * main hand closes it, or opens it back to Overworld inside a realm. What happens lives in {@link RealmPortals}; the
 * setting lives in its block entity. A frame a trip built has {@link PortalStoneBlock#GENERATED} set, and drops nothing.
 */
public final class RealmSelectorBlock extends BaseEntityBlock {
    public static final MapCodec<RealmSelectorBlock> CODEC = simpleCodec(RealmSelectorBlock::new);

    public RealmSelectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PortalStoneBlock.GENERATED, false));
    }

    @Override
    protected MapCodec<RealmSelectorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PortalStoneBlock.GENERATED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RealmSelectorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        // The realm registry is synced, so the client knows a sigil too.
        Optional<Holder.Reference<Realm>> realm = Realms.bySigil(level.registryAccess(), stack.getItem());
        if (realm.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            RealmPortals.useSigil(serverPlayer, server, pos, realm.get());
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
