package io.github.matejkadlec.realmforge.world;

import com.mojang.serialization.Codec;
import io.github.matejkadlec.realmforge.Realmforge;
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
     * {@code realmforge:dark_vision}: whether the player's Dark Vision is switched on. On by default, because every
     * player has it (vision). Copied on death, because the effect itself is not; not synced, because the effect is.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> DARK_VISION =
            ATTACHMENTS.register("dark_vision", () -> AttachmentType.builder(() -> Boolean.TRUE)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build());

    private WorldAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
