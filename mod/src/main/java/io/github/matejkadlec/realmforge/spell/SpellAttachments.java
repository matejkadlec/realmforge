package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Attachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The spell feature's data attachments (architecture 7). */
public final class SpellAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Realmforge.MOD_ID);

    /** {@code realmforge:spellbook}: a player's known spells and spell bar. Owner-only sync; copied on death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Spellbook>> SPELLBOOK =
            ATTACHMENTS.register("spellbook", () -> AttachmentType.builder(() -> Spellbook.EMPTY)
                    .serialize(Spellbook.CODEC)
                    .copyOnDeath()
                    .sync(Attachments::toOwner, Spellbook.STREAM_CODEC)
                    .build());

    /**
     * {@code realmforge:combat}: resource, cooldowns and current cast of players and of mobs that cast. A mutable
     * value: change it in place and call {@code syncData}. Synced to the owner only (a mob's copy reaches nobody but
     * the pairing sync, and clients never read it). Copied on death without the current cast.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CombatState>> COMBAT =
            ATTACHMENTS.register("combat", () -> AttachmentType.builder(CombatState::new)
                    .serialize(CombatState.CODEC)
                    .copyOnDeath()
                    .sync(Attachments::toOwner, CombatState.STREAM_CODEC)
                    .build());

    private SpellAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
