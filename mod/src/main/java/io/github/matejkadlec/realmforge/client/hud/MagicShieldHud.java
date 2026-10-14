package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.MagicShields;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The magic shield's bar (architecture 9.1, 18; RLM-160, Matěj, 2026-10-14): a translucent bar over the bottom row
 * of hearts, so the hearts read as shielded, in a 1-pixel border with rounded corners. Every 10 points of shield fill
 * it once, in the next tier's colour over the tier below ({@link MagicShieldBar}), and the border takes the top
 * tier's colour. Nothing shows while the pool is empty. It reads the local player's synced
 * {@code realmforge:magic_shield} attachment.
 *
 * <p>Two layers frame vanilla's health layer: the one below notes where the bottom row of hearts goes (the left
 * column's height, which layers below may have raised), the one above draws over the hearts.
 */
public final class MagicShieldHud {
    private static final Identifier ANCHOR = Realmforge.id("magic_shield_anchor");
    private static final Identifier LAYER = Realmforge.id("magic_shield");

    /** The top of the bottom row of hearts in this frame, from the anchor layer. */
    private static int rowY;

    private MagicShieldHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerBelow(VanillaGuiLayers.PLAYER_HEALTH, ANCHOR, MagicShieldHud::anchor);
            event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, LAYER, MagicShieldHud::render);
        });
    }

    private static void anchor(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        rowY = graphics.guiHeight() - Minecraft.getInstance().gui.leftHeight;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.gameMode == null
                || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        float points = MagicShields.points(player);
        int tier = MagicShieldBar.tier(points);
        if (tier < 0) {
            return;
        }
        int x = graphics.guiWidth() / 2 - 91;
        int top = MagicShieldBar.topWidth(points);
        fillInside(graphics, x, rowY, 0, top, MagicShieldBar.fill(tier));
        if (tier > 0) {
            fillInside(graphics, x, rowY, top, MagicShieldBar.INNER_WIDTH, MagicShieldBar.fill(tier - 1));
        }
        drawBorder(graphics, x, rowY, MagicShieldBar.border(tier));
    }

    /**
     * Fills the inner columns {@code from} (inclusive) to {@code to} (exclusive), counted from the left inside the
     * border. The first and last inner columns are shorter by a pixel at each end: those pixels belong to the
     * rounded corners.
     */
    private static void fillInside(GuiGraphicsExtractor graphics, int x, int y, int from, int to, int color) {
        int width = MagicShieldBar.INNER_WIDTH;
        int height = MagicShieldBar.ROW_HEIGHT;
        if (from >= to) {
            return;
        }
        if (from == 0) {
            graphics.fill(x + 1, y + 2, x + 2, y + height - 2, color);
            from = 1;
        }
        if (to == width) {
            graphics.fill(x + width, y + 2, x + width + 1, y + height - 2, color);
            to = width - 1;
        }
        if (from < to) {
            graphics.fill(x + 1 + from, y + 1, x + 1 + to, y + height - 1, color);
        }
    }

    /**
     * A 1-pixel border around the row with rounded corners: the straight edges stop 2 pixels short of each corner,
     * and one pixel in from each corner closes the round, so the 3 corner pixels outside the border form an L.
     */
    private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int color) {
        int right = x + MagicShieldBar.ROW_WIDTH;
        int bottom = y + MagicShieldBar.ROW_HEIGHT;
        graphics.fill(x + 2, y, right - 2, y + 1, color);
        graphics.fill(x + 2, bottom - 1, right - 2, bottom, color);
        graphics.fill(x, y + 2, x + 1, bottom - 2, color);
        graphics.fill(right - 1, y + 2, right, bottom - 2, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(right - 2, y + 1, right - 1, y + 2, color);
        graphics.fill(x + 1, bottom - 2, x + 2, bottom - 1, color);
        graphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, color);
    }
}
