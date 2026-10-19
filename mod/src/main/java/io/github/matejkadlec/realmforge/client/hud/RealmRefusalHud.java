package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.world.net.RealmRefusedPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3x2fStack;

/**
 * The refusal of a trip into a realm the player has not unlocked ({@link RealmRefusedPayload}; architecture 15.1, 18):
 * "You are not eligible to pass through the portal." in red in the middle of the screen, the realm's hint below it,
 * drawn like vanilla's title and subtitle (fade in, stay, fade out). The title is 3x, not vanilla's 4x, at which the
 * sentence runs into both edges of a 1080p screen (Matěj, 2026-10-19), and never wider than {@value #MAX_WIDTH_SHARE}
 * of the screen, so it keeps room to both edges at any window size.
 */
public final class RealmRefusalHud {
    private static final Identifier LAYER = Realmforge.id("realm_refusal");
    private static final float TITLE_SCALE = 3.0F;
    private static final float HINT_SCALE = 2.0F;
    /** The most of the screen's width the title may take. */
    private static final float MAX_WIDTH_SHARE = 0.8F;
    /** Vanilla's title timing: 5 ticks in, 40 shown, 10 out. */
    private static final long FADE_IN_MS = 250;
    private static final long STAY_MS = 2000;
    private static final long FADE_OUT_MS = 500;

    private static Component hint = Component.empty();
    private static long startMs = Long.MIN_VALUE;

    private RealmRefusalHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.TITLE, LAYER, RealmRefusalHud::render));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> startMs = Long.MIN_VALUE);
    }

    /** Shows the refusal; called by the payload's handler on the main thread. */
    public static void show(RealmRefusedPayload payload) {
        hint = payload.hintKey().isEmpty() ? Component.empty() : Component.translatable(payload.hintKey());
        startMs = Util.getMillis();
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        long age = Util.getMillis() - startMs;
        long total = FADE_IN_MS + STAY_MS + FADE_OUT_MS;
        if (minecraft.options.hideGui || startMs == Long.MIN_VALUE || age < 0 || age >= total) {
            return;
        }
        int alpha = age < FADE_IN_MS ? (int) (255 * age / FADE_IN_MS)
                : age < FADE_IN_MS + STAY_MS ? 255
                : (int) (255 * (total - age) / FADE_OUT_MS);
        if (alpha <= 8) {
            return;
        }
        Font font = minecraft.font;
        Component title =
                Component.translatable(GeneratedTexts.MESSAGE_REALM_NOT_ELIGIBLE).withStyle(ChatFormatting.RED);
        int titleWidth = font.width(title);
        float titleScale = Math.min(TITLE_SCALE, graphics.guiWidth() * MAX_WIDTH_SHARE / Math.max(1, titleWidth));
        int color = ARGB.white(alpha);
        graphics.nextStratum();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(graphics.guiWidth() / 2.0F, graphics.guiHeight() / 2.0F);
        pose.pushMatrix();
        pose.scale(titleScale, titleScale);
        graphics.textWithBackdrop(font, title, -titleWidth / 2, -10, titleWidth, color);
        pose.popMatrix();
        if (!hint.getString().isEmpty()) {
            int hintWidth = font.width(hint);
            float hintScale = Math.min(HINT_SCALE, titleScale);
            pose.pushMatrix();
            pose.scale(hintScale, hintScale);
            graphics.textWithBackdrop(font, hint, -hintWidth / 2, 5, hintWidth, color);
            pose.popMatrix();
        }
        pose.popMatrix();
    }
}
