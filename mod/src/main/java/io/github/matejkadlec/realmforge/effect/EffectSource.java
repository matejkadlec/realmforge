package io.github.matejkadlec.realmforge.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who applied a status effect, and with what (architecture 11.4): plain ids, because {@code effect} sits below
 * {@code spell}. Dispels and talents read it ({@link Effects#source}), and the ticks of damage and healing over time
 * take the caster's stats and carry the proc origin ({@link EffectTicks}).
 *
 * @param caster the entity that applied it
 * @param spell the spell that applied it, if a spell did
 * @param procOrigin the proc whose spell applied it, if a proc did; its ticks then trigger no proc (architecture 12)
 */
public record EffectSource(UUID caster, Optional<Identifier> spell, Optional<Identifier> procOrigin) {
    public static final Codec<EffectSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("caster").forGetter(EffectSource::caster),
                    Identifier.CODEC.optionalFieldOf("spell").forGetter(EffectSource::spell),
                    Identifier.CODEC.optionalFieldOf("proc_origin").forGetter(EffectSource::procOrigin))
            .apply(instance, EffectSource::new));

    public EffectSource {
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(procOrigin, "procOrigin");
    }

    /** The caster as an entity in {@code level}, while it lives there; null once it died, left or logged out. */
    @Nullable
    public LivingEntity casterIn(ServerLevel level) {
        Entity entity = level.getEntity(caster);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }
}
