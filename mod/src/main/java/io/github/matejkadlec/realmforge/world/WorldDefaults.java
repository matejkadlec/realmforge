package io.github.matejkadlec.realmforge.world;

import java.util.List;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The vision's gamerules, set once when a world is created (architecture 19.1). Players may change them afterwards,
 * and worlds that existed before Realmforge keep theirs.
 *
 * <p>The signal is {@link LevelEvent.CreateSpawnPosition}: {@code MinecraftServer#createLevels} posts it only while it
 * sets up a world whose level data is not initialized yet, which no flag in our own saved data could tell apart from
 * an existing world that Realmforge was added to. It is listened to, never cancelled: cancelling skips vanilla's
 * spawn search.
 */
public final class WorldDefaults {
    /** A boolean gamerule and the value a new world starts with. */
    public record Rule(GameRules.Key<GameRules.BooleanValue> key, boolean value) {
    }

    /** Every default: keep the inventory and respawn at once; explosions drop every block they destroy. */
    public static final List<Rule> RULES = List.of(
            new Rule(GameRules.RULE_KEEPINVENTORY, true),
            new Rule(GameRules.RULE_DO_IMMEDIATE_RESPAWN, true),
            new Rule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY, false),
            new Rule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY, false),
            new Rule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY, false));

    private WorldDefaults() {
    }

    /** Applies {@link #RULES} to a world's gamerules. */
    public static void apply(GameRules rules) {
        // No server: the world is still being created, so there is no player to tell about the change. A player who
        // joins later gets doImmediateRespawn with the login packets.
        for (Rule rule : RULES) {
            rules.getRule(rule.key()).set(rule.value(), null);
        }
    }

    static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        apply(event.getSettings().getGameRules());
    }
}
