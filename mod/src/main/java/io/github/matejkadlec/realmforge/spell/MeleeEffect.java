package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:melee}: a melee spell's hit shape in front of the caster (RLM-399; architecture 11.3). Every enemy
 * whose hitbox reaches into the shape ({@link MeleeShape}) when the effect runs (the swing's hit moment) takes its
 * {@code effects}, the same for each; most melee spells hit an area, as mobs come in groups in Minecraft (Matěj,
 * 2026-10-24). The shape follows the caster's look, so the spell needs no target: an empty shape is a swing that hits
 * nothing.
 *
 * <p><b>Cover</b> (Matěj, 2026-10-24): never through a wall, and an enemy more than half behind blocks is spared: of
 * points spread through its hitbox, at least half must be in the caster's sight ({@link #inTheOpen}). Better Combat's
 * swing hits a mob when a ray reaches its middle or its nearest point, which lets one mostly behind a corner be hit; the
 * share is Matěj's rule instead.
 *
 * @param shape a sector or a box
 * @param reach a sector's radius, or a box's depth beyond the caster's body, in blocks
 * @param angleDegrees a sector's width
 * @param width a box's width
 * @param height how tall round the look's line
 * @param effects what it does to each enemy in the shape, in order
 */
public record MeleeEffect(MeleeShape.Kind shape, double reach, Optional<Double> angleDegrees, Optional<Double> width,
        double height, List<SpellEffect> effects) implements SpellEffect {
    public static final double MAX_REACH = 6;
    /** Points spread through a hitbox for the cover check: at most 3 along each axis, inside its faces. */
    private static final int COVER_SAMPLES = 3;
    private static final double COVER_SPACING = 0.5;
    private static final double COVER_INSET = 0.1;

    public static final MapCodec<MeleeEffect> MAP_CODEC = RecordCodecBuilder.<MeleeEffect>mapCodec(instance -> instance
                    .group(MeleeShape.Kind.CODEC.fieldOf("shape").forGetter(MeleeEffect::shape),
                            Codec.doubleRange(0, MAX_REACH).fieldOf("reach").forGetter(MeleeEffect::reach),
                            Codec.doubleRange(0, 360).optionalFieldOf("angle_degrees")
                                    .forGetter(MeleeEffect::angleDegrees),
                            Codec.doubleRange(0, MAX_REACH).optionalFieldOf("width").forGetter(MeleeEffect::width),
                            Codec.doubleRange(0, MAX_REACH).fieldOf("height").forGetter(MeleeEffect::height),
                            ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects")
                                    .forGetter(MeleeEffect::effects))
                    .apply(instance, MeleeEffect::new))
            .validate(melee -> (melee.shape() == MeleeShape.Kind.SECTOR) == melee.angleDegrees().isPresent()
                    && (melee.shape() == MeleeShape.Kind.BOX) == melee.width().isPresent()
                    ? DataResult.success(melee)
                    : DataResult.error(() -> "A sector names angle_degrees, a box names width: " + melee));

    public MeleeEffect {
        effects = List.copyOf(effects);
    }

    @Override
    public MapCodec<MeleeEffect> codec() {
        return MAP_CODEC;
    }

    /** Its geometry. */
    public MeleeShape geometry() {
        return new MeleeShape(shape, reach, angleDegrees.orElse(0.0), width.orElse(0.0), height);
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        LivingEntity caster = context.caster();
        for (LivingEntity entity : struck(caster)) {
            SpellTarget hit = SpellTarget.of(caster, entity);
            for (SpellEffect effect : effects) {
                effect.apply(context, hit);
            }
        }
    }

    /**
     * The enemies the shape strikes for {@code caster}'s look now: alive, damageable, not its allies, their hitboxes in
     * the shape and at least half of them in its sight.
     */
    public List<LivingEntity> struck(LivingEntity caster) {
        MeleeShape geometry = geometry();
        double body = caster.getBbWidth() / 2;
        MeleeShape.Frame frame = frame(caster);
        AABB search = new AABB(frame.eyes(), frame.eyes()).inflate(geometry.extent(body) + 1);
        return caster.level().getEntitiesOfClass(LivingEntity.class, search, entity -> entity != caster
                        && entity.isAlive() && !entity.isSpectator() && !Targets.allied(caster, entity)
                        && entity.canBeSeenAsEnemy())
                .stream()
                .filter(entity -> geometry.reaches(frame, body, entity.getBoundingBox()))
                .filter(entity -> inTheOpen(caster, entity))
                .toList();
    }

    /** {@code caster}'s look as the frame of a shape: from its eyes, along its pitch and yaw. */
    public static MeleeShape.Frame frame(LivingEntity caster) {
        return MeleeShape.Frame.of(caster.getEyePosition(), caster.getXRot(), caster.getYRot());
    }

    /**
     * Whether {@code entity} is no more than half hidden from {@code caster}'s eyes by blocks: of points spread through
     * its hitbox, at least half have no block between them and the eyes.
     */
    static boolean inTheOpen(LivingEntity caster, LivingEntity entity) {
        Vec3 eyes = caster.getEyePosition();
        List<Vec3> points = MeleeShape.samples(entity.getBoundingBox(), COVER_SPACING, COVER_SAMPLES, COVER_INSET);
        int seen = 0;
        for (Vec3 point : points) {
            if (caster.level().clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    caster)).getType() == HitResult.Type.MISS) {
                seen++;
            }
        }
        return seen * 2 >= points.size();
    }
}
