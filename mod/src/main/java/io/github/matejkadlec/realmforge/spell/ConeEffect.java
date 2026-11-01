package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:cone}: a cone along the caster's look (RLM-542: Cone of Cold; architecture 11.3). Every enemy whose
 * hitbox the cone holds when the effect runs ({@link ConeShape}) takes its {@code effects}, the same for each. Its tip
 * is a melee shape's origin (the eyes, lowered by {@link MeleeShape#ORIGIN_DROP} of the caster's height,
 * {@link MeleeEffect#frame}), and it follows the look, so the spell needs no target: an empty cone hits nothing. Never
 * through a wall: an enemy more than half behind blocks is spared, as a melee shape spares it
 * ({@link MeleeEffect#inTheOpen}). Its sound plays from the caster, and its particle block's break particles fill it
 * (placeholders until the Frost visuals, RLM-544).
 *
 * @param range how far it reaches, in blocks
 * @param angleDegrees how wide it opens, in degrees
 * @param effects what it does to each enemy in it, in order
 * @param sound a sound event played from the caster, if any
 * @param particles a block whose break particles fill the cone, if any
 */
public record ConeEffect(double range, double angleDegrees, List<SpellEffect> effects, Optional<Identifier> sound,
        Optional<Identifier> particles) implements SpellEffect {
    public static final double MAX_RANGE = 16;
    private static final int PARTICLES = 40;

    public static final MapCodec<ConeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, MAX_RANGE).fieldOf("range").forGetter(ConeEffect::range),
                    Codec.doubleRange(0, 180).fieldOf("angle_degrees").forGetter(ConeEffect::angleDegrees),
                    ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects")
                            .forGetter(ConeEffect::effects),
                    Identifier.CODEC.optionalFieldOf("sound").forGetter(ConeEffect::sound),
                    Identifier.CODEC.optionalFieldOf("particles").forGetter(ConeEffect::particles))
            .apply(instance, ConeEffect::new));

    public ConeEffect {
        effects = List.copyOf(effects);
    }

    @Override
    public MapCodec<ConeEffect> codec() {
        return MAP_CODEC;
    }

    /** Its geometry. */
    public ConeShape geometry() {
        return new ConeShape(range, angleDegrees);
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        LivingEntity caster = context.caster();
        MeleeShape.Frame frame = MeleeEffect.frame(caster);
        show(context.level(), caster, frame);
        for (LivingEntity entity : struck(caster)) {
            SpellTarget hit = SpellTarget.of(caster, entity);
            for (SpellEffect effect : effects) {
                effect.apply(context, hit);
            }
        }
    }

    /**
     * The enemies the cone holds for {@code caster}'s look now: alive, damageable, not its allies, their hitboxes in
     * the cone and at least half of them in its sight.
     */
    public List<LivingEntity> struck(LivingEntity caster) {
        ConeShape geometry = geometry();
        MeleeShape.Frame frame = MeleeEffect.frame(caster);
        AABB search = new AABB(frame.origin(), frame.origin()).inflate(range + 1);
        return caster.level().getEntitiesOfClass(LivingEntity.class, search, entity -> entity != caster
                        && entity.isAlive() && !entity.isSpectator() && !Targets.allied(caster, entity)
                        && entity.canBeSeenAsEnemy())
                .stream()
                .filter(entity -> geometry.strikes(frame, entity.getBoundingBox().inflate(entity.getPickRadius())))
                .filter(entity -> MeleeEffect.inTheOpen(caster, entity))
                .toList();
    }

    /** Its sound from the caster, and its particles through the cone. */
    private void show(ServerLevel level, LivingEntity caster, MeleeShape.Frame frame) {
        sound.map(BuiltInRegistries.SOUND_EVENT::getValue).ifPresent((SoundEvent event) -> level.playSound(null,
                caster.getX(), caster.getY(), caster.getZ(), event, caster.getSoundSource(), 1.0F, 1.0F));
        particles.map(BuiltInRegistries.BLOCK::getValue).filter(block -> block != Blocks.AIR)
                .ifPresent((Block block) -> show(level, caster, frame, block));
    }

    /** Its particle block's break particles through the cone. */
    private void show(ServerLevel level, LivingEntity caster, MeleeShape.Frame frame, Block block) {
        BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState());
        for (Vec3 point : geometry().particlePoints(frame, caster.getRandom(), PARTICLES)) {
            level.sendParticles(option, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.05);
        }
    }
}
