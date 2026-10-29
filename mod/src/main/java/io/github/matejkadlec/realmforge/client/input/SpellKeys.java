package io.github.matejkadlec.realmforge.client.input;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.screen.SpellbookScreen;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * The spell bar's keys (C3b, RLM-25), rebindable in Options > Controls under Realmforge Spell Bar, and the spellbook's
 * key. Defaults (Matěj, 2026-10-15, 2026-10-16 and his playtest of 2026-10-21): R, T, F, G, H and C for the bar's
 * first six slots, Ů and § (the two keys right of L on his Czech QWERTZ keyboard: a US keyboard's {@code ;} and
 * {@code '}) for the last two, X for the racial ability beside the bar; P opens the spellbook. GLFW names keys by their
 * position on a US keyboard. Vanilla's chat (T), Swap Item With Offhand (F) and the creative toolbar keys (C, X) move
 * or go unbound in the pack's default keys ({@code pack/config/defaultoptions/keybindings.txt}); {@link KeyLabels}
 * makes the § key's label visible.
 */
public final class SpellKeys {
    /** The heading of the spell bar's keys; its name is the text {@code key.category.realmforge.spell_bar}. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Realmforge.id("spell_bar"));

    private static final int[] DEFAULTS = {GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G,
            GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_X};
    private static final String[] NAMES = {GeneratedTexts.KEY_SPELL_SLOT_1, GeneratedTexts.KEY_SPELL_SLOT_2,
            GeneratedTexts.KEY_SPELL_SLOT_3, GeneratedTexts.KEY_SPELL_SLOT_4, GeneratedTexts.KEY_SPELL_SLOT_5,
            GeneratedTexts.KEY_SPELL_SLOT_6, GeneratedTexts.KEY_SPELL_SLOT_7, GeneratedTexts.KEY_SPELL_SLOT_8,
            GeneratedTexts.KEY_RACIAL_ABILITY};

    /** The key of every slot, by slot: the bar's eight, then the racial one ({@link Spellbook#RACIAL_SLOT}). */
    private static final List<Lazy<KeyMapping>> SLOTS = slots();

    public static final Lazy<KeyMapping> SPELLBOOK = Lazy.of(() -> new RealmforgeKeyMapping(
            GeneratedTexts.KEY_SPELLBOOK, GLFW.GLFW_KEY_P, NightVisionKey.CATEGORY));

    /**
     * Held, it shows the target circle under the mob an enemy spell would go to now (RLM-468; Left Alt by default,
     * which no key of the pack takes): {@code client/spell/TargetCircle} reads it.
     */
    public static final Lazy<KeyMapping> SHOW_TARGET = Lazy.of(() -> new RealmforgeKeyMapping(
            GeneratedTexts.KEY_SHOW_TARGET, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

    private SpellKeys() {
    }

    /** Called once from {@code RealmforgeClient}, after {@link NightVisionKey}, which registers our main category. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CATEGORY);
            SLOTS.forEach(key -> event.register(key.get()));
            event.register(SPELLBOOK.get());
            event.register(SHOW_TARGET.get());
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
    }

    /** The key of {@code slot}, 0 to {@link Spellbook#RACIAL_SLOT}. */
    public static KeyMapping slot(int slot) {
        return SLOTS.get(slot).get();
    }

    private static List<Lazy<KeyMapping>> slots() {
        List<Lazy<KeyMapping>> keys = new ArrayList<>();
        for (int slot = 0; slot < Spellbook.SLOT_COUNT; slot++) {
            int index = slot;
            keys.add(Lazy.of(() -> new RealmforgeKeyMapping(NAMES[index], DEFAULTS[index], CATEGORY)));
        }
        return List.copyOf(keys);
    }

    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (SPELLBOOK.get().consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new SpellbookScreen());
            }
        }
    }
}
