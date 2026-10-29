package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Health regeneration (architecture 9.1; Matěj, 2026-10-14): a player with X {@code realmforge:health_regeneration}
 * heals X health every 5 seconds, WotLK's "health per 5" (Matěj, 2026-10-15, RLM-170: every second was too strong), on
 * top of vanilla's food regeneration, never beyond max health, so it never overheals. Logical server only.
 */
public final class HealthRegeneration {
    /** Five seconds. */
    public static final int INTERVAL_TICKS = 100;

    private HealthRegeneration() {
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel) || player.tickCount % INTERVAL_TICKS != 0 || !player.isAlive()) {
            return;
        }
        double heal = DamageCalculator.regeneration(DamagePipeline.stat(player, GeneratedStats.HEALTH_REGENERATION),
                player.getHealth(), player.getMaxHealth());
        if (heal > 0) {
            player.heal((float) heal);
        }
    }
}
