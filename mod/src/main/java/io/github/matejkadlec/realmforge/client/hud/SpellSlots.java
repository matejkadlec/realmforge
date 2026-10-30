package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * Draws a spell bar slot (C3b, RLM-25), on the HUD ({@link SpellBarHud}) and in the spellbook: a dark square framed in
 * the spell's school colour (a spell of no school: black, filled dark grey), the spell's initials until spells have
 * icons (C6, A1; the frame stays then, Matěj 2026-10-21), a blue tint while the player lacks its resource, the cooldown
 * sweep ({@link CooldownSweep}) with the seconds left of a long cooldown, a gold frame while it is being cast, and the
 * slot's key in the corner.
 */
public final class SpellSlots {
    private static final int BACKGROUND = 0xA0101010;
    private static final int EMPTY_FRAME = 0xFF4A4A4A;
    /** A spell of no school (Will To Survive): a black frame and a dark grey fill (Matěj, 2026-10-30). */
    private static final int NO_SCHOOL_FRAME = 0xFF000000;
    private static final int NO_SCHOOL_FILL_ALPHA = 200;
    private static final int SCHOOL_FILL_ALPHA = 70;
    private static final int CASTING_FRAME = 0xFFFFD24A;
    private static final int UNAFFORDABLE = 0x903048C8;
    private static final int SWEEP = 0xB0000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int KEY_TEXT = 0xFFD0D0D0;
    private static final int COOLDOWN_TEXT = 0xFFFFE08A;
    /** A cooldown with at least this much left shows its seconds over the sweep. */
    private static final int SECONDS_FROM_TICKS = 40;
    private static final float KEY_SCALE = 2 / 3F;
    /** The key label's distance from the slot's top left corner, and the least from its right edge. */
    private static final int KEY_INSET = 2;

    private SpellSlots() {
    }

    /**
     * Draws {@code slot} as a square {@code size} wide with its top left corner at x, y, labelled with its key
     * ({@code key}, empty if unbound).
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, SpellBarView.Slot slot,
            Optional<Component> key) {
        graphics.fill(x, y, x + size, y + size, BACKGROUND);
        Optional<Spell> spell = slot.spell();
        int frame = spell.map(value -> value.school().isPresent() ? ARGB.opaque(value.color()) : NO_SCHOOL_FRAME)
                .orElse(EMPTY_FRAME);
        int textY = y + size / 2 - 2;
        if (spell.isPresent()) {
            int alpha = spell.get().school().isPresent() ? SCHOOL_FILL_ALPHA : NO_SCHOOL_FILL_ALPHA;
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, ARGB.color(alpha, spell.get().color()));
            graphics.centeredText(font, initials(Language.getInstance().getOrDefault(spell.get().name())),
                    x + size / 2 + 1, textY, TEXT);
            if (!slot.affordable()) {
                graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, UNAFFORDABLE);
            }
            for (CooldownSweep.Span span : CooldownSweep.spans(size - 2, slot.cooldown())) {
                graphics.fill(x + 1 + span.x(), y + 1 + span.top(), x + 2 + span.x(), y + 1 + span.bottom(), SWEEP);
            }
            if (slot.cooldownTicks() >= SECONDS_FROM_TICKS) {
                String seconds = Integer.toString((slot.cooldownTicks() + 19) / 20);
                graphics.centeredText(font, seconds, x + size / 2 + 1, textY, COOLDOWN_TEXT);
            }
        }
        graphics.outline(x, y, size, size, slot.casting() ? CASTING_FRAME : frame);
        key.ifPresent(label -> {
            // Two thirds of the font, so the key fits above the initials; smaller still if it would leave the slot.
            float scale = Math.min(KEY_SCALE, (size - 2 * KEY_INSET) / (float) Math.max(1, font.width(label)));
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + KEY_INSET, y + KEY_INSET);
            graphics.pose().scale(scale, scale);
            graphics.text(font, label, 0, 0, KEY_TEXT);
            graphics.pose().popMatrix();
        });
    }

    /** "Test Bolt" reads "TB", "Fireball" "Fi": up to two letters, from the first two words. */
    static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2 && !words[0].isEmpty() && !words[1].isEmpty()) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.ROOT);
        }
        return words[0].length() > 1 ? words[0].substring(0, 2) : words[0];
    }
}
