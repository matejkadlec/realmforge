package io.github.matejkadlec.realmforge.stats;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A source of saved-but-recomputed stat modifiers that a feature above {@code stats} provides (architecture 4.4: lower
 * asks higher through provider interfaces), such as race and class from {@code character}. {@link CharacterStats}
 * applies every source before the stat rules, so a primary stat a source raises gives its derived bonuses in the same
 * recompute.
 */
public interface StatSource {
    /** Whether the modifier id is one this source writes, so a recompute may replace or remove it. */
    boolean owns(Identifier id);

    /** The modifiers the player should have from this source now. Ids must be ones {@link #owns} accepts. */
    List<StatBonus> bonuses(ServerPlayer player);
}
