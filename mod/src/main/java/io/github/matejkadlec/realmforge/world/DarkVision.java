package io.github.matejkadlec.realmforge.world;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Dark Vision (architecture 19.1): vanilla night vision as an infinite, hidden effect that every player can switch on
 * and off with a key. The switch lives in {@link WorldAttachments#DARK_VISION}, because effects do not survive death:
 * the effect is reapplied on login and respawn. Nothing but the switch removes it: while the switch is on, every
 * removal of the effect is cancelled ({@link MobEffectEvent.Remove}), so milk, a totem of undying and
 * {@code /effect clear} leave it alone, and every other night vision is refused ({@link MobEffectEvent.Applicable}),
 * so a potion cannot make it visible.
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
        player.sendOverlayMessage(Component.translatable(message));
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

    /** A new Dark Vision effect: infinite, without particles or icon. */
    public static MobEffectInstance effect() {
        return new MobEffectInstance(
                MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false);
    }

    /**
     * Whether {@code effect} is exactly what {@link #effect()} creates. A hidden, infinite night vision from
     * {@code /effect} looks the same and counts as Dark Vision, which changes nothing a player sees.
     */
    public static boolean isDarkVision(@Nullable MobEffectInstance effect) {
        return effect != null
                && effect.is(MobEffects.NIGHT_VISION)
                && effect.isInfiniteDuration()
                && effect.getAmplifier() == 0
                && !effect.isVisible()
                && !effect.showIcon();
    }

    /**
     * Keeps Dark Vision while the switch is on: milk and a totem clear every effect
     * ({@code LivingEntity#removeAllEffects}), and the event lets each effect veto its own removal. The switch itself
     * turns off first, then removes the effect.
     */
    static void onEffectRemove(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player && isEnabled(player)
                && isDarkVision(event.getEffectInstance())) {
            event.setCanceled(true);
        }
    }

    /**
     * Keeps Dark Vision hidden while the switch is on. Night vision from anywhere else (a potion, a splash potion, a
     * tipped arrow, suspicious stew, {@code /effect give}) would merge into it through {@code MobEffectInstance#update},
     * which copies the newcomer's particles and icon onto the infinite effect. Dark Vision outlasts every other night
     * vision, and a higher amplifier changes nothing for night vision, so the newcomer is refused instead.
     */
    static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer player && isEnabled(player)
                && event.getEffectInstance().is(MobEffects.NIGHT_VISION)
                && isDarkVision(player.getEffect(MobEffects.NIGHT_VISION))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
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
