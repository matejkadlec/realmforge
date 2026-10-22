package io.github.matejkadlec.betterkeybinds;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * The secondary key column of the Key Binds screen, shared by the row mixins of vanilla's list and Controlling's: a
 * second key button right of the primary one, WoW style ({@code [name] [primary] [secondary] [Reset]}). The rows grow
 * by {@link #SHIFT} and the primary button moves left by as much, so the secondary button takes the primary's old
 * place next to Reset. The labels follow the screen's own: {@code [ key ]} in yellow for a conflict,
 * {@code > key <} while the button waits for a key.
 */
public final class SecondaryKeyColumn {
    public static final int BUTTON_WIDTH = 75;
    /** The secondary button's width and the gap before it. */
    public static final int SHIFT = BUTTON_WIDTH + 5;
    // The screen's conflict marker colour, a yellow stripe left of a conflicting key's button.
    private static final int CONFLICT_STRIPE = 0xFFFFFF00;
    // AbstractSelectionList puts the scroll bar 8 px right of a row (scrollbar width 6 plus 2), and it must stay in
    // the list, with a margin, on either side.
    private static final int ROW_MARGIN = 28;

    private SecondaryKeyColumn() {
    }

    /** The secondary key button of the row of {@code primary}, named {@code name}; a press waits for its key. */
    public static Button createButton(KeyMapping primary, Component name) {
        SecondaryKeys.getOrCreate(primary);
        Component secondaryName = Component.translatable("better_key_binds.secondary_of", name);
        return Button.builder(Component.empty(), button -> SecondaryKeyCapture.start(primary))
                .bounds(0, 0, BUTTON_WIDTH, 20)
                .createNarration(defaultNarration -> SecondaryKeys.isUnbound(primary)
                        ? Component.translatable("narrator.controls.unbound", secondaryName)
                        : Component.translatable("narrator.controls.bound", secondaryName, defaultNarration.get()))
                .build();
    }

    /** A Key Binds list's row width with room for the secondary column, as far as the list is wide. */
    public static int widenRow(int rowWidth, int listWidth) {
        return Math.max(rowWidth, Math.min(rowWidth + SHIFT, listWidth - ROW_MARGIN));
    }

    /**
     * Updates the secondary button of the row of {@code primary}: its key, the conflict marker and tooltip, the
     * waiting marker. Returns whether the secondary key conflicts with another key.
     */
    public static boolean refresh(Button button, KeyMapping primary) {
        SecondaryKeyMapping secondary = SecondaryKeys.getOrCreate(primary);
        List<Component> conflicts = Conflicts.of(secondary, primary, keyMappings(), true, true);
        MutableComponent tooltip = Component.translatable("better_key_binds.secondary_key");
        Component message = secondary.getTranslatedKeyMessage();
        if (!conflicts.isEmpty()) {
            message = conflictLabel(message);
            tooltip.append(CommonComponents.NEW_LINE).append(duplicates(conflicts));
        }
        if (SecondaryKeyCapture.isWaitingFor(primary)) {
            message = waitingLabel(message);
        }
        button.setMessage(message);
        button.setTooltip(Tooltip.create(tooltip));
        return !conflicts.isEmpty();
    }

    /**
     * Adds the conflicts of a row's primary key with other key binds' secondary keys to its button, which the
     * screen's own refresh has just set; null when there are none and the button stays as the screen left it.
     * Otherwise returns the full list of conflicting keys (primary and secondary), the button relabelled with the
     * conflict marker (and the waiting marker when {@code selected}). {@code tooltipPrefix}, Controlling's category
     * line, goes before the conflict line of the new tooltip.
     */
    public static @Nullable List<Component> refreshPrimary(Button changeButton, KeyMapping primary, boolean selected,
            @Nullable Component tooltipPrefix) {
        KeyMapping[] keyMappings = keyMappings();
        if (Conflicts.of(primary, primary, keyMappings, false, true).isEmpty()) {
            return null;
        }
        List<Component> conflicts = Conflicts.of(primary, primary, keyMappings, true, true);
        Component message = conflictLabel(primary.getTranslatedKeyMessage());
        changeButton.setMessage(selected ? waitingLabel(message) : message);
        MutableComponent tooltip = tooltipPrefix == null
                ? duplicates(conflicts)
                : tooltipPrefix.copy().append(CommonComponents.NEW_LINE).append(duplicates(conflicts));
        changeButton.setTooltip(Tooltip.create(tooltip));
        return conflicts;
    }

    /** Draws the secondary button and, for a conflict, the screen's yellow stripe in the gap left of it. */
    public static void render(GuiGraphicsExtractor graphics, Button button, boolean conflict, int top, int bottom,
            int mouseX, int mouseY, float partialTick) {
        button.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (conflict) {
            int stripeLeft = button.getX() - 4;
            graphics.fill(stripeLeft, top - 1, stripeLeft + 3, bottom, CONFLICT_STRIPE);
        }
    }

    static KeyMapping[] keyMappings() {
        return Minecraft.getInstance().options.keyMappings;
    }

    // "Also used for: a, b", as the screen lists a primary key's conflicts.
    private static MutableComponent duplicates(List<Component> names) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(names.get(i));
        }
        return Component.translatable("controls.keybinds.duplicateKeybinds", list);
    }

    private static Component conflictLabel(Component message) {
        return Component.literal("[ ")
                .append(message.copy().withStyle(ChatFormatting.WHITE))
                .append(" ]")
                .withStyle(ChatFormatting.YELLOW);
    }

    private static Component waitingLabel(Component message) {
        return Component.literal("> ")
                .append(message.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                .append(" <")
                .withStyle(ChatFormatting.YELLOW);
    }
}
