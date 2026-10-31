package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:melee}: a melee spell's hit shape in front of the caster (RLM-399; architecture 11.3). Every enemy
 * whose hitbox the shape strikes ({@link MeleeShape}) when the effect runs (the swing's hit moment) takes its
 * {@code effects}, the same for each; most melee spells hit an area, as mobs come in groups in Minecraft (Matěj,
 * 2026-10-24). The shape is one of Better Combat's sword hitboxes at the caster's melee reach ({@link #reach}), so it
 * grows with the reach as a melee attack's does (Matěj, 2026-10-31), and it follows the caster's look, so the spell
 * needs no target: an empty shape is a swing that hits nothing.
 *
 * <p><b>Cover</b> (Matěj, 2026-10-24): never through a wall, and an enemy more than half behind blocks is spared: of
 * points spread through its hitbox, at least half must be in the caster's sight ({@link #inTheOpen}). Better Combat's
 * swing hits a mob when a ray reaches its middle or its nearest point, which lets one mostly behind a corner be hit; the
 * share is Matěj's rule instead.
 *
 * @param shape a swing (the default melee hit shape) or a stab
 * @param effects what it does to each enemy in the shape, in order
 */
public record MeleeEffect(MeleeShape.Kind shape, List<SpellEffect> effects) implements SpellEffect {
    /** Points spread through a hitbox for the cover check: at most 3 along each axis, inside its faces. */
    private static final int COVER_SAMPLES = 3;
    private static final double COVER_SPACING = 0.5;
    private static final double COVER_INSET = 0.1;

    public static final MapCodec<MeleeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(MeleeShape.Kind.CODEC.fieldOf("shape").forGetter(MeleeEffect::shape),
                    ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects")
                            .forGetter(MeleeEffect::effects))
            .apply(instance, MeleeEffect::new));

    public MeleeEffect {
        effects = List.copyOf(effects);
    }

    @Override
    public MapCodec<MeleeEffect> codec() {
        return MAP_CODEC;
    }

    /** Its geometry for {@code caster}: the shape at the caster's melee reach. */
    public MeleeShape geometry(LivingEntity caster) {
        return new MeleeShape(shape, reach(caster));
    }

    /**
     * {@code caster}'s melee reach, in blocks: its entity interaction range (a player's is 3, 5 in creative mode, and
     * grows with the attribute), or vanilla's default 3 for a mob without the attribute. Better Combat's melee swing
     * reaches as far (a weapon's own range bonus aside).
     */
    public static double reach(LivingEntity caster) {
        return caster.getAttributes().hasAttribute(Attributes.ENTITY_INTERACTION_RANGE)
                ? caster.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
                : Player.DEFAULT_ENTITY_INTERACTION_RANGE;
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
        MeleeShape geometry = geometry(caster);
        MeleeShape.Frame frame = frame(caster);
        AABB search = new AABB(frame.origin(), frame.origin()).inflate(geometry.extent() + 1);
        return caster.level().getEntitiesOfClass(LivingEntity.class, search, entity -> entity != caster
                        && entity.isAlive() && !entity.isSpectator() && !Targets.allied(caster, entity)
                        && entity.canBeSeenAsEnemy())
                .stream()
                .filter(entity -> geometry.strikes(frame, entity.getBoundingBox().inflate(entity.getPickRadius())))
                .filter(entity -> inTheOpen(caster, entity))
                .toList();
    }

    /**
     * {@code caster}'s look as the frame of a shape: from its eyes lowered by {@link MeleeShape#ORIGIN_DROP} of its
     * height (Better Combat's swing's origin), along its pitch and yaw.
     */
    public static MeleeShape.Frame frame(LivingEntity caster) {
        return frame(caster, caster.getEyePosition(), caster.getXRot(), caster.getYRot());
    }

    /** The frame of {@code caster}'s look of {@code pitch} and {@code yaw} from {@code eyes}, as {@link #frame}. */
    public static MeleeShape.Frame frame(LivingEntity caster, Vec3 eyes, float pitch, float yaw) {
        double drop = caster.getBbHeight() * MeleeShape.ORIGIN_DROP * caster.getAgeScale();
        return MeleeShape.Frame.of(eyes.subtract(0, drop, 0), pitch, yaw);
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
