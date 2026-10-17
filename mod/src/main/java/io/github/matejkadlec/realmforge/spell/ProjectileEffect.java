package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:projectile}: launches a {@link SpellProjectile} from the caster's eyes along the target's direction
 * (toward an enemy, a ground point, or along a direction), which runs its own {@code effects} on the first enemy it
 * hits (architecture 11.3).
 *
 * @param speed blocks per second
 * @param size the width and height of its hitbox, in blocks
 * @param lifetimeTicks how long it flies before it fizzles
 * @param effects what it does to the enemy it hits, in order
 */
public record ProjectileEffect(double speed, double size, int lifetimeTicks, List<SpellEffect> effects)
        implements SpellEffect {
    public static final double MAX_SPEED = 100;
    public static final double MAX_SIZE = 4;
    public static final int MAX_LIFETIME_TICKS = 200;

    public static final MapCodec<ProjectileEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, MAX_SPEED).fieldOf("speed").forGetter(ProjectileEffect::speed),
                    Codec.doubleRange(0, MAX_SIZE).optionalFieldOf("size", 0.5).forGetter(ProjectileEffect::size),
                    Codec.intRange(1, MAX_LIFETIME_TICKS).optionalFieldOf("lifetime_ticks", 100)
                            .forGetter(ProjectileEffect::lifetimeTicks),
                    ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects")
                            .forGetter(ProjectileEffect::effects))
            .apply(instance, ProjectileEffect::new));

    public ProjectileEffect {
        effects = List.copyOf(effects);
    }

    @Override
    public MapCodec<ProjectileEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        Vec3 velocity = target.direction().scale(speed / SharedConstants.TICKS_PER_SECOND);
        SpellProjectile.launch(context, this, velocity);
    }
}
