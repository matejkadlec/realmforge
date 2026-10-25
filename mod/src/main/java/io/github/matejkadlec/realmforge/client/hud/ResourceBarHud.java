package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.character.ResourceBarView;
import io.github.matejkadlec.realmforge.spell.ResourceType;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/**
 * The resource bar (RLM-400; Matěj, 2026-10-24): the local player's class resource, right under the spell bar and as
 * wide, drawn by {@link SpellBarHud} in its place ({@link SpellBarLayout#resourceBarTop}). A 1 px black border with the
 * bottom corners' pixels left out, a dark gray empty part, the fill in the resource's colour ({@link ResourceBarStyle}),
 * shaded darker at its top and bottom edges, with a darker line at its leading edge while the pool is neither empty nor
 * full, and "current / maximum" in the middle, white with a black outline like vanilla's experience level. The fill
 * eases to a new value over 150 ms; the numbers change at once. Hidden without a character or a pool
 * ({@link ResourceBarView}).
 */
public final class ResourceBarHud {
    /** The resource the fill last showed, so a class change jumps instead of easing. */
    @Nullable
    private static ResourceKey<ResourceType> shownType;
    /** Where the fill eases from and to, and when it began. */
    private static double from;
    private static double to;
    private static long since;

    private ResourceBarHud() {
    }

    /** Draws the bar of {@code player}'s resource with its top left corner at x, y, unless it has none. */
    static void draw(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y) {
        Optional<ResourceBarView> view = ResourceBarView.of(player);
        if (view.isEmpty()) {
            shownType = null;
            return;
        }
        ResourceKey<ResourceType> type = view.get().type().unwrapKey().orElse(null);
        ResourceBarStyle.Colors colors = type == null ? ResourceBarStyle.FALLBACK
                : ResourceBarStyle.colors(type.identifier());
        double fraction = fill(type, view.get().fraction());

        int width = SpellBarLayout.WIDTH;
        int height = SpellBarLayout.RESOURCE_BAR_HEIGHT;
        int border = ARGB.opaque(ResourceBarStyle.BORDER);
        // The border, its bottom corner pixels left out: rounded at the GUI's pixel size, the top square.
        graphics.fill(x, y, x + width, y + height - 1, border);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, border);

        int innerX = x + 1;
        int innerY = y + 1;
        int innerWidth = width - 2;
        int innerHeight = height - 2;
        graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight, ARGB.opaque(ResourceBarStyle.EMPTY));
        int filled = ResourceBarStyle.fillWidth(fraction, innerWidth);
        for (int row = 0; row < innerHeight && filled > 0; row++) {
            float shade = ResourceBarStyle.rowShade(row, innerHeight);
            graphics.fill(innerX, innerY + row, innerX + filled, innerY + row + 1,
                    CastBarStyle.shade(colors.fill(), shade));
            if (view.get().leadingEdge() && filled < innerWidth) {
                graphics.fill(innerX + filled - 1, innerY + row, innerX + filled, innerY + row + 1,
                        CastBarStyle.shade(colors.edge(), shade));
            }
        }

        String text = view.get().text();
        int textX = x + (width - font.width(text)) / 2;
        int textY = SpellBarLayout.resourceTextTop(y);
        int outline = ARGB.opaque(ResourceBarStyle.BORDER);
        graphics.text(font, text, textX + 1, textY, outline, false);
        graphics.text(font, text, textX - 1, textY, outline, false);
        graphics.text(font, text, textX, textY + 1, outline, false);
        graphics.text(font, text, textX, textY - 1, outline, false);
        graphics.text(font, text, textX, textY, ARGB.opaque(ResourceBarStyle.TEXT), false);
    }

    /** The fill to show now: easing toward {@code target}, from wherever it showed when the target last changed. */
    private static double fill(@Nullable ResourceKey<ResourceType> type, double target) {
        long now = Util.getMillis();
        if (type == null || !type.equals(shownType)) {
            shownType = type;
            from = target;
            to = target;
            since = now;
        } else if (target != to) {
            from = ResourceBarStyle.animate(from, to, now - since);
            to = target;
            since = now;
        }
        return ResourceBarStyle.animate(from, to, now - since);
    }
}
