package io.github.matejkadlec.realmforge.mob;

import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.RecomputedModifiers;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.world.DimensionRules;
import io.github.matejkadlec.realmforge.world.MobLevelRule;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/**
 * Mob levels and the scaling they bring (architecture 14.1). Every hostile mob ({@link #getsLevel}) gets its level
 * from the rules of its dimension ({@link DimensionRules}), which follow the nearest player ({@link #levelAt}) above
 * the floor of the realm or of a structure holding the mob ({@link #floorAt}), and the {@link MobScaling} of that
 * level, and no mob carries a level without its scaling. A mob keeps the level it got:
 * <ul>
 *   <li>{@code FinalizeSpawnEvent} levels a spawning mob and marks it ({@link MobAttachments#PENDING_REFILL}); when it
 *   joins the level, its health is refilled to its maximum, as vanilla's {@code Mob#finalizeSpawn} runs after the
 *   event and may still raise that maximum (the leader zombie bonus). A mob spawned with a new chunk is leveled at
 *   its join instead.</li>
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
     * The level the rules of the level's dimension give a mob spawning at a position, drawn from {@code random}, or
     * empty when the dimension has no mob level rule. It follows the nearest player who is not a spectator, at any
     * distance (RLM-240), never below the floor there ({@link #floorAt}); with no such player in the dimension, a mob
     * gets the floor.
     */
    public static OptionalInt levelAt(ServerLevel level, double x, double y, double z, RandomSource random) {
        Optional<MobLevelRule> rule = DimensionRules.mobLevels(level.registryAccess(), level.dimension());
        if (rule.isEmpty()) {
            return OptionalInt.empty();
        }
        int floor = floorAt(level, BlockPos.containing(x, y, z), rule.get());
        Player nearest = level.getNearestPlayer(x, y, z, -1, EntitySelector.NO_SPECTATORS);
        int playerLevel = nearest != null ? level(nearest).orElse(EntityLevel.MIN) : floor;
        return OptionalInt.of(rule.get().levelFor(playerLevel, floor, random));
    }

    /**
     * The floor of a mob that gets its level at {@code pos} (RLM-342): the highest of the rule's structure floors
     * whose structure has a piece there, or the realm's entry level. It reads the structure references as vanilla's
     * {@code StructureManager#getStructureWithPieceAt} does, but never loads a chunk: a mob can join while its chunk
     * is still loading ({@code EntityJoinLevelEvent}), where loading another one can deadlock. Nothing it reads is
     * missing then: a chunk on its way to full status holds the chunks within 8 at structure starts or later
     * ({@code ChunkLevel}), and a reference never points farther.
     */
    public static int floorAt(ServerLevel level, BlockPos pos, MobLevelRule rule) {
        int floor = rule.entryLevel();
        if (rule.structureFloors().isEmpty()) {
            return floor;
        }
        ServerChunkCache chunks = level.getChunkSource();
        ChunkAccess here = chunks.getChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.STRUCTURE_REFERENCES, false);
        if (here == null) {
            return floor;
        }
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, LongSet> references : here.getAllReferences().entrySet()) {
            int raised = rule.floorOf(structures.wrapAsHolder(references.getKey()));
            if (raised > floor && hasPieceAt(level, chunks, pos, references.getKey(), references.getValue())) {
                floor = raised;
            }
        }
        return floor;
    }

    /** Whether one of the starts of {@code structure} that {@code references} point to has a piece at {@code pos}. */
    private static boolean hasPieceAt(ServerLevel level, ServerChunkCache chunks, BlockPos pos, Structure structure,
            LongSet references) {
        for (long reference : references) {
            ChunkPos startPos = ChunkPos.unpack(reference);
            ChunkAccess startChunk = chunks.getChunk(startPos.x(), startPos.z(), ChunkStatus.STRUCTURE_STARTS, false);
            StructureStart start = startChunk != null ? startChunk.getStartForStructure(structure) : null;
            if (start != null && start.isValid() && level.structureManager().structureHasPieceAt(pos, start)) {
                return true;
            }
        }
        return false;
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

    /**
     * Levels a spawning mob from the rules and the player nearest its spawn position and marks it, so its health is
     * refilled when it joins. A mob spawned with a new chunk ({@code NaturalSpawner#spawnMobsForChunkGeneration}, structure pieces) is
     * finalized on a world generation thread, where setting the synced level would read the server's entity tracking
     * from that thread: it is only marked, and its join, on the server thread, levels it.
     */
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (!getsLevel(mob)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            mob.setData(MobAttachments.PENDING_REFILL, Boolean.TRUE);
            return;
        }
        OptionalInt rolled = levelAt(level, event.getX(), event.getY(), event.getZ(), mob.getRandom());
        if (rolled.isPresent()) {
            setLevel(mob, rolled.getAsInt());
            mob.setData(MobAttachments.PENDING_REFILL, Boolean.TRUE);
        }
    }

    /**
     * Levels a mob that joins without a level (keeping its share of health), rescales one loaded from disk, and
     * refills a marked one, leveled in its spawn. A mob moving between dimensions keeps what it has.
     */
    static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || !(event.getEntity() instanceof Mob mob)
                || !getsLevel(mob)) {
            return;
        }
        boolean refill = mob.hasData(MobAttachments.PENDING_REFILL);
        if (refill) {
            mob.removeData(MobAttachments.PENDING_REFILL);
        }
        OptionalInt level = level(mob);
        if (level.isEmpty()) {
            levelAt(serverLevel, mob.getX(), mob.getY(), mob.getZ(), mob.getRandom())
                    .ifPresent(rolled -> setLevel(mob, rolled));
        } else if (event.loadedFromDisk() && !refill) {
            applyScaling(mob, level.getAsInt());
        }
        if (refill) {
            mob.setHealth(mob.getMaxHealth());
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
