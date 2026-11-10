package io.github.matejkadlec.realmforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.spell.ClientCasts;
import io.github.matejkadlec.realmforge.client.spell.CurrentCasts;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellLook;
import io.github.matejkadlec.realmforge.spell.SpellModel;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import io.github.matejkadlec.realmforge.spell.SpellVisuals;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.TintedParticles;
import io.github.matejkadlec.realmforge.spell.Visuals;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What shows in a caster's hands while it casts (RLM-640, ADR 0073 decision 7, architecture 11.6): one renderer for
 * every caster this client knows to be casting ({@link ClientCasts}), players and mobs alike, drawn into the level
 * through NeoForge's custom geometry events, not by the casters' own renderers.
 *
 * <ul>
 *   <li><b>{@code grow}</b> draws the spell's projectile model growing cube by cube from its heart, and
 *   <b>{@code storm}</b> the whole model scaling up ({@link FormingCurves}, {@link SpellModels}), both in a swirl of
 *   the look's particles flying in, with a spark at the growing edge; <b>{@code hands}</b> shows the swirl
 *   alone.</li>
 *   <li><b>Where:</b> a player's hands meet below the line of sight ({@link HandPoints}); a mob's are its form point
 *   ({@link SpellVisuals#formOffset}), where its projectile leaves from.</li>
 *   <li><b>One size in first and third person:</b> a player's forming model is drawn at {@link #PLAYER_SHARE} of
 *   its size in flight, in both views, so that through the caster's own eyes it stays below the screen's middle and
 *   off the crosshair; the local player in first person also gets a third of the swirl, none of it above the model.
 *   A mob's model forms at its full size.</li>
 *   <li><b>An interrupted cast's model fades away</b> over {@link FormingCurves#FADE_TICKS}, at the size it had
 *   reached; a caster who casts again at once shows the new cast's model alone.</li>
 *   <li><b>The projectile leaves the hands:</b> for its first {@link FormingCurves#LEAVE_TICKS} ticks a player's
 *   projectile is drawn blended from the hands, and from the forming model's size, to its own path and size
 *   ({@link #launch}).</li>
 * </ul>
 *
 * Main thread only.
 */
public final class SpellForming {
    /** A player's forming model's size as a share of its size in flight, the same in first and third person. */
    static final float PLAYER_SHARE = 0.75F;
    /** A projectile leaves the hands of a caster who was casting at most this many ticks ago. */
    private static final int LAUNCH_WITHIN_TICKS = 3;
    /** How far from the hands the swirl's particles appear, in blocks, at least; a wide model's start outside it. */
    private static final double SWIRL_RADIUS = 0.3;
    /** How many ticks a swirl's particle takes to reach the hands, about. */
    private static final double SWIRL_TICKS = 7;
    /** The local player in first person sees this share of the swirl (docs/design/spell-visuals.md). */
    private static final float FIRST_PERSON_SWIRL = 1 / 3F;
    /** Casters farther than this from the camera show nothing in their hands, in blocks. */
    private static final double DRAW_DISTANCE = 64;

    /**
     * How a projectile is drawn while it leaves the hands.
     *
     * @param offset how far from its own position it is drawn, in blocks
     * @param size its size as a share of its own
     */
    record Launch(Vec3 offset, float size) {
        static final Launch NONE = new Launch(Vec3.ZERO, 1);
    }

    /** What a cast shows in the caster's hands at a moment. */
    private record Hands(Vec3 point, Holder<SpellLook> look, Optional<Holder<SpellModel>> model,
            SpellModels.Pose pose) {
    }

    /** One model to draw in this frame, extracted with the level's render state. */
    private record Draw(Vec3 point, Identifier material, Holder<SpellModel> model, SpellLook.Light light,
            SpellModels.Pose pose) {
    }

    /** A cast that was interrupted: its model fades from how far it had formed. */
    private record Fading(Cast cast, long endedAt, float formed) {
    }

    /** A caster that was casting at a game time, and its forming model's size as a share of its projectile's. */
    private record Casting(long gameTime, float share) {
    }

    private static final ContextKey<List<Draw>> DRAWS = new ContextKey<>(Realmforge.id("spell_forming"));

    private static final Map<Integer, Fading> FADING = new HashMap<>();
    private static final Map<Integer, Casting> CASTING = new HashMap<>();
    /** Where each young projectile was first drawn from, by entity id. */
    private static final Map<Integer, Launch> LAUNCHES = new HashMap<>();

    private SpellForming() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent.class, SpellForming::extract);
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, SpellForming::submit);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> clear());
    }

    private static void clear() {
        FADING.clear();
        CASTING.clear();
        LAUNCHES.clear();
    }

    /**
     * A cast ended, before {@link ClientCasts} drops it: one that did not complete leaves its model to fade away. A
     * completed one's projectile takes over ({@link #launch}).
     */
    public static void ended(CastEndedPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || payload.end().completed()) {
            return;
        }
        Entity caster = level.getEntity(payload.casterId());
        Optional<Cast> cast = caster == null ? Optional.empty() : ClientCasts.current(caster);
        cast.filter(current -> current.spell().equals(payload.spell()) && !current.swing() && !current.channel())
                .ifPresent(current -> FADING.put(payload.casterId(), new Fading(current, level.getGameTime(),
                        CurrentCasts.progress(current, level.getGameTime()))));
    }

    /**
     * What {@code cast} shows in the hands of the entity {@code casterId} at {@code formed} of its length: nothing
     * for a caster that is not there, a swing, or a spell whose visuals name no forming.
     */
    private static Optional<Hands> hands(ClientLevel level, int casterId, Cast cast, float partialTick,
            float formed, float alpha) {
        if (cast.swing() || !(level.getEntity(casterId) instanceof LivingEntity caster) || !caster.isAlive()) {
            return Optional.empty();
        }
        Optional<Spell> spell = Spells.get(level.registryAccess(), cast.spell()).map(Holder::value);
        Optional<Visuals> visuals = spell.flatMap(Spell::visuals);
        Optional<Visuals.Forming> style = visuals.flatMap(Visuals::forming);
        if (style.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProjectileEffect> projectile = spell.flatMap(SpellVisuals::formedProjectile);
        boolean player = caster instanceof Player;
        boolean firstPerson = firstPerson(caster);
        Vec3 point;
        float yaw;
        float pitch;
        if (player) {
            float lookPitch = firstPerson ? caster.getViewXRot(partialTick)
                    : Mth.lerp(partialTick, caster.xRotO, caster.getXRot());
            float lookYaw = firstPerson ? caster.getViewYRot(partialTick)
                    : Mth.rotLerp(partialTick, caster.yHeadRotO, caster.yHeadRot);
            point = HandPoints.player(caster.getPosition(partialTick), caster.getEyeHeight(), caster.getScale(),
                    lookPitch, lookYaw, firstPerson);
            // A projectile's own angles: where it flies, up positive.
            yaw = -lookYaw;
            pitch = -lookPitch;
        } else {
            float bodyYaw = Mth.rotLerp(partialTick, caster.yBodyRotO, caster.yBodyRot);
            point = caster.getPosition(partialTick).add(SpellVisuals.formOffset(caster, bodyYaw));
            yaw = -bodyYaw;
            pitch = 0;
        }
        float width = projectile.map(formedOne -> (float) formedOne.size()
                * formedOne.model().orElseThrow().value().scale() * (player ? PLAYER_SHARE : 1)).orElse(0.0F);
        int light = LevelRenderer.getLightCoords(level, BlockPos.containing(point));
        float age = level.getGameTime() + partialTick - cast.startGameTime();
        return Optional.of(new Hands(point, visuals.get().look(), projectile.flatMap(ProjectileEffect::model),
                new SpellModels.Pose(width, yaw, pitch, age, light, alpha, formed,
                        style.get() == Visuals.Forming.GROW)));
    }

    /** Whether the camera looks through {@code caster}'s own eyes. */
    private static boolean firstPerson(@Nullable Entity caster) {
        Minecraft minecraft = Minecraft.getInstance();
        return caster != null && minecraft.getCameraEntity() == caster
                && minecraft.options.getCameraType().isFirstPerson();
    }

    /** Remembers what every caster's hands hold in this frame. */
    private static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = event.getCamera().position();
        List<Draw> draws = new ArrayList<>();
        Map<Integer, Cast> casts = ClientCasts.all();
        casts.forEach((casterId, cast) -> {
            if (!cast.channel()) {
                hands(level, casterId, cast, partialTick, CurrentCasts.progress(cast, now), 1)
                        .ifPresent(hands -> draw(hands, camera, draws));
            }
        });
        FADING.forEach((casterId, fading) -> {
            // A caster who casts again at once holds the new cast's model: the old one is not drawn over it.
            if (!casts.containsKey(casterId)) {
                hands(level, casterId, fading.cast(), partialTick, fading.formed(),
                        FormingCurves.fade((float) (now - fading.endedAt())))
                        .ifPresent(hands -> draw(hands, camera, draws));
            }
        });
        if (!draws.isEmpty()) {
            event.getRenderState().setRenderData(DRAWS, draws);
        }
    }

    private static void draw(Hands hands, Vec3 camera, List<Draw> draws) {
        Optional<Identifier> material = SpellLook.material(hands.look());
        if (hands.model().isPresent() && material.isPresent()
                && hands.point().distanceToSqr(camera) <= DRAW_DISTANCE * DRAW_DISTANCE) {
            draws.add(new Draw(hands.point(), material.get(), hands.model().get(), hands.look().value().light(),
                    hands.pose()));
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        List<Draw> draws = event.getLevelRenderState().getRenderData(DRAWS);
        if (draws == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (Draw draw : draws) {
            poseStack.pushPose();
            poseStack.translate(draw.point().x - camera.x, draw.point().y - camera.y, draw.point().z - camera.z);
            SpellModels.submit(poseStack, event.getSubmitNodeCollector(), draw.material(), draw.model(), draw.light(),
                    draw.pose(), 0);
            poseStack.popPose();
        }
    }

    /**
     * Every tick: the swirl round each caster's hands, and what is over is forgotten. The client's tick event fires
     * while the game is paused too, when no particle ages and no cast moves on, so nothing swirls then
     * ({@link FormingCurves#swirls}): the particles would pile up unseen and all appear at once on resume.
     */
    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        long now = level.getGameTime();
        boolean swirls = FormingCurves.swirls(minecraft.isPaused(), level.tickRateManager().runsNormally());
        ClientCasts.all().forEach((casterId, cast) -> {
            float formed = CurrentCasts.progress(cast, now);
            hands(level, casterId, cast, 1, formed, 1).ifPresent(hands -> {
                Entity caster = level.getEntity(casterId);
                boolean shrunk = hands.model().isPresent() && caster instanceof Player;
                CASTING.put(casterId, new Casting(now, shrunk ? PLAYER_SHARE : 1));
                if (swirls) {
                    swirl(level, hands, firstPerson(caster));
                }
            });
        });
        FADING.values().removeIf(fading -> now - fading.endedAt() >= FormingCurves.FADE_TICKS);
        CASTING.values().removeIf(casting -> now - casting.gameTime() > LAUNCH_WITHIN_TICKS);
        LAUNCHES.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /**
     * The swirl of a tick: the look's particles appear round the hands and fly in, one of them at the growing edge
     * of a model that forms cube by cube. In the caster's own first person a third of them, and none above the
     * hands, so nothing drifts over the crosshair.
     */
    private static void swirl(ClientLevel level, Hands hands, boolean firstPerson) {
        SpellLook look = hands.look().value();
        if (look.swirl().isEmpty()) {
            return;
        }
        SpellLook.Swirl swirl = look.swirl().get();
        RandomSource random = level.getRandom();
        float rate = swirl.perTick() * (firstPerson ? FIRST_PERSON_SWIRL : 1);
        int count = Mth.floor(rate) + (random.nextFloat() < rate - Mth.floor(rate) ? 1 : 0);
        double radius = Math.max(SWIRL_RADIUS, hands.pose().width());
        Vec3 point = hands.point();
        for (int n = 0; n < count; n++) {
            Vec3 at;
            Vec3 speed;
            if (n == 0 && hands.model().isPresent() && hands.pose().cubeByCube() && hands.pose().formed() < 1) {
                at = point.add(SpellModels.edge(hands.model().get(), hands.pose()));
                speed = new Vec3(random.triangle(0, 0.01), random.triangle(0, 0.01), random.triangle(0, 0.01));
            } else {
                Vec3 out = new Vec3(random.triangle(0, 1), random.triangle(0, 1), random.triangle(0, 1));
                if (out.lengthSqr() < 1e-4) {
                    continue;
                }
                out = out.normalize();
                if (firstPerson && out.y > 0) {
                    out = new Vec3(out.x, -out.y, out.z);
                }
                at = point.add(out.scale(radius));
                speed = out.scale(-radius / SWIRL_TICKS);
            }
            Vec3 where = at;
            Vec3 velocity = speed;
            TintedParticles.of(swirl.particle(), look.color(random)).ifPresent(particle -> level.addParticle(
                    particle, where.x, where.y, where.z, velocity.x, velocity.y, velocity.z));
        }
    }

    /**
     * How {@code projectile} is drawn now: for its first {@link FormingCurves#LEAVE_TICKS} ticks a player's
     * projectile is drawn from the player's hands, where its forming model was (or the swirl of a {@code hands}
     * forming), to its own path, and from that model's size to its own, when the player was casting as it appeared.
     * A mob's projectile leaves from where it formed already.
     */
    static Launch launch(SpellProjectile projectile, float partialTick) {
        float flown = projectile.flownTicks() + partialTick;
        if (flown >= FormingCurves.LEAVE_TICKS) {
            return Launch.NONE;
        }
        Launch start = LAUNCHES.computeIfAbsent(projectile.getId(), id -> start(projectile, partialTick));
        if (start == Launch.NONE) {
            return start;
        }
        float left = FormingCurves.left(flown);
        return new Launch(start.offset().scale(1 - left), Mth.lerp(left, start.size(), 1));
    }

    /** Where {@code projectile} leaves from on screen, as its offset from its own launch point, and at what size. */
    private static Launch start(SpellProjectile projectile, float partialTick) {
        if (!(projectile.getOwner() instanceof Player owner)) {
            return Launch.NONE;
        }
        Casting casting = CASTING.get(owner.getId());
        if (casting == null || projectile.level().getGameTime() - casting.gameTime() > LAUNCH_WITHIN_TICKS) {
            return Launch.NONE;
        }
        boolean firstPerson = firstPerson(owner);
        float pitch = firstPerson ? owner.getViewXRot(partialTick) : owner.getXRot();
        float yaw = firstPerson ? owner.getViewYRot(partialTick) : owner.yHeadRot;
        Vec3 hands = HandPoints.player(owner.getPosition(partialTick), owner.getEyeHeight(), owner.getScale(), pitch,
                yaw, firstPerson);
        Vec3 launched = FormingCurves.launchPoint(projectile.position().add(0, projectile.getBbHeight() / 2, 0),
                projectile.getDeltaMovement(), projectile.flownTicks());
        return new Launch(hands.subtract(launched), casting.share());
    }
}
