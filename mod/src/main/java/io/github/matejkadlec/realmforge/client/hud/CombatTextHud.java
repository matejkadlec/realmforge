package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.combat.net.CombatTextPayload;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3x2fStack;

/**
 * Floating combat text (architecture 9.1, 18): a HUD layer that draws each {@link CombatTextPayload} for a moment,
 * rising and fading. Damage the player dealt floats over the entity it hit, projected from the world onto the screen;
 * damage the player took rises left of the crosshair in red. Crits are bigger, a dodge reads "Dodge".
 */
public final class CombatTextHud {
    private static final Identifier LAYER = Realmforge.id("combat_text");
    private static final long LIFETIME_MS = 1200;
    private static final int MAX_TEXTS = 64;
    /** How far a text rises over its lifetime: blocks over an entity, GUI pixels for the player's own damage. */
    private static final double RISE_BLOCKS = 1.0;
    private static final int RISE_PIXELS = 30;
    private static final int INCOMING_COLOR = 0xFFFF5555;
    private static final int DODGE_COLOR = 0xFFBBBBBB;
    private static final float CRIT_SCALE = 1.5F;

    private static final List<Text> TEXTS = new ArrayList<>();

    /** One text on screen: where it started, what it reads, when it arrived; a small sideways offset spreads hits. */
    private record Text(Vec3 position, Component label, int color, boolean crit, boolean incoming, long startMs,
            int offset) {
    }

    private CombatTextHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, CombatTextHud::render));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> TEXTS.clear());
    }

    /** Shows a combat text; called by the payload's handler on the main thread. */
    public static void add(CombatTextPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(payload.entityId());
        if (entity == null && !payload.incoming()) {
            return;
        }
        Vec3 position = entity != null ? entity.position().add(0, entity.getBbHeight() + 0.3, 0) : Vec3.ZERO;
        Component label = payload.dodge()
                ? Component.translatable(GeneratedTexts.COMBAT_TEXT_DODGE)
                : Component.literal(DamageNumbers.format(payload.amount()));
        if (payload.crit()) {
            label = label.copy().withStyle(ChatFormatting.BOLD);
        }
        int color = payload.dodge() ? DODGE_COLOR : payload.incoming() ? INCOMING_COLOR : payload.color();
        int offset = (int) ((Util.getMillis() / 37) % 21) - 10;
        TEXTS.add(new Text(position, label, color, payload.crit(), payload.incoming(), Util.getMillis(), offset));
        if (TEXTS.size() > MAX_TEXTS) {
            TEXTS.removeFirst();
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        TEXTS.removeIf(text -> now - text.startMs() >= LIFETIME_MS);
        if (minecraft.options.hideGui || TEXTS.isEmpty()) {
            return;
        }
        for (Text text : TEXTS) {
            float age = (now - text.startMs()) / (float) LIFETIME_MS;
            float x;
            float y;
            if (text.incoming()) {
                x = graphics.guiWidth() / 2.0F - 40 + text.offset();
                y = graphics.guiHeight() / 2.0F + 10 - age * RISE_PIXELS;
            } else {
                // Normalized device coordinates; z above 1 means behind the camera (as vanilla's TrackedWaypoint).
                Vec3 screen = minecraft.gameRenderer.projectPointToScreen(text.position().add(0, age * RISE_BLOCKS, 0));
                if (screen.z > 1 || Math.abs(screen.x) > 1 || Math.abs(screen.y) > 1) {
                    continue;
                }
                x = (float) ((screen.x + 1) / 2 * graphics.guiWidth()) + text.offset();
                y = (float) ((1 - screen.y) / 2 * graphics.guiHeight());
            }
            int alpha = Math.round(255 * (1 - age * age));
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            if (text.crit()) {
                pose.scale(CRIT_SCALE);
            }
            graphics.centeredText(minecraft.font, text.label(), 0, -minecraft.font.lineHeight / 2,
                    ARGB.color(Math.max(alpha, 4), text.color()));
            pose.popMatrix();
        }
    }
}
