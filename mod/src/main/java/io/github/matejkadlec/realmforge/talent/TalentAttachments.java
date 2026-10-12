package io.github.matejkadlec.realmforge.talent;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Attachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The talent feature's data attachments (architecture 7). */
public final class TalentAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /** {@code realmforge:talents}: a player's allocated ranks and unspent points. Owner-only sync; copied on death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TalentAllocation>> TALENTS =
            ATTACHMENTS.register("talents", () -> AttachmentType.builder(() -> TalentAllocation.NONE)
                    .serialize(TalentAllocation.CODEC)
                    .copyOnDeath()
                    .sync(Attachments::toOwner, TalentAllocation.STREAM_CODEC)
                    .build());

    private TalentAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
