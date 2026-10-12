package io.github.matejkadlec.realmforge.world;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Dark Vision (architecture 19.1): vanilla night vision as an infinite, hidden effect that every player can switch on
 * and off with a key. The switch lives in {@link WorldAttachments#DARK_VISION}, because effects do not survive death:
 * the effect is reapplied on login and respawn. Nothing but the switch removes it: milk, a totem of undying and the
 * other cures leave it alone.
 */
public final class DarkVision {
    private DarkVision() {
    }

    public static boolean isEnabled(Player player) {
        return player.getData(WorldAttachments.DARK_VISION);
    }

    /** Flips the player's switch, updates the effect and confirms it on the action bar; returns the new state. */
    public static boolean toggle(ServerPlayer player) {
        boolean enabled = !isEnabled(player);
        setEnabled(player, enabled);
        String message = enabled ? GeneratedTexts.MESSAGE_DARK_VISION_ON : GeneratedTexts.MESSAGE_DARK_VISION_OFF;
        player.displayClientMessage(Component.translatable(message), true);
        return enabled;
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        player.setData(WorldAttachments.DARK_VISION, enabled);
        refresh(player);
    }

    /**
     * Makes the player's night vision match the switch. When it is on, any other night vision (a potion's) is replaced
     * by Dark Vision, which outlasts it. When it is off, an infinite night vision is Dark Vision, perhaps merged with a
     * potion drunk meanwhile, and goes; a potion's own, finite night vision stays.
     */
    public static void refresh(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (isEnabled(player)) {
            if (!isDarkVision(current)) {
                player.removeEffect(MobEffects.NIGHT_VISION);
                player.addEffect(effect());
            }
        } else if (current != null && current.isInfiniteDuration()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /** A new Dark Vision effect: infinite, without particles or icon, and curable by nothing. */
    public static MobEffectInstance effect() {
        MobEffectInstance effect = new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false);
        effect.getCures().clear();
        return effect;
    }

    /** Whether {@code effect} is exactly what {@link #effect()} creates. */
    public static boolean isDarkVision(@Nullable MobEffectInstance effect) {
        return effect != null
                && effect.is(MobEffects.NIGHT_VISION)
                && effect.isInfiniteDuration()
                && effect.getAmplifier() == 0
                && !effect.isVisible()
                && !effect.showIcon()
                && effect.getCures().isEmpty();
    }

    static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }
}
