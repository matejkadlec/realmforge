package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.combat.net.CombatTextPayload;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
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
import org.joml.Vector3fc;

/**
 * Floating combat text (architecture 9.1, 18; Matěj, 2026-10-13): a HUD layer that draws each
 * {@link CombatTextPayload} over the entity it is about, popping in, rising and fading. A text follows its entity
 * while the client has it, from where the entity was last seen once it is gone (dead, unloaded), and it is projected
 * from the world onto the screen every frame, so it shrinks as the camera moves away and grows as it comes closer,
 * within a minimum that keeps a far hit readable (Matěj's playtest, 2026-10-14: RLM-185). Texts close together spread
 * in the world, sideways and upward around the entity, so they do not cover each other. Every text is bold; the
 * server picks the colour. A crit's number ends with "!", a heal's starts with "+", and a dodged hit reads "Miss!";
 * the player's own dodge shows beside the crosshair, at a fixed size.
 */
public final class CombatTextHud {
    private static final Identifier LAYER = Realmforge.id("combat_text");
    private static final long LIFETIME_MS = 1100;
    private static final int MAX_TEXTS = 64;
    /** How far a text over an entity rises over its lifetime, in blocks; a miss by the crosshair, in GUI pixels. */
    private static final double RISE_BLOCKS = 1.0;
    private static final int RISE_PIXELS = 20;
    /** How far above the entity's head a text starts, in blocks. */
    private static final double ABOVE_HEAD = 0.3;
    /** The player's own miss by the crosshair is drawn at a fixed size; texts over entities by their distance. */
    private static final float INCOMING_SCALE = CombatTextScale.BASE;
    /** A text pops in: this share bigger at first, back to its size after {@link #POP_MS}. */
    private static final float POP = 0.6F;
    private static final long POP_MS = 150;
    /** The last part of a text's lifetime fades it out. */
    private static final float FADE_FROM = 0.6F;
    /**
     * Texts spread at random around the entity, so hits close together do not cover each other: sideways (across the
     * view) and upward in blocks, and for the miss by the crosshair in GUI pixels.
     */
    private static final double SPREAD_SIDEWAYS = 0.7;
    private static final double SPREAD_UP = 0.25;
    private static final float SPREAD_PIXELS = 14;

    private static final List<Text> TEXTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * One text on screen: the entity it is about and where that entity was last seen, what it reads, when it arrived,
     * and its random spread.
     */
    private static final class Text {
        private final int entityId;
        private final Component label;
        private final int color;
        private final boolean incoming;
        private final long startMs;
        private final float sideways;
        private final float up;
        private Vec3 anchor;

        private Text(int entityId, Vec3 anchor, Component label, int color, boolean incoming, long startMs,
                float sideways, float up) {
            this.entityId = entityId;
            this.anchor = anchor;
            this.label = label;
            this.color = color;
            this.incoming = incoming;
            this.startMs = startMs;
            this.sideways = sideways;
            this.up = up;
        }
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
        Vec3 anchor = entity != null ? overHead(entity, 1) : Vec3.ZERO;
        Component label = (payload.miss()
                ? Component.translatable(GeneratedTexts.COMBAT_TEXT_MISS)
                : Component.literal(DamageNumbers.label(payload.amount(), payload.crit(), payload.heal())))
                .withStyle(ChatFormatting.BOLD);
        TEXTS.add(new Text(payload.entityId(), anchor, label, payload.color(), payload.incoming(), Util.getMillis(),
                RANDOM.nextFloat() - 0.5F, RANDOM.nextFloat()));
        if (TEXTS.size() > MAX_TEXTS) {
            TEXTS.removeFirst();
        }
    }

    private static Vec3 overHead(Entity entity, float partialTick) {
        return entity.getPosition(partialTick).add(0, entity.getBbHeight() + ABOVE_HEAD, 0);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        TEXTS.removeIf(text -> now - text.startMs >= LIFETIME_MS);
        if (minecraft.options.hideGui || TEXTS.isEmpty() || minecraft.level == null) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        for (Text text : TEXTS) {
            long ageMs = now - text.startMs;
            float age = ageMs / (float) LIFETIME_MS;
            float x;
            float y;
            float scale;
            if (text.incoming) {
                x = graphics.guiWidth() / 2.0F - 44 + text.sideways * SPREAD_PIXELS;
                y = graphics.guiHeight() / 2.0F + 12 - age * RISE_PIXELS;
                scale = INCOMING_SCALE;
            } else {
                Entity entity = minecraft.level.getEntity(text.entityId);
                if (entity != null && !entity.isRemoved()) {
                    text.anchor = overHead(entity, partialTick);
                }
                Vector3fc left = camera.leftVector();
                Vec3 point = text.anchor.add(left.x() * text.sideways * SPREAD_SIDEWAYS,
                        text.up * SPREAD_UP + age * RISE_BLOCKS, left.z() * text.sideways * SPREAD_SIDEWAYS);
                // Normalized device coordinates; z above 1 means behind the camera (as vanilla's TrackedWaypoint).
                Vec3 screen = minecraft.gameRenderer.projectPointToScreen(point);
                if (screen.z > 1 || Math.abs(screen.x) > 1 || Math.abs(screen.y) > 1) {
                    continue;
                }
                x = (float) ((screen.x + 1) / 2 * graphics.guiWidth());
                y = (float) ((1 - screen.y) / 2 * graphics.guiHeight());
                scale = CombatTextScale.at(camera.position().distanceTo(point));
            }
            float fade = age < FADE_FROM ? 1 : 1 - (age - FADE_FROM) / (1 - FADE_FROM);
            scale *= 1 + POP * Math.max(0, 1 - ageMs / (float) POP_MS);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale);
            graphics.centeredText(minecraft.font, text.label, 0, -minecraft.font.lineHeight / 2,
                    ARGB.color(Math.max(4, Math.round(255 * fade)), text.color));
            pose.popMatrix();
        }
    }
}
