package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.MagicShields;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The magic shield's bar (architecture 9.1, 18; RLM-160): a translucent blue bar over the bottom row of hearts, as wide
 * as the share of the shield that is left, so the hearts read as shielded. Nothing shows while the pool is empty or
 * the player has no magic shield. It reads the local player's synced {@code realmforge:magic_shield} attachment and
 * stat.
 *
 * <p>Two layers frame vanilla's health layer: the one below notes where the bottom row of hearts goes (the left
 * column's height, which layers below may have raised), the one above draws over the hearts.
 */
public final class MagicShieldHud {
    private static final Identifier ANCHOR = Realmforge.id("magic_shield_anchor");
    private static final Identifier LAYER = Realmforge.id("magic_shield");
    private static final int ROW_HEIGHT = 9;
    private static final int FILL = 0x7333B5FF;
    private static final int EDGE = 0xFF8FE3FF;

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
        int width = MagicShieldBar.width(MagicShields.points(player),
                player.getAttributeValue(GeneratedStats.MAGIC_SHIELD));
        if (width == 0) {
            return;
        }
        int x = graphics.guiWidth() / 2 - 91;
        graphics.fill(x, rowY, x + width, rowY + ROW_HEIGHT, FILL);
        graphics.fill(x, rowY, x + width, rowY + 1, EDGE);
    }
}
