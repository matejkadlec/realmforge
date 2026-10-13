package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.net.HitDirectionPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix3x2fStack;

/**
 * Where an attack came from (architecture 9.1, 18; Matěj, 2026-10-13, after MMM Indicators): for a moment after each
 * attack on the player, a hit that landed or one dodged ({@link HitDirectionPayload}), a red arc around the crosshair
 * on the attacker's side, at the top for an attacker ahead and at the bottom for one behind. It turns with the camera
 * and follows the attacker while the client has it. One arc per attacker: a new attack restarts it.
 */
public final class HitDirectionHud {
    private static final Identifier LAYER = Realmforge.id("hit_direction");
    private static final long LIFETIME_MS = 1500;
    /** The arc's distance from the crosshair, its half width as an angle, and its thickness in the middle (GUI px). */
    private static final float RADIUS = 34;
    private static final float HALF_ARC = (float) Math.toRadians(24);
    private static final float THICKNESS = 4;
    private static final int SEGMENTS = 13;
    private static final int COLOR = 0xE3262A;
    private static final int EDGE = 0x4A0808;
    /** Attacks without an entity (an explosion) get their own arcs, under these keys counting down from -1. */
    private static final int ANONYMOUS_KEYS = 16;

    private static final Map<Integer, Indicator> INDICATORS = new LinkedHashMap<>();
    private static int anonymous;

    private record Indicator(int attackerId, double x, double z, long startMs) {
    }

    private HitDirectionHud() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, HitDirectionHud::render));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> INDICATORS.clear());
    }

    /** Shows the direction of an attack; called by the payload's handler on the main thread. */
    public static void add(HitDirectionPayload payload) {
        int key = payload.attackerId() >= 0 ? payload.attackerId() : -1 - Math.floorMod(anonymous++, ANONYMOUS_KEYS);
        INDICATORS.put(key, new Indicator(payload.attackerId(), payload.x(), payload.z(), Util.getMillis()));
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        INDICATORS.values().removeIf(indicator -> now - indicator.startMs() >= LIFETIME_MS);
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null || INDICATORS.isEmpty()) {
            return;
        }
        float yaw = player.getViewYRot(deltaTracker.getGameTimeDeltaPartialTick(true));
        for (Indicator indicator : INDICATORS.values()) {
            Entity attacker = indicator.attackerId() >= 0 ? minecraft.level.getEntity(indicator.attackerId()) : null;
            double x = attacker != null ? attacker.getX() : indicator.x();
            double z = attacker != null ? attacker.getZ() : indicator.z();
            float angle = HitDirections.screenAngle(player.getX(), player.getZ(), yaw, x, z);
            float age = (now - indicator.startMs()) / (float) LIFETIME_MS;
            int alpha = Math.max(4, Math.round(255 * (1 - age * age)));
            drawArc(graphics, angle, 1.5F, ARGB.color(alpha, EDGE));
            drawArc(graphics, angle, 0, ARGB.color(alpha, COLOR));
        }
    }

    /** The arc as short rotated bars along a circle, thick in the middle and thin at the ends; {@code grow} pads it. */
    private static void drawArc(GuiGraphicsExtractor graphics, float angle, float grow, int color) {
        float centerX = graphics.guiWidth() / 2.0F;
        float centerY = graphics.guiHeight() / 2.0F;
        float length = RADIUS * 2 * HALF_ARC / (SEGMENTS - 1) + 1 + grow;
        Matrix3x2fStack pose = graphics.pose();
        for (int i = 0; i < SEGMENTS; i++) {
            float along = i / (float) (SEGMENTS - 1);
            float theta = angle - HALF_ARC + 2 * HALF_ARC * along;
            float thickness = THICKNESS * (1 - 0.7F * Math.abs(2 * along - 1)) + grow;
            pose.pushMatrix();
            pose.translate(centerX + RADIUS * Mth.sin(theta), centerY - RADIUS * Mth.cos(theta));
            pose.rotate(theta);
            pose.scale(length / 2, thickness / 2);
            graphics.fill(-1, -1, 1, 1, color);
            pose.popMatrix();
        }
    }
}
