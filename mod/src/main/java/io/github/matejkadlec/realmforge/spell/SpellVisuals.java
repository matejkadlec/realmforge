package io.github.matejkadlec.realmforge.spell;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where and how a spell shows, for the server and its clients alike (architecture 11.3, 11.6), until C6 (RLM-29) gives
 * spells their full visuals: the point in front of a caster where a spell that {@code forms_projectile} forms its
 * projectile (in a custom mob's raised hands, {@link Hands}), and the texture of a projectile's appearance.
 */
public final class SpellVisuals {
    /**
     * How far in front of the hitbox, in blocks, a projectile forms for a caster without {@link Hands} of its own:
     * about where a humanoid's raised hands reach (once the Hewn Chanter's, about 1.1 blocks from its middle).
     */
    private static final double FORM_AHEAD = 0.75;
    /**
     * How far above the eyes, in blocks, a projectile forms for a caster without {@link Hands}: in its raised hands, in
     * front of its forehead (Matěj's playtest, 2026-10-24: 0.1 formed the Chanter's stone below its hands).
     */
    private static final double FORM_ABOVE = 0.4;

    private SpellVisuals() {
    }

    /**
     * A caster whose model says where its raised hands are while it casts: a custom mob, whose generated model
     * places them at the end of its arms (Matěj's playtest of Astone, 2026-10-29: a fixed point in front of the hitbox
     * formed her boulder two thirds along her long arms and below them).
     */
    public interface Hands {
        /**
         * Where its hands hold a forming projectile, facing south (yaw 0): {@code z} blocks in front of its middle and
         * {@code y} above its feet ({@code x} is 0, between the hands).
         */
        Vec3 formingHands();
    }

    /**
     * Where {@code caster} forms a projectile while it casts, in its raised hands in front of its forehead, given its
     * body's yaw in degrees; the projectile flies from there.
     */
    public static Vec3 formPoint(LivingEntity caster, float bodyYaw) {
        return caster.position().add(formOffset(caster, bodyYaw));
    }

    /**
     * The form point's offset from {@code caster}'s feet facing {@code bodyYaw} degrees: in its hands when it has
     * {@link Hands}, otherwise just in front of its hitbox and a little above its eyes. It is never above the caster's
     * height, the room the caster stands in, so a projectile never starts in the ceiling above it.
     */
    public static Vec3 formOffset(LivingEntity caster, float bodyYaw) {
        if (caster instanceof Hands hands) {
            Vec3 held = hands.formingHands();
            return turned(held.z, Math.min(held.y, caster.getBbHeight()), bodyYaw);
        }
        return formOffset(caster.getBbWidth(), caster.getBbHeight(), caster.getEyeHeight(), bodyYaw);
    }

    /**
     * The form point's offset from the feet of a caster {@code width} wide and {@code height} tall with its eyes
     * {@code eyeHeight} up, facing {@code bodyYaw} degrees (vanilla's yaw: 0 faces south, +z). It is never above the
     * caster's height, the room the caster stands in, so a projectile never starts in the ceiling above it.
     */
    public static Vec3 formOffset(float width, float height, float eyeHeight, float bodyYaw) {
        return turned(width / 2 + FORM_AHEAD, Math.min(eyeHeight + FORM_ABOVE, height), bodyYaw);
    }

    /** {@code ahead} blocks in front of a caster facing {@code bodyYaw} degrees, {@code up} above its feet. */
    private static Vec3 turned(double ahead, double up, float bodyYaw) {
        float radians = bodyYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians) * ahead, up, Mth.cos(radians) * ahead);
    }

    /** The texture of the projectile appearance of {@code spell}: {@code <ns>:textures/entity/spell/<path>.png}. */
    public static Identifier projectileTexture(ResourceKey<Spell> spell) {
        Identifier id = spell.identifier();
        return id.withPath("textures/entity/spell/" + id.getPath() + ".png");
    }

    /**
     * The projectile {@code spell} forms during its cast: its first effect, when the cast forms it and it has an
     * appearance.
     */
    public static Optional<ProjectileEffect> formedProjectile(Spell spell) {
        if (!spell.cast().formsProjectile() || spell.effects().isEmpty()
                || !(spell.effects().getFirst() instanceof ProjectileEffect projectile)
                || projectile.appearance().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(projectile);
    }
}
