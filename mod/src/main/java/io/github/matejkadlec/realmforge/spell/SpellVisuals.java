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
 * projectile, and the texture of a projectile's appearance.
 */
public final class SpellVisuals {
    /** How far in front of the caster's hitbox, in blocks, a projectile forms. */
    private static final double FORM_AHEAD = 0.7;
    /** How far above the caster's eyes, in blocks: in front of its forehead. */
    private static final double FORM_ABOVE = 0.1;

    private SpellVisuals() {
    }

    /**
     * Where {@code caster} forms a projectile while it casts, in front of its forehead, given its body's yaw in
     * degrees; the projectile flies from there.
     */
    public static Vec3 formPoint(LivingEntity caster, float bodyYaw) {
        return caster.position().add(formOffset(caster.getBbWidth(), caster.getEyeHeight(), bodyYaw));
    }

    /**
     * The form point's offset from the feet of a caster {@code width} wide with its eyes {@code eyeHeight} up, facing
     * {@code bodyYaw} degrees (vanilla's yaw: 0 faces south, +z).
     */
    public static Vec3 formOffset(float width, float eyeHeight, float bodyYaw) {
        double ahead = width / 2 + FORM_AHEAD;
        float radians = bodyYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians) * ahead, eyeHeight + FORM_ABOVE, Mth.cos(radians) * ahead);
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
