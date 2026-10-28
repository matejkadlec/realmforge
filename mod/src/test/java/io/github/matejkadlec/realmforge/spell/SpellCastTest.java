package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The spell format's {@code cast} (C3a-2): instant, cast time or channel, in the shape {@code rf gen} writes, with the
 * kept target, the sound and the formed projectile of W3 (RLM-33, RLM-405).
 */
class SpellCastTest {
    /** The Hewn Chanter's Stoneform as {@code rf gen} writes it: every field W3 added, set. */
    private static final SpellCast STONEFORM = new SpellCast(SpellCast.Type.CAST_TIME, 30, 0, false, false, true,
            Optional.of(0.5), Optional.of(Identifier.parse("realmforge:spell.stoneform.form")), true);

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
        assertEquals(STONEFORM, parse("{\"castable_while_moving\": false, \"lock_on\": false, \"keep_sight\": true, "
                + "\"keep_range\": 0.5, \"sound\": \"realmforge:spell.stoneform.form\", \"type\": \"cast_time\", "
                + "\"duration_ticks\": 30, \"forms_projectile\": true}").getOrThrow());
    }

    @Test
    void roundTrips() {
        for (SpellCast cast : new SpellCast[] {SpellCast.INSTANT, SpellCast.castTime(25, true, false),
                SpellCast.channel(45, 15, false, true), STONEFORM}) {
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
    void refusesKeptTargetsSoundsAndFormingWhereTheyDoNotFit() {
        assertTrue(parse("{\"type\": \"cast_time\", \"duration_ticks\": 20, \"lock_on\": true, \"keep_sight\": true}")
                .isError(), "a locked-on cast that keeps sight");
        assertTrue(parse("{\"type\": \"cast_time\", \"duration_ticks\": 20, \"lock_on\": true, \"keep_range\": 0.5}")
                .isError(), "a locked-on cast that keeps range");
        assertTrue(parse("{\"type\": \"cast_time\", \"duration_ticks\": 20, \"keep_range\": 0}").isError(),
                "a kept range of no share of the cast");
        assertTrue(parse("{\"type\": \"channel\", \"duration_ticks\": 60, \"interval_ticks\": 20, "
                + "\"forms_projectile\": true}").isError(), "a channel that forms a projectile");
        assertTrue(parse("{\"type\": \"instant\", \"sound\": \"realmforge:spell.stoneform.form\"}").isError(),
                "an instant cast with a sound");
        assertTrue(parse("{\"type\": \"cast_time\", \"duration_ticks\": 20, \"keep_sight\": true, "
                + "\"keep_range\": 1.0}").isSuccess(), "a cast time keeps sight and its whole range");
    }

    @Test
    void onlyAnInstantCastHasNoCurrentCast() {
        assertTrue(SpellCast.INSTANT.instant());
        assertFalse(SpellCast.castTime(1, false, false).instant());
        assertEquals(1, SpellCast.castTime(40, false, false).tickCount());
        assertEquals(4, SpellCast.channel(80, 20, false, false).tickCount());
    }
}
