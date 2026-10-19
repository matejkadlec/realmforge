package io.github.matejkadlec.realmforge.dungeon.portal;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.plan.PlanningException;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.slf4j.Logger;

/**
 * What using a dungeon portal does (W4d, architecture 15.5). A key opens it: a new run of the key's dungeon type for
 * the player who used it (solo, D8), built in a plot and open for the type's open time, and the key is used up; a key
 * that opens nothing stays with the player. Without a key, an open portal takes that player in once the run is built.
 * An open portal withstands breaking and explosions.
 */
public final class DungeonPortals {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DungeonPortals() {
    }

    static void register() {
        // Creative players break blocks without a destroy progress, so the guard covers them too.
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, event -> {
            if (isOpenPortal(event.getState())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, event -> event.getAffectedBlocks()
                .removeIf(pos -> isOpenPortal(event.getLevel().getBlockState(pos))));
    }

    /** {@code stack}, a dungeon key, was used on the portal at {@code pos}. */
    static void useKey(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        if (InstanceDimension.isInstance(level)) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_INSIDE_DUNGEON));
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof DungeonPortalBlockEntity portal)) {
            return;
        }
        if (portal.isOpen(level)) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_ALREADY_OPEN));
            return;
        }
        Optional<Holder.Reference<DungeonType>> type = typeOfKey(level, stack);
        InstanceManager manager = InstanceManager.get(level.getServer());
        if (type.isEmpty() || manager == null) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_FAILED));
            return;
        }
        if (manager.plotOf(player).isPresent()) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_DUNGEON_ALREADY_OPEN));
            return;
        }
        Plot plot;
        try {
            plot = manager.openPortal(player, type.get(), GlobalPos.of(level.dimension(), pos));
        } catch (PlanningException | IllegalArgumentException e) {
            LOGGER.error("A {} portal could not open: {}", type.get().key().identifier(), e.getMessage(), e);
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_FAILED));
            return;
        }
        stack.consume(1, player);
        portal.open(plot.id(), plot.run());
        DungeonType dungeon = type.get().value();
        tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_OPENING,
                Component.translatable(dungeon.name()), dungeon.openMinutes()));
    }

    /** The portal at {@code pos} was used without a key. */
    static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof DungeonPortalBlockEntity portal)) {
            return;
        }
        InstanceManager manager = InstanceManager.get(level.getServer());
        if (manager == null || !portal.isOpen(level)) {
            tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_NEEDS_KEY));
            return;
        }
        switch (manager.usePortal(player, portal.plot(), portal.run())) {
            case ENTERED -> {
            }
            case STILL_OPENING -> tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_STILL_OPENING));
            case NOT_YOURS -> tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_NOT_YOURS));
            case CLOSED -> tell(player, Component.translatable(GeneratedTexts.MESSAGE_PORTAL_NEEDS_KEY));
        }
    }

    /** The dungeon type whose key {@code stack} is. */
    public static Optional<Holder.Reference<DungeonType>> typeOfKey(Level level, ItemStack stack) {
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return level.registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE).listElements()
                .filter(type -> type.value().key().equals(key))
                .findFirst();
    }

    private static boolean isOpenPortal(BlockState state) {
        return state.getBlock() instanceof DungeonPortalBlock && state.getValue(DungeonPortalBlock.OPEN);
    }

    private static void tell(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
}
