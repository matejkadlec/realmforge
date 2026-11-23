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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The sealstone's one setting (realm portal spec, "The sealstone"): the entry of {@code realmforge:realm} its
 * portal opens onto, a realm's or, for a return portal, Overworld's. It counts only while the opening is lit
 * ({@link RealmPortals#destination}), so a portal that went dark, closed by the sneak gesture or by a broken frame
 * block, leads nowhere until a signet or the gesture lights it again. The sealstone stores no item, so hoppers have
 * nothing to take.
 *
 * <p>Clients get the setting with the chunk and on every change, because the portal blocks' colour comes from it. The
 * colour is baked into the chunk mesh, so a change marks the opening for re-rendering.
 *
 * <p>A storm blink (RLM-300, {@link PortalStorm}) reaches clients as the block event {@link #EVENT_STORM}: each then
 * counts the blink down on its own ({@link #clientTick}) and redraws the opening whenever its darkness changes, which
 * the portal's tint reads from {@link #stormDark}. Nothing of it is saved.
 */
public final class SealstoneBlockEntity extends BlockEntity {
    /** The block event that starts a storm blink on clients. */
    public static final int EVENT_STORM = 1;
    private static final String DESTINATION = "destination";
    /** No blink has begun. */
    private static final long NO_STORM = Long.MIN_VALUE;

    private @Nullable Identifier destination;
    /** On a client: the game time at which the running storm blink began, or {@link #NO_STORM}. */
    private volatile long stormStart = NO_STORM;
    /** On a client: whether the opening was last drawn dark, so it is redrawn only when that changes. */
    private boolean drawnDark;

    public SealstoneBlockEntity(BlockPos pos, BlockState state) {
        super(RealmPortalBlocks.SEALSTONE_ENTITY.get(), pos, state);
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

    /** On a client: whether a storm blink darkens the portal right now. */
    public boolean stormDark() {
        return level != null && stormStart != NO_STORM && PortalStorm.dark(level.getGameTime() - stormStart);
    }

    /** The server's block event: a client starts counting the blink; the server only lets the event through. */
    @Override
    public boolean triggerEvent(int id, int type) {
        if (id != EVENT_STORM) {
            return super.triggerEvent(id, type);
        }
        if (level != null && level.isClientSide()) {
            stormStart = level.getGameTime();
            drawnDark = true;
            rerenderOpening();
        }
        return true;
    }

    /**
     * A client's tick: while a blink runs, the opening is redrawn whenever its darkness changes (compared with how
     * it was last drawn, so a game time the server corrected by a tick never leaves it dark), and the blink is
     * forgotten once it is over. Block entities do not tick while the game is paused or its tick rate frozen, and
     * the game time stands still then, so a blink simply waits.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SealstoneBlockEntity entity) {
        if (entity.stormStart == NO_STORM) {
            return;
        }
        long ticks = level.getGameTime() - entity.stormStart;
        boolean dark = PortalStorm.dark(ticks);
        if (dark != entity.drawnDark) {
            entity.drawnDark = dark;
            entity.rerenderOpening();
        }
        if (PortalStorm.over(ticks)) {
            entity.stormStart = NO_STORM;
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
