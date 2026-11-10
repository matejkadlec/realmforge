package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.generated.GeneratedParticles;
import java.util.Optional;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Our particle types (architecture 11.3, ADR 0073): a few greyscale shapes from {@code content/particles/}, each spawn
 * tinted with the colour its options carry, vanilla's {@link ColorParticleOption}. {@code GeneratedParticles} registers
 * one type per shape with how it moves; the client's provider draws them ({@code client/particle}). They are spawned
 * on the client only, through the normal path, so they follow vanilla's Particles option.
 */
public final class TintedParticles {
    private TintedParticles() {
    }

    /**
     * How a particle of a type moves and how long it lives, from its data.
     *
     * @param shortestLifetimeTicks the shortest it lives
     * @param longestLifetimeTicks the longest it lives; each spawn lives something in between
     * @param size half its width in blocks (vanilla's quad size)
     * @param gravity how hard it falls: 1 falls like a block, 0 floats, less than 0 rises
     * @param friction the share of its speed it keeps every tick
     * @param glows whether it is drawn at full brightness instead of in the light where it is
     */
    public record Motion(int shortestLifetimeTicks, int longestLifetimeTicks, float size, float gravity, float friction,
            boolean glows) {
    }

    /** One of our particle types with how its particles move. */
    public record Entry(DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> type, Motion motion) {
    }

    /** A new particle type whose options are a colour. */
    public static ParticleType<ColorParticleOption> type() {
        return new ParticleType<>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }

    /** {@code id}'s particle at {@code color} (ARGB), if it is one of ours. */
    public static Optional<ColorParticleOption> of(Identifier id, int color) {
        for (Entry entry : GeneratedParticles.ALL) {
            if (entry.type().getId().equals(id)) {
                return Optional.of(ColorParticleOption.create(entry.type().get(), color));
            }
        }
        return Optional.empty();
    }
}
