package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.client.input.SpellKeys;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastTargetPayload;
import io.github.matejkadlec.realmforge.spell.net.ShowTargetPayload;
import io.github.matejkadlec.realmforge.spell.net.ShownTargetPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The target circle (aim assistance, RLM-345, Q4; architecture 11.6): a ring of dust on the ground under the mob a spell
 * will hit, which the server names. During the local player's cast-time or channelled spell it is in the spell school's
 * colour, under the cast's target or the mob a bolt that follows the crosshair would be assisted to now
 * ({@code CastTargetPayload}). While the Show target key is held (RLM-468) and no cast names one, it is white, under the
 * mob an enemy spell would go to now ({@code ShowTargetPayload} up, {@code ShownTargetPayload} down). Otherwise there is
 * none. Particles until the spell visuals (C6) give it its art. Main thread only.
 */
public final class TargetCircle {
    /** How often the ring is drawn again, in ticks; a dust particle of {@link #DUST_SCALE} lasts 6 to 30 ticks. */
    private static final int INTERVAL_TICKS = 2;
    private static final float DUST_SCALE = 0.75F;
    /** How far the ring lies outside the target's hitbox, in blocks. */
    private static final double MARGIN = 0.35;
    /** Particles per block of the ring's circumference. */
    private static final double DENSITY = 5;
    /** How high above the target's feet the ring lies, so it shows on the ground and not in it. */
    private static final double LIFT = 0.05;
    /** The ring's colour for the Show target key: no spell, so no school's colour. */
    static final int SHOW_COLOR = 0xFFFFFF;

    /** The mob the server named for the local player's cast. */
    private static Optional<Integer> target = Optional.empty();
    /** The mob the server named while the Show target key is held. */
    private static Optional<Integer> shown = Optional.empty();
    /** Whether the server was last told that the Show target key is down. */
    private static boolean showing;
    /** The game time of the last ring drawn. */
    private static long lastRing = Long.MIN_VALUE;

    private TargetCircle() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            target = Optional.empty();
            shown = Optional.empty();
            showing = false;
            lastRing = Long.MIN_VALUE;
        });
    }

    public static void set(CastTargetPayload payload) {
        target = payload.target();
    }

    public static void shown(ShownTargetPayload payload) {
        shown = payload.target();
    }

    /** The local player's cast ended: its circle goes with it. */
    public static void ended(CastEndedPayload payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && payload.casterId() == player.getId()) {
            target = Optional.empty();
        }
    }

    /**
     * Tells the server when the Show target key goes down or up, then draws the ring every {@value #INTERVAL_TICKS}
     * game ticks. The client ticks on while singleplayer is paused, but the game time stands still and the particle
     * engine does not tick, so it would queue a ring every client tick until the game resumes; a game tick gets one ring
     * at most (RLM-474).
     */
    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        updateShowKey(minecraft.getConnection(), SpellKeys.SHOW_TARGET.get().isDown());
        if (!ringDue(level.getGameTime(), lastRing)) {
            return;
        }
        Optional<Integer> castColor = ClientCasts.current(player)
                .flatMap(cast -> Spells.get(level.registryAccess(), cast.spell()))
                .map(spell -> spell.value().school().value().color());
        boolean forCast = castColor.isPresent() && target.isPresent();
        Optional<Integer> under = under(castColor.isPresent(), target, showing, shown);
        Entity entity = under.map(level::getEntity).orElse(null);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            draw(level, living, forCast ? castColor.get() : SHOW_COLOR);
            lastRing = level.getGameTime();
        }
    }

    /**
     * Sends the Show target key's state when it changed. Letting go forgets the shown mob at once, without waiting for
     * the server. A server without the payload (no Realmforge) is never told, as an unknown payload would drop the
     * connection.
     */
    private static void updateShowKey(ClientPacketListener connection, boolean down) {
        if (down == showing) {
            return;
        }
        showing = down;
        if (!down) {
            shown = Optional.empty();
        }
        if (connection != null && connection.hasChannel(ShowTargetPayload.TYPE)) {
            ClientPacketDistributor.sendToServer(new ShowTargetPayload(down));
        }
    }

    /**
     * The mob the ring goes under: the one the server named for the local player's cast while it casts and one is
     * named; else, while the Show target key is held, the one it named for the key; else none.
     */
    static Optional<Integer> under(boolean casting, Optional<Integer> castTarget, boolean showing,
            Optional<Integer> shown) {
        if (casting && castTarget.isPresent()) {
            return castTarget;
        }
        return showing ? shown : Optional.empty();
    }

    /** Whether a ring is due at game time {@code gameTime}, the last one drawn at {@code lastRing}. */
    static boolean ringDue(long gameTime, long lastRing) {
        return gameTime % INTERVAL_TICKS == 0 && gameTime != lastRing;
    }

    private static void draw(ClientLevel level, LivingEntity entity, int color) {
        double radius = entity.getBbWidth() / 2 + MARGIN;
        int count = Mth.ceil(2 * Math.PI * radius * DENSITY);
        DustParticleOptions dust = new DustParticleOptions(color, DUST_SCALE);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            level.addParticle(dust, entity.getX() + Math.cos(angle) * radius, entity.getY() + LIFT,
                    entity.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        }
    }
}
