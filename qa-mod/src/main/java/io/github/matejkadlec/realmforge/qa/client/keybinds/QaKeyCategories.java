package io.github.matejkadlec.realmforge.qa.client.keybinds;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The dev client's check of the Key Binds screen's headers (RLM-382): once the title screen shows with its resources
 * loaded, it logs every key category the screen lists with its header, and an error for each category whose header
 * has no translation, which the screen shows as the raw key, usually {@code key.category.<namespace>.<path>}. It reads
 * the header the screen shows, {@code KeyMapping.Category#label()}, which a mod may change: Just Zoom's mixin labels
 * its category with a key of its own. The dev client loads the whole pack (ADR 0042), so a mod update that brings such
 * a header back shows up in the log of the next launch. The core mod's resources carry the headers the pack's mods
 * lack ({@code assets/<mod id>/lang/en_us.json}, architecture 19.4).
 */
public final class QaKeyCategories {
    private static boolean checked;

    private QaKeyCategories() {
    }

    /** Registers the check. Called once from the QA mod's client entry point. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            // The title screen exists behind the loading overlay before the first resource load has finished.
            if (!checked && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                checked = true;
                check(minecraft);
            }
        });
    }

    /** The categories the Key Binds screen lists, in its order (vanilla's sort of the key mappings). */
    static List<KeyMapping.Category> listed(Minecraft minecraft) {
        KeyMapping[] mappings = minecraft.options.keyMappings.clone();
        Arrays.sort(mappings);
        Set<KeyMapping.Category> categories = new LinkedHashSet<>();
        for (KeyMapping mapping : mappings) {
            categories.add(mapping.getCategory());
        }
        return new ArrayList<>(categories);
    }

    /** The translation key a category's header shows raw, or null when it has a text. */
    static String rawHeaderKey(KeyMapping.Category category) {
        Component label = category.label();
        if (label.getContents() instanceof TranslatableContents contents && contents.getFallback() == null
                && !I18n.exists(contents.getKey())) {
            return contents.getKey();
        }
        return null;
    }

    private static void check(Minecraft minecraft) {
        List<KeyMapping.Category> categories = listed(minecraft);
        List<String> headers = new ArrayList<>();
        int missing = 0;
        for (KeyMapping.Category category : categories) {
            String key = rawHeaderKey(category);
            headers.add(category.label().getString() + " [" + category.id() + "]");
            if (key != null) {
                missing++;
                String mod = ModList.get().getModContainerById(category.id().getNamespace())
                        .map(container -> container.getModInfo().getDisplayName())
                        .orElse(category.id().getNamespace());
                RealmforgeQa.LOGGER.error("Key category {} of {} has no translation for {}: Key Binds shows the raw "
                        + "key as its header (RLM-382)", category.id(), mod, key);
            }
        }
        RealmforgeQa.LOGGER.info("Key categories in Key Binds ({}, {} without a header): {}", categories.size(),
                missing, String.join(", ", headers));
    }
}
