package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.Map;
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

    /** What forms in every caster's hands is drawn from all of them at once (RLM-640). */
    @Test
    void everyCastInProgressIsListedByItsCaster() {
        CurrentCasts casts = new CurrentCasts();
        Cast drain = new Cast(DRAIN, 1_040L, 60, true);
        casts.start(5, CAST);
        casts.start(9, drain);
        assertEquals(Map.of(5, CAST, 9, drain), casts.all(1_041L));
        assertEquals(Map.of(9, drain), casts.all(1_041L + CurrentCasts.GRACE_TICKS),
                "a cast whose end never arrived is gone after the grace");
        assertEquals(1, casts.size());
        assertEquals(Map.of(), new CurrentCasts().all(0));
    }

    @Test
    void onlyACompletedCastTimeThatFormedAProjectileEndsInTheRelease() {
        assertTrue(CurrentCasts.releases(new Cast(BOLT, 100, 30, false), true), "a cast time that formed one");
        assertFalse(CurrentCasts.releases(new Cast(BOLT, 100, 30, false), false), "nothing formed");
        assertFalse(CurrentCasts.releases(new Cast(BOLT, 100, 30, true), true), "a channel");
        assertFalse(CurrentCasts.releases(new Cast(BOLT, 100, 10, false, true), true), "a swing");
        assertFalse(CurrentCasts.releases(Cast.instant(BOLT, 100, 10), true), "an instant spell's animation");
    }
}
