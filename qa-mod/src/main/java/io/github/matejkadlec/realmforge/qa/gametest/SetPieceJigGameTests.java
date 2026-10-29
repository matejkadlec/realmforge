package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.qa.setpiece.SetPieceJig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /qa setpiece jig} (RLM-281) builds the workshop frame for a mask north of where it runs: the walls, the glass
 * on the clearance, the floor, and a structure block in Save mode whose box is the piece's cells three high, named
 * {@code realmforge_qa:set_piece/<mask>_<token>}; saving it gives a template of that size, and no two jigs share a
 * name. The command runs through the
 * server's dispatcher as typed, from the 9×9 platform's south edge (x 4, z 8).
 */
public final class SetPieceJigGameTests {
    private static final BlockPos FEET = new BlockPos(4, 1, 8);

    private SetPieceJigGameTests() {
    }

    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void theJigBuildsTheFrameOfAWallMask(GameTestHelper helper) {
        run(helper, "qa setpiece jig wall_3x1");
        // The mask's three rows lie at z 3 (wall), 4 (the piece) and 5 (clearance), x 3 to 5; the player stands at z 8.
        for (int x = 3; x <= 5; x++) {
            for (int y = 1; y <= SetPieceJig.HEIGHT; y++) {
                helper.assertBlockPresent(Blocks.STONE_BRICKS, x, y, 3);
                helper.assertBlockPresent(Blocks.AIR, x, y, 4);
            }
            helper.assertBlockPresent(SetPieceJig.CLEARANCE, x, 1, 5);
            helper.assertBlockPresent(SetPieceJig.FLOOR, x, 0, 4);
        }
        StructureBlockEntity block = structureBlock(helper, new BlockPos(2, 0, 6));
        helper.assertValueEqual(block.getStructurePos(), new BlockPos(1, 1, -2), "the box's offset from the block");
        helper.assertValueEqual(block.getStructureSize(), new Vec3i(3, SetPieceJig.HEIGHT, 1), "the box's size");
        helper.assertTrue(block.getStructureName().startsWith("realmforge_qa:set_piece/wall_3x1_"),
                "the name the save takes: " + block.getStructureName());
        helper.assertFalse(block.isIgnoreEntities(), "a piece keeps its item frames and armor stands");
        helper.assertTrue(block.saveStructure(false), "the structure block saves");
        StructureTemplate saved = helper.getLevel().getStructureManager()
                .get(Identifier.parse(block.getStructureName())).orElseThrow();
        helper.assertValueEqual(saved.getSize(), new Vec3i(3, SetPieceJig.HEIGHT, 1), "the saved template's size");
        helper.succeed();
    }

    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void twoJigsForOneMaskSaveUnderDifferentNames(GameTestHelper helper) {
        // RLM-390: neither jig is saved, so the structure manager knows neither name; the second must not take the
        // first one's, or its Save would overwrite that build.
        run(helper, "qa setpiece jig wall_3x1");
        String first = structureBlock(helper, new BlockPos(2, 0, 6)).getStructureName();
        run(helper, "qa setpiece jig wall_3x1");
        String second = structureBlock(helper, new BlockPos(2, 0, 6)).getStructureName();
        String pattern = "realmforge_qa:set_piece/wall_3x1_[a-z0-9]{" + SetPieceJig.TOKEN_LENGTH + "}";
        helper.assertTrue(first.matches(pattern), "a name the import reads: " + first);
        helper.assertTrue(second.matches(pattern), "a name the import reads: " + second);
        helper.assertFalse(first.equals(second), "two jigs of one mask share the name " + first);
        helper.succeed();
    }

    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void aPieceWithItsOwnWallsGetsAFlatWallBehindIt(GameTestHelper helper) {
        run(helper, "qa setpiece jig built_niche_3x1 minecraft:deepslate_tiles");
        // Five wide (x 2 to 6): the flat wall at z 2, the piece's own cells at z 3 and 4, its clearance at z 5.
        for (int x = 2; x <= 6; x++) {
            helper.assertBlockPresent(Blocks.DEEPSLATE_TILES, x, 1, 2);
            helper.assertBlockPresent(Blocks.AIR, x, 1, 3);
            helper.assertBlockPresent(Blocks.AIR, x, 1, 4);
            helper.assertBlockPresent(SetPieceJig.CLEARANCE, x, 1, 5);
        }
        StructureBlockEntity block = structureBlock(helper, new BlockPos(1, 0, 6));
        helper.assertValueEqual(block.getStructurePos(), new BlockPos(1, 1, -3), "the box covers the piece's walls");
        helper.assertValueEqual(block.getStructureSize(), new Vec3i(5, SetPieceJig.HEIGHT, 2), "the box's size");
        helper.succeed();
    }

    private static StructureBlockEntity structureBlock(GameTestHelper helper, BlockPos pos) {
        helper.assertBlockPresent(Blocks.STRUCTURE_BLOCK, pos);
        StructureBlockEntity block = helper.getBlockEntity(pos, StructureBlockEntity.class);
        helper.assertValueEqual(block.getMode(), StructureMode.SAVE, "the structure block's mode");
        return block;
    }

    private static void run(GameTestHelper helper, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPosition(Vec3.atBottomCenterOf(helper.absolutePos(FEET)))
                .withPermission(PermissionSet.ALL_PERMISSIONS);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        helper.assertBlockPresent(Blocks.AIR, FEET);
        helper.assertBlockPresent(SetPieceJig.FLOOR, FEET.below());
    }
}
