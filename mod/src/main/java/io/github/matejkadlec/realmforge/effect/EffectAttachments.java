package io.github.matejkadlec.realmforge.effect;

import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The effect feature's data attachments (architecture 7). */
public final class EffectAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:effect_sources}: who applied each status effect a spell gave the holder ({@link EffectSources}).
     * Saved, never synced, not copied on death (a death clears the effects).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EffectSources>> SOURCES =
            ATTACHMENTS.register("effect_sources", () -> AttachmentType.builder(() -> EffectSources.EMPTY)
                    .serialize(EffectSources.CODEC.fieldOf("value"))
                    .build());

    private EffectAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
