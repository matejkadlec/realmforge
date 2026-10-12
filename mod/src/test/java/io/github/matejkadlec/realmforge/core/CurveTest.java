package io.github.matejkadlec.realmforge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.core.Curve.Point;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurveTest {
    private static final Curve CURVE = new Curve(Optional.empty(),
            List.of(new Point(1, 0.0), new Point(10, 450.0), new Point(20, 1200.5)));

    @Test
    void valueIsInterpolatedBetweenPoints() {
        assertEquals(225.0, CURVE.valueAt(5.5), 1e-9);
        assertEquals(825.25, CURVE.valueAt(15), 1e-9);
    }

    @Test
    void valueAtAPointIsThatPointsValue() {
        assertEquals(0.0, CURVE.valueAt(1));
        assertEquals(450.0, CURVE.valueAt(10));
        assertEquals(1200.5, CURVE.valueAt(20));
    }

    @Test
    void valueIsConstantBeyondTheEnds() {
        assertEquals(0.0, CURVE.valueAt(0));
        assertEquals(0.0, CURVE.valueAt(-5));
        assertEquals(1200.5, CURVE.valueAt(21));
        assertEquals(1200.5, CURVE.valueAt(1000));
    }

    @Test
    void singlePointCurveIsConstant() {
        Curve flat = new Curve(Optional.empty(), List.of(new Point(5, 3.0)));
        assertEquals(3.0, flat.valueAt(0));
        assertEquals(3.0, flat.valueAt(5));
        assertEquals(3.0, flat.valueAt(50));
    }

    @Test
    void codecReadsTheGeneratedFormat() {
        Curve curve = decode("""
                {"name": "curve.realmforge.x.name", "points": [{"level": 1, "value": 0}, {"level": 3, "value": 2.5}]}
                """).getOrThrow();
        assertEquals(Optional.of("curve.realmforge.x.name"), curve.name());
        assertEquals(List.of(new Point(1, 0.0), new Point(3, 2.5)), curve.points());
    }

    @Test
    void nameIsOptional() {
        Curve curve = decode("{\"points\": [{\"level\": 0, \"value\": 1}]}").getOrThrow();
        assertEquals(Optional.empty(), curve.name());
    }

    @Test
    void codecRejectsLevelsOutOfOrder() {
        DataResult<Curve> result = decode(
                "{\"points\": [{\"level\": 5, \"value\": 1}, {\"level\": 5, \"value\": 2}]}");
        assertTrue(result.isError(), "equal levels must be rejected");
        assertTrue(decode("{\"points\": [{\"level\": 5, \"value\": 1}, {\"level\": 2, \"value\": 2}]}").isError(),
                "decreasing levels must be rejected");
    }

    @Test
    void codecRejectsEmptyAndNegativeLevels() {
        assertTrue(decode("{\"points\": []}").isError(), "a curve needs a point");
        assertTrue(decode("{\"points\": [{\"level\": -1, \"value\": 1}]}").isError(), "levels are non-negative");
    }

    @Test
    void codecRoundTrips() {
        JsonElement json = Curve.CODEC.encodeStart(JsonOps.INSTANCE, CURVE).getOrThrow();
        assertEquals(CURVE, Curve.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    /** The file {@code rf gen} writes for {@code content/curves/test_curve.yaml} decodes and interpolates. */
    @Test
    void generatedTestCurveDecodes() throws IOException {
        String path = "/data/realmforge/realmforge/curve/test_curve.json";
        try (InputStream stream = CurveTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, path + " is missing; run `uv run rf gen`");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Curve curve = Curve.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(Optional.of("curve.realmforge.test_curve.name"), curve.name());
            assertEquals(225.0, curve.valueAt(5.5), 1e-9);
        }
    }

    private static DataResult<Curve> decode(String json) {
        return Curve.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
