package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class CombatStateTest {
    private static final ResourceKey<Spell> FIREBOLT =
            ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("firebolt"));
    private static final ResourceKey<Spell> BLINK = ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("blink"));
    private static final ResourceKey<ResourceType> MANA =
            ResourceKey.create(SpellRegistries.RESOURCE_TYPE, Realmforge.id("mana"));
    private static final ResourceKey<ResourceType> RAGE =
            ResourceKey.create(SpellRegistries.RESOURCE_TYPE, Realmforge.id("rage"));
    private static final Cast CAST = new Cast(FIREBOLT, 12_000L, 30);

    @Test
    void codecRoundTripsWithoutTheCurrentCast() {
        CombatState state = sample();
        CombatState saved = RoundTrip.nbt(CombatState.CODEC, state);
        assertEquals(Optional.empty(), saved.currentCast(), "the current cast is never saved");
        state.clearCast();
        assertEquals(state, saved);
        assertEquals(state, RoundTrip.json(CombatState.CODEC, state));
    }

    @Test
    void streamCodecRoundTripsWithTheCurrentCast() {
        CombatState state = sample();
        CombatState synced = RoundTrip.stream(CombatState.STREAM_CODEC, state);
        assertEquals(state, synced);
        assertEquals(Optional.of(CAST), synced.currentCast());
        assertEquals(new CombatState(), RoundTrip.stream(CombatState.STREAM_CODEC, new CombatState()));
    }

    @Test
    void emptySaveIsAFreshState() {
        assertEquals(new CombatState(), RoundTrip.parseSnbt(CombatState.CODEC, "{}").getOrThrow());
    }

    @Test
    void savedShapeReads() {
        CombatState read = RoundTrip.parseSnbt(CombatState.CODEC, "{resources: {\"realmforge:mana\": 40.5f}, "
                + "cooldowns: {\"realmforge:blink\": 300L}, global_cooldown_end: 120L}").getOrThrow();
        assertEquals(Map.of(MANA, 40.5F), read.resources());
        assertEquals(Map.of(BLINK, 300L), read.cooldowns());
        assertEquals(120L, read.globalCooldownEnd());
    }

    @Test
    void invalidSavesAreRefused() {
        assertTrue(RoundTrip.parseSnbt(CombatState.CODEC, "{resources: {\"realmforge:mana\": -1.0f}}").isError(),
                "negative resource");
        assertTrue(RoundTrip.parseSnbt(CombatState.CODEC, "{cooldowns: {\"realmforge:blink\": -5L}}").isError(),
                "a cooldown ending before time began");
        assertTrue(RoundTrip.parseSnbt(CombatState.CODEC, "{global_cooldown_end: -1L}").isError(), "negative GCD");
    }

    @Test
    void cooldownsRunUntilTheirEndTime() {
        CombatState state = new CombatState();
        state.setCooldownEnd(BLINK, 300L);
        assertTrue(state.isOnCooldown(BLINK, 299L));
        assertFalse(state.isOnCooldown(BLINK, 300L), "the cooldown is over at its end time");
        assertFalse(state.isOnCooldown(FIREBOLT, 0L), "a spell never cast has no cooldown");
    }

    @Test
    void theGlobalCooldownRunsUntilItsEndTime() {
        CombatState state = new CombatState();
        assertFalse(state.isOnGlobalCooldown(0L), "it never ran");
        state.setGlobalCooldownEnd(120L);
        assertTrue(state.isOnGlobalCooldown(119L));
        assertFalse(state.isOnGlobalCooldown(120L));
    }

    @Test
    void clearingCooldownsEndsTheGlobalOneToo() {
        CombatState state = sample();
        state.clearCooldowns();
        assertEquals(Map.of(), state.cooldowns());
        assertEquals(0L, state.globalCooldownEnd());
    }

    @Test
    void eachResourceTypeHasItsOwnPool() {
        CombatState state = new CombatState();
        assertFalse(state.hasResource(MANA), "no pool until one is filled");
        assertEquals(0.0F, state.resource(MANA), "a missing pool reads as empty");
        state.setResource(MANA, 80.0F);
        state.setResource(RAGE, 0.0F);
        assertTrue(state.hasResource(RAGE), "an empty pool is still a pool");
        assertEquals(Map.of(MANA, 80.0F, RAGE, 0.0F), state.resources());
    }

    @Test
    void endedCooldownsAreCleared() {
        CombatState state = new CombatState();
        state.setCooldownEnd(BLINK, 300L);
        state.setCooldownEnd(FIREBOLT, 500L);
        state.clearEndedCooldowns(300L);
        assertEquals(Map.of(FIREBOLT, 500L), state.cooldowns());
    }

    @Test
    void castsStartAndClear() {
        CombatState state = new CombatState();
        state.startCast(CAST);
        assertEquals(Optional.of(CAST), state.currentCast());
        assertEquals(12_030L, CAST.endGameTime());
        state.clearCast();
        assertEquals(Optional.empty(), state.currentCast());
    }

    @Test
    void invalidValuesAreRefused() {
        CombatState state = new CombatState();
        assertThrows(IllegalArgumentException.class, () -> state.setResource(MANA, -0.5F));
        assertThrows(IllegalArgumentException.class, () -> state.setResource(MANA, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.setResource(MANA, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> state.setCooldownEnd(BLINK, -1L));
        assertThrows(IllegalArgumentException.class, () -> state.setGlobalCooldownEnd(-1L));
        assertThrows(IllegalArgumentException.class, () -> new Cast(FIREBOLT, 0L, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cast(FIREBOLT, -1L, 10));
    }

    /** {@code cooldowns()} and {@code resources()} are snapshots: changing them bypasses neither checks nor sync. */
    @Test
    void cooldownsAndPoolsAreReadOnly() {
        CombatState state = sample();
        assertThrows(UnsupportedOperationException.class, () -> state.cooldowns().put(BLINK, 1L));
        assertThrows(UnsupportedOperationException.class, () -> state.resources().put(RAGE, 1.0F));
    }

    /** Each holder gets its own fresh state: the default supplier must never hand out a shared instance. */
    @Test
    void freshStatesAreIndependent() {
        CombatState first = new CombatState();
        CombatState second = new CombatState();
        first.setResource(MANA, 10.0F);
        assertNotEquals(first, second);
    }

    private static CombatState sample() {
        CombatState state = new CombatState();
        state.setResource(MANA, 73.25F);
        state.setResource(RAGE, 12.0F);
        state.setCooldownEnd(BLINK, 9_000L);
        state.setCooldownEnd(FIREBOLT, 12_100L);
        state.setGlobalCooldownEnd(12_030L);
        state.startCast(CAST);
        return state;
    }
}
