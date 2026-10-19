package io.github.matejkadlec.realmforge.spell;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The resource pools in the {@code realmforge:combat} attachment (architecture 7, 11.5). Each resource type says how
 * big a holder's pool is and what it regains every second ({@link ResourceType}): mana is Max Mana big, starts full the
 * first time its maximum is above 0 (a new Mage, a mana item) and regains Mana Regeneration every second (Matěj,
 * 2026-10-14); rage is 100 big, starts empty, fills from hits and decays out of combat ({@link Rage}). Logical server
 * only.
 */
public final class Resources {
    /** One second. */
    public static final int INTERVAL_TICKS = 20;

    private Resources() {
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel && player.tickCount % INTERVAL_TICKS == 0 && player.isAlive()) {
            tick(player);
        }
    }

    /**
     * One second of every pool of {@code holder}: a missing pool that starts full fills once its maximum is above 0,
     * and every pool regains its regeneration within 0 and its maximum ({@link SpellScaling#regenerate}). Syncs the
     * holder's combat state when a pool changed.
     */
    public static void tick(LivingEntity holder) {
        CombatState state = holder.getData(SpellAttachments.COMBAT);
        boolean changed = false;
        for (Holder.Reference<ResourceType> type : SpellRegistries.RESOURCE_TYPES.listElements().toList()) {
            ResourceKey<ResourceType> key = type.key();
            double maximum = type.value().maximum(holder);
            if (!state.hasResource(key)) {
                if (maximum > 0 && type.value().startsFull()) {
                    state.setResource(key, (float) maximum);
                    changed = true;
                }
                continue;
            }
            float current = state.resource(key);
            float next = (float) SpellScaling.regenerate(current, type.value().regeneration(holder), maximum);
            if (next != current) {
                state.setResource(key, next);
                changed = true;
            }
        }
        if (changed) {
            holder.syncData(SpellAttachments.COMBAT);
        }
    }

    /** The amount in {@code holder}'s pool of {@code type}; 0 without a pool. */
    public static float amount(LivingEntity holder, Holder<ResourceType> type) {
        return holder.getExistingData(SpellAttachments.COMBAT)
                .map(state -> state.resource(type.unwrapKey().orElseThrow()))
                .orElse(0.0F);
    }

    /**
     * Adds {@code amount} to {@code holder}'s pool of {@code type}, creating it, as {@link #set} does: within 0 and the
     * pool's maximum, synced to the holder. Returns the amount the pool now holds.
     */
    public static float add(LivingEntity holder, Holder<ResourceType> type, double amount) {
        return set(holder, type, amount(holder, type) + amount);
    }

    /**
     * Sets the amount in {@code holder}'s pool of {@code type}, within 0 and the pool's maximum (a resource without a
     * maximum keeps the amount as given), and syncs it to the holder. Returns the amount set.
     */
    public static float set(LivingEntity holder, Holder<ResourceType> type, double amount) {
        double maximum = type.value().maximum(holder);
        float value = (float) (maximum > 0 ? Mth.clamp(amount, 0, maximum) : Math.max(0, amount));
        holder.getData(SpellAttachments.COMBAT).setResource(type.unwrapKey().orElseThrow(), value);
        holder.syncData(SpellAttachments.COMBAT);
        return value;
    }
}
