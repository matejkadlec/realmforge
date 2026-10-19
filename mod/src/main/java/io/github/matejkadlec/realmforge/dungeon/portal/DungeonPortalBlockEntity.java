package io.github.matejkadlec.realmforge.dungeon.portal;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The run an open dungeon portal leads to: its plot and the run's id, so a later run in the same plot is not taken for
 * it. While the portal is open it checks once a second whether its run is still open, and closes when it is not: the
 * timer ran out, the last player left, or the dungeon could not be built (architecture 15.5).
 */
public final class DungeonPortalBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 20;

    private int plot = -1;
    private long run;

    public DungeonPortalBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonBlocks.PORTAL_ENTITY.get(), pos, state);
    }

    /** The plot of the run the portal leads to; -1 while it is closed. */
    public int plot() {
        return plot;
    }

    /** The id of the run the portal leads to; 0 while it is closed. */
    public long run() {
        return run;
    }

    /** Whether the portal leads to a run that is still open. */
    public boolean isOpen(ServerLevel level) {
        InstanceManager manager = InstanceManager.get(level.getServer());
        return plot >= 0 && manager != null && manager.isOpen(plot, run);
    }

    /** Opens the portal to run {@code run} of plot {@code plot}. */
    void open(int plot, long run) {
        this.plot = plot;
        this.run = run;
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(DungeonPortalBlock.OPEN, true), Block.UPDATE_ALL);
    }

    /** Closes the portal: it leads nowhere and looks closed again. */
    void close() {
        plot = -1;
        run = 0L;
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(DungeonPortalBlock.OPEN, false), Block.UPDATE_ALL);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, DungeonPortalBlockEntity portal) {
        if (level instanceof ServerLevel server && server.getGameTime() % CHECK_INTERVAL == 0
                && !portal.isOpen(server)) {
            portal.close();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        plot = input.getIntOr("plot", -1);
        run = input.getLongOr("run", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("plot", plot);
        output.putLong("run", run);
    }
}
