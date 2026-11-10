package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.client.hud.SpellIcons;
import io.github.matejkadlec.realmforge.client.hud.SpellSlots;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;

/**
 * A spell's tooltip in the spellbook (A1, RLM-642): the lines {@code SpellTooltip} writes, with the spell's icon in
 * its frame beside the name when it has one ({@link SpellIcons}). The icon is a tooltip component of our own, built
 * here and drawn with the screen's tooltip, so it needs no registered factory.
 */
final class SpellTooltips {
    /** The width a tooltip's lines wrap at. */
    static final int WIDTH = 200;
    /** The icon's slot in the tooltip, as small as a slot of the spell list. */
    private static final int SLOT = 18;
    private static final int NAME_GAP = 5;
    private static final int NAME_COLOR = 0xFFFFFFFF;

    private SpellTooltips() {
    }

    /**
     * Shows {@code lines} as the tooltip of {@code spell} at the mouse, wrapped at {@link #WIDTH}; the first line is
     * the spell's name, which stands beside its icon.
     */
    static void show(GuiGraphicsExtractor graphics, Font font, ResourceKey<Spell> key, Spell spell,
            List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        List<ClientTooltipComponent> components = new ArrayList<>();
        int text = 0;
        if (SpellIcons.sprite(key).isPresent()) {
            components.add(new Header(key, spell, lines.getFirst().getVisualOrderText()));
            text = 1;
        }
        for (Component line : lines.subList(text, lines.size())) {
            for (FormattedCharSequence part : font.split(line, WIDTH)) {
                components.add(ClientTooltipComponent.create(part));
            }
        }
        // As a screen's own tooltip is drawn: on a stratum above everything the screen has drawn so far.
        graphics.nextStratum();
        graphics.tooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
    }

    /** The tooltip's first row: the spell's icon in its slot, and its name beside it. */
    private record Header(ResourceKey<Spell> key, Spell spell, FormattedCharSequence name)
            implements ClientTooltipComponent {
        @Override
        public int getHeight(Font font) {
            return SLOT;
        }

        @Override
        public int getWidth(Font font) {
            return SLOT + NAME_GAP + font.width(name);
        }

        @Override
        public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
            graphics.text(font, name, x + SLOT + NAME_GAP, y + (SLOT - font.lineHeight) / 2 + 1, NAME_COLOR);
        }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
            SpellSlots.draw(graphics, font, x, y, SLOT, new SpellBarView.Slot(Optional.of(key), Optional.of(spell), 1,
                    0, 0, true, false), Optional.empty());
        }
    }
}
