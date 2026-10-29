package io.github.matejkadlec.realmforge.world;

import com.mojang.serialization.Codec;
import io.github.matejkadlec.realmforge.Realmforge;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The world feature's data attachments (architecture 7). */
public final class WorldAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:night_vision}: whether the player's Night Vision is switched on. On by default, because every
     * player has it (vision). Copied on death, because the effect itself is not; not synced, because the effect is.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> NIGHT_VISION =
            ATTACHMENTS.register("night_vision", () -> AttachmentType.builder(() -> Boolean.TRUE)
                    .serialize(Codec.BOOL.fieldOf("value"))
                    .copyOnDeath()
                    .build());

    /**
     * {@code realmforge:visited_realms}: the ids of the realms the player has arrived in, so the arrival shows a realm's
     * flavour line only the first time (realm portal spec, "Arriving"). Copied on death; not synced.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<List<String>>> VISITED_REALMS =
            ATTACHMENTS.register("visited_realms", () -> AttachmentType.builder(() -> List.<String>of())
                    .serialize(Codec.STRING.listOf().fieldOf("value"))
                    .copyOnDeath()
                    .build());

    private WorldAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
