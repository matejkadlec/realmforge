package io.github.matejkadlec.realmforge.progression;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our custom statistics ({@code minecraft:custom}, architecture 16, Q1-2), which count repeatable things for quests:
 * FTB Quests' stat task reads them, retroactively. The pipeline names the same ones ({@code schema/quest.CUSTOM_STATS}),
 * so a quest's stat task may name them, and the Statistics screen shows each under its {@code stat.realmforge.<name>}
 * text ({@code content/texts/stat.yaml}).
 *
 * <p>{@link Stats#CUSTOM} keeps its statistics by the identity of the registered {@link Identifier}, so the registry
 * holds the very instance this class names, and {@link #createStats} makes their {@code Stat}s on both sides, as vanilla's
 * {@code Stats#makeCustomStat} does, so the Statistics screen lists them before the first is awarded.
 */
public final class ProgressionStats {
    private static final DeferredRegister<Identifier> STATS = DeferredRegister.create(Registries.CUSTOM_STAT,
            Realmforge.MOD_ID);

    /** {@code realmforge:dungeons_completed}: dungeon runs the player was in the party of when their goal was done. */
    public static final Identifier DUNGEONS_COMPLETED = Realmforge.id("dungeons_completed");

    static {
        STATS.register(DUNGEONS_COMPLETED.getPath(), () -> DUNGEONS_COMPLETED);
    }

    private ProgressionStats() {
    }

    static void register(IEventBus modBus) {
        STATS.register(modBus);
    }

    /** Makes the {@code Stat} of every statistic of ours; at common setup, once the registry holds them. */
    static void createStats() {
        Stats.CUSTOM.get(DUNGEONS_COMPLETED, StatFormatter.DEFAULT);
    }

    /** Counts a dungeon run the player completed. */
    public static void dungeonCompleted(ServerPlayer player) {
        player.awardStat(DUNGEONS_COMPLETED);
    }
}
