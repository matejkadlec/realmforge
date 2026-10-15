package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.dungeon.instance.DataMarker;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRoomExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRoomExpectations.RoomExpectation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Checks that the game loads every room in {@code content/rooms/} as written, using expectations generated from
 * the same templates ({@link GeneratedRoomExpectations}). The structure manager upgrades an imported room from the
 * data version it was saved with (Minecraft 1.17 for the oldest), so this is where a template the game cannot read
 * shows: it does not load at all (the manager swallows the error), or a block it does not know becomes air. Every
 * structure block in a room must be a data marker the builder knows (content validation allows the same names).
 */
public final class ContentRoomGameTests {
    private ContentRoomGameTests() {
    }

    @QaGameTest
    public static void contentRoomsLoadAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedRoomExpectations.ROOMS.isEmpty(), "no room expectations were generated");
        StructureTemplateManager templates = helper.getLevel().getStructureManager();
        for (RoomExpectation expected : GeneratedRoomExpectations.ROOMS) {
            Optional<StructureTemplate> loaded = templates.get(Identifier.parse(expected.id()));
            helper.assertTrue(loaded.isPresent(), "room does not load: " + expected.id());
            StructureTemplate room = loaded.get();
            helper.assertValueEqual(room.getSize(), new Vec3i(expected.sizeX(), expected.sizeY(), expected.sizeZ()),
                    expected.id() + " size");
            helper.assertValueEqual(room.getJigsaws(BlockPos.ZERO, Rotation.NONE).size(), expected.jigsaws(),
                    expected.id() + " jigsaws");
            helper.assertValueEqual(room.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR).size(),
                    expected.air(), expected.id() + " air blocks (a block the game cannot read becomes air)");
            for (StructureTemplate.StructureBlockInfo marker
                    : room.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.STRUCTURE_BLOCK)) {
                CompoundTag data = marker.nbt();
                String name = data == null ? "" : data.getStringOr("metadata", "");
                helper.assertTrue(data != null && data.read("mode", StructureMode.LEGACY_CODEC)
                                .orElse(null) == StructureMode.DATA && DataMarker.Kind.byName(name).isPresent(),
                        expected.id() + ": the structure block at " + marker.pos().toShortString()
                                + " is no data marker the builder knows ('" + name + "')");
            }
        }
        helper.succeed();
    }
}
