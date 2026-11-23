package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.StringRepresentable;

/**
 * How a spell looks, combined from the parts of the spell visuals kit (architecture 11.3, ADR 0073,
 * docs/design/spell-visuals.md): its {@code visuals} in {@code content/spells/}. Its projectiles name their model and
 * their trajectory themselves ({@link ProjectileEffect}).
 *
 * @param look its element's look: the material its projectile's model is drawn with, the particles of its trail and
 *     its impact, and the impact's sound
 * @param forming what shows in the caster's hands while it casts, if anything
 * @param impactSound a sound event of its own for its projectile's hit, instead of its look's
 */
public record Visuals(Holder<SpellLook> look, Optional<Forming> forming, Optional<Identifier> impactSound) {
    public static final Codec<Visuals> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    RegistryFixedCodec.create(SpellRegistries.SPELL_LOOK).fieldOf("look").forGetter(Visuals::look),
                    Forming.CODEC.optionalFieldOf("forming").forGetter(Visuals::forming),
                    Identifier.CODEC.optionalFieldOf("impact_sound").forGetter(Visuals::impactSound))
            .apply(instance, Visuals::new));

    /** The sound of its projectile's hit: its own, or its look's. */
    public Optional<Identifier> impactSoundOrLooks() {
        return impactSound.or(() -> look.value().impactSound());
    }

    /**
     * What shows in a caster's hands during a cast (Matěj's choice of 2026-10-29, RLM-463), for players and mobs
     * alike ({@code client/render/SpellForming}, RLM-640). Only a model of cubes grows cube by cube: the orb, which
     * has none, scales up as a whole under {@link #GROW} too.
     */
    public enum Forming implements StringRepresentable {
        /** The projectile's model grows there cube by cube during a cast time, in a storm of the look's particles. */
        GROW("grow", true),
        /** The projectile's model scales up there as a whole: {@link #GROW}'s fallback. */
        STORM("storm", true),
        /** Only the look's particles at the hands, during a cast time or a channel. */
        HANDS("hands", false);

        public static final Codec<Forming> CODEC = StringRepresentable.fromEnum(Forming::values);

        private final String serializedName;
        private final boolean formsModel;

        Forming(String serializedName, boolean formsModel) {
            this.serializedName = serializedName;
            this.formsModel = formsModel;
        }

        /**
         * Whether the spell's projectile, its first effect, forms in the caster's hands during a cast time and flies
         * from there.
         */
        public boolean formsModel() {
            return formsModel;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
