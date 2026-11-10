package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Draws a spell bar slot (C3b, RLM-25), on the HUD ({@link SpellBarHud}) and in the spellbook: a dark square framed in
 * the spell's school colour (a spell of no school: black, filled dark grey), the spell's icon in its middle
 * ({@link SpellIcons}; A1, RLM-642: the frame and the key stay, the initials go, Matěj 2026-10-21) or, for a spell
 * without an icon, its initials; a passive spell's slot has its corners cut, a medallion instead of a square, which
 * sets it apart from the spells a player casts (RLM-588, RLM-591); a blue tint while the player lacks its resource,
 * the cooldown sweep ({@link CooldownSweep}) with the seconds left of a long cooldown, a gold frame while it is being
 * cast, and the slot's key in the corner. A spell usable only in its window (RLM-540: Overpower, Victory Rush) is greyed out while the
 * window is closed, and glows with the seconds left in its lower corner while it is open; a spell with a target health
 * condition (Execute, RLM-543) the same while the player's current target meets it, without seconds. An aura spell that
 * is on (RLM-595) has a bright green frame inside its own, grey-green while its weapon condition pauses it.
 */
public final class SpellSlots {
    /** Dark enough for an icon to stand out on it, over the world and on the spellbook's light page alike. */
    private static final int BACKGROUND = 0xD8101010;
    private static final int EMPTY_FRAME = 0xFF4A4A4A;
    /** A spell of no school (Will To Survive): a black frame and a dark grey fill (Matěj, 2026-10-30). */
    private static final int NO_SCHOOL_FRAME = 0xFF000000;
    private static final int NO_SCHOOL_FILL_ALPHA = 200;
    private static final int SCHOOL_FILL_ALPHA = 70;
    private static final int CASTING_FRAME = 0xFFFFD24A;
    private static final int UNAFFORDABLE = 0x903048C8;
    /** A closed window (RLM-540): grey over the slot. */
    private static final int WINDOW_CLOSED = 0xA0303030;
    /** An aura that is on (RLM-595): a bright green frame two pixels wide inside the school's. */
    private static final int AURA_ON = 0xFF7CFF5C;
    /** An aura that is on but paused by its weapon condition: the same frame, dimmed to grey-green. */
    private static final int AURA_PAUSED = 0xFF6A7F64;
    /** An open window: a bright frame inside the school's, and its seconds left. */
    private static final int WINDOW_GLOW = 0xFFFFF27A;
    private static final int WINDOW_TEXT = 0xFFFFF27A;
    private static final int SWEEP = 0xB0000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int KEY_TEXT = 0xFFD0D0D0;
    private static final int COOLDOWN_TEXT = 0xFFFFE08A;
    /** A cooldown with at least this much left shows its seconds over the sweep. */
    private static final int SECONDS_FROM_TICKS = 40;
    private static final float KEY_SCALE = 2 / 3F;
    /** The key label's distance from the slot's top left corner, and the least from its right edge. */
    private static final int KEY_INSET = 2;
    /** A passive spell's slot loses a sixth of its width at each corner: 4 pixels of the bar's 24. */
    private static final int CUT_DIVISOR = 6;

    private SpellSlots() {
    }

    /**
     * Draws {@code slot} as a square {@code size} wide with its top left corner at x, y, labelled with its key
     * ({@code key}, empty if unbound).
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, SpellBarView.Slot slot,
            Optional<Component> key) {
        draw(graphics, font, x, y, size, slot, key, 0, 0);
    }

    /**
     * {@link #draw(GuiGraphicsExtractor, Font, int, int, int, SpellBarView.Slot, Optional)} with the spell's initials
     * moved by {@code initialsX} and {@code initialsY}, for a caller that draws something else in the slot.
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, SpellBarView.Slot slot,
            Optional<Component> key, int initialsX, int initialsY) {
        Optional<Spell> spell = slot.spell();
        int cut = cut(spell, size);
        area(graphics, x, y, size, 0, cut, BACKGROUND);
        int frame = spell.map(value -> value.school().isPresent() ? ARGB.opaque(value.color()) : NO_SCHOOL_FRAME)
                .orElse(EMPTY_FRAME);
        int textY = y + size / 2 - 2;
        if (spell.isPresent()) {
            int alpha = spell.get().school().isPresent() ? SCHOOL_FILL_ALPHA : NO_SCHOOL_FILL_ALPHA;
            area(graphics, x, y, size, 1, cut, ARGB.color(alpha, spell.get().color()));
            Optional<Identifier> icon = SpellIcons.sprite(slot.key());
            if (icon.isPresent()) {
                int inset = (size - SpellIcons.SIZE) / 2;
                SpellIcons.draw(graphics, icon.get(), x + inset, y + inset, 255);
            } else {
                graphics.centeredText(font, initials(Language.getInstance().getOrDefault(spell.get().name())),
                        x + size / 2 + 1 + initialsX, textY + initialsY, TEXT);
            }
            if (!slot.affordable()) {
                graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, UNAFFORDABLE);
            }
            if (slot.windowTicks() == 0) {
                graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, WINDOW_CLOSED);
            }
            for (CooldownSweep.Span span : CooldownSweep.spans(size - 2, slot.cooldown())) {
                graphics.fill(x + 1 + span.x(), y + 1 + span.top(), x + 2 + span.x(), y + 1 + span.bottom(), SWEEP);
            }
            if (slot.cooldownTicks() >= SECONDS_FROM_TICKS) {
                String seconds = Integer.toString((slot.cooldownTicks() + 19) / 20);
                graphics.centeredText(font, seconds, x + size / 2 + 1, textY, COOLDOWN_TEXT);
            }
        }
        frame(graphics, x, y, size, cut, slot.casting() ? CASTING_FRAME : frame);
        if (slot.aura() == SpellBarView.AuraState.ON || slot.aura() == SpellBarView.AuraState.PAUSED) {
            int aura = slot.aura() == SpellBarView.AuraState.ON ? AURA_ON : AURA_PAUSED;
            graphics.outline(x + 1, y + 1, size - 2, size - 2, aura);
            graphics.outline(x + 2, y + 2, size - 4, size - 4, aura);
        }
        if (slot.windowTicks() > 0) {
            graphics.outline(x + 1, y + 1, size - 2, size - 2, WINDOW_GLOW);
            // A target health condition (Execute) glows while the target meets it: no seconds to count down.
            if (spell.flatMap(value -> value.conditions().window()).isPresent()) {
                String seconds = Integer.toString((slot.windowTicks() + 19) / 20);
                graphics.text(font, seconds, x + size - 2 - font.width(seconds), y + size - 2 - font.lineHeight + 1,
                        WINDOW_TEXT);
            }
        }
        key.ifPresent(label -> {
            // Two thirds of the font, so the key sits in the corner; smaller still if it would leave the slot.
            float scale = Math.min(KEY_SCALE, (size - 2 * KEY_INSET) / (float) Math.max(1, font.width(label)));
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + KEY_INSET, y + KEY_INSET);
            graphics.pose().scale(scale, scale);
            graphics.text(font, label, 0, 0, KEY_TEXT);
            graphics.pose().popMatrix();
        });
    }

    /**
     * Covers the slot of {@code spell} with {@code color}, in the slot's own shape: a caller's shade or hover light
     * over a passive spell's slot leaves its cut corners alone.
     */
    public static void overlay(GuiGraphicsExtractor graphics, int x, int y, int size, Optional<Spell> spell,
            int color) {
        area(graphics, x, y, size, 0, cut(spell, size), color);
    }

    /**
     * How many pixels the slot of {@code spell} loses at each corner: a passive spell's is a medallion (RLM-588,
     * RLM-591), any other a square, which loses none.
     */
    static int cut(Optional<Spell> spell, int size) {
        return spell.map(Spell::isPassive).orElse(false) ? size / CUT_DIVISOR : 0;
    }

    /**
     * How far in from the slot's side its row {@code row} begins and ends, for a slot that loses {@code cut} pixels
     * at each corner: the corner's diagonal near the top and the bottom, 0 in between.
     */
    static int indent(int row, int size, int cut) {
        return Math.max(0, cut - Math.min(row, size - 1 - row));
    }

    /**
     * Fills the slot {@code inset} pixels in from its edge; with a {@code cut} the rows near the top and the bottom
     * are shorter, each by the pixels the corner loses there. No pixel is filled twice, so a translucent colour stays
     * even.
     */
    private static void area(GuiGraphicsExtractor graphics, int x, int y, int size, int inset, int cut, int color) {
        if (cut == 0) {
            graphics.fill(x + inset, y + inset, x + size - inset, y + size - inset, color);
            return;
        }
        for (int row = inset; row < size - inset; row++) {
            int indent = Math.max(inset, indent(row, size, cut));
            graphics.fill(x + indent, y + row, x + size - indent, y + row + 1, color);
        }
    }

    /** The slot's frame, one pixel wide: a square's outline, or with a {@code cut} the outline round the corners. */
    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int size, int cut, int color) {
        if (cut == 0) {
            graphics.outline(x, y, size, size, color);
            return;
        }
        graphics.fill(x + cut, y, x + size - cut, y + 1, color);
        graphics.fill(x + cut, y + size - 1, x + size - cut, y + size, color);
        for (int row = 1; row < size - 1; row++) {
            int indent = indent(row, size, cut);
            graphics.fill(x + indent, y + row, x + indent + 1, y + row + 1, color);
            graphics.fill(x + size - 1 - indent, y + row, x + size - indent, y + row + 1, color);
        }
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
