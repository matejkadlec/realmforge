package io.github.matejkadlec.realmforge.mob;

import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.RecomputedModifiers;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.world.DimensionRules;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/**
 * Mob levels and the scaling they bring (architecture 14.1). Every hostile mob ({@link #getsLevel}) gets its level
 * from the rules of its dimension ({@link DimensionRules}) and the {@link MobScaling} of that level, and no mob carries
 * a level without its scaling:
 * <ul>
 *   <li>{@code FinalizeSpawnEvent} levels a spawning mob and marks it ({@link MobAttachments#PENDING_REFILL}); when it
 *   joins the level, its health is refilled to its maximum, as vanilla's {@code Mob#finalizeSpawn} runs after the
 *   event and may still raise that maximum (the leader zombie bonus).</li>
 *   <li>A slime's split children take the parent's level ({@code MobSplitEvent}).</li>
 *   <li>A mob that joins without a level, because its spawn skipped {@code finalizeSpawn} (silverfish from infested
 *   blocks, {@code GameTestHelper#spawn}) or it was saved before mob levels existed, is leveled from the rules then.</li>
 *   <li>A converted mob keeps the level NeoForge copies onto it ({@code LivingConversionEvent.Post}) and gets that
 *   level's scaling; a mob converted into one that gets no level (a cured zombie villager) loses it.</li>
 *   <li>A mob loaded from disk gets its scaling again from its level and the current curves, so a changed curve
 *   reaches old mobs too.</li>
 * </ul>
 * Rescaling keeps a mob's share of health. The damage multiplier applies to every hit a leveled mob causes.
 */
public final class MobLevels {
    private MobLevels() {
    }

    /**
     * Whether a mob gets a level: every hostile mob (vanilla's {@link Enemy} marker, which our mobs will carry too).
     * Bosses take theirs from their definition (W5).
     */
    public static boolean getsLevel(Entity entity) {
        return entity instanceof Mob && entity instanceof Enemy;
    }

    /** The entity's level, or empty when it has none: read without creating the attachment. */
    public static OptionalInt level(LivingEntity entity) {
        return entity.getExistingData(StatsAttachments.LEVEL).map(level -> OptionalInt.of(level.value()))
                .orElse(OptionalInt.empty());
    }

    /**
     * The level the rules of {@code dimension} give a mob at a position, with its jitter drawn from {@code random}, or
     * empty when the dimension has no mob level rule.
     */
    public static OptionalInt levelAt(RegistryAccess registries, ResourceKey<Level> dimension, double x, double y,
            double z, RandomSource random) {
        return DimensionRules.mobLevels(registries, dimension)
                .map(rule -> OptionalInt.of(rule.levelAt(x, y, z, random)))
                .orElse(OptionalInt.empty());
    }

    /**
     * Sets the mob's level and applies the scaling it brings, keeping the mob's share of health. A mob that gets no
     * level ({@link #getsLevel}) keeps the number but no scaling.
     */
    public static void setLevel(Mob mob, int level) {
        mob.setData(StatsAttachments.LEVEL, new EntityLevel(level));
        applyScaling(mob, level);
    }

    /**
     * Makes the mob's health and armor modifiers those of {@code level} (none for a mob that gets no level), keeping
     * its share of health when its maximum changes.
     */
    public static void applyScaling(Mob mob, int level) {
        List<StatBonus> modifiers = getsLevel(mob)
                ? MobScaling.at(mob.registryAccess(), level).modifiers()
                : List.of();
        float maxBefore = mob.getMaxHealth();
        float share = maxBefore > 0 ? mob.getHealth() / maxBefore : 1;
        if (RecomputedModifiers.replace(mob.getAttributes(), MobScaling::owns, modifiers)) {
            mob.setHealth(share * mob.getMaxHealth());
        }
    }

    /** Levels a spawning mob from the rules at its spawn position; its health is refilled when it joins. */
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (!getsLevel(mob)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        OptionalInt rolled = levelAt(event.getLevel().registryAccess(), level.dimension(), event.getX(),
                event.getY(), event.getZ(), mob.getRandom());
        if (rolled.isPresent()) {
            setLevel(mob, rolled.getAsInt());
            mob.setData(MobAttachments.PENDING_REFILL, Boolean.TRUE);
        }
    }

    /**
     * Refills a mob leveled in its spawn, levels one that joins without a level (its share of health kept), and
     * rescales one loaded from disk. A mob moving between dimensions keeps what it has.
     */
    static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !getsLevel(mob)) {
            return;
        }
        if (mob.hasData(MobAttachments.PENDING_REFILL)) {
            mob.removeData(MobAttachments.PENDING_REFILL);
            mob.setHealth(mob.getMaxHealth());
            return;
        }
        OptionalInt level = level(mob);
        if (level.isEmpty()) {
            levelAt(event.getLevel().registryAccess(), event.getLevel().dimension(), mob.getX(), mob.getY(),
                    mob.getZ(), mob.getRandom()).ifPresent(rolled -> setLevel(mob, rolled));
        } else if (event.loadedFromDisk()) {
            applyScaling(mob, level.getAsInt());
        }
    }

    /** A slime's or magma cube's children take the parent's level and its scaling. */
    static void onMobSplit(MobSplitEvent event) {
        OptionalInt level = level(event.getParent());
        if (level.isEmpty()) {
            return;
        }
        for (Mob child : event.getChildren()) {
            if (getsLevel(child)) {
                setLevel(child, level.getAsInt());
            }
        }
    }

    /**
     * After NeoForge copied the old mob's level onto the new one ({@code AttachmentInternals}, at normal priority): a
     * new mob that gets levels gets that level's scaling, one that does not loses the level. A new mob that holds no
     * level is left to {@link #onJoinLevel}. Vanilla posts the event before it adds the new mob, so the sync reaches
     * nobody yet and the players who start tracking it get the level then; it matters for a caller that adds first.
     */
    static void onConversion(LivingConversionEvent.Post event) {
        if (event.getOutcome().level().isClientSide() || !(event.getOutcome() instanceof Mob mob)) {
            return;
        }
        Optional<EntityLevel> level = mob.getExistingData(StatsAttachments.LEVEL);
        if (level.isEmpty()) {
            return;
        }
        if (getsLevel(mob)) {
            applyScaling(mob, level.get().value());
            mob.syncData(StatsAttachments.LEVEL);
        } else {
            mob.removeData(StatsAttachments.LEVEL);
            applyScaling(mob, EntityLevel.MIN);
        }
    }

    /** Every hit a leveled mob causes, melee, arrows and explosions alike, deals its level's damage multiple. */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob attacker) || !getsLevel(attacker)
                || !(attacker.level() instanceof ServerLevel)) {
            return;
        }
        OptionalInt level = level(attacker);
        if (level.isEmpty()) {
            return;
        }
        double damage = MobScaling.at(attacker.registryAccess(), level.getAsInt()).damage();
        if (damage != 1) {
            event.setAmount((float) (event.getAmount() * damage));
        }
    }
}
