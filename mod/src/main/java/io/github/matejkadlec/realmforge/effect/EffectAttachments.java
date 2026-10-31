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

    /**
     * {@code realmforge:effect_stacks}: the stacks of damage over time the holder carries ({@link EffectStacks},
     * RLM-539). Saved, never synced, not copied on death.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EffectStacks>> STACKS =
            ATTACHMENTS.register("effect_stacks", () -> AttachmentType.builder(EffectStacks::new)
                    .serialize(EffectStacks.CODEC.fieldOf("value"))
                    .build());

    /**
     * {@code realmforge:effect_looks}: how clients draw the holder because of its effects ({@link EffectLooks},
     * RLM-539). Saved, synced to every tracking player, not copied on death.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EffectLooks>> LOOKS =
            ATTACHMENTS.register("effect_looks", () -> AttachmentType.builder(() -> EffectLooks.NONE)
                    .serialize(EffectLooks.CODEC.fieldOf("value"))
                    .sync(EffectLooks.STREAM_CODEC)
                    .build());

    private EffectAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
