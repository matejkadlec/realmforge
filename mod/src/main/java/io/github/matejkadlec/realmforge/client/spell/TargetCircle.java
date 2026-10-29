package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastTargetPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The target circle (aim assistance, RLM-345, Q4; architecture 11.6): during the local player's cast-time or channelled
 * spell, a ring of dust in the spell school's colour on the ground under the mob the spell will hit, which the server
 * names ({@code CastTargetPayload}): the cast's target, or the mob a bolt that follows the crosshair would be assisted
 * to now. Never outside a cast. Particles until the spell visuals (C6) give it its art. Main thread only.
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

    private static Optional<Integer> target = Optional.empty();
    /** The game time of the last ring drawn. */
    private static long lastRing = Long.MIN_VALUE;

    private TargetCircle() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            target = Optional.empty();
            lastRing = Long.MIN_VALUE;
        });
    }

    public static void set(CastTargetPayload payload) {
        target = payload.target();
    }

    /** The local player's cast ended: its circle goes with it. */
    public static void ended(CastEndedPayload payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && payload.casterId() == player.getId()) {
            target = Optional.empty();
        }
    }

    /**
     * Draws the ring every {@value #INTERVAL_TICKS} game ticks. The client ticks on while singleplayer is paused, but
     * the game time stands still and the particle engine does not tick, so it would queue a ring every client tick
     * until the game resumes; a game tick gets one ring at most (RLM-474).
     */
    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || target.isEmpty() || !ringDue(level.getGameTime(), lastRing)) {
            return;
        }
        Entity entity = level.getEntity(target.get());
        Optional<Integer> color = ClientCasts.current(player)
                .flatMap(cast -> Spells.get(level.registryAccess(), cast.spell()))
                .map(spell -> spell.value().school().value().color());
        if (entity instanceof LivingEntity living && living.isAlive() && color.isPresent()) {
            draw(level, living, color.get());
            lastRing = level.getGameTime();
        }
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
