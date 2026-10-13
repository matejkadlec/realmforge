package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedCurveExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedCurveExpectations.CurveExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedCurveExpectations.Point;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedCurveExpectations.Sample;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Checks that every curve in {@code content/curves/} reached the game's datapack registry {@code realmforge:curve}
 * exactly as the content says, using expectations generated from the same YAML
 * ({@link GeneratedCurveExpectations}). This is the pattern for every datapack content type (architecture 6.5): the
 * world load decodes each generated file with the type's codec, and the test compares the decoded entries with the
 * expectations.
 */
public final class ContentCurveGameTests {
    // The pipeline and the game compute the same formula in doubles; allow for rounding only.
    private static final double TOLERANCE = 1e-9;

    private ContentCurveGameTests() {
    }

    @QaGameTest
    public static void contentCurvesAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedCurveExpectations.CURVES.isEmpty(), "no curve expectations were generated");
        Registry<Curve> curves = helper.getLevel().registryAccess().lookupOrThrow(CoreRegistries.CURVE);

        Set<String> expectedIds = new TreeSet<>();
        GeneratedCurveExpectations.CURVES.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        curves.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "curves in realmforge:curve");

        for (CurveExpectation expected : GeneratedCurveExpectations.CURVES) {
            Curve curve = curves.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(curve != null, "curve is not loaded: " + expected.id());
            helper.assertValueEqual(curve.name(), expected.nameKey(), expected.id() + " name");
            expected.nameKey().ifPresent(key -> helper.assertTrue(Language.getInstance().has(key),
                    expected.id() + ": no en_us text for " + key));

            helper.assertValueEqual(curve.points().size(), expected.points().size(), expected.id() + " point count");
            for (int i = 0; i < expected.points().size(); i++) {
                Point want = expected.points().get(i);
                Curve.Point got = curve.points().get(i);
                helper.assertValueEqual(got.level(), want.level(), expected.id() + " level of point " + i);
                helper.assertValueEqual(got.value(), want.value(), expected.id() + " value of point " + i);
            }
            for (Sample sample : expected.samples()) {
                double got = curve.valueAt(sample.level());
                helper.assertTrue(Math.abs(got - sample.value()) <= TOLERANCE * Math.max(1.0, Math.abs(sample.value())),
                        expected.id() + " at level " + sample.level() + ": expected " + sample.value() + ", got " + got);
            }
        }
        helper.succeed();
    }

    @QaGameTest
    public static void generatedCurveKeysResolve(GameTestHelper helper) {
        Registry<Curve> curves = helper.getLevel().registryAccess().lookupOrThrow(CoreRegistries.CURVE);
        helper.assertValueEqual(GeneratedCurves.ALL.size(), GeneratedCurveExpectations.CURVES.size(),
                "generated curve keys");
        for (ResourceKey<Curve> key : GeneratedCurves.ALL) {
            helper.assertTrue(curves.get(key).isPresent(), "generated key does not resolve: " + key.identifier());
        }
        helper.succeed();
    }
}
