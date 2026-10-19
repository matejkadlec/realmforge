package io.github.matejkadlec.realmforge.world.realm;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The realm selector's one setting (realm portal spec, "The realm selector"): the entry of {@code realmforge:realm} its
 * portal opens onto, a realm's or, for a return portal, Overworld's. It counts only while the opening is lit
 * ({@link RealmPortals#destination}), so a portal that went dark, closed by the sneak gesture or by a broken frame
 * block, leads nowhere until a sigil or the gesture lights it again. The selector stores no item, so hoppers have
 * nothing to take.
 *
 * <p>Clients get the setting with the chunk and on every change, because the portal blocks' colour comes from it. The
 * colour is baked into the chunk mesh, so a change marks the opening for re-rendering.
 */
public final class RealmSelectorBlockEntity extends BlockEntity {
    private static final String DESTINATION = "destination";

    private @Nullable Identifier destination;

    public RealmSelectorBlockEntity(BlockPos pos, BlockState state) {
        super(RealmPortalBlocks.SELECTOR_ENTITY.get(), pos, state);
    }

    /** The entry the portal opens onto while it is lit; empty when none was set. */
    public Optional<Identifier> storedDestination() {
        return Optional.ofNullable(destination);
    }

    /** Sets where the portal opens onto (null: nowhere) and sends it to clients. */
    void setDestination(@Nullable Identifier destination) {
        this.destination = destination;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        destination = input.getString(DESTINATION).map(Identifier::tryParse).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (destination != null) {
            output.putString(DESTINATION, destination.toString());
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        super.onDataPacket(connection, input);
        rerenderOpening();
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        rerenderOpening();
    }

    /** On the client: the opening's portal blocks take the new colour, which the chunk mesh bakes in. */
    private void rerenderOpening() {
        if (level == null || !level.isClientSide()) {
            return;
        }
        BlockState above = level.getBlockState(worldPosition.above());
        if (!above.is(RealmPortalBlocks.portal())) {
            return;
        }
        PortalFrame.Frame frame = new PortalFrame.Frame(worldPosition, above.getValue(RealmPortalBlock.AXIS));
        for (BlockPos pos : frame.opening()) {
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
        }
    }
}
