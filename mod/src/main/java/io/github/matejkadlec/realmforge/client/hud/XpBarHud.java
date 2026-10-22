package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterData;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.XpBarView;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The character XP bar (C2, architecture 10.2, 18): as wide as the hotbar, above the health and food rows and every
 * other status row, which NeoForge stacks through {@code Gui#leftHeight} and {@code Gui#rightHeight}. It moves up with
 * more heart rows, and raises those heights so later layers (the selected item's name) stay above it. Purple while it
 * fills toward the next level, full and gold while the level cap holds the player, who then gains no XP; the level
 * sits on its middle like vanilla's experience level. Vanilla's experience bar keeps its place: vanilla XP still pays
 * for enchanting (decision D3). Shown only for a player with a character. Its place: {@link XpBarLayout}.
 */
public final class XpBarHud {
    static final Identifier LAYER = Realmforge.id("xp_bar");
    private static final int WIDTH = XpBarLayout.WIDTH;
    private static final int HEIGHT = XpBarLayout.HEIGHT;
    private static final int BORDER = 0xFF000000;
    private static final int BACKGROUND = 0xFF2E1F45;
    private static final int FILL = 0xFF9D5CE6;
    private static final int FILL_HIGHLIGHT = 0xFFC9A2F5;
    private static final int CAPPED_FILL = 0xFFE0A526;
    private static final int CAPPED_HIGHLIGHT = 0xFFF7DC8B;
    private static final int TEXT = 0xFFE4CCFF;
    private static final int CAPPED_TEXT = 0xFFFFE08A;

    private XpBarHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.AIR_LEVEL, LAYER, XpBarHud::render));
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        CharacterData data = Characters.data(player);
        Optional<Curve> curve = player.registryAccess().lookupOrThrow(CoreRegistries.CURVE)
                .get(GeneratedCurves.XP_TO_NEXT_LEVEL).map(Holder::value);
        if (!data.exists() || curve.isEmpty()) {
            return;
        }
        int level = Characters.level(player);
        XpBarView view = XpBarView.of(curve.get(), data.totalXp(), level, data.levelCap().orElse(Integer.MAX_VALUE));
        Gui gui = minecraft.gui;
        int stack = XpBarLayout.stack(gui.leftHeight, gui.rightHeight);
        int top = XpBarLayout.barTop(graphics.guiHeight(), stack);
        drawBar(graphics, (graphics.guiWidth() - WIDTH) / 2, top, view);
        drawLevel(graphics, minecraft.font, XpBarLayout.textTop(top), view);
        int raised = XpBarLayout.raisedStack(stack, minecraft.gameMode.canHurtPlayer());
        gui.leftHeight = raised;
        gui.rightHeight = raised;
    }

    private static void drawBar(GuiGraphicsExtractor graphics, int left, int top, XpBarView view) {
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, BORDER);
        graphics.fill(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, BACKGROUND);
        int filled = Math.round(view.progress() * (WIDTH - 2));
        if (filled > 0) {
            graphics.fill(left + 1, top + 1, left + 1 + filled, top + HEIGHT - 1, view.capped() ? CAPPED_FILL : FILL);
            graphics.fill(left + 1, top + 1, left + 1 + filled, top + 2,
                    view.capped() ? CAPPED_HIGHLIGHT : FILL_HIGHLIGHT);
        }
    }

    /** The level on the bar's middle, outlined like vanilla's experience level. */
    private static void drawLevel(GuiGraphicsExtractor graphics, Font font, int y, XpBarView view) {
        Component text = Component.translatable(GeneratedTexts.GUI_XP_BAR_LEVEL, view.level());
        int x = (graphics.guiWidth() - font.width(text)) / 2;
        graphics.text(font, text, x + 1, y, BORDER, false);
        graphics.text(font, text, x - 1, y, BORDER, false);
        graphics.text(font, text, x, y + 1, BORDER, false);
        graphics.text(font, text, x, y - 1, BORDER, false);
        graphics.text(font, text, x, y, view.capped() ? CAPPED_TEXT : TEXT, false);
    }
}
