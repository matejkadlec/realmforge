package io.github.matejkadlec.realmforge.client.compat.apotheosis;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Apotheosis's potion charms are off in Realmforge, their recipes removed (RLM-272), but its client still marks every
 * potion that could not become one with "Cannot be converted into a Potion Charm." ({@code
 * AdventureModuleClient#showBlacklistedPotions}, the key {@value #BLACKLISTED_POTION}), an item that no longer exists
 * (Matěj, 2026-10-29). This listener runs after Apotheosis's (lowest priority) and drops that line. Runs only while
 * Apotheosis is loaded (architecture 19.4); it reads nothing of Apotheosis's but the translation key.
 */
public final class PotionCharmTooltip {
    private static final String MOD_ID = "apotheosis";
    static final String BLACKLISTED_POTION = "misc.apotheosis.blacklisted_potion";

    private PotionCharmTooltip() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        if (ModList.get().isLoaded(MOD_ID)) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ItemTooltipEvent.class,
                    event -> removeCharmLine(event.getToolTip()));
        }
    }

    /** Drops every line that says a potion cannot become a potion charm. */
    static void removeCharmLine(List<Component> lines) {
        lines.removeIf(PotionCharmTooltip::isCharmLine);
    }

    static boolean isCharmLine(Component line) {
        return line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(BLACKLISTED_POTION);
    }
}
