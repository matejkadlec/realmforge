package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Attachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The attachments of the {@code combat} feature (architecture 7). */
public final class CombatAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:magic_shield}: the points left in a player's magic shield and when it refills (architecture
     * 9.1). Transient: neither saved nor copied, so every new player entity (a login, a respawn) starts with a full
     * shield at its first tick. Synced to the owner, points only, for the HUD bar.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MagicShield>> MAGIC_SHIELD =
            ATTACHMENTS.register("magic_shield", () -> AttachmentType.builder(() -> MagicShield.EMPTY)
                    .sync(Attachments::toOwner, MagicShield.STREAM_CODEC).build());

    private CombatAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
