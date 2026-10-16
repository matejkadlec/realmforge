package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class CurrentCastsTest {
    private static final ResourceKey<Spell> BOLT = ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("bolt"));
    private static final ResourceKey<Spell> DRAIN = ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("drain"));
    private static final Cast CAST = new Cast(BOLT, 1_000L, 40, false);

    @Test
    void aCastLastsFromItsStartUntilItsEnd() {
        CurrentCasts casts = new CurrentCasts();
        casts.start(5, CAST);
        assertEquals(Optional.of(CAST), casts.get(5, 1_010L));
        assertEquals(Optional.empty(), casts.get(6, 1_010L), "another caster");
        casts.end(5, BOLT);
        assertEquals(Optional.empty(), casts.get(5, 1_011L));
    }

    @Test
    void theEndOfAnOlderCastLeavesANewerOne() {
        CurrentCasts casts = new CurrentCasts();
        casts.start(5, CAST);
        Cast drain = new Cast(DRAIN, 1_040L, 60, true);
        casts.start(5, drain);
        casts.end(5, BOLT);
        assertEquals(Optional.of(drain), casts.get(5, 1_041L));
    }

    /** A player who stops tracking a caster never hears of the end, so an old cast drops out by itself. */
    @Test
    void aCastWithoutItsEndDropsOutAfterTheGrace() {
        CurrentCasts casts = new CurrentCasts();
        casts.start(5, CAST);
        assertEquals(Optional.of(CAST), casts.get(5, 1_040L + CurrentCasts.GRACE_TICKS));
        assertEquals(Optional.empty(), casts.get(5, 1_041L + CurrentCasts.GRACE_TICKS));
        assertEquals(0, casts.size());
    }

    @Test
    void progressRunsFromZeroToOne() {
        assertEquals(0.0F, CurrentCasts.progress(CAST, 990.0));
        assertEquals(0.25F, CurrentCasts.progress(CAST, 1_010.0));
        assertEquals(1.0F, CurrentCasts.progress(CAST, 1_100.0));
    }
}
