package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.plan.Connector;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.LayoutRules;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedDungeonTypeExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedDungeonTypeExpectations.ConnectorExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedDungeonTypeExpectations.DungeonTypeExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedDungeonTypeExpectations.RoomExpectation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Checks that every entry of {@code content/dungeons/} reached the server's datapack registry
 * {@code realmforge:dungeon_type} as the content says, with its pool's rooms resolved
 * ({@link GeneratedDungeonTypeExpectations}, architecture 6.5), and that each room's connectors are the jigsaw blocks
 * the game reads from its template, in every rotation (architecture 15.3): the planner turns them with the same math
 * the builder places the template with.
 */
public final class ContentDungeonTypeGameTests {
    private ContentDungeonTypeGameTests() {
    }

    @QaGameTest
    public static void contentDungeonTypesAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedDungeonTypeExpectations.DUNGEON_TYPES.isEmpty(),
                "no dungeon type expectations were generated");
        Registry<DungeonType> registry = registry(helper);
        Set<String> expectedIds = GeneratedDungeonTypeExpectations.DUNGEON_TYPES.stream()
                .map(DungeonTypeExpectation::id)
                .collect(Collectors.toCollection(TreeSet::new));
        helper.assertValueEqual(ours(registry), expectedIds, "entries in realmforge:dungeon_type");

        for (DungeonTypeExpectation expected : GeneratedDungeonTypeExpectations.DUNGEON_TYPES) {
            DungeonType type = registry.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(type != null, "dungeon type is not loaded: " + expected.id());
            LayoutRules layout = type.layout();
            helper.assertValueEqual(type.name(), expected.name(), expected.id() + " name");
            helper.assertValueEqual(type.pool().toString(), expected.pool(), expected.id() + " pool");
            helper.assertValueEqual(List.of(layout.mainPath().minInclusive(), layout.mainPath().maxInclusive(),
                    layout.branches().minInclusive(), layout.branches().maxInclusive(),
                    layout.branchRooms().minInclusive(), layout.branchRooms().maxInclusive()),
                    List.of(expected.mainPathMin(), expected.mainPathMax(), expected.branchesMin(),
                            expected.branchesMax(), expected.branchRoomsMin(), expected.branchRoomsMax()),
                    expected.id() + " layout ranges");
            helper.assertValueEqual(layout.ending().getSerializedName(), expected.ending(), expected.id() + " ending");
            helper.assertValueEqual(type.rooms().stream().map(ContentDungeonTypeGameTests::asWritten).toList(),
                    expected.rooms(), expected.id() + " rooms");
        }
        helper.succeed();
    }

    @QaGameTest
    public static void roomConnectorsMatchTheirTemplates(GameTestHelper helper) {
        Registry<DungeonType> registry = registry(helper);
        int checked = 0;
        for (Identifier id : ours(registry).stream().map(Identifier::parse).toList()) {
            for (DungeonRoom room : registry.getValue(id).rooms()) {
                Optional<StructureTemplate> loaded = helper.getLevel().getStructureManager().get(room.template());
                helper.assertTrue(loaded.isPresent(), "room does not load: " + room.template());
                StructureTemplate template = loaded.get();
                helper.assertValueEqual(template.getSize(), room.size(), room.template() + " size");
                for (Rotation rotation : Rotation.values()) {
                    String where = room.template() + " turned " + rotation.getSerializedName();
                    Set<String> game = template.getJigsaws(BlockPos.ZERO, rotation).stream()
                            .map(ContentDungeonTypeGameTests::describe)
                            .collect(Collectors.toCollection(TreeSet::new));
                    Set<String> planned = room.connectors().stream()
                            .map(connector -> connector.placed(BlockPos.ZERO, rotation))
                            .map(ContentDungeonTypeGameTests::describe)
                            .collect(Collectors.toCollection(TreeSet::new));
                    helper.assertValueEqual(planned, game, where + ": connectors");
                    helper.assertValueEqual(room.box(BlockPos.ZERO, rotation),
                            template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), BlockPos.ZERO),
                            where + ": box");
                }
                checked++;
            }
        }
        helper.assertTrue(checked > 0, "no dungeon type has rooms");
        helper.succeed();
    }

    private static Registry<DungeonType> registry(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE);
    }

    private static Set<String> ours(Registry<DungeonType> registry) {
        return registry.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .map(Identifier::toString)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static RoomExpectation asWritten(DungeonRoom room) {
        List<ConnectorExpectation> connectors = room.connectors().stream()
                .map(connector -> new ConnectorExpectation(connector.pos().getX(), connector.pos().getY(),
                        connector.pos().getZ(), connector.front().getSerializedName(),
                        connector.top().getSerializedName(), connector.name().toString(),
                        connector.target().toString(), connector.joint().getSerializedName()))
                .toList();
        return new RoomExpectation(room.template().toString(), room.size().getX(), room.size().getY(),
                room.size().getZ(), room.role().getSerializedName(), room.weight(), connectors);
    }

    private static String describe(StructureTemplate.JigsawBlockInfo jigsaw) {
        return describe(jigsaw.info().pos(), JigsawBlock.getFrontFacing(jigsaw.info().state()).getSerializedName(),
                JigsawBlock.getTopFacing(jigsaw.info().state()).getSerializedName(), jigsaw.name(), jigsaw.target(),
                jigsaw.jointType().getSerializedName());
    }

    private static String describe(Connector connector) {
        return describe(connector.pos(), connector.front().getSerializedName(), connector.top().getSerializedName(),
                connector.name(), connector.target(), connector.joint().getSerializedName());
    }

    private static String describe(BlockPos pos, String front, String top, Identifier name, Identifier target,
            String joint) {
        return pos.toShortString() + " " + front + "/" + top + " " + name + " -> " + target + " " + joint;
    }
}
