package io.github.matejkadlec.realmforge.stats;

import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The stats feature's data attachments (architecture 7). */
public final class StatsAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:level}: the level of every living entity, the one fact many features read (architecture
     * 4.4). Synced to the entity itself and everyone tracking it. Copied on death, and so also when a mob converts
     * (a zombie drowns): NeoForge copies the same attachments on {@code LivingConversionEvent.Post}.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EntityLevel>> LEVEL =
            ATTACHMENTS.register("level", () -> AttachmentType.builder(() -> EntityLevel.DEFAULT)
                    .serialize(EntityLevel.CODEC.fieldOf("value")).copyOnDeath().sync(EntityLevel.STREAM_CODEC).build());

    private StatsAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
