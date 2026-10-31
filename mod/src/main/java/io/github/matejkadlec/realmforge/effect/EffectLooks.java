package io.github.matejkadlec.realmforge.effect;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * The value of the {@code realmforge:effect_looks} attachment (RLM-539; architecture 7, 18): how clients draw the
 * holder because of its status effects ({@link StatusEffect.Look}), Frozen's ice. Vanilla tells clients only of a
 * player's own effects, so the server keeps this set of looks on the holder and syncs it to every tracking player.
 * Saved with the holder, as its effects are.
 *
 * @param looks the looks its effects give it now
 */
public record EffectLooks(Set<StatusEffect.Look> looks) {
    public static final EffectLooks NONE = new EffectLooks(Set.of());
    public static final Codec<EffectLooks> CODEC = StatusEffect.Look.CODEC.listOf()
            .xmap(list -> new EffectLooks(Set.copyOf(list)), value -> value.looks().stream().sorted().toList());
    public static final StreamCodec<ByteBuf, EffectLooks> STREAM_CODEC = StatusEffect.Look.STREAM_CODEC
            .apply(ByteBufCodecs.list()).map(list -> new EffectLooks(Set.copyOf(list)),
                    value -> value.looks().stream().sorted().toList());

    public EffectLooks {
        looks = looks.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(looks));
    }

    /** Whether {@code entity} has {@code look} now; clients read it to draw the entity. */
    public static boolean has(LivingEntity entity, StatusEffect.Look look) {
        return entity.getExistingData(EffectAttachments.LOOKS).map(value -> value.looks().contains(look))
                .orElse(false);
    }

    static void onEffectAdded(MobEffectEvent.Added event) {
        if (!event.getEntity().level().isClientSide() && look(event.getEffectInstance().getEffect()) != null) {
            // Added fires before the effect joins the holder's.
            refresh(event.getEntity(), event.getEffectInstance().getEffect(), true);
        }
    }

    static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!event.getEntity().level().isClientSide() && look(event.getEffect()) != null) {
            refresh(event.getEntity(), event.getEffect(), false);
        }
    }

    static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && !event.getEntity().level().isClientSide() && look(instance.getEffect()) != null) {
            refresh(event.getEntity(), instance.getEffect(), false);
        }
    }

    /** Sets the holder's looks from its effects, with {@code changed} counted as {@code present} whatever the map says. */
    private static void refresh(LivingEntity entity, Holder<MobEffect> changed, boolean present) {
        Set<StatusEffect.Look> looks = EnumSet.noneOf(StatusEffect.Look.class);
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            StatusEffect.Look look = look(instance.getEffect());
            if (look != null && !instance.getEffect().equals(changed)) {
                looks.add(look);
            }
        }
        StatusEffect.Look own = look(changed);
        if (present && own != null) {
            looks.add(own);
        }
        EffectLooks value = new EffectLooks(looks);
        if (!value.equals(entity.getExistingData(EffectAttachments.LOOKS).orElse(NONE))) {
            entity.setData(EffectAttachments.LOOKS, value);
        }
    }

    private static StatusEffect.Look look(Holder<MobEffect> effect) {
        return effect.value() instanceof StatusEffect status ? status.look().orElse(null) : null;
    }
}
