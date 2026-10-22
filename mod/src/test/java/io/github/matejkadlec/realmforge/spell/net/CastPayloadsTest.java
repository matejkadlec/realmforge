package io.github.matejkadlec.realmforge.spell.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.Optional;
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

    @Test
    void castSpellRoundTripsWithAndWithoutATarget() {
        for (CastSpellPayload payload : new CastSpellPayload[] {new CastSpellPayload(DRAIN, Optional.of(3), true),
                new CastSpellPayload(DRAIN, Optional.empty(), false)}) {
            assertEquals(payload, RoundTrip.stream(CastSpellPayload.STREAM_CODEC, payload));
        }
    }

    @Test
    void castRefusedRoundTripsEveryFailure() {
        for (CastFailure failure : CastFailure.values()) {
            CastRefusedPayload payload = new CastRefusedPayload(DRAIN, failure);
            assertEquals(payload, RoundTrip.stream(CastRefusedPayload.STREAM_CODEC, payload));
        }
    }

    @Test
    void setSpellBarSlotRoundTripsAPlacementAndAnEmptying() {
        for (SetSpellBarSlotPayload payload : new SetSpellBarSlotPayload[] {
                new SetSpellBarSlotPayload(5, Optional.of(DRAIN)), new SetSpellBarSlotPayload(0, Optional.empty())}) {
            assertEquals(payload, RoundTrip.stream(SetSpellBarSlotPayload.STREAM_CODEC, payload));
        }
    }
}
