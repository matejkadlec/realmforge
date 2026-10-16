package io.github.matejkadlec.realmforge.client.tooltip;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

/**
 * Tooltip lines aligned right, under a line above them that is {@code minWidth} wide: the character sheet's {@code +}
 * says "Spend a point on Strength" with what the point gives beneath, ending under "Strength" (RLM-196; Matěj,
 * 2026-10-16). Vanilla's text lines are aligned left, so these come as one tooltip component, whose width is the widest
 * of the lines and the line above.
 *
 * @param lines the lines, each in its own colour
 * @param minWidth the width of the line above, which the lines end under
 */
public record RightAlignedLines(List<FormattedCharSequence> lines, int minWidth) implements TooltipComponent {
    /** Vanilla's height of a tooltip text line ({@code ClientTextTooltip}). */
    private static final int LINE = 10;

    public RightAlignedLines {
        lines = List.copyOf(lines);
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class,
                event -> event.register(RightAlignedLines.class, Client::new));
    }

    /** Draws the lines, each ending at the component's right edge. */
    private record Client(RightAlignedLines tooltip) implements ClientTooltipComponent {
        @Override
        public int getHeight(Font font) {
            return LINE * tooltip.lines().size();
        }

        @Override
        public int getWidth(Font font) {
            int width = tooltip.minWidth();
            for (FormattedCharSequence line : tooltip.lines()) {
                width = Math.max(width, font.width(line));
            }
            return width;
        }

        @Override
        public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
            int right = x + getWidth(font);
            for (int i = 0; i < tooltip.lines().size(); i++) {
                FormattedCharSequence line = tooltip.lines().get(i);
                graphics.text(font, line, right - font.width(line), y + LINE * i, -1, true);
            }
        }
    }
}
