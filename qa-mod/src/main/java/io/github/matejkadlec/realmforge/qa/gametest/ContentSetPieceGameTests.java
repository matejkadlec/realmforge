package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.plan.SetPieces;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceExpectations.SetPieceExpectation;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Checks that the game loads every set piece in {@code content/set_pieces/} as written (ADR 0050), using expectations
 * generated from the same files ({@link GeneratedSetPieceExpectations}): its template with its size, and, in every
 * dungeon type that places it, the same size and as many blocks that vary (chance blocks and swaps,
 * {@code generate/set_pieces.py}, ADR 0077). {@code DungeonSetPieceGameTests} builds a dungeon with them.
 */
public final class ContentSetPieceGameTests {
    private static final String TEST_PIECE = "realmforge:set_pieces/test_barrel";

    private ContentSetPieceGameTests() {
    }

    @QaGameTest
    public static void contentSetPiecesLoadAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedSetPieceExpectations.SET_PIECES.isEmpty(), "no set piece expectations were generated");
        boolean testPiecePlaced = false;
        for (SetPieceExpectation expected : GeneratedSetPieceExpectations.SET_PIECES) {
            Optional<StructureTemplate> loaded = helper.getLevel().getStructureManager()
                    .get(Identifier.parse(expected.id()));
            helper.assertTrue(loaded.isPresent(), "set piece does not load: " + expected.id());
            Vec3i size = new Vec3i(expected.sizeX(), expected.sizeY(), expected.sizeZ());
            helper.assertValueEqual(loaded.get().getSize(), size, expected.id() + " size");
            for (DungeonType type : helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)) {
                for (SetPieces.Piece piece : type.setPieces().pieces().values()) {
                    if (piece.template().toString().equals(expected.id())) {
                        helper.assertValueEqual(piece.size(), size, expected.id() + " size in a dungeon type");
                        helper.assertValueEqual(piece.variants().size(), expected.variants(),
                                expected.id() + " blocks that vary");
                        testPiecePlaced |= expected.id().equals(TEST_PIECE);
                    }
                }
            }
        }
        helper.assertTrue(testPiecePlaced, "no dungeon type places the test piece " + TEST_PIECE);
        helper.succeed();
    }
}
