package io.github.matejkadlec.realmforge.world;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Every block a player breaks passes vanilla's {@code Level#mayInteract}: spawn protection (Overworld's, on a dedicated
 * server) and the world border (RLM-559). Vanilla asks it only for the block a player starts breaking
 * ({@code ServerPlayerGameMode#handleBlockBreakAction}); an area tool breaks the blocks around that one through
 * {@code ServerPlayerGameMode#destroyBlock}, which skips it: Apotheosis's Radial and Super-Massive did, so a 3 by 3
 * pickaxe at the edge of spawn protection broke into it. Instances need nothing more: every extra block posts
 * {@link BreakBlockEvent}, which {@code dungeon/instance/InstanceRules} cancels for a block outside its allowlist, and
 * Adventure mode refuses it before that.
 */
public final class BlockBreakGuard {
    private BlockBreakGuard() {
    }

    /** Cancels a break of a block the player may not touch there. */
    static void onBreakBlock(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !level.mayInteract(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
        }
    }
}
