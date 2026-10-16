package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import org.junit.jupiter.api.Test;

/** The spell format's {@code cast} (C3a-2): instant, cast time or channel, in the shape {@code rf gen} writes. */
class SpellCastTest {
    private static DataResult<SpellCast> parse(String json) {
        return SpellCast.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void readsTheGeneratedShapes() {
        assertEquals(SpellCast.INSTANT, parse("{\"type\": \"instant\"}").getOrThrow());
        assertEquals(SpellCast.castTime(40, false, false), parse(
                "{\"castable_while_moving\": false, \"lock_on\": false, \"type\": \"cast_time\", \"duration_ticks\": 40}")
                .getOrThrow());
        assertEquals(SpellCast.channel(60, 20, true, true), parse("{\"castable_while_moving\": true, \"lock_on\": true, "
                + "\"type\": \"channel\", \"duration_ticks\": 60, \"interval_ticks\": 20}").getOrThrow());
    }

    @Test
    void roundTrips() {
        for (SpellCast cast : new SpellCast[] {SpellCast.INSTANT, SpellCast.castTime(25, true, false),
                SpellCast.channel(45, 15, false, true)}) {
            assertEquals(cast, RoundTrip.json(SpellCast.CODEC, cast));
        }
    }

    @Test
    void refusesShapesThatDoNotFitTheirType() {
        assertTrue(parse("{\"type\": \"cast_time\"}").isError(), "a cast time without a duration");
        assertTrue(parse("{\"type\": \"instant\", \"duration_ticks\": 20}").isError(), "an instant with a duration");
        assertTrue(parse("{\"type\": \"instant\", \"lock_on\": true}").isError(), "an instant that locks on");
        assertTrue(parse("{\"type\": \"cast_time\", \"duration_ticks\": 20, \"interval_ticks\": 5}").isError(),
                "a cast time with an interval");
        assertTrue(parse("{\"type\": \"channel\", \"duration_ticks\": 60}").isError(), "a channel without interval");
        assertTrue(parse("{\"type\": \"channel\", \"duration_ticks\": 50, \"interval_ticks\": 20}").isError(),
                "a channel of two and a half intervals");
        assertTrue(parse("{\"type\": \"charge\"}").isError(), "an unknown type");
    }

    @Test
    void onlyAnInstantCastHasNoCurrentCast() {
        assertTrue(SpellCast.INSTANT.instant());
        assertFalse(SpellCast.castTime(1, false, false).instant());
        assertEquals(1, SpellCast.castTime(40, false, false).tickCount());
        assertEquals(4, SpellCast.channel(80, 20, false, false).tickCount());
    }
}
