package io.github.matejkadlec.realmforge.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Applies the stat sources and the stat rules to players (architecture 8.2, 8.3): first the modifiers of every
 * {@link StatSource} (race and class), then, from the primary stats those leave, each rule's modifiers that
 * {@link StatRules} gives, all as saved-but-recomputed modifiers ({@link RecomputedModifiers}); and keeps health right
 * across logins and respawns.
 *
 * <p>When it recomputes, always at the end of a player tick ({@code PlayerTickEvent.Post}), after vanilla applied the
 * tick's equipment changes:
 * <ul>
 *   <li>on the first tick of every new player entity, after a login and after a respawn. Equipment modifiers are
 *   transient and arrive only with that tick ({@code LivingEntity#detectEquipmentUpdates}), so an earlier recompute
 *   would read lower stats than the saved modifiers were computed with;</li>
 *   <li>at the end of a tick in which the player's equipment or effects changed, or {@link #markDirty} was called
 *   (level, talents);</li>
 *   <li>every {@value #CHECK_INTERVAL} ticks, when a primary stat or the player's level differs from the value the
 *   last recompute used (curios, other mods, commands such as {@code /qa level}).</li>
 * </ul>
 *
 * <p>Health: loading a player applies the saved modifiers before its health, so health above the base maximum
 * survives a relog by itself. A respawn after death copies only base values and sets health before any of our
 * modifiers exist ({@code ServerPlayer#restoreFrom}), so the first-tick recompute refills health to the new maximum.
 * Leaving the End is no death: vanilla copies the permanent modifiers and the health, but clamps the health while the
 * transient equipment modifiers are still missing (another mod's gear with max health), so the health the old entity
 * had is restored after the first-tick recompute.
 */
public final class CharacterStats {
    /** Ticks between the checks for primary stats that changed without an event. */
    public static final int CHECK_INTERVAL = 10;

    /** The registered sources, applied in registration order. */
    private static final List<StatSource> SOURCES = new CopyOnWriteArrayList<>();

    private CharacterStats() {
    }

    /** Adds a source of modifiers that every recompute applies. Called once per source at mod construction. */
    public static void addSource(StatSource source) {
        SOURCES.add(source);
    }

    /** Recomputes the player's stat modifiers at the end of its next tick: call it after changing a stat source. */
    public static void markDirty(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            tracker(serverPlayer).dirty = true;
        }
    }

    /**
     * Recomputes the player's stat modifiers now: every source's, then every stat rule against the primary stats they
     * leave. Returns whether any modifier changed. The event hooks call it when needed; tests and commands may call it
     * directly.
     */
    public static boolean recompute(ServerPlayer player) {
        boolean changed = false;
        for (StatSource source : SOURCES) {
            changed |= RecomputedModifiers.replace(player.getAttributes(), source::owns, source.bonuses(player));
        }
        Registry<StatRule> rules = player.registryAccess().lookupOrThrow(StatsRegistries.STAT_RULE);
        List<StatBonus> wanted = new ArrayList<>();
        Map<Holder<Attribute>, Double> applied = new HashMap<>();
        for (Map.Entry<ResourceKey<StatRule>, StatRule> entry : rules.entrySet()) {
            StatRule rule = entry.getValue();
            double value = statValue(player, rule.stat());
            applied.put(rule.stat(), value);
            wanted.addAll(StatRules.bonuses(entry.getKey().identifier(), rule, value));
        }
        Tracker tracker = tracker(player);
        tracker.applied = applied;
        tracker.level = level(player);
        tracker.dirty = false;
        tracker.computed = true;
        return RecomputedModifiers.replace(player.getAttributes(), StatRules::isStatModifier, wanted) | changed;
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Tracker tracker = tracker(player);
        if (!tracker.computed) {
            recompute(player);
            applyHealthRule(player, tracker);
        } else if (tracker.dirty || player.tickCount % CHECK_INTERVAL == 0
                && (tracker.level != level(player) || primaryStatsChanged(player, tracker))) {
            recompute(player);
        }
    }

    /** Fired on a respawn's new entity, after vanilla copied the old one's base values (and more, from the End). */
    static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Tracker tracker = tracker(player);
            if (event.isWasDeath()) {
                tracker.refillHealth = true;
            } else {
                tracker.restoredHealth = event.getOriginal().getHealth();
            }
        }
    }

    static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    /** Effects can carry attribute modifiers, primary stats included: added, removed and expired. */
    static void onEffectChange(MobEffectEvent event) {
        if (event.getEntity() instanceof Player player) {
            markDirty(player);
        }
    }

    private static void applyHealthRule(ServerPlayer player, Tracker tracker) {
        if (tracker.refillHealth) {
            player.setHealth(player.getMaxHealth());
        } else if (!Float.isNaN(tracker.restoredHealth)) {
            player.setHealth(tracker.restoredHealth);
        }
        tracker.refillHealth = false;
        tracker.restoredHealth = Float.NaN;
    }

    private static boolean primaryStatsChanged(LivingEntity entity, Tracker tracker) {
        for (Map.Entry<Holder<Attribute>, Double> entry : tracker.applied.entrySet()) {
            if (statValue(entity, entry.getKey()) != entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private static int level(ServerPlayer player) {
        return player.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
    }

    private static double statValue(LivingEntity entity, Holder<Attribute> stat) {
        return entity.getAttributes().hasAttribute(stat) ? entity.getAttributeValue(stat) : 0;
    }

    private static Tracker tracker(ServerPlayer player) {
        return player.getData(StatsAttachments.STAT_TRACKER);
    }

    /**
     * The recompute state of one player entity, in the transient {@code realmforge:stat_tracker} attachment: every new
     * entity (a login, a respawn) starts with a fresh one, which is what makes its first tick recompute.
     */
    static final class Tracker {
        private boolean computed;
        private boolean dirty;
        private boolean refillHealth;
        private float restoredHealth = Float.NaN;
        private Map<Holder<Attribute>, Double> applied = Map.of();
        private int level;
    }
}
