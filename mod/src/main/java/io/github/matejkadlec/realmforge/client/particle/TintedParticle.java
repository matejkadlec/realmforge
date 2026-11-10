package io.github.matejkadlec.realmforge.client.particle;

import io.github.matejkadlec.realmforge.generated.GeneratedParticles;
import io.github.matejkadlec.realmforge.spell.TintedParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * A particle of one of our types (architecture 11.3, 18; ADR 0073): a greyscale shape from
 * {@code content/particles/}, tinted with the colour its spawn carries, moving as its type's data says and playing its
 * frames over its life. It flies exactly as it was sent off, without vanilla's random kick, passes through blocks (a
 * short-lived spark needs no collision) and fades out over the last third of its life.
 */
public final class TintedParticle extends SingleQuadParticle {
    /** The share of its life after which it starts to fade. */
    private static final float FADE_FROM = 2.0F / 3;

    private final SpriteSet sprites;
    private final boolean glows;

    private TintedParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
            double zSpeed, SpriteSet sprites, TintedParticles.Motion motion, ColorParticleOption options,
            RandomSource random) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        glows = motion.glows();
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        gravity = motion.gravity();
        friction = motion.friction();
        quadSize = motion.size();
        hasPhysics = false;
        lifetime = motion.shortestLifetimeTicks()
                + random.nextInt(motion.longestLifetimeTicks() - motion.shortestLifetimeTicks() + 1);
        setColor(options.getRed(), options.getGreen(), options.getBlue());
        setSpriteFromAge(sprites);
    }

    /** Registers the provider of every particle type of ours with its sprite set. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterParticleProvidersEvent.class, event -> {
            for (TintedParticles.Entry entry : GeneratedParticles.ALL) {
                event.registerSpriteSet(entry.type().get(), sprites -> (options, level, x, y, z, xSpeed, ySpeed,
                        zSpeed, random) -> new TintedParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites,
                                entry.motion(), options, random));
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float lived = (float) age / lifetime;
        setAlpha(lived <= FADE_FROM ? 1.0F : Math.max(0.0F, (1 - lived) / (1 - FADE_FROM)));
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return glows ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
    }
}
