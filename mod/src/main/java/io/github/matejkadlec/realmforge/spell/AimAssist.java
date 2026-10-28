package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatConfig;
import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;

/**
 * Aim assistance for a player's spells (RLM-345, ADR 0055, architecture 11.2; Matěj, 2026-10-27): the mob a spell
 * aimed at an enemy goes to, when the crosshair is near it but not on it.
 *
 * <ul>
 *   <li><b>Direct aim first:</b> the first mob under the crosshair, in front of every block, is the target, a passive
 *   one too (Q1).</li>
 *   <li><b>The cone:</b> otherwise a hostile mob in sight whose hitbox the crosshair misses by at most its distance / 8
 *   ({@link #MISS_PER_BLOCK}), about 7 degrees around the crosshair: none right next to the player, 0.5 block at 4
 *   blocks, 1 at 8, 2 at 16, 4 at 32. Assistance reaches {@value #REACH} blocks.</li>
 *   <li><b>Passive and calm neutral mobs</b> get no assistance at any distance; only direct aim reaches them (Q6). A
 *   mob is hostile when it is a monster ({@link Enemy}) that is not a calm neutral mob, when it targets the player,
 *   or when a neutral mob is angry at them.</li>
 *   <li><b>Several mobs in the cone:</b> the rules of {@link #preference} decide, in order, each breaking only the ties
 *   of the rules above it (Q2).</li>
 * </ul>
 *
 * <p>The server picks: when a spell bar cast of a targeted spell names no entity ({@code SpellBar}), and when a bolt
 * that follows the crosshair leaves the caster ({@link ProjectileEffect}), which for a cast time or a channel only the
 * server sees. An ally spell takes the ally the crosshair is on or near the same way, every ally being assistable.
 * {@link #choose} and the cone's math are pure, for JUnit; {@link #enemy} and {@link #ally} gather the candidates.
 */
public final class AimAssist {
    /** How far assistance reaches, in blocks (Matěj, 2026-10-27: "16 seems too restrictive"). */
    public static final double REACH = 32;
    /** The miss the cone allows per block of distance: 1/8, about 7 degrees around the crosshair. */
    public static final double MISS_PER_BLOCK = 1.0 / 8;
    /** Below this share of its maximum health a candidate is close to death (rule 3). */
    public static final double LOW_HEALTH = 0.25;
    /** How far an assisted bolt turns toward its target in one tick: a gentle curve (Matěj, 2026-10-27). */
    public static final double TURN_PER_TICK = Math.toRadians(6);

    /** How precisely {@link #miss} measures, in halvings of the allowed miss. */
    private static final int MISS_STEPS = 20;
    /** What a mob of no other rank counts as (rule 5): a normal mob's kill XP multiplier. */
    private static final double DEFAULT_RANK = 1.0;

    private static volatile ToDoubleFunction<LivingEntity> rank = entity -> DEFAULT_RANK;

    private AimAssist() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, AimAssist::onHit);
    }

    /**
     * Sets where a mob's rank comes from (rule 5): {@code character} provides the kill XP multiplier (architecture 4.4,
     * lower asks higher through a provider).
     */
    public static void rankBy(ToDoubleFunction<LivingEntity> source) {
        rank = source;
    }

    /**
     * The enemy a spell of {@code caster}'s aimed where they look goes to within {@code reach} blocks (at most
     * {@link #REACH} is assisted; direct aim reaches the full {@code reach}): the mob under the crosshair, else the
     * best hostile mob in the cone, else none.
     */
    public static Optional<LivingEntity> enemy(Player caster, double reach) {
        return pick(caster, reach, false);
    }

    /** The ally a helpful spell of {@code caster}'s aimed where they look goes to, as {@link #enemy}; never them. */
    public static Optional<LivingEntity> ally(Player caster, double reach) {
        return pick(caster, reach, true);
    }

    /**
     * The allowed miss at {@code distance} blocks along the crosshair: {@code distance} / 8, so nothing right next to
     * the player.
     */
    public static double allowedMiss(double distance) {
        return Math.max(0, distance) * MISS_PER_BLOCK;
    }

    /**
     * By how much the line from {@code from} to {@code to} misses {@code box}: 0 when it passes through it or starts in
     * it, else the smallest growth of the box on every side that the line meets, found to a millionth of {@code limit};
     * infinite when that is more than {@code limit}.
     */
    public static double miss(Vec3 from, Vec3 to, AABB box, double limit) {
        if (meets(box, from, to)) {
            return 0;
        }
        if (!(limit > 0) || !meets(box.inflate(limit), from, to)) {
            return Double.POSITIVE_INFINITY;
        }
        double low = 0;
        double high = limit;
        for (int step = 0; step < MISS_STEPS; step++) {
            double middle = (low + high) / 2;
            if (meets(box.inflate(middle), from, to)) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    /**
     * The target among {@code candidates}: the nearest one under the crosshair (direct aim, Q1); else, of the
     * assistable ones in the cone, the one {@link #preference} puts first; else none.
     */
    public static <T> Optional<T> choose(List<Candidate<T>> candidates) {
        Optional<Candidate<T>> direct = candidates.stream().filter(Candidate::direct)
                .min(Comparator.comparingDouble(Candidate::along));
        if (direct.isPresent()) {
            return direct.map(Candidate::entity);
        }
        return candidates.stream().filter(Candidate::inCone).min(preference()).map(Candidate::entity);
    }

    /**
     * The order of the candidates in the cone (Q2: in order, each rule breaking only the ties of those above):
     * <ol>
     *   <li>a boss's adds the fight wants killed first (W5's boss framework sets them);</li>
     *   <li>a boss ({@code #c:bosses});</li>
     *   <li>below {@link #LOW_HEALTH} of its maximum health;</li>
     *   <li>the current target: the mob the player aimed a targeted spell at, or hit, in the last 10 s (the in-combat
     *   time, RLM-348);</li>
     *   <li>a higher rank (its kill XP multiplier);</li>
     *   <li>a higher level;</li>
     *   <li>nearest to the crosshair: the smallest miss for its distance;</li>
     * </ol>
     * and at last the nearest, so the choice never depends on the order the candidates came in.
     */
    public static <T> Comparator<Candidate<T>> preference() {
        return Comparator.<Candidate<T>, Boolean>comparing(candidate -> !candidate.priorityAdd())
                .thenComparing(candidate -> !candidate.boss())
                .thenComparing(candidate -> !(candidate.healthShare() < LOW_HEALTH))
                .thenComparing(candidate -> !candidate.currentTarget())
                .thenComparingDouble(candidate -> -candidate.rank())
                .thenComparingInt(candidate -> -candidate.level())
                .thenComparingDouble(Candidate::angle)
                .thenComparingDouble(Candidate::along);
    }

    /**
     * {@code velocity} turned toward {@code toward} by at most {@code maxAngle} radians, at the same speed: an assisted
     * bolt's curve. Exactly toward it once the angle left is smaller; unchanged when either has no direction or they
     * point opposite ways.
     */
    public static Vec3 turn(Vec3 velocity, Vec3 toward, double maxAngle) {
        double speed = velocity.length();
        if (speed < 1e-9 || toward.lengthSqr() < 1e-12) {
            return velocity;
        }
        Vec3 from = velocity.scale(1 / speed);
        Vec3 to = toward.normalize();
        double cos = Mth.clamp(from.dot(to), -1, 1);
        if (Math.acos(cos) <= maxAngle) {
            return to.scale(speed);
        }
        Vec3 side = to.subtract(from.scale(cos));
        if (side.lengthSqr() < 1e-12) {
            return velocity;
        }
        return from.scale(Math.cos(maxAngle)).add(side.normalize().scale(Math.sin(maxAngle))).scale(speed);
    }

    /**
     * Whether {@code entity} is hostile to {@code player}, so assistance may pick it (Q6): a neutral mob only while it
     * is angry at them or targets them (a wolf or an iron golem that attacks, never a calm enderman), any other mob
     * while it targets them, and every monster ({@link Enemy}) that is not neutral. On the server, which knows a mob's
     * target and anger.
     */
    static boolean hostile(Player player, LivingEntity entity) {
        boolean targets = entity instanceof Mob mob && mob.getTarget() == player;
        if (entity instanceof NeutralMob neutral) {
            return targets || player.level() instanceof ServerLevel level && neutral.isAngryAt(player, level);
        }
        return targets || entity instanceof Enemy;
    }

    /**
     * The candidates around {@code caster}'s crosshair within {@code reach} blocks, enemies or allies, and the target
     * {@link #choose} picks among them. Direct aim stops at the first block the crosshair shows; a candidate in the
     * cone must be in sight, within {@link #REACH} and assistable (hostile, or an ally).
     */
    private static Optional<LivingEntity> pick(Player caster, double reach, boolean allies) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, caster));
        double blocked = block.getType() == HitResult.Type.MISS ? reach : block.getLocation().distanceTo(eye);
        double assisted = Math.min(reach, REACH);
        AABB around = caster.getBoundingBox().expandTowards(look.scale(reach)).inflate(allowedMiss(assisted) + 1);
        long now = caster.level().getGameTime();
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        List<Candidate<LivingEntity>> candidates = new ArrayList<>();
        for (LivingEntity entity : caster.level().getEntitiesOfClass(LivingEntity.class, around, entity ->
                entity != caster && entity.isAlive() && !entity.isSpectator() && entity.isPickable()
                        && !entity.isInvisibleTo(caster) && Targets.allied(caster, entity) == allies
                        && (allies ? entity.canBeSeenByAnyone() : entity.canBeSeenAsEnemy()))) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            double along = box.getCenter().subtract(eye).dot(look);
            double distance = Targets.distance(caster, entity);
            if (along <= 0 || distance > reach) {
                continue;
            }
            boolean direct = box.contains(eye) || box.clip(eye, end)
                    .filter(hit -> hit.distanceTo(eye) <= blocked).isPresent();
            double miss = direct ? 0 : miss(eye, eye.add(look.scale(reach + allowedMiss(reach))), box,
                    allowedMiss(along));
            if (!direct && (distance > assisted || Double.isInfinite(miss) || !caster.hasLineOfSight(entity))) {
                continue;
            }
            candidates.add(new Candidate<>(entity, along, miss, direct, allies || hostile(caster, entity), false,
                    entity.is(Tags.EntityTypes.BOSSES), entity.getHealth() / entity.getMaxHealth(),
                    state.isCurrentTarget(entity.getUUID(), now, CombatConfig.inCombatTicks()),
                    entity instanceof Mob ? rank.applyAsDouble(entity) : DEFAULT_RANK,
                    entity.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN)));
        }
        return choose(candidates);
    }

    /** A player's hit makes its victim their current target (rule 4), a melee swing, a bolt or a spell's alike. */
    private static void onHit(CombatEvent.Hit event) {
        LivingEntity victim = event.getEntity();
        if (victim.level() instanceof ServerLevel level && event.getSource().getEntity() instanceof Player player
                && player != victim) {
            player.getData(SpellAttachments.COMBAT).setCurrentTarget(victim.getUUID(), level.getGameTime());
        }
    }

    /** Whether {@code box} lies on the line from {@code from} to {@code to}, or holds its start. */
    private static boolean meets(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) || box.clip(from, to).isPresent();
    }

    /**
     * What {@link #choose} knows of one entity around the crosshair.
     *
     * @param entity the entity
     * @param along how far its hitbox's middle lies along the crosshair's line, in blocks
     * @param miss by how much the crosshair misses its hitbox, in blocks; 0 under it, infinite beyond the cone
     * @param direct whether it is under the crosshair, in front of every block (direct aim)
     * @param assistable whether the cone may pick it: hostile, or an ally for an ally spell
     * @param priorityAdd whether it is an add its boss fight wants killed first (rule 1)
     * @param boss whether it is a boss (rule 2)
     * @param healthShare its health as a share of its maximum (rule 3)
     * @param currentTarget whether it is the player's current target (rule 4)
     * @param rank its rank, the kill XP multiplier (rule 5)
     * @param level its level (rule 6)
     */
    public record Candidate<T>(T entity, double along, double miss, boolean direct, boolean assistable,
            boolean priorityAdd, boolean boss, double healthShare, boolean currentTarget, double rank, int level) {
        /** Whether the cone may pick it: assistable, and missed by no more than its distance allows. */
        public boolean inCone() {
            return assistable && miss <= allowedMiss(along);
        }

        /** How far from the crosshair it is for its distance (rule 7): its miss per block. */
        public double angle() {
            return along > 0 ? miss / along : Double.POSITIVE_INFINITY;
        }
    }
}
