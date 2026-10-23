package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceExpectations.SetPieceExpectation;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Checks that the game loads every set piece in {@code content/set_pieces/} as written (ADR 0050), using expectations
 * generated from the same files ({@link GeneratedSetPieceExpectations}): its template with its size, and its processor
 * list (chance blocks and swaps, {@code generate/set_pieces.py}) from the registry, which refuses a rule the game cannot
 * read. The test piece placed with its processors stands as a barrel or the chest it may become, with the skull on it
 * or not.
 */
public final class ContentSetPieceGameTests {
    private static final String TEST_PIECE = "realmforge:set_pieces/test_barrel";

    private ContentSetPieceGameTests() {
    }

    @QaGameTest
    public static void contentSetPiecesLoadAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedSetPieceExpectations.SET_PIECES.isEmpty(), "no set piece expectations were generated");
        Registry<StructureProcessorList> lists = helper.getLevel().registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
        for (SetPieceExpectation expected : GeneratedSetPieceExpectations.SET_PIECES) {
            Optional<StructureTemplate> loaded = helper.getLevel().getStructureManager()
                    .get(Identifier.parse(expected.id()));
            helper.assertTrue(loaded.isPresent(), "set piece does not load: " + expected.id());
            helper.assertValueEqual(loaded.get().getSize(), new Vec3i(expected.sizeX(), expected.sizeY(),
                    expected.sizeZ()), expected.id() + " size");
            if (!expected.processorList().isEmpty()) {
                helper.assertTrue(lists.get(Identifier.parse(expected.processorList())).isPresent(),
                        "processor list does not load: " + expected.processorList());
            }
        }
        helper.succeed();
    }

    @QaGameTest
    public static void theTestPiecePlacesWithItsProcessors(GameTestHelper helper) {
        StructureTemplate piece = helper.getLevel().getStructureManager().get(Identifier.parse(TEST_PIECE))
                .orElseThrow();
        Holder<StructureProcessorList> processors = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.PROCESSOR_LIST).get(Identifier.parse(TEST_PIECE)).orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        processors.value().list().forEach(settings::addProcessor);
        for (int x = 0; x < 5; x++) {
            BlockPos at = helper.absolutePos(new BlockPos(x, 1, 2));
            piece.placeInWorld(helper.getLevel(), at, at, settings, helper.getLevel().getRandom(), Block.UPDATE_CLIENTS);
            helper.assertTrue(Set.of(Blocks.BARREL, Blocks.CHEST).contains(helper.getBlockState(new BlockPos(x, 1, 2))
                    .getBlock()), "a barrel, or the chest it may become, at x " + x);
            helper.assertTrue(Set.of(Blocks.SKELETON_SKULL, Blocks.AIR).contains(helper.getBlockState(
                    new BlockPos(x, 2, 2)).getBlock()), "the skull on it, or nothing, at x " + x);
        }
        helper.succeed();
    }
}
