package io.github.matejkadlec.realmforge.client.spell;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.client.ClientConfig.HighlightMode;
import io.github.matejkadlec.realmforge.client.input.SpellKeys;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastTargetPayload;
import io.github.matejkadlec.realmforge.spell.net.HighlightTargetPayload;
import io.github.matejkadlec.realmforge.spell.net.HighlightedTargetPayload;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The target circle (aim assistance, RLM-345, Q4; architecture 11.6): a thin, light ring lying flat on the ground under
 * the mob a spell will hit, which the server names. During the local player's cast-time or channelled spell it goes
 * under the cast's target or the mob a bolt that follows the crosshair would be assisted to now
 * ({@code CastTargetPayload}). While the Highlight Spell Target is on (RLM-468: the key toggled on, or held, as
 * {@link ClientConfig#HIGHLIGHT_SPELL_TARGET} says) and no cast names one, it goes under the mob an enemy spell would
 * go to now ({@code HighlightTargetPayload} up, {@code HighlightedTargetPayload} down). Otherwise there is none.
 *
 * <p>Matěj's playtest of 2026-10-29 gave it its look: a smooth circle, no particles and no animation, very light
 * yellow, as easy to see in the dark as in daylight but lighting nothing around it, and as wide as the mob. It is drawn
 * as geometry, not a texture: many segments of a 1 px wide core with a soft glow fading out on both sides, unlit and
 * unfogged (vanilla's translucent position and colour quads), so it stays round at any size. Main thread only.
 */
public final class TargetCircle {
    /** How far the ring lies outside the target's hitbox, in blocks. */
    static final float MARGIN = 0.35F;
    /** The ring's bright core: one pixel of a block. */
    static final float CORE = 1 / 16F;
    /** How far the glow fades out on each side of the core, in blocks. */
    static final float GLOW = 0.1F;
    /** How high above the target's feet the ring lies, so it shows on the ground and not in it. */
    private static final double LIFT = 0.02;
    /** Very light yellow, the same for every spell (Matěj, 2026-10-29). */
    static final int COLOR = 0xFFF7DC;
    /** The core's alpha; the glow fades from it to nothing. */
    private static final int CORE_ALPHA = 235;
    /** Segments per block of the ring's circumference, within a floor and a cap. */
    private static final int SEGMENTS_PER_BLOCK = 48;
    private static final int MIN_SEGMENTS = 96;
    private static final int MAX_SEGMENTS = 720;

    /** Where the ring goes in this frame, extracted with the level's render state. */
    private record Ring(Vec3 position, float radius) {
    }

    private static final ContextKey<Ring> RING = new ContextKey<>(Realmforge.id("target_circle"));

    /** The mob the server named for the local player's cast. */
    private static Optional<Integer> target = Optional.empty();
    /** The mob the server named while the highlight is on. */
    private static Optional<Integer> highlighted = Optional.empty();
    /** Whether the server was last told that the highlight is on. */
    private static boolean highlightOn;
    /** Whether the key toggled the highlight on, in Toggle mode. */
    private static boolean toggled;

    private TargetCircle() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent.class, TargetCircle::extract);
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, TargetCircle::submit);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            target = Optional.empty();
            highlighted = Optional.empty();
            highlightOn = false;
            toggled = false;
        });
    }

    public static void set(CastTargetPayload payload) {
        target = payload.target();
    }

    public static void highlighted(HighlightedTargetPayload payload) {
        highlighted = payload.target();
    }

    /** The local player's cast ended: its circle goes with it. */
    public static void ended(CastEndedPayload payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && payload.casterId() == player.getId()) {
            target = Optional.empty();
        }
    }

    /** Reads the Highlight Spell Target key: each press flips the toggle in Toggle mode; Hold follows the key. */
    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        KeyMapping key = SpellKeys.HIGHLIGHT_SPELL_TARGET.get();
        HighlightMode mode = ClientConfig.HIGHLIGHT_SPELL_TARGET.get();
        while (key.consumeClick()) {
            toggled = !toggled;
        }
        if (mode != HighlightMode.TOGGLE) {
            toggled = false;
        }
        updateHighlight(minecraft.getConnection(), highlightOn(mode, toggled, key.isDown()));
    }

    /**
     * Sends the highlight's state when it changed. Switching it off forgets the highlighted mob at once, without
     * waiting for the server. A server without the payload (no Realmforge) is never told, as an unknown payload would
     * drop the connection.
     */
    private static void updateHighlight(@Nullable ClientPacketListener connection, boolean on) {
        if (on == highlightOn) {
            return;
        }
        highlightOn = on;
        if (!on) {
            highlighted = Optional.empty();
        }
        if (connection != null && connection.hasChannel(HighlightTargetPayload.TYPE)) {
            ClientPacketDistributor.sendToServer(new HighlightTargetPayload(on));
        }
    }

    /** Whether the highlight is on: toggled on in Toggle mode, the key held in Hold mode. */
    static boolean highlightOn(HighlightMode mode, boolean toggled, boolean keyDown) {
        return mode == HighlightMode.TOGGLE ? toggled : keyDown;
    }

    /**
     * The mob the ring goes under: the one the server named for the local player's cast while it casts and one is
     * named; else, while the highlight is on, the one it named for the highlight; else none.
     */
    static Optional<Integer> under(boolean casting, Optional<Integer> castTarget, boolean highlightOn,
            Optional<Integer> highlighted) {
        if (casting && castTarget.isPresent()) {
            return castTarget;
        }
        return highlightOn ? highlighted : Optional.empty();
    }

    /** The ring's radius around a mob {@code bbWidth} wide: outside its hitbox, a Warden's wider than an Enderman's. */
    static float radius(float bbWidth) {
        return bbWidth / 2 + MARGIN;
    }

    /** How many segments make a ring of {@code radius} look round. */
    static int segments(float radius) {
        return Math.clamp((int) Math.ceil(2 * Math.PI * radius * SEGMENTS_PER_BLOCK), MIN_SEGMENTS, MAX_SEGMENTS);
    }

    /** Remembers where the ring goes in this frame, the mob's position between the last two ticks. */
    private static void extract(ExtractLevelRenderStateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Optional<Integer> under = under(ClientCasts.current(player).isPresent(), target, highlightOn, highlighted);
        Entity entity = under.map(event.getLevel()::getEntity).orElse(null);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            event.getRenderState().setRenderData(RING,
                    new Ring(living.getPosition(partialTick), radius(living.getBbWidth())));
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Ring ring = event.getLevelRenderState().getRenderData(RING);
        if (ring == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(ring.position().x - camera.x, ring.position().y - camera.y + LIFT,
                ring.position().z - camera.z);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                (pose, buffer) -> drawRing(pose, buffer, ring.radius()));
        poseStack.popPose();
    }

    /**
     * Three bands of quads around the centre: the inner glow from nothing to the core's alpha, the core, and the outer
     * glow back to nothing.
     */
    private static void drawRing(PoseStack.Pose pose, VertexConsumer buffer, float radius) {
        float[] radii = {radius - CORE / 2 - GLOW, radius - CORE / 2, radius + CORE / 2, radius + CORE / 2 + GLOW};
        int[] colors = {ARGB.color(0, COLOR), ARGB.color(CORE_ALPHA, COLOR), ARGB.color(CORE_ALPHA, COLOR),
                ARGB.color(0, COLOR)};
        int segments = segments(radius);
        for (int i = 0; i < segments; i++) {
            float from = (float) (2 * Math.PI * i / segments);
            float to = (float) (2 * Math.PI * (i + 1) / segments);
            float cosFrom = (float) Math.cos(from);
            float sinFrom = (float) Math.sin(from);
            float cosTo = (float) Math.cos(to);
            float sinTo = (float) Math.sin(to);
            for (int band = 0; band < radii.length - 1; band++) {
                float inner = radii[band];
                float outer = radii[band + 1];
                buffer.addVertex(pose, cosFrom * inner, 0, sinFrom * inner).setColor(colors[band]);
                buffer.addVertex(pose, cosFrom * outer, 0, sinFrom * outer).setColor(colors[band + 1]);
                buffer.addVertex(pose, cosTo * outer, 0, sinTo * outer).setColor(colors[band + 1]);
                buffer.addVertex(pose, cosTo * inner, 0, sinTo * inner).setColor(colors[band]);
            }
        }
    }
}
