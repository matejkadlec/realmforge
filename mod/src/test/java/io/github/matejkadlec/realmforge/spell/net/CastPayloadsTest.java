package io.github.matejkadlec.realmforge.spell.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class CastPayloadsTest {
    private static final ResourceKey<Spell> DRAIN = ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("drain"));

    @Test
    void castStartedRoundTripsWithItsStartTime() {
        CastStartedPayload payload = new CastStartedPayload(42, new Cast(DRAIN, 123_456L, 60, true));
        assertEquals(payload, RoundTrip.stream(CastStartedPayload.STREAM_CODEC, payload));
    }

    @Test
    void castEndedRoundTripsEveryEnd() {
        for (CastEnd end : CastEnd.values()) {
            CastEndedPayload payload = new CastEndedPayload(7, DRAIN, end);
            assertEquals(payload, RoundTrip.stream(CastEndedPayload.STREAM_CODEC, payload));
        }
    }
}
