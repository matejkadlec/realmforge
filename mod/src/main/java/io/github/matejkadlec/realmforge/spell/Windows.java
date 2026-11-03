package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatConfig;
import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The windows of spells usable only for a while after something happens (RLM-540; architecture 11.2): Overpower after
 * the player blocks a hit, Victory Rush after they kill an enemy that is not gray, and whatever a passive's proc opens
 * (Taste for Blood). A window opens for the player's known spells that name its opener, for the spell's
 * {@link SpellWindow#durationTicks}; {@link SpellCaster} refuses a closed window's spell and closes the window it
 * uses. Windows live in the {@code combat} attachment, synced to the player for the spell bar's glow, and are transient.
 *
 * <p>A spell castable only on a target below a share of its health (Execute) lights up the same way while the player's
 * current target (the enemy they last hit or aimed a spell at, {@link CombatState#currentTarget}) is below it (Matěj's
 * playtest of 2026-11-03): every {@value #TARGET_CHECK_TICKS} ticks the server opens its entry in the windows for
 * {@value #TARGET_WINDOW_TICKS} ticks, renewing it before it runs out, and closes it once the target is no longer below
 * the share. That entry only shows on the bar: the cast checks the health of the target it aims at, not a window.
 * Logical server only.
 */
public final class Windows {
    /** A kill opens a window only when the victim's level is at least the player's less this: not a gray enemy. */
    public static final int GRAY_LEVELS = 3;
    /** How often the server checks a player's current target for a spell with a target health condition. */
    static final int TARGET_CHECK_TICKS = 5;
    /** How long one check keeps that spell lit; renewed while less than half of it is left. */
    static final int TARGET_WINDOW_TICKS = 40;

    private Windows() {
    }

    /**
     * Opens {@code spell}'s window for {@code player}, if they know it and it has one; returns whether it opened. An open
     * window stays open at least as long as it was.
     */
    public static boolean open(Player player, ResourceKey<Spell> spell) {
        if (player.getData(SpellAttachments.SPELLBOOK).level(spell).isEmpty()) {
            return false;
        }
        Optional<SpellWindow> window = Spells.get(player.registryAccess(), spell)
                .flatMap(holder -> holder.value().conditions().window());
        if (window.isEmpty()) {
            return false;
        }
        CombatState state = player.getData(SpellAttachments.COMBAT);
        state.openWindow(spell, player.level().getGameTime() + window.get().durationTicks());
        player.syncData(SpellAttachments.COMBAT);
        return true;
    }

    /** Whether a kill of {@code victim} by {@code player} opens a kill window: an enemy at most 3 levels below them. */
    public static boolean worthy(Player player, LivingEntity victim) {
        return victim != player && !Targets.allied(player, victim)
                && DamagePipeline.level(victim) >= DamagePipeline.level(player) - GRAY_LEVELS;
    }

    static void onBlock(CombatEvent.Block event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            openAll(player, SpellWindow.Opener.BLOCK);
        }
    }

    static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && worthy(player, event.getEntity())) {
            openAll(player, SpellWindow.Opener.KILL);
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % TARGET_CHECK_TICKS == 0) {
            event.getServer().getPlayerList().getPlayers().forEach(Windows::checkTargetHealth);
        }
    }

    /**
     * Lights the spells {@code player} knows that need a target below a share of its health while their current target
     * is below it, and puts them out once it is not.
     */
    public static void checkTargetHealth(ServerPlayer player) {
        CombatState state = player.getData(SpellAttachments.COMBAT);
        long now = player.level().getGameTime();
        Optional<LivingEntity> target = state.currentTarget(now, CombatConfig.inCombatTicks())
                .map(uuid -> player.level().getEntity(uuid)).filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).filter(entity -> entity.isAlive() && entity.getMaxHealth() > 0
                        && !Targets.allied(player, entity));
        boolean changed = false;
        for (ResourceKey<Spell> key : player.getData(SpellAttachments.SPELLBOOK).spells().keySet()) {
            Optional<Double> below = Spells.get(player.registryAccess(), key).map(Holder::value)
                    .filter(spell -> spell.conditions().window().isEmpty())
                    .flatMap(spell -> spell.conditions().targetHealthBelow());
            if (below.isEmpty()) {
                continue;
            }
            boolean low = target.filter(entity -> entity.getHealth() / entity.getMaxHealth() < below.get()).isPresent();
            long end = state.windows().getOrDefault(key, 0L);
            if (low && end - now < TARGET_WINDOW_TICKS / 2) {
                state.openWindow(key, now + TARGET_WINDOW_TICKS);
                changed = true;
            } else if (!low && end > now) {
                state.closeWindow(key);
                changed = true;
            }
        }
        if (changed) {
            player.syncData(SpellAttachments.COMBAT);
        }
    }

    /** Opens the window of every spell {@code player} knows that {@code opener} opens. */
    private static void openAll(ServerPlayer player, SpellWindow.Opener opener) {
        for (Map.Entry<ResourceKey<Spell>, Integer> known : player.getData(SpellAttachments.SPELLBOOK).spells()
                .entrySet()) {
            Spells.get(player.registryAccess(), known.getKey()).map(Holder::value)
                    .flatMap(spell -> spell.conditions().window())
                    .filter(window -> window.openedBy().contains(opener))
                    .ifPresent(window -> open(player, known.getKey()));
        }
    }
}
