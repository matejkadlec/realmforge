package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The character feature's data attachments (architecture 7). */
public final class CharacterAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:character}: a player's race, classes, character XP and cached level cap. Small and nothing
     * secret, so it is synced to the player and everyone tracking them. Copied on death.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CharacterData>> CHARACTER =
            ATTACHMENTS.register("character", () -> AttachmentType.builder(() -> CharacterData.NONE)
                    .serialize(CharacterData.CODEC.fieldOf("value")).copyOnDeath().sync(CharacterData.STREAM_CODEC).build());

    private CharacterAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
