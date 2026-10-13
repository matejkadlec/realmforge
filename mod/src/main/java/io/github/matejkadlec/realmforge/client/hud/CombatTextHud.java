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
import net.minecraft.util.RandomSource;
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
 * Floating combat text (architecture 9.1, 18; Matěj, 2026-10-13): a HUD layer that draws each
 * {@link CombatTextPayload} over the entity it is about, projected from the world onto the screen, popping in, rising
 * and fading. Every text is bold and the same size; the server picks the colour. A crit's number ends with "!", a
 * heal's starts with "+", and a dodged hit reads "Miss!"; the player's own dodge shows beside the crosshair.
 */
public final class CombatTextHud {
    private static final Identifier LAYER = Realmforge.id("combat_text");
    private static final long LIFETIME_MS = 1100;
    private static final int MAX_TEXTS = 64;
    /** How far a text over an entity rises over its lifetime, in blocks; a miss by the crosshair, in GUI pixels. */
    private static final double RISE_BLOCKS = 1.0;
    private static final int RISE_PIXELS = 20;
    /** Every text is this many times the font's size. */
    private static final float SCALE = 1.75F;
    /** A text pops in: this share bigger at first, back to its size after {@link #POP_MS}. */
    private static final float POP = 0.6F;
    private static final long POP_MS = 150;
    /** The last part of a text's lifetime fades it out. */
    private static final float FADE_FROM = 0.6F;
    /** Texts spread sideways and upward at random, in GUI pixels, so hits close together do not cover each other. */
    private static final float SPREAD_X = 28;
    private static final float SPREAD_Y = 10;

    private static final List<Text> TEXTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    /** One text on screen: where it started, what it reads, when it arrived, and its random offset. */
    private record Text(Vec3 position, Component label, int color, boolean incoming, long startMs, float offsetX,
            float offsetY) {
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
        Component label = (payload.miss()
                ? Component.translatable(GeneratedTexts.COMBAT_TEXT_MISS)
                : Component.literal(DamageNumbers.label(payload.amount(), payload.crit(), payload.heal())))
                .withStyle(ChatFormatting.BOLD);
        TEXTS.add(new Text(position, label, payload.color(), payload.incoming(), Util.getMillis(),
                (RANDOM.nextFloat() - 0.5F) * SPREAD_X, RANDOM.nextFloat() * SPREAD_Y));
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
            long ageMs = now - text.startMs();
            float age = ageMs / (float) LIFETIME_MS;
            float x;
            float y;
            if (text.incoming()) {
                x = graphics.guiWidth() / 2.0F - 44 + text.offsetX() / 2;
                y = graphics.guiHeight() / 2.0F + 12 - age * RISE_PIXELS;
            } else {
                // Normalized device coordinates; z above 1 means behind the camera (as vanilla's TrackedWaypoint).
                Vec3 screen = minecraft.gameRenderer.projectPointToScreen(text.position().add(0, age * RISE_BLOCKS, 0));
                if (screen.z > 1 || Math.abs(screen.x) > 1 || Math.abs(screen.y) > 1) {
                    continue;
                }
                x = (float) ((screen.x + 1) / 2 * graphics.guiWidth()) + text.offsetX();
                y = (float) ((1 - screen.y) / 2 * graphics.guiHeight()) - text.offsetY();
            }
            float fade = age < FADE_FROM ? 1 : 1 - (age - FADE_FROM) / (1 - FADE_FROM);
            float scale = SCALE * (1 + POP * Math.max(0, 1 - ageMs / (float) POP_MS));
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale);
            graphics.centeredText(minecraft.font, text.label(), 0, -minecraft.font.lineHeight / 2,
                    ARGB.color(Math.max(4, Math.round(255 * fade)), text.color()));
            pose.popMatrix();
        }
    }
}
