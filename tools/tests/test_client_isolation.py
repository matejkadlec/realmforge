from __future__ import annotations

from pathlib import Path

from realmforge_tools.client_isolation import (
    SourceRoot,
    banned_references,
    find_violations,
    source_roots,
    strip_comments_and_literals,
)
from realmforge_tools.project import RepoPaths
from tests.conftest import write

CLIENT_PACKAGE = "io.github.matejkadlec.realmforge.client"
PACKAGE_DIR = "io/github/matejkadlec/realmforge"


def _root(tmp_path: Path) -> SourceRoot:
    return SourceRoot(tmp_path / "java", CLIENT_PACKAGE)


def test_mod_sources_keep_client_code_under_client() -> None:
    paths = RepoPaths()
    violations = find_violations(source_roots(paths))
    assert not violations, "\n".join(v.describe(paths) for v in violations)


def test_the_check_scans_the_core_mod_and_the_qa_mod() -> None:
    roots = {RepoPaths().rel(root.path): root.client_package for root in source_roots(RepoPaths())}
    assert roots["mod/src/main/java"] == CLIENT_PACKAGE
    assert roots["mod/src/generated/java"] == CLIENT_PACKAGE
    assert roots["qa-mod/src/main/java"] == "io.github.matejkadlec.realmforge.qa.client"


def test_a_client_import_outside_client_fails_the_check(tmp_path: Path) -> None:
    source = write(
        tmp_path / "java" / PACKAGE_DIR / "spell" / "SpellFeature.java",
        "package io.github.matejkadlec.realmforge.spell;\n\nimport net.minecraft.client.Minecraft;\n",
    )
    [violation] = find_violations([_root(tmp_path)])
    assert (violation.path, violation.line, violation.reference) == (source, 3, "net.minecraft.client")


def test_every_banned_package_is_caught_in_any_form() -> None:
    source = """
import static net.minecraft.client.Minecraft.getInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
class A { Object m() { return net.minecraft.client.Minecraft.getInstance(); } }
"""
    assert banned_references(source) == [
        (2, "net.minecraft.client"),
        (3, "com.mojang.blaze3d"),
        (4, "net.neoforged.neoforge.client"),
        (5, "dev.kosmx.playerAnim"),
        (6, "net.minecraft.client"),
    ]


def test_code_under_client_may_use_client_classes(tmp_path: Path) -> None:
    write(
        tmp_path / "java" / PACKAGE_DIR / "client" / "hud" / "CastBar.java",
        "package io.github.matejkadlec.realmforge.client.hud;\nimport net.minecraft.client.Minecraft;\n",
    )
    assert find_violations([_root(tmp_path)]) == []


def test_a_nested_client_package_is_not_exempt(tmp_path: Path) -> None:
    write(
        tmp_path / "java" / PACKAGE_DIR / "spell" / "client" / "Icons.java",
        "package io.github.matejkadlec.realmforge.spell.client;\nimport net.minecraft.client.Minecraft;\n",
    )
    assert len(find_violations([_root(tmp_path)])) == 1


def test_comments_and_literals_are_not_code() -> None:
    source = """
// import net.minecraft.client.Minecraft;
/* net.minecraft.client.Minecraft
   com.mojang.blaze3d */
/** {@link net.neoforged.neoforge.client.event.RenderGuiEvent} */
String a = "net.minecraft.client.Minecraft";
String b = \"\"\"
    com.mojang.blaze3d.systems.RenderSystem
    \"\"\";
char c = '"'; String d = "it's \\" net.minecraft.client";
"""
    assert banned_references(source) == []


def test_similar_names_are_not_banned() -> None:
    source = "import net.minecraft.clientbound.Foo;\nimport my.net.minecraft.client.Bar;\nimport net.minecraft.world.Clients;\n"
    assert banned_references(source) == []


def test_stripping_keeps_line_numbers() -> None:
    source = 'a /* x\ny */ b\n"c\nd'
    stripped = strip_comments_and_literals(source)
    assert stripped.count("\n") == source.count("\n")
    assert stripped.splitlines()[1].strip() == "b"
