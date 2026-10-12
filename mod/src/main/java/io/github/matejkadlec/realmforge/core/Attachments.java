package io.github.matejkadlec.realmforge.core;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/** Building blocks for the features' data attachments (architecture 7). */
public final class Attachments {
    private Attachments() {
    }

    /**
     * Sync predicate for owner-only data ({@code AttachmentType.Builder#sync(predicate, streamCodec)}): a player
     * receives updates of their own attachment only. It filters updates, not the first sync when another player
     * starts tracking the holder, so owner-only data must hold nothing other clients must not see (architecture 7).
     */
    public static boolean toOwner(IAttachmentHolder holder, ServerPlayer to) {
        return holder == to;
    }
}
