package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Realmforge Bricks, the realm portal's frame block (realm portal spec, "The frame"). A frame a trip built has
 * {@link #GENERATED} set on its blocks, and its loot table drops the block only without it, so entering a portal and
 * breaking the frame the trip built yields nothing ("Building a portal").
 */
public final class RealmforgeBricksBlock extends Block {
    public static final MapCodec<RealmforgeBricksBlock> CODEC = simpleCodec(RealmforgeBricksBlock::new);
    /** True on the blocks of a frame a trip built; a player's placement never sets it. */
    public static final BooleanProperty GENERATED = BooleanProperty.create("generated");

    public RealmforgeBricksBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GENERATED, false));
    }

    @Override
    protected MapCodec<RealmforgeBricksBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GENERATED);
    }
}
