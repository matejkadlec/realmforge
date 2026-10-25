package io.github.matejkadlec.realmforge.dungeon.instance;

import io.github.matejkadlec.realmforge.world.GriefingProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * The rules inside the instance dimension: nobody edits the rooms (architecture 15.5), but for the blocks a room lets
 * explosions break. Survival players play in
 * Adventure mode there, which makes vanilla refuse their edits and lets the client predict the refusal; these guards
 * stop what Adventure mode lets through (items with {@code can_break} or {@code can_place_on}, buckets) and every edit
 * by anything that is not a player. Creative players keep their game mode and may edit.
 */
public final class InstanceRules {
    private InstanceRules() {
    }

    /** Registers the edit guards; the block break and bucket guards run on both sides, so the client predicts them. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, event -> {
            if (isInstance(event.getLevel()) && !isCreativePlayer(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, event -> {
            if (isInstance(event.getLevel()) && !event.getPlayer().isCreative()) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, event -> {
            // Buckets check only Level#mayInteract and Player#mayUseItemAt, which a matching can_place_on passes, and
            // place or pick up fluids without a block event.
            if (event.getItemStack().getItem() instanceof BucketItem && isInstance(event.getLevel())
                    && !event.getEntity().isCreative()) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        // The event's caller ignores cancelling and places the event's new state, so keep the block that was there.
        NeoForge.EVENT_BUS.addListener(BlockEvent.FluidPlaceBlockEvent.class, event -> {
            if (isInstance(event.getLevel())) {
                event.setNewState(event.getOriginalState());
            }
        });
        NeoForge.EVENT_BUS.addListener(BlockEvent.FarmlandTrampleEvent.class, event -> {
            if (isInstance(event.getLevel())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EntityMobGriefingEvent.class, event -> {
            if (InstanceDimension.isInstance(event.getEntity().level())) {
                event.setCanGrief(false);
            }
        });
        // The explosion still hurts and pushes players and mobs; it breaks no block and, with the same list, lights no
        // fire, but for the blocks its room lets explosions break (a trap's TNT blowing its way open, RLM-257), and it
        // spares the rooms' item frames and paintings, which its push alone would break (BlockAttachedEntity#push).
        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && InstanceDimension.isInstance(level)) {
                InstanceManager manager = InstanceManager.get(level.getServer());
                event.getAffectedBlocks().removeIf(pos -> manager == null || !explosionBreaks(manager, level, pos));
                event.getAffectedEntities().removeIf(GriefingProtection::isPlacedObject);
            }
        });
    }

    /**
     * Switches a survival player who is inside to Adventure mode, and returns the visit that remembers the game mode
     * to give back: the one it already remembers, else survival when this switched them. Other game modes stay.
     */
    static InstanceVisit enterAdventure(ServerPlayer player, InstanceVisit visit) {
        if (player.gameMode() != GameType.SURVIVAL || !player.setGameMode(GameType.ADVENTURE)) {
            return visit;
        }
        return visit.gameMode().isPresent() ? visit : visit.withGameMode(GameType.SURVIVAL);
    }

    /**
     * Gives a player who leaves their run back the game mode entering took from them, unless something else changed
     * it since (an operator's {@code /gamemode}).
     */
    static void restoreGameMode(ServerPlayer player, InstanceVisit visit) {
        visit.gameMode().ifPresent(mode -> {
            if (player.gameMode() == GameType.ADVENTURE) {
                player.setGameMode(mode);
            }
        });
    }

    private static boolean explosionBreaks(InstanceManager manager, ServerLevel level, BlockPos pos) {
        Identifier block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return manager.plot(PlotGrid.plotAt(pos.getX(), pos.getZ()))
                .map(plot -> plot.explosionBreaks(pos, block))
                .orElse(false);
    }

    private static boolean isInstance(LevelAccessor level) {
        return level instanceof Level world && InstanceDimension.isInstance(world);
    }

    private static boolean isCreativePlayer(Entity entity) {
        return entity instanceof Player player && player.isCreative();
    }
}
