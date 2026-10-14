package io.github.matejkadlec.realmforge.mob;

import com.mojang.serialization.Codec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The mob feature's data attachments (architecture 7). */
public final class MobAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:pending_refill}: marks a mob leveled as it spawned, whose health {@link MobLevels} refills to
     * its scaled maximum when it joins the level, removing the mark (architecture 14.1). Saved, because a mob spawned
     * with a new chunk waits in the chunk's data until the chunk is added to the world; neither synced nor copied.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> PENDING_REFILL =
            ATTACHMENTS.register("pending_refill", () -> AttachmentType.builder(() -> Boolean.TRUE)
                    .serialize(Codec.BOOL.fieldOf("value"))
                    .build());

    private MobAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
