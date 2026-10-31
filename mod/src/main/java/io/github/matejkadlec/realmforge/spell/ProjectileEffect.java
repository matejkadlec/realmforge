package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:projectile}: launches a {@link SpellProjectile} from the caster's eyes along the target's direction
 * (toward an enemy, a ground point, or along a direction: where the crosshair points, RLM-344), which runs its own
 * {@code effects} on the first enemy it hits (architecture 11.3). A spell that forms it during its cast
 * ({@link SpellCast#formsProjectile}) launches it from where it formed, in front of the caster
 * ({@link SpellVisuals#formPoint}), toward the target; a channel launches each from there too. A player's projectile
 * is aim assisted (RLM-345): it leaves toward the enemy it homes in on and curves after it ({@link #homing}). A bolt
 * with a {@link Splash} explodes where it hits an enemy (RLM-399): the enemies round the target take its effects too,
 * its damage at a share and every other effect at full strength.
 *
 * @param speed blocks per second
 * @param size the width and height of its hitbox, in blocks
 * @param lifetimeTicks how long it flies before it fizzles; without one it flies until it hits something or leaves
 *     the world ({@link SpellProjectile})
 * @param effects what it does to the enemy it hits, in order
 * @param spares an entity type tag whose entities stop it but take none of its effects
 * @param impact its sound and particles where it hits a block or an entity
 * @param appearance how it looks; without it, a trail of sparks
 * @param splash its small explosion where it hits an enemy, if it has one (RLM-399)
 */
public record ProjectileEffect(double speed, double size, Optional<Integer> lifetimeTicks, List<SpellEffect> effects,
        Optional<TagKey<EntityType<?>>> spares, Optional<Impact> impact, Optional<Appearance> appearance,
        Optional<Splash> splash) implements SpellEffect {
    public static final double MAX_SPEED = 100;
    public static final double MAX_SIZE = 4;
    public static final int MAX_LIFETIME_TICKS = 200;

    public static final MapCodec<ProjectileEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, MAX_SPEED).fieldOf("speed").forGetter(ProjectileEffect::speed),
                    Codec.doubleRange(0, MAX_SIZE).optionalFieldOf("size", 0.5).forGetter(ProjectileEffect::size),
                    Codec.intRange(1, MAX_LIFETIME_TICKS).optionalFieldOf("lifetime_ticks")
                            .forGetter(ProjectileEffect::lifetimeTicks),
                    ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects")
                            .forGetter(ProjectileEffect::effects),
                    TagKey.codec(Registries.ENTITY_TYPE).optionalFieldOf("spares").forGetter(ProjectileEffect::spares),
                    Impact.CODEC.optionalFieldOf("impact").forGetter(ProjectileEffect::impact),
                    Appearance.CODEC.optionalFieldOf("appearance").forGetter(ProjectileEffect::appearance),
                    Splash.CODEC.optionalFieldOf("splash").forGetter(ProjectileEffect::splash))
            .apply(instance, ProjectileEffect::new));

    /**
     * A bolt's small explosion where it hits an enemy (RLM-399, Matěj, 2026-10-24): every other enemy whose hitbox lies
     * within {@code radius} of the target's takes the projectile's effects too, its damage at the {@code damage} share
     * (half for the Mage's bolts) and every other effect at full strength.
     *
     * @param radius in blocks from the target's hitbox
     * @param damage the share of the damage the enemies round the target take
     */
    public record Splash(double radius, double damage) {
        public static final double MAX_RADIUS = 4;
        public static final Codec<Splash> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.doubleRange(0, MAX_RADIUS).fieldOf("radius").forGetter(Splash::radius),
                        Codec.doubleRange(0, 1).fieldOf("damage").forGetter(Splash::damage))
                .apply(instance, Splash::new));
    }

    public ProjectileEffect {
        effects = List.copyOf(effects);
    }

    @Override
    public MapCodec<ProjectileEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        Spell spell = context.spell().value();
        boolean formed = context.direct() == context.caster()
                && SpellVisuals.formedProjectile(spell).filter(formedOne -> formedOne == this).isPresent();
        LivingEntity caster = context.caster();
        // A channel's projectiles leave from the hands too, as a formed one does: from the eyes, a missile fills the
        // middle of the caster's own screen for a frame (Arcane Missiles, Matěj's playtest of 2026-10-30).
        boolean fromHands = formed || context.direct() == caster && spell.cast().type() == SpellCast.Type.CHANNEL;
        Vec3 origin = fromHands ? SpellVisuals.formPoint(caster, caster.yBodyRot) : caster.getEyePosition();
        Vec3 direction = target.direction();
        Optional<LivingEntity> homing = homing(context, target);
        Optional<LivingEntity> toward = homing.isPresent() ? homing : target.entity().filter(entity -> formed);
        if (toward.isPresent()) {
            Vec3 offset = toward.get().getBoundingBox().getCenter().subtract(origin);
            direction = offset.lengthSqr() < 1e-8 ? direction : offset.normalize();
        }
        SpellProjectile.launch(context, this, origin, direction.scale(speed / SharedConstants.TICKS_PER_SECOND),
                homing.orElse(null));
    }

    /**
     * The enemy a player's projectile homes in on (aim assistance, RLM-345, ADR 0055), launched by the player
     * themselves: for a bolt that follows the crosshair, the mob {@link AimAssist#enemy} picks where they look as it
     * leaves; for a spell aimed at an enemy, that enemy. A mob's projectile, a direction aimed at an entity or a point,
     * and one an area or another projectile launches fly straight.
     */
    private static Optional<LivingEntity> homing(EffectContext context, SpellTarget target) {
        LivingEntity caster = context.caster();
        if (!(caster instanceof Player player) || context.direct() != caster) {
            return Optional.empty();
        }
        if (target.followsLook()) {
            return AimAssist.enemy(player, AimAssist.REACH);
        }
        return target.entity().filter(entity -> entity != caster && !Targets.allied(caster, entity));
    }

    /**
     * What a projectile does where it hits a block or an entity.
     *
     * @param sound the sound event it makes there
     * @param particles the block whose break particles burst there, as a mined block shatters
     */
    public record Impact(Optional<Identifier> sound, Optional<Identifier> particles) {
        public static final Codec<Impact> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.optionalFieldOf("sound").forGetter(Impact::sound),
                        Identifier.CODEC.optionalFieldOf("particles").forGetter(Impact::particles))
                .apply(instance, Impact::new));
    }

    /**
     * How a projectile looks, with the texture of its spell ({@link SpellVisuals#projectileTexture}). The YAML's
     * {@code color} is only its placeholder texture's, which the pipeline draws.
     *
     * @param model its shape
     */
    public record Appearance(Model model) {
        public static final Codec<Appearance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Model.CODEC.fieldOf("model").forGetter(Appearance::model))
                .apply(instance, Appearance::new));
    }

    /** The shapes a projectile can have. */
    public enum Model implements StringRepresentable {
        /** A small spinning orb of three crossed slabs, the shape of vanilla's shulker bullet. */
        ORB("orb");

        public static final Codec<Model> CODEC = StringRepresentable.fromEnum(Model::values);

        private final String serializedName;

        Model(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
