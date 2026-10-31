package io.github.matejkadlec.realmforge.qa;

import net.minecraft.network.chat.Component;

/**
 * FTB Library greets every player who logs in with "Hello from FTB Library!" in rainbow colours, but only in a
 * development environment ({@code FTBLibrary#playerJoined} checks its {@code Platform#isDev}, which is
 * {@code !FMLEnvironment.isProduction()}), so the pack never shows it and our dev runs always do (RLM-520). FTB
 * Library has no config for it. The dev client drops the line ({@code client/FtbLibraryGreetingFilter}), so joining a
 * world shows only our welcome line there too; {@code WelcomeGameTests} check that this matches the line FTB Library
 * really sends.
 */
public final class FtbLibraryGreeting {
    /** The greeting's text, a literal in FTB Library 26.1.2.9. */
    public static final String TEXT = "Hello from FTB Library!";

    private FtbLibraryGreeting() {
    }

    /** Whether {@code message} is FTB Library's greeting, whatever its colours. */
    public static boolean isGreeting(Component message) {
        return TEXT.equals(message.getString());
    }
}
