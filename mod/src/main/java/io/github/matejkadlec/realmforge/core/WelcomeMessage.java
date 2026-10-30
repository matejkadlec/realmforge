package io.github.matejkadlec.realmforge.core;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * The line a player gets in chat on joining a world, once per login (never on a respawn or a dimension change):
 * "Welcome to Realmforge v0.0.1 (Alpha)" (RLM-520, Matěj's format of 2026-10-29). Nothing in it is typed by hand: the
 * version is the mod's own ({@code mod_version} in {@code gradle.properties}, which the pack's version matches), and
 * the phase is the mod property {@code phase} that {@code neoforge.mods.toml} takes from {@code mod_phase} next to it.
 * The same line everywhere, the dev client included; with an empty phase (v1.0.0) the parentheses go too.
 */
public final class WelcomeMessage {
    private WelcomeMessage() {
    }

    static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(message());
        }
    }

    /** The welcome line for the running mod's version and phase. */
    public static Component message() {
        IModInfo info = ModList.get().getModContainerById(Realmforge.MOD_ID).orElseThrow().getModInfo();
        return message(version(info.getVersion().toString()), phase(info.getModProperties().get("phase")));
    }

    /** The welcome line for {@code version} and {@code phase}; an empty phase leaves out the parentheses. */
    public static Component message(String version, String phase) {
        return phase.isEmpty()
                ? Component.translatable(GeneratedTexts.MESSAGE_WELCOME, version)
                : Component.translatable(GeneratedTexts.MESSAGE_WELCOME_PHASE, version, phase);
    }

    /**
     * The version a player reads: the mod's version without its build metadata, so {@code 0.0.1+mc26.1.2} (the jar's
     * version, architecture 2) reads {@code 0.0.1}.
     */
    public static String version(String modVersion) {
        int metadata = modVersion.indexOf('+');
        return metadata < 0 ? modVersion : modVersion.substring(0, metadata);
    }

    /** The phase from the mod property, trimmed; missing means none. */
    public static String phase(Object property) {
        return property == null ? "" : property.toString().trim();
    }
}
