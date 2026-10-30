package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
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

    /**
     * {@code realmforge:starting_kit}: whether the player has received a class's starting kit. It comes with their
     * first character only, so a new character after a reset (a QA command in M1, an in-game way later) brings no
     * second kit (Matěj, 2026-10-30). Server-side, saved and copied on death.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> STARTING_KIT =
            ATTACHMENTS.register("starting_kit", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("value")).copyOnDeath().build());

    private CharacterAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
