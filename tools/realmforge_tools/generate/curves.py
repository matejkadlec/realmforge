"""Curves: ``ResourceKey`` constants for Java and the QA mod's expectations (the data itself: datapack.py)."""

from __future__ import annotations

from itertools import pairwise
from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import translation_key
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.curve import CurveDef

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
REGISTRY = "curve"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    return {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedCurves"): _keys(content),
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedCurveExpectations"): (
            _expectations(content)
        ),
    }


def sample_levels(curve: CurveDef) -> list[float]:
    """Where the GameTest compares the game's interpolation with ``CurveDef.value_at``: below the first point,
    at every point, halfway between neighbours, and above the last point."""
    levels = [point.level for point in curve.points]
    samples = [levels[0] - 1.0]
    for low, high in pairwise(levels):
        samples += [float(low), (low + high) / 2]
    samples += [float(levels[-1]), levels[-1] + 1.0]
    return samples


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    return repr(float(value))


def _keys(content: Content) -> bytes:
    constants = [constant_name(entry.model.id) for entry in content.curves]
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{entry.model.id}}} from {{@code {entry.source}}}. */\n"
        f"    public static final ResourceKey<Curve> {constant} = key({string_literal(entry.model.id)});\n"
        "\n"
        for entry, constant in zip(content.curves, constants, strict=True)
    )
    source = (
        header(Path(__file__), "content/curves/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        f"import {JAVA_PACKAGE}.core.CoreRegistries;\n"
        f"import {JAVA_PACKAGE}.core.Curve;\n"
        "import java.util.List;\n"
        "import net.minecraft.resources.ResourceKey;\n"
        "\n"
        "/** Keys of the curves in {@code content/curves/}, so Java names a curve without typing its id. */\n"
        "public final class GeneratedCurves {\n" + fields + "    /** Every key above, in id order. */\n"
        f"    public static final List<ResourceKey<Curve>> ALL = List.of({', '.join(constants)});\n"
        "\n"
        "    private GeneratedCurves() {\n"
        "    }\n"
        "\n"
        "    private static ResourceKey<Curve> key(String id) {\n"
        "        return ResourceKey.create(CoreRegistries.CURVE, Realmforge.id(id));\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _expectations(content: Content) -> bytes:
    rows = []
    for entry in content.curves:
        curve = entry.model
        name_key = (
            f"Optional.of({string_literal(translation_key(REGISTRY, curve.id, 'name'))})"
            if curve.name is not None
            else "Optional.empty()"
        )
        points = ", ".join(f"new Point({p.level}, {_double(p.value)})" for p in curve.points)
        samples = ", ".join(
            f"new Sample({_double(level)}, {_double(curve.value_at(level))})"
            for level in sample_levels(curve)
        )
        rows.append(
            "            new CurveExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{curve.id}')},\n"
            f"                    {name_key},\n"
            f"                    List.of({points}),\n"
            f"                    List.of({samples}))"
        )
    curves_list = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"

    source = (
        header(Path(__file__), "content/curves/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/curves/} says about every curve, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedCurveExpectations {\n"
        "    /** A point as written in the YAML. */\n"
        "    public record Point(int level, double value) {\n"
        "    }\n"
        "\n"
        "    /** The value the pipeline's interpolation gives at a level, which the game must match. */\n"
        "    public record Sample(double level, double value) {\n"
        "    }\n"
        "\n"
        "    /** Expected state of one curve. Ids are full resource locations, e.g. {@code realmforge:test_curve}. */\n"
        "    public record CurveExpectation(String id, Optional<String> nameKey, List<Point> points, "
        "List<Sample> samples) {\n"
        "    }\n"
        "\n"
        f"    public static final List<CurveExpectation> CURVES = {curves_list};\n"
        "\n"
        "    private GeneratedCurveExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
