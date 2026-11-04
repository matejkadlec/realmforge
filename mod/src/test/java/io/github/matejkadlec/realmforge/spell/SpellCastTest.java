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
 * kept target, the sound and the formed projectile of W3 (RLM-33, RLM-405) and a mob's cast animation (W5b, RLM-491).
 */
class SpellCastTest {
    /** The Hewn Chanter's Stoneform as {@code rf gen} writes it: every field W3 added, set. */
    private static final SpellCast STONEFORM = new SpellCast(SpellCast.Type.CAST_TIME, 30, 0, false, false, true,
            Optional.of(0.5), Optional.of(Identifier.parse("realmforge:spell.stoneform.form")), true,
            SpellCast.Animation.RAISE, 0, SpellCast.DEFAULT_HIT_AT, SpellCast.Haste.SHORTENS);
    /** Astone's Quake as {@code rf gen} writes it: a channel that slams. */
    private static final SpellCast QUAKE = new SpellCast(SpellCast.Type.CHANNEL, 60, 15, false, false, false,
            Optional.empty(), Optional.of(Identifier.parse("realmforge:spell.astone_quake.rumble")), false,
            SpellCast.Animation.SLAM, 0, SpellCast.DEFAULT_HIT_AT, SpellCast.Haste.SHORTENS);

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
        assertEquals(QUAKE, parse("{\"castable_while_moving\": false, \"lock_on\": false, \"keep_sight\": false, "
                + "\"sound\": \"realmforge:spell.astone_quake.rumble\", \"animation\": \"slam\", "
                + "\"type\": \"channel\", \"duration_ticks\": 60, \"interval_ticks\": 15}").getOrThrow());
    }

    @Test
    void roundTrips() {
        for (SpellCast cast : new SpellCast[] {SpellCast.INSTANT, SpellCast.castTime(25, true, false),
                SpellCast.channel(45, 15, false, true), STONEFORM, QUAKE}) {
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
        assertTrue(parse("{\"type\": \"instant\", \"animation\": \"slam\"}").isError(),
                "an instant cast with an animation: nothing runs long enough to show it");
        assertTrue(parse("{\"type\": \"cast_time\", \"duration_ticks\": 20, \"animation\": \"dance\"}").isError(),
                "an unknown animation");
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

    /**
     * RLM-572: a channel whose ticks Cast Time Reduction multiplies keeps its duration and runs round(ticks × (1 +
     * reduction)) ticks, halves up (Blizzard's 8 waves in 8 s: 10 with 25 % and with 20 %, 9.5 rounds up, 9.49 down).
     */
    @Test
    void aChannelThatAddsTicksKeepsItsDuration() {
        SpellCast blizzard = parse("{\"type\": \"channel\", \"duration_ticks\": 160, \"interval_ticks\": 20, "
                + "\"cast_time_reduction\": \"adds_ticks\"}").getOrThrow();
        assertEquals(SpellCast.channel(160, 20, false, false, SpellCast.Haste.ADDS_TICKS), blizzard);
        assertEquals(8, blizzard.tickCountFor(0));
        assertEquals(10, blizzard.tickCountFor(0.25));
        assertEquals(10, blizzard.tickCountFor(0.2), "9.6 rounds to 10");
        assertEquals(10, blizzard.tickCountFor(0.1875), "9.5, a half, rounds up");
        assertEquals(9, blizzard.tickCountFor(0.18625), "9.49 rounds down");
        assertEquals(14, blizzard.tickCountFor(0.75), "Cast Time Reduction's cap");
        assertEquals(160, blizzard.durationFor(0.25), "its 8 s stay");

        SpellCast missiles = SpellCast.channel(30, 10, false, false);
        assertEquals(3, missiles.tickCountFor(0.25), "a channel that the reduction shortens keeps its ticks");
        assertEquals(24, missiles.durationFor(0.25), "and is shorter: 3 intervals of 8 ticks");
    }

    @Test
    void onlyAChannelAddsTicks() {
        assertTrue(parse("{\"type\": \"cast_time\", \"duration_ticks\": 40, \"cast_time_reduction\": "
                + "\"adds_ticks\"}").isError());
    }
}
