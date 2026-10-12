package io.github.matejkadlec.realmforge.dungeon;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceVisit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The dungeon feature's data attachments (architecture 7). */
public final class DungeonAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:instance}: the run a player belongs to and where to send them back. Present only while they
     * belong to a run, so it is read with {@code getExistingData}; it has no meaningful default. Not synced.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<InstanceVisit>> VISIT =
            ATTACHMENTS.register("instance", () -> AttachmentType.<InstanceVisit>builder(() -> {
                throw new IllegalStateException("realmforge:instance has no default; read it with getExistingData");
            }).serialize(InstanceVisit.CODEC).copyOnDeath().build());

    private DungeonAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
