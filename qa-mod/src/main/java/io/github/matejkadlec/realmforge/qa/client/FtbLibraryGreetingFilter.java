package io.github.matejkadlec.realmforge.qa.client;

import io.github.matejkadlec.realmforge.qa.FtbLibraryGreeting;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Drops FTB Library's dev-only greeting from the dev client's chat ({@link FtbLibraryGreeting}, RLM-520), so joining a
 * world shows only Realmforge's welcome line, as it does in the pack. A chat line, not an action bar one.
 */
public final class FtbLibraryGreetingFilter {
    private FtbLibraryGreetingFilter() {
    }

    /** Called once from {@code RealmforgeQaClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, event -> {
            if (!event.isOverlay() && FtbLibraryGreeting.isGreeting(event.getMessage())) {
                event.setCanceled(true);
            }
        });
    }
}
