package io.github.matejkadlec.realmforge.core;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code realmforge:has_advancement}: the player has earned {@code advancement}, a realm's unlock
 * {@code realmforge:realm/<realm>} for example (realm portal spec, "Access and unlocks"). An advancement that does not
 * exist (content removed, a data pack off) is never earned, so the requirement fails closed.
 */
public record HasAdvancement(Identifier advancement) implements Requirement {
    public static final MapCodec<HasAdvancement> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Identifier.CODEC.fieldOf("advancement").forGetter(HasAdvancement::advancement))
            .apply(instance, HasAdvancement::new));

    @Override
    public MapCodec<HasAdvancement> codec() {
        return MAP_CODEC;
    }

    @Override
    public boolean test(ServerPlayer player) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(advancement);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
