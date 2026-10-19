package io.github.matejkadlec.realmforge.dungeon.portal;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The way out of a dungeon (W4d, architecture 15.5): a plate one pixel high without collision, which the builder lays
 * where players arrive and at every exit marker. Using it leaves the dungeon at any time, as {@code /instance leave}
 * does.
 */
public final class DungeonExitBlock extends Block {
    public static final MapCodec<DungeonExitBlock> CODEC = simpleCodec(DungeonExitBlock::new);
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 1.0);

    public DungeonExitBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DungeonExitBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            InstanceManager manager = InstanceManager.get(serverPlayer.level().getServer());
            if (manager != null && manager.leave(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_DUNGEON_LEFT));
            } else {
                serverPlayer.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_NOT_IN_DUNGEON));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }
}
