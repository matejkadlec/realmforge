package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Gatewright Bricks, the realm portal's frame block (realm portal spec, "The frame"). A frame a trip built has
 * {@link #GENERATED} set on its blocks, and its loot table drops the block only without it, so entering a portal and
 * breaking the frame the trip built yields nothing ("Building a portal"). Its {@link FrameLook#PROPERTY look} is
 * washed out until the frame it is part of lights: then {@link RealmPortals} gives it the look of the realm the portal
 * opens onto, and takes it back when the portal goes dark (RLM-301).
 */
public final class GatewrightBricksBlock extends Block {
    public static final MapCodec<GatewrightBricksBlock> CODEC = simpleCodec(GatewrightBricksBlock::new);
    /** True on the blocks of a frame a trip built; a player's placement never sets it. */
    public static final BooleanProperty GENERATED = BooleanProperty.create("generated");

    public GatewrightBricksBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GENERATED, false)
                .setValue(FrameLook.PROPERTY, FrameLook.WASHED));
    }

    @Override
    protected MapCodec<GatewrightBricksBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GENERATED, FrameLook.PROPERTY);
    }
}
