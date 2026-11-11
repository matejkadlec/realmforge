package io.github.matejkadlec.realmforge.client.render;

import io.github.matejkadlec.realmforge.world.WorldRegistries;
import io.github.matejkadlec.realmforge.world.realm.PortalFrame;
import io.github.matejkadlec.realmforge.world.realm.PortalStorm;
import io.github.matejkadlec.realmforge.world.realm.Realm;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalBlock;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalBlocks;
import io.github.matejkadlec.realmforge.world.realm.RealmPortals;
import io.github.matejkadlec.realmforge.world.realm.Realms;
import io.github.matejkadlec.realmforge.world.realm.SealstoneBlockEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * The realm portal's colour (architecture 18; realm portal spec, "Look and sound"): the portal's greyscale animated
 * textures, tinted with the colour of the entry its frame's sealstone opens onto, from the synced realm registry, and
 * pulled towards a dark grey while a storm blink darkens it ({@link PortalStorm}, RLM-300). The colour is baked into
 * the chunk mesh, so the sealstone marks the opening for re-rendering when its setting or the blink's darkness changes
 * ({@code SealstoneBlockEntity}). The chunk mesher's region holds the block entities of its sections, so the
 * sealstone, at most five blocks below a portal block, is there.
 */
public final class RealmPortalColors {
    /** Until the level is known: Overworld's green, the return portals' colour. */
    private static final int FALLBACK = 0xFF5DBB63;

    private RealmPortalColors() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class,
                event -> event.register(List.of(new Tint()), RealmPortalBlocks.portal()));
    }

    private static final class Tint implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? FALLBACK : Realms.overworld(level.registryAccess())
                    .map(entry -> entry.value().colour())
                    .orElse(FALLBACK);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            ClientLevel client = Minecraft.getInstance().level;
            if (client == null || !state.hasProperty(RealmPortalBlock.AXIS)) {
                return color(state);
            }
            Optional<PortalFrame.Frame> frame = RealmPortals.frameOf(level, pos, state.getValue(RealmPortalBlock.AXIS));
            Optional<Realm> destination = frame
                    .flatMap(found -> RealmPortals.destination(level, found.sealstone()))
                    .flatMap(id -> client.registryAccess().lookupOrThrow(WorldRegistries.REALM).get(id))
                    .map(Holder::value);
            int colour = destination.map(Realm::colour).orElseGet(() -> color(state));
            boolean dark = frame.map(found -> level.getBlockEntity(found.sealstone()))
                    .filter(SealstoneBlockEntity.class::isInstance)
                    .map(SealstoneBlockEntity.class::cast)
                    .filter(SealstoneBlockEntity::stormDark)
                    .isPresent();
            return dark ? PortalStorm.darken(colour) : colour;
        }
    }
}
